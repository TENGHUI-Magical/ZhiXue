"""轻量 RAG 向量索引：numpy 优先，缺 numpy/模型时退回纯 Python 字符 n-gram。

设计口径（docs/05 §4）：
- 切片 300—500 字、重叠 50，chunkId = kpId#序号；
- 混合分 = 0.7 * cosine + 0.3 * char_bigram_jaccard；
- topK 默认 5，阈值 0.35；无命中返回空列表，由 Java 侧映射 60003；
- 不引入 Chroma/Milvus；200 条量级直接矩阵点积。
"""
from __future__ import annotations

import hashlib
import json
import logging
import math
import re
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from app.config import settings

logger = logging.getLogger(__name__)

try:  # CI 的 requirements-dev 不装 numpy，必须允许无 numpy 运行
    import numpy as _np
except Exception:  # pragma: no cover - 环境分支
    _np = None

_MODEL = None
_MODEL_LOCK = threading.Lock()
_INDEX: "VectorIndex | None" = None
_INDEX_LOCK = threading.Lock()


@dataclass(frozen=True)
class Chunk:
    chunk_id: str
    kp_id: str
    kp_name: str
    course_id: int | None
    chapter_id: int | None
    ref: str
    content: str


@dataclass(frozen=True)
class Hit:
    chunk: Chunk
    score: float
    vector_score: float
    keyword_score: float


def _clean(text: str) -> str:
    return re.sub(r"\s+", " ", (text or "")).strip()


def chunk_text(text: str, kp_id: str, size: int | None = None,
               overlap: int | None = None) -> list[Chunk]:
    """按段落/句子切分，目标 size 字、重叠 overlap 字；不足一段则保留一段。"""
    size = size or settings.rag_chunk_size
    overlap = overlap if overlap is not None else settings.rag_chunk_overlap
    clean = _clean(text)
    if not clean:
        return []
    if len(clean) <= size:
        return [Chunk(f"{kp_id}#1", kp_id, "", None, None, "", clean)]

    # 先按句子边界切，再拼到目标长度；句子过长时硬切
    pieces = [p.strip() for p in re.split(r"(?<=[。！？!?；;])", clean) if p.strip()]
    if not pieces:
        pieces = [clean]
    chunks: list[str] = []
    buf = ""
    for piece in pieces:
        if len(piece) > size:
            if buf:
                chunks.append(buf)
                buf = ""
            start = 0
            while start < len(piece):
                chunks.append(piece[start:start + size])
                start += max(1, size - overlap)
            continue
        if len(buf) + len(piece) <= size:
            buf += piece
        else:
            if buf:
                chunks.append(buf)
            buf = piece
    if buf:
        chunks.append(buf)

    # 简单重叠：把上一段尾部拼到下一段头部，保证召回连续性
    if overlap > 0 and len(chunks) > 1:
        overlapped = [chunks[0]]
        for prev, cur in zip(chunks, chunks[1:]):
            tail = prev[-overlap:]
            overlapped.append((tail + cur)[:size])
        chunks = overlapped
    return [Chunk(f"{kp_id}#{i + 1}", kp_id, "", None, None, "", content)
            for i, content in enumerate(chunks)]


def _bigrams(text: str) -> set[str]:
    clean = _clean(text)
    if len(clean) < 2:
        return {clean} if clean else set()
    return {clean[i:i + 2] for i in range(len(clean) - 1)}


def char_bigram_jaccard(a: str, b: str) -> float:
    """中文无需分词的字符 bigram Jaccard，返回 0—1。"""
    left, right = _bigrams(a), _bigrams(b)
    if not left or not right:
        return 0.0
    return len(left & right) / len(left | right)


def _hash_vector(text: str, dim: int = 256) -> list[float]:
    """确定性字符 n-gram 向量：同一文本永远同一向量，L2 归一化。"""
    vector = [0.0] * dim
    tokens = _bigrams(text)
    if not tokens:
        return vector
    for token in tokens:
        digest = hashlib.blake2b(token.encode("utf-8"), digest_size=8).digest()
        index = int.from_bytes(digest[:4], "big") % dim
        sign = 1.0 if digest[4] & 1 else -1.0
        vector[index] += sign
    norm = math.sqrt(sum(v * v for v in vector))
    if norm == 0:
        return vector
    return [v / norm for v in vector]


def _load_model():
    """懒加载 bge；本地没有模型时直接回退，不联网下载、不阻塞启动。"""
    global _MODEL
    if _MODEL is not None:
        return _MODEL
    with _MODEL_LOCK:
        if _MODEL is not None:
            return _MODEL
        try:
            from sentence_transformers import SentenceTransformer
            _MODEL = SentenceTransformer(settings.rag_embed_model, local_files_only=True)
        except Exception as exc:  # 模型未下载/依赖缺失
            logger.info("bge 模型不可用，使用字符 n-gram 向量：%s", exc)
            _MODEL = False
    return _MODEL


def embed_texts(texts: Iterable[str]) -> tuple[list[list[float]], str]:
    """返回 (vectors, mode)；mode=bge 或 hash-bigram。"""
    items = [_clean(t) for t in texts]
    model = _load_model()
    if model:
        try:
            vectors = model.encode(items, normalize_embeddings=True).tolist()
            return vectors, "bge"
        except Exception as exc:
            logger.warning("bge 编码失败，回退 hash-bigram：%s", exc)
    return [_hash_vector(t) for t in items], "hash-bigram"


def _dot(a: list[float], b: list[float]) -> float:
    if _np is not None:
        return float(_np.dot(_np.asarray(a, dtype=_np.float32),
                             _np.asarray(b, dtype=_np.float32)))
    return sum(x * y for x, y in zip(a, b))


class VectorIndex:
    def __init__(self) -> None:
        self._chunks: dict[str, Chunk] = {}
        self._vectors: dict[str, list[float]] = {}
        self.mode = "hash-bigram"

    @property
    def size(self) -> int:
        return len(self._chunks)

    def add(self, chunks: list[Chunk], vectors: list[list[float]], mode: str) -> int:
        if len(chunks) != len(vectors):
            raise ValueError("chunks 与 vectors 数量不一致")
        for chunk, vector in zip(chunks, vectors):
            self._chunks[chunk.chunk_id] = chunk
            self._vectors[chunk.chunk_id] = vector
        self.mode = mode
        return len(chunks)

    def search(self, query: str, top_k: int | None = None,
               threshold: float | None = None, kp_id: str | None = None,
               course_id: int | None = None) -> list[Hit]:
        top_k = top_k or settings.rag_top_k
        threshold = settings.rag_score_threshold if threshold is None else threshold
        # 无 bge 时字符 n-gram 分数量纲明显更低；用 0.05 兜底阈值，保证离线可用。
        effective_threshold = min(threshold, 0.05) if self.mode == "hash-bigram" else threshold
        if not self._chunks or not _clean(query):
            return []
        query_vector, _ = embed_texts([query])
        qv = query_vector[0]
        hits: list[Hit] = []
        for chunk_id, chunk in self._chunks.items():
            if kp_id and chunk.kp_id != kp_id:
                continue
            if course_id is not None and chunk.course_id not in (None, course_id):
                continue
            vec_score = max(0.0, _dot(qv, self._vectors[chunk_id]))
            kw_score = char_bigram_jaccard(query, chunk.content)
            final = settings.rag_hybrid_vector * vec_score + (1 - settings.rag_hybrid_vector) * kw_score
            if final >= effective_threshold:
                hits.append(Hit(chunk, round(final, 4), round(vec_score, 4), round(kw_score, 4)))
        hits.sort(key=lambda h: h.score, reverse=True)
        return hits[:top_k]

    def save(self, directory: str | Path | None = None) -> Path:
        path = Path(directory or settings.vector_index_dir).resolve()
        path.mkdir(parents=True, exist_ok=True)
        chunks = list(self._chunks.values())
        (path / "chunks.json").write_text(
            json.dumps([chunk.__dict__ for chunk in chunks], ensure_ascii=False),
            encoding="utf-8",
        )
        vectors = [self._vectors[chunk.chunk_id] for chunk in chunks]
        if _np is not None:
            _np.save(path / "vectors.npy", _np.asarray(vectors, dtype=_np.float32))
        else:
            (path / "vectors.json").write_text(json.dumps(vectors), encoding="utf-8")
        (path / "meta.json").write_text(json.dumps({"mode": self.mode}), encoding="utf-8")
        return path

    @classmethod
    def load(cls, directory: str | Path | None = None) -> "VectorIndex":
        path = Path(directory or settings.vector_index_dir).resolve()
        index = cls()
        chunks_file = path / "chunks.json"
        if not chunks_file.exists():
            return index
        try:
            raw_chunks = json.loads(chunks_file.read_text(encoding="utf-8"))
            chunks = [Chunk(**item) for item in raw_chunks]
            if _np is not None and (path / "vectors.npy").exists():
                vectors = _np.load(path / "vectors.npy", allow_pickle=False).tolist()
            elif (path / "vectors.json").exists():
                vectors = json.loads((path / "vectors.json").read_text(encoding="utf-8"))
            else:
                return index
            meta_file = path / "meta.json"
            mode = "hash-bigram"
            if meta_file.exists():
                mode = json.loads(meta_file.read_text(encoding="utf-8")).get("mode", mode)
            index.add(chunks, vectors, mode)
        except Exception as exc:
            logger.warning("向量索引加载失败，按空索引处理：%s", exc)
        return index


def get_index() -> VectorIndex:
    global _INDEX
    if _INDEX is None:
        with _INDEX_LOCK:
            if _INDEX is None:
                _INDEX = VectorIndex.load()
    return _INDEX


def reset_index() -> None:
    """测试/重建用：清空进程内索引。"""
    global _INDEX
    with _INDEX_LOCK:
        _INDEX = None


def embed_document(kp_id: str, text: str, kp_name: str = "", course_id: int | None = None,
                   chapter_id: int | None = None, ref: str = "") -> tuple[int, str]:
    chunks = chunk_text(text, kp_id)
    if not chunks:
        return 0, "empty"
    enriched = [Chunk(c.chunk_id, c.kp_id, kp_name, course_id, chapter_id, ref, c.content)
                for c in chunks]
    vectors, mode = embed_texts([c.content for c in enriched])
    index = get_index()
    index.add(enriched, vectors, mode)
    index.save()
    return len(enriched), mode


def retrieve(query: str, top_k: int | None = None, threshold: float | None = None,
             kp_id: str | None = None, course_id: int | None = None) -> list[Hit]:
    return get_index().search(query, top_k=top_k, threshold=threshold,
                              kp_id=kp_id, course_id=course_id)
