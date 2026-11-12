"""RAG 向量索引最小测试：切片、字符 bigram、阈值、过滤与持久化。"""
from __future__ import annotations

import pytest

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
    assert all(len(chunk.content) <= settings.rag_chunk_size + settings.rag_chunk_overlap
               for chunk in chunks)
    assert chunks[0].content


def test_chunk_overlap_keeps_all_text():
    text = ("关系模型用二维表描述实体及其联系。" * 30) + "最后一句必须保留。"
    chunks = vector_index.chunk_text(text, "KP0201")
    clean = " ".join(text.split())
    joined = chunks[0].content
    for prev, cur in zip(chunks, chunks[1:]):
        tail = prev.content[-settings.rag_chunk_overlap:]
        assert cur.content.startswith(tail)
        joined += cur.content[len(tail):]
    assert joined == clean


def test_overlap_not_smaller_than_size_is_clamped():
    text = "数据库" * 600
    chunks = vector_index.chunk_text(text, "KP0201", size=100, overlap=100)
    assert 2 <= len(chunks) <= 20
    assert all(len(chunk.content) <= 110 for chunk in chunks)


def test_chunk_size_zero_falls_back_to_one_instead_of_hanging(monkeypatch):
    """配置误写 RAG_CHUNK_SIZE=0 时不得让切片步长退化为 0（否则 while 死循环）。"""
    monkeypatch.setattr(settings, "rag_chunk_size", 0)
    chunks = vector_index.chunk_text("数据库系统", "KP0201")
    assert len(chunks) == 5
    assert all(len(chunk.content) == 1 for chunk in chunks)


def test_bm25_counts_ignore_punctuation_and_case():
    assert vector_index._bigram_counts("关系，模型") == vector_index._bigram_counts("关系模型")
    assert vector_index._bigram_counts("SQL 查询") == vector_index._bigram_counts("sql查询")
    assert vector_index._bigram_counts("") == {}


def test_bm25_keyword_score_is_bounded_and_lexically_monotonic():
    chunk = vector_index.Chunk("KP0203#1", "KP0203", "候选码", 1, None, "", "候选码是唯一标识元组的属性集")
    index = vector_index.VectorIndex()
    index.add([chunk], [vector_index._hash_vector(chunk.content)], "hash-bigram")
    exact = index.search(chunk.content, top_k=3, threshold=0.0)[0]
    assert exact.keyword_score == 1.0
    assert 0 <= exact.score <= 1
    unrelated = index.search("今天天气不错", top_k=3, threshold=0.0)[0]
    assert unrelated.keyword_score == 0


def test_bm25_ranks_lexical_match_first(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    vector_index.embed_document("KP0203", "候选码是能唯一标识元组且真子集不能唯一标识的属性集。",
                                "候选码", course_id=1)
    vector_index.embed_document("KP0304", "连接查询把两个表按连接条件组合成结果表。",
                                "连接查询", course_id=1)
    hits = vector_index.retrieve("候选码的真子集不能唯一标识元组", top_k=2, threshold=0.0)
    assert hits[0].chunk.kp_id == "KP0203"
    assert hits[0].keyword_score > 0


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
    hits = vector_index.retrieve("候选码和主码的区别", top_k=5, course_id=1)
    assert hits
    assert hits[0].chunk.kp_id == "KP0203"
    assert hits[0].score > 0


def test_threshold_can_filter_all(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    vector_index.embed_document("KP0201", "关系模型是二维表。", "关系模型", course_id=1)
    hits = vector_index.retrieve("完全不相关的内容", top_k=5, threshold=0.99)
    assert hits == []


def test_default_threshold_filters_unrelated_long_text(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    text = "关系模型是二维表，主码用于唯一标识元组。" * 20
    vector_index.embed_document("KP0201", text, "关系模型", course_id=1)
    assert vector_index.retrieve("今天天气怎么样，适合出门吗", top_k=5) == []


def test_filter_by_kp_id(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    vector_index.embed_document("KP0201", "关系模型是二维表。", "关系模型", course_id=1)
    vector_index.embed_document("KP0202", "关系模型的形式化定义。", "形式化定义", course_id=1)
    hits = vector_index.retrieve("关系模型", top_k=5, threshold=0.0, kp_id="KP0202")
    assert hits
    assert all(hit.chunk.kp_id == "KP0202" for hit in hits)


def test_missing_bge_model_falls_back_without_loading(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "rag_embed_model", str(tmp_path / "no-such-model"))
    monkeypatch.setattr(vector_index, "_MODEL", None)
    vectors, mode = vector_index.embed_texts(["关系模型"])
    assert mode == "hash-bigram"
    assert len(vectors[0]) == 256
    monkeypatch.setattr(vector_index, "_MODEL", False)


def test_save_and_load_roundtrip(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    vector_index.embed_document("KP0203", "候选码是唯一标识元组的属性集。", "候选码", course_id=1)
    vector_index.reset_index()
    loaded = vector_index.VectorIndex.load(tmp_path)
    assert loaded.size >= 1
    hits = loaded.search("候选码", top_k=3, threshold=0.0)
    assert hits and hits[0].chunk.kp_id == "KP0203"


def test_reembed_replaces_old_chunks(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    long_text = "关系模型用二维表描述实体。" * 120
    count, _ = vector_index.embed_document("KP0201", long_text, "关系模型", course_id=1)
    assert count > 1
    vector_index.embed_document("KP0201", "关系模型是二维表。", "关系模型", course_id=1)
    index = vector_index.get_index()
    assert index.size == 1
    assert "KP0201#1" in index._chunks
    assert all(chunk_id == "KP0201#1" for chunk_id in index._chunks)


def test_vector_dimension_and_mode_mismatch_are_rejected():
    index = vector_index.VectorIndex()
    chunk_a = vector_index.Chunk("KP1#1", "KP1", "", 1, None, "", "a")
    chunk_b = vector_index.Chunk("KP2#1", "KP2", "", 1, None, "", "b")
    index.add([chunk_a], [[1.0, 0.0]], "hash-bigram")
    try:
        index.add([chunk_b], [[1.0, 0.0, 0.0]], "hash-bigram")
        raise AssertionError("维度不一致应抛错")
    except ValueError:
        pass
    assert index.size == 1
    try:
        index.add([chunk_b], [[1.0, 0.0]], "bge")
        raise AssertionError("模式不一致应抛错")
    except ValueError:
        pass
    assert index.size == 1


def test_corrupt_index_loads_empty(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    (tmp_path / "chunks.json").write_text(
        '[{"chunk_id":"KP1#1","kp_id":"KP1","kp_name":"","course_id":1,'
        '"chapter_id":null,"ref":"","content":"a"},'
        '{"chunk_id":"KP1#2","kp_id":"KP1","kp_name":"","course_id":1,'
        '"chapter_id":null,"ref":"","content":"b"}]', encoding="utf-8")
    (tmp_path / "vectors.json").write_text("[[1.0,0.0]]", encoding="utf-8")
    loaded = vector_index.VectorIndex.load(tmp_path)
    assert loaded.size == 0


def test_course_filter_does_not_leak_shared_chunks(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    vector_index.embed_document("KP0201", "关系模型是二维表。", "关系模型")
    assert vector_index.retrieve("关系模型", top_k=5, threshold=0.0, course_id=1) == []


def test_save_leaves_no_temp_files(tmp_path, monkeypatch):
    _prepare(tmp_path, monkeypatch)
    vector_index.embed_document("KP0201", "关系模型是二维表。", "关系模型", course_id=1)
    assert not list(tmp_path.glob("*.tmp"))


# ---- numpy 快路径与纯 Python 回退路径的数学等价性（升级新增） ----

def _build_equivalence_corpus():
    texts = [
        "关系数据库的标准语言SQL支持数据定义、数据查询、数据操纵和数据控制",
        "事务的ACID特性包括原子性、一致性、隔离性和持久性",
        "B+树索引的所有关键字都出现在叶子节点，非叶节点仅作索引导向",
        "查询优化的目标是选择高效合理的等价查询表达式",
        "实体完整性要求主属性不能取空值",
    ]
    chunks = [vector_index.Chunk(f"KPEQ{i}#1", f"KPEQ{i}", "测试", 9, None, "", t)
              for i, t in enumerate(texts)]
    return chunks, [vector_index._hash_vector(t) for t in texts]


def test_fast_path_matches_fallback_ranking_and_scores(monkeypatch):
    """numpy GEMV+倒排快路径 与 逐文档循环回退路径：排序与分数一致（<1e-3）。"""
    chunks, vectors = _build_equivalence_corpus()
    original_np = vector_index._np

    fast = vector_index.VectorIndex()
    fast.add(chunks, vectors, "hash-bigram")
    assert fast._matrix is not None  # 快路径缓存已建立

    monkeypatch.setattr(vector_index, "_np", None)
    slow = vector_index.VectorIndex()
    slow.add(chunks, [list(v) for v in vectors], "hash-bigram")
    assert slow._matrix is None  # 无 numpy 环境不建矩阵
    monkeypatch.setattr(vector_index, "_np", original_np)

    queries = ["什么是SQL的标准语言", "事务的特性有哪些", "B+树叶子节点",
               "主属性空值", "查询优化目标", "完全无关的查询词xyz"]
    for query in queries:
        fast_hits = fast.search(query, top_k=5, threshold=0.0)
        slow_hits = slow.search(query, top_k=5, threshold=0.0)
        assert [h.chunk.chunk_id for h in fast_hits] == [h.chunk.chunk_id for h in slow_hits]
        for f, s in zip(fast_hits, slow_hits):
            assert abs(f.score - s.score) < 1e-3, (query, f.chunk.chunk_id)
            assert abs(f.vector_score - s.vector_score) < 1e-3
            assert abs(f.keyword_score - s.keyword_score) < 1e-3


def test_inverted_candidate_branch_matches_csr_ranking(monkeypatch):
    """scipy 缺失（numpy 可用）时的倒排候选回退路径，与 CSR 快路径等价。

    CI 无 numpy 只覆盖纯 Python 路径，本机有 scipy 只覆盖 CSR 路径；
    "有 numpy 无 scipy"这条生产回退路径若无测试将零覆盖。
    """
    pytest.importorskip("scipy")
    chunks, vectors = _build_equivalence_corpus()

    csr = vector_index.VectorIndex()
    csr.add(chunks, vectors, "hash-bigram")
    assert csr._stats is not None and csr._stats["sparse"] is not None  # CSR 路径

    monkeypatch.setattr(vector_index, "_sparse_module", lambda: None)
    fallback = vector_index.VectorIndex()
    fallback.add(chunks, [list(v) for v in vectors], "hash-bigram")
    assert fallback._matrix is not None  # numpy 通道仍在
    assert fallback._stats is not None and fallback._stats["sparse"] is None  # 走倒排候选

    queries = ["什么是SQL的标准语言", "事务的特性有哪些", "B+树叶子节点",
               "主属性空值", "查询优化目标", "完全无关的查询词xyz"]
    for query in queries:
        csr_hits = csr.search(query, top_k=5, threshold=0.0)
        fallback_hits = fallback.search(query, top_k=5, threshold=0.0)
        assert [h.chunk.chunk_id for h in csr_hits] == \
               [h.chunk.chunk_id for h in fallback_hits], query
        for c, f in zip(csr_hits, fallback_hits):
            assert abs(c.score - f.score) < 1e-3, (query, c.chunk.chunk_id)
            assert abs(c.keyword_score - f.keyword_score) < 1e-3


def test_fast_path_respects_kp_and_course_filters():
    chunks, vectors = _build_equivalence_corpus()
    index = vector_index.VectorIndex()
    index.add(chunks, vectors, "hash-bigram")
    hits = index.search("SQL 数据定义", top_k=10, threshold=0.0, kp_id="KPEQ0")
    assert hits and all(h.chunk.kp_id == "KPEQ0" for h in hits)
    hits = index.search("SQL 数据定义", top_k=10, threshold=0.0, course_id=9)
    assert hits and all(h.chunk.course_id == 9 for h in hits)
    assert index.search("SQL 数据定义", top_k=10, threshold=0.0, course_id=8) == []
