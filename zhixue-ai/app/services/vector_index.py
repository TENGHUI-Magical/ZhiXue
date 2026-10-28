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
import os
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

# 无 bge 时字符 n-gram 分数量纲远低于余弦；用比例+下限换算，而不是把调用方阈值直接压成 0.05。
_HASH_THRESHOLD_RATIO = 0.25
_HASH_THRESHOLD_FLOOR = 0.08

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
    """按段落/句子切分，目标 size 字、重叠 overlap 字；不足一段则保留一段。

    overlap 必须小于 size；配置写反时收敛到 size/10，避免步长退化为 1 造成切片爆炸。
    """
    size = int(size or settings.rag_chunk_size)
    overlap = int(overlap if overlap is not None else settings.rag_chunk_overlap)
    if size <= 0:
        size = settings.rag_chunk_size
    if overlap < 0:
        overlap = 0
    if overlap >= size:
        logger.warning("chunk overlap=%s 不小于 size=%s，已收敛为 size/10", overlap, size)
        overlap = max(0, size // 10)
    clean = _clean(text)
    if not clean:
        return []
    if len(clean) <= size:
        return [Chunk(f"{kp_id}#1", kp_id, "", None, None, "", clean)]

    # 先按句子边界切，再拼到目标长度；句子过长时硬切（不在这里做重叠，统一在末尾处理）
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
                start += size
            continue
        if len(buf) + len(piece) <= size:
            buf += piece
        else:
            if buf:
                chunks.append(buf)
            buf = piece
    if buf:
        chunks.append(buf)

    # 简单重叠：把上一段尾部拼到下一段头部；不截断当前段，否则每段尾部会丢字
    if overlap > 0 and len(chunks) > 1:
        overlapped = [chunks[0]]
        for prev, cur in zip(chunks, chunks[1:]):
            overlapped.append(prev[-overlap:] + cur)
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


def _bge_model_present() -> bool:
    """不导入 sentence_transformers 也能判断模型是否在本机：避免离线环境首次请求卡 10s。"""
    model = (settings.rag_embed_model or "").strip()
    if not model:
        return False
    if Path(model).expanduser().exists():
        return True
    hub_home = Path(os.environ.get("HF_HOME", str(Path.home() / ".cache" / "huggingface")))
    return (hub_home / "hub" / ("models--" + model.replace("/", "--"))).exists()


def _load_model():
    """懒加载 bge；本地没有模型时直接回退，不联网下载、不阻塞启动。"""
    global _MODEL
    if _MODEL is not None:
        return _MODEL
    with _MODEL_LOCK:
        if _MODEL is not None:
            return _MODEL
        if not _bge_model_present():
            logger.info("bge 模型不在本机，使用字符 n-gram 向量：%s", settings.rag_embed_model)
            _MODEL = False
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
        self._dim: int | None = None
        self._lock = threading.RLock()
        self.mode = "hash-bigram"

    @property
    def size(self) -> int:
        with self._lock:
            return len(self._chunks)

    def add(self, chunks: list[Chunk], vectors: list[list[float]], mode: str) -> int:
        if len(chunks) != len(vectors):
            raise ValueError("chunks 与 vectors 数量不一致")
        with self._lock:
            # 先做完整校验，再改内存，避免校验失败时留下半套索引
            if self._chunks and mode != self.mode:
                raise ValueError(f"索引模式不一致：已有 {self.mode}，本次 {mode}；请清空索引后重建")
            target_dim = self._dim
            for vector in vectors:
                if target_dim is None:
                    target_dim = len(vector)
                elif len(vector) != target_dim:
                    raise ValueError(
                        f"向量维度不一致：索引 {target_dim}，本次 {len(vector)}；请清空索引后重建")
            # 同一知识点重复 embed 时先删旧切片，避免旧正文继续被检索/引用
            kp_ids = {chunk.kp_id for chunk in chunks}
            for chunk_id, existing in list(self._chunks.items()):
                if existing.kp_id in kp_ids:
                    self._chunks.pop(chunk_id, None)
                    self._vectors.pop(chunk_id, None)
            for chunk, vector in zip(chunks, vectors):
                self._chunks[chunk.chunk_id] = chunk
                self._vectors[chunk.chunk_id] = vector
            self._dim = target_dim
            self.mode = mode
            return len(chunks)

    def _effective_threshold(self, threshold: float) -> float:
        if self.mode != "hash-bigram":
            return threshold
        if threshold <= 0:
            return 0.0
        # 保留调用方阈值的相对高低，同时承认字面分数量纲更低
        return max(_HASH_THRESHOLD_FLOOR, threshold * _HASH_THRESHOLD_RATIO)

    def search(self, query: str, top_k: int | None = None,
               threshold: float | None = None, kp_id: str | None = None,
               course_id: int | None = None) -> list[Hit]:
        top_k = top_k or settings.rag_top_k
        threshold = settings.rag_score_threshold if threshold is None else threshold
        with self._lock:
            if not self._chunks:
                return []
            effective_threshold = self._effective_threshold(threshold)
            snapshot = list(self._chunks.items())
            dim = self._dim
        if not _clean(query):
            return []
        query_vector, _ = embed_texts([query])
        qv = query_vector[0]
        if dim is not None and len(qv) != dim:
            logger.warning("查询向量维度 %s 与索引 %s 不一致，返回空结果", len(qv), dim)
            return []
        hits: list[Hit] = []
        with self._lock:
            for chunk_id, chunk in snapshot:
                if kp_id and chunk.kp_id != kp_id:
                    continue
                if course_id is not None and chunk.course_id != course_id:
                    continue
                vector = self._vectors.get(chunk_id)
                if vector is None:  # 已被并发 add 删除
                    continue
                vec_score = max(0.0, _dot(qv, vector))
                kw_score = char_bigram_jaccard(query, chunk.content)
                final = settings.rag_hybrid_vector * vec_score + (1 - settings.rag_hybrid_vector) * kw_score
                if final >= effective_threshold:
                    hits.append(Hit(chunk, round(final, 4), round(vec_score, 4), round(kw_score, 4)))
        hits.sort(key=lambda h: h.score, reverse=True)
        return hits[:top_k]

    def save(self, directory: str | Path | None = None) -> Path:
        path = Path(directory or settings.vector_index_dir).resolve()
        path.mkdir(parents=True, exist_ok=True)
        with self._lock:
            chunks = list(self._chunks.values())
            vectors = [self._vectors[chunk.chunk_id] for chunk in chunks]
            mode = self.mode
        # 三个文件写临时文件后原子替换，避免崩溃/并发时数量错位
        _atomic_write_text(path / "chunks.json",
                           json.dumps([chunk.__dict__ for chunk in chunks], ensure_ascii=False))
        if _np is not None:
            # numpy 会给非 .npy 后缀自动追加 .npy，临时文件名必须自带后缀
            tmp = path / "vectors.tmp.npy"
            _np.save(tmp, _np.asarray(vectors, dtype=_np.float32))
            os.replace(tmp, path / "vectors.npy")
        else:
            _atomic_write_text(path / "vectors.json", json.dumps(vectors))
        _atomic_write_text(path / "meta.json", json.dumps({"mode": mode}))
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
            if len(chunks) != len(vectors):
                raise ValueError(f"切片数 {len(chunks)} 与向量数 {len(vectors)} 不一致")
            meta_file = path / "meta.json"
            mode = "hash-bigram"
            if meta_file.exists():
                mode = json.loads(meta_file.read_text(encoding="utf-8")).get("mode", mode)
            index.add(chunks, vectors, mode)
        except Exception as exc:
            # 不自动删用户数据；保留现场，让运维按日志重建索引
            logger.error("向量索引加载失败（%s），按空索引启动；请重新 embed 重建：%s", path, exc)
        return index


def _atomic_write_text(path: Path, text: str) -> None:
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, path)


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
