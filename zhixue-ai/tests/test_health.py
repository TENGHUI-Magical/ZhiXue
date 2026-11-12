"""索引写入竞态 / 加载健康状态 / 条数上限 / 入参护栏单测（Python_02 P1-2/3/4）。"""
from __future__ import annotations

import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.config import settings
from app.main import app
from app.schemas import RagEmbedRequest
from app.services import vector_index


@pytest.fixture(autouse=True)
def _clean_index_state(monkeypatch, tmp_path):
    monkeypatch.setattr(settings, "vector_index_dir", str(tmp_path))
    monkeypatch.setattr(vector_index, "_MODEL", False)
    monkeypatch.setattr(vector_index, "_INDEX_LOAD_ERROR", None)
    vector_index.reset_index()
    yield
    vector_index.reset_index()


# ---- P1-2：_atomic_write_text 临时文件名唯一，并发写不再互相踩踏 ----

def test_atomic_write_uses_unique_temp_names(tmp_path, monkeypatch) -> None:
    seen: list[str] = []
    real_replace = vector_index.os.replace

    def spy(src, dst):
        seen.append(Path(src).name)
        return real_replace(src, dst)

    monkeypatch.setattr(vector_index.os, "replace", spy)
    target = tmp_path / "chunks.json"
    for _ in range(3):
        vector_index._atomic_write_text(target, "{}")
    assert len(seen) == 3
    assert len(set(seen)) == 3, "每次写必须用不同的唯一临时名，不能固定 .tmp 互相覆盖"
    assert all(name.endswith(".tmp") for name in seen)
    assert not list(tmp_path.glob("*.tmp")), "原子替换后不得残留临时文件"


# ---- P1-4：索引条数上限（add 内存 + save 磁盘） ----

def test_add_caps_chunk_count_at_limit(monkeypatch) -> None:
    monkeypatch.setattr(vector_index, "MAX_INDEX_CHUNKS", 10)
    index = vector_index.VectorIndex()
    chunks = [vector_index.Chunk(f"KP{i}#1", f"KP{i}", "", 1, None, "", f"内容{i}")
              for i in range(20)]
    vectors = [vector_index._hash_vector(c.content) for c in chunks]
    index.add(chunks, vectors, "hash-bigram")
    assert index.size == 10, "超出上限的切片必须被截断，防止内存无界增长"


def test_save_caps_persisted_chunk_count(tmp_path, monkeypatch) -> None:
    monkeypatch.setattr(vector_index, "MAX_INDEX_CHUNKS", 100)
    index = vector_index.VectorIndex()
    chunks = [vector_index.Chunk(f"KP{i}#1", f"KP{i}", "", 1, None, "", f"内容{i}")
              for i in range(20)]
    vectors = [vector_index._hash_vector(c.content) for c in chunks]
    index.add(chunks, vectors, "hash-bigram")
    # 调低上限后持久化：save() 同样要按上限切片，防御历史/绕过 add 的大索引
    monkeypatch.setattr(vector_index, "MAX_INDEX_CHUNKS", 5)
    index.save(tmp_path)
    saved = json.loads((tmp_path / "chunks.json").read_text(encoding="utf-8"))
    assert len(saved) == 5


# ---- P1-3 / P1-4：load() 失败登记状态，/health 反映 DEGRADED ----

def test_get_index_status_true_on_fresh_index() -> None:
    assert vector_index.get_index_status() is True, "全新空索引是合法状态，应保持 UP"


def test_get_index_status_false_on_corrupt_index(tmp_path) -> None:
    (tmp_path / "chunks.json").write_text(
        '[{"chunk_id":"KP1#1","kp_id":"KP1","kp_name":"","course_id":1,'
        '"chapter_id":null,"ref":"","content":"a"}]', encoding="utf-8")
    (tmp_path / "vectors.json").write_text("[]", encoding="utf-8")
    assert vector_index.get_index_status() is False, "加载失败不得再被当作健康 UP"


def test_health_reports_degraded_when_index_load_fails(tmp_path) -> None:
    (tmp_path / "chunks.json").write_text(
        '[{"chunk_id":"KP1#1","kp_id":"KP1","kp_name":"","course_id":1,'
        '"chapter_id":null,"ref":"","content":"a"}]', encoding="utf-8")
    (tmp_path / "vectors.json").write_text("[]", encoding="utf-8")
    with TestClient(app) as client:
        resp = client.get("/health")
    body = resp.json()
    assert resp.status_code == 200
    assert body["status"] == "DEGRADED"
    assert body["components"]["vectorIndex"] == "DOWN"
    # 主审已加的其它组件状态检查不得被破坏
    assert set(body["components"]) >= {"vectorIndex", "deepseekKey", "glmKey", "internalToken"}


def test_health_reports_up_on_fresh_index() -> None:
    with TestClient(app) as client:
        body = client.get("/health").json()
    assert body["status"] == "UP"
    assert body["components"]["vectorIndex"] == "UP"


# ---- P1-3：RagEmbedRequest.text 长度上限 ----

def test_rag_embed_text_length_is_capped() -> None:
    with pytest.raises(ValidationError):
        RagEmbedRequest(kpId="KP0201", text="字" * 20001)
    assert RagEmbedRequest(kpId="KP0201", text="字" * 20000)
