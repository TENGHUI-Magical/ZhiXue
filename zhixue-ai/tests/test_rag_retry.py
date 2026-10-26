"""讲解降级链单测（契约 §6）：L1 失败重试 1 次，仍失败才降 L2。

直接调用 rag_service.stream_explain，不引入 FastAPI/TestClient，
CI 只需轻量 dev 依赖即可运行（路由层只是 HTTP 外壳）。
"""
from __future__ import annotations

import asyncio
import json

import httpx
import pytest

from app.config import settings
from app.schemas import RagExplainRequest
from app.services import rag_service

PAYLOAD = {
    "traceId": "t-test",
    "kpId": "KP0201",
    "kpName": "关系模型",
    "conceptText": "关系模型用二维表表示实体与实体间的联系",
    "misconception": "混淆主键与候选键",
    "textbookPage": "P34",
    "questionText": "关系模型的三要素是什么？",
    "errorType": "CONCEPT_CONFUSION",
    "courseId": 1,
    "logId": "EL_1",
}


def _collect() -> list[tuple[str, dict]]:
    async def run() -> list[str]:
        frames = []
        async for frame in rag_service.stream_explain(RagExplainRequest(**PAYLOAD)):
            frames.append(frame)
        return frames

    events: list[tuple[str, dict]] = []
    for frame in asyncio.run(run()):
        event = ""
        data = ""
        for line in frame.splitlines():
            if line.startswith("event: "):
                event = line[len("event: "):]
            elif line.startswith("data: "):
                data = line[len("data: "):]
        if event:
            events.append((event, json.loads(data)))
    return events


def test_l1_retries_once_then_succeeds(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "deepseek_api_key", "test-key")
    calls = {"count": 0}

    async def fake_stream(url, key, model, messages, usage, include_usage=False):
        calls["count"] += 1
        if calls["count"] == 1:
            raise httpx.ConnectError("L1 首次失败")
        yield "第一段讲解。"

    monkeypatch.setattr(rag_service, "_stream_openai", fake_stream)

    events = _collect()

    names = [name for name, _ in events]
    assert calls["count"] == 2, "L1 失败后必须重试 1 次"
    assert "degraded" not in names, "重试成功不得标记降级"
    done = [data for name, data in events if name == "done"][-1]
    assert done["degraded"] is False
    assert done["modelUsed"] == settings.llm_model_main


def test_l1_fails_twice_then_degrades_to_l2(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "deepseek_api_key", "test-key")
    monkeypatch.setattr(settings, "glm_api_key", "test-glm-key")
    urls: list[str] = []

    async def fake_stream(url, key, model, messages, usage, include_usage=False):
        urls.append(url)
        if "deepseek" in url:
            raise httpx.ConnectError("L1 持续失败")
        yield "GLM 讲解。"

    monkeypatch.setattr(rag_service, "_stream_openai", fake_stream)

    events = _collect()

    assert sum("deepseek" in url for url in urls) == 2, "L1 必须尝试两次"
    degraded = [data for name, data in events if name == "degraded"]
    assert degraded and degraded[0]["level"] == "L2"
    done = [data for name, data in events if name == "done"][-1]
    assert done["degraded"] is True
    assert done["modelUsed"] == settings.llm_model_fallback
