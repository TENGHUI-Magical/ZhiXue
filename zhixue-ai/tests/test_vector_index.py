"""RAG 向量索引最小测试：切片、字符 bigram、阈值、过滤与持久化。"""
from __future__ import annotations

from app.config import settings
from app.services import vector_index


def _prepare(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "vector_index_dir", str(tmp_path))
    monkeypatch.setattr(vector_index, "_MODEL", False)
    vector_index.reset_index()


def test_chunk_text_ids_and_bounds():
    text = "数据库关系模型是二维表。" * 80
    chunks = vector_index.chunk_text(text, "KP0201")
    assert len(chunks) >= 2
    assert all(chunk.chunk_id.startswith("KP0201#") for chunk in chunks)
    assert all(len(chunk.content) <= settings.rag_chunk_size for chunk in chunks)
    assert chunks[0].content


def test_char_bigram_jaccard_is_deterministic():
    assert vector_index.char_bigram_jaccard("候选码是唯一标识", "候选码是唯一标识") == 1.0
    assert vector_index.char_bigram_jaccard("候选码", "完全无关") == 0.0
    assert 0 < vector_index.char_bigram_jaccard("关系模型", "关系模型与二维表") < 1


def test_embed_then_retrieve_top1(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    text = "候选码是能唯一标识元组且真子集不能唯一标识的属性集。主码是从候选码中选定的一个。"
    count, mode = vector_index.embed_document("KP0203", text, "候选码", course_id=1, ref="P38")
    assert count >= 1
    assert mode in {"hash-bigram", "bge"}
    hits = vector_index.retrieve("候选码和主码的区别", top_k=5, threshold=0.05, course_id=1)
    assert hits
    assert hits[0].chunk.kp_id == "KP0203"
    assert hits[0].score > 0


def test_threshold_can_filter_all(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    vector_index.embed_document("KP0201", "关系模型是二维表。", "关系模型", course_id=1)
    hits = vector_index.retrieve("完全不相关的内容", top_k=5, threshold=0.99)
    assert hits == []


def test_filter_by_kp_id(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    vector_index.embed_document("KP0201", "关系模型是二维表。", "关系模型", course_id=1)
    vector_index.embed_document("KP0202", "关系模型的形式化定义。", "形式化定义", course_id=1)
    hits = vector_index.retrieve("关系模型", top_k=5, threshold=0.0, kp_id="KP0202")
    assert hits
    assert all(hit.chunk.kp_id == "KP0202" for hit in hits)


def test_save_and_load_roundtrip(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    vector_index.embed_document("KP0203", "候选码是唯一标识元组的属性集。", "候选码", course_id=1)
    vector_index.reset_index()
    loaded = vector_index.VectorIndex.load(tmp_path)
    assert loaded.size >= 1
    hits = loaded.search("候选码", top_k=3, threshold=0.0)
    assert hits and hits[0].chunk.kp_id == "KP0203"
