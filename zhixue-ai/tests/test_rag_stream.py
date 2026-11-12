"""_stream_openai / _guard_pages / 连接池单例的单测（P1-2 / P1-3 / P1-4）。

用 httpx.MockTransport 回放构造的 SSE 帧，不发起真实网络请求、不需要 API Key。
"""
from __future__ import annotations

import asyncio
import logging

import httpx
import pytest

from app.services import rag_service

URL = "http://mock.local/chat/completions"


def _mock_client(frames: list[str]) -> httpx.AsyncClient:
    body = "".join(f"data: {f}\n\n" for f in frames) + "data: [DONE]\n\n"

    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=body.encode("utf-8"),
                              headers={"content-type": "text/event-stream"})

    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


async def _collect(monkeypatch: pytest.MonkeyPatch, frames: list[str]):
    client = _mock_client(frames)
    monkeypatch.setattr(rag_service, "_HTTP_CLIENT", client)
    usage: dict = {}
    out: list[str] = []
    try:
        async for delta in rag_service._stream_openai(URL, "test-key", "mock-model", [], usage):
            out.append(delta)
    finally:
        await client.aclose()
    return out, usage


# ---- P1-3：畸形帧不再静默吞，超阈值主动中止 ----

def test_malformed_frames_are_counted_logged_and_abort(monkeypatch, caplog) -> None:
    frames = ["{not-json"] * 5 + ['{"choices":[{"delta":{"content":"不应出现"}}]}']
    with caplog.at_level(logging.WARNING):
        with pytest.raises(RuntimeError):
            asyncio.run(_collect(monkeypatch, frames))
    assert "畸形 SSE 帧" in caplog.text


def test_malformed_frame_below_threshold_is_skipped(monkeypatch) -> None:
    frames = ["{bad}", '{"choices":[{"delta":{"content":"正文"}}]}']
    out, _usage = asyncio.run(_collect(monkeypatch, frames))
    assert out == ["正文"]


# ---- P1-3：choices/delta/content 为 null / 异常结构时安全跳过，不抛 TypeError ----

def test_null_and_malformed_structures_are_skipped_safely(monkeypatch) -> None:
    frames = [
        '{"choices": null}',
        '{"choices": [null]}',
        '{"choices": [{"delta": null}]}',
        '{"choices": [{"delta": {"content": 123}}]}',
        '{"choices": [], "usage": {"prompt_tokens": 3}}',
        '{"choices": [{"delta": {"content": "正文"}}], "usage": {"completion_tokens": 7}}',
    ]
    out, usage = asyncio.run(_collect(monkeypatch, frames))
    assert out == ["正文"], "异常结构帧不得抛 TypeError，也不得产出内容"
    assert usage == {"prompt_tokens": 3, "completion_tokens": 7}


# ---- P1-2：连接池单例 / 关闭 / 并发信号量 ----

def test_http_client_is_a_reusable_singleton(monkeypatch) -> None:
    monkeypatch.setattr(rag_service, "_HTTP_CLIENT", None)

    async def run() -> None:
        first = await rag_service._get_http_client()
        second = await rag_service._get_http_client()
        assert first is second, "L1/L2/重试必须复用同一 AsyncClient"
        assert not first.is_closed
        await rag_service.aclose_http_client()
        assert first.is_closed
        assert rag_service._HTTP_CLIENT is None

    asyncio.run(run())


def test_generation_semaphore_is_rebuilt_per_event_loop() -> None:
    seen = []

    def one_loop() -> None:
        async def inner() -> None:
            seen.append(rag_service._generation_semaphore())
        asyncio.run(inner())

    one_loop()
    one_loop()
    assert seen[0]._value == rag_service._MAX_CONCURRENT_GENERATIONS
    assert seen[0] is not seen[1], "跨 asyncio.run() 的新 loop 不得复用旧 loop 的信号量"


# ---- P1-4：无标点长流 buf 有硬上界，且内容不丢 ----

def test_guard_pages_caps_buffer_without_losing_content() -> None:
    async def run() -> list[str]:
        async def source():
            for _ in range(20):
                yield "字" * 500  # 10000 字、无任何句末标点

        return [c async for c in rag_service._guard_pages(source(), "P38")]

    chunks = asyncio.run(run())
    assert len(chunks) >= 2, "无标点长流必须被强制切块，buf 不得无界增长"
    assert "".join(chunks) == "字" * 10000, "强制切块只是提前切，输出内容不得丢"


# ---- P1-2：整体超时兜底 → 走既有降级链到 L3 ----

class _SlowStream(httpx.AsyncByteStream):
    """先吐一个不足 12 字的片段，再长时间挂住，模拟「慢速吐字」拖过整体超时。"""

    async def __aiter__(self):
        yield b'data: {"choices":[{"delta":{"content":"a"}}]}\n\n'
        await asyncio.sleep(1.0)
        yield b"data: [DONE]\n\n"

    async def aclose(self) -> None:
        return None


def test_overall_timeout_falls_back_to_l3(monkeypatch) -> None:
    from app.config import settings
    from app.schemas import RagExplainRequest
    from app.services import vector_index

    def handler(_req):
        return httpx.Response(200, stream=_SlowStream(),
                              headers={"content-type": "text/event-stream"})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    monkeypatch.setattr(rag_service, "_HTTP_CLIENT", client)
    monkeypatch.setattr(rag_service, "_GENERATION_TIMEOUT_SECONDS", 0.05)
    monkeypatch.setattr(rag_service, "RETRY_BACKOFF_SECONDS", 0)  # 跳过真实退避，保持快速
    monkeypatch.setattr(settings, "deepseek_api_key", "test-key")
    monkeypatch.setattr(settings, "glm_api_key", "")
    monkeypatch.setattr(vector_index, "retrieve", lambda *a, **k: [])  # 本用例不关心检索

    payload = {
        "kpId": "KP0201", "kpName": "关系模型",
        "conceptText": "关系模型用二维表表示实体与实体间的联系",
        "questionText": "关系模型的三要素是什么？",
        "answerText": "关系、域、笛卡尔积",
        "textbookPage": "P34", "courseId": 1,
    }

    async def run() -> list[str]:
        frames: list[str] = []
        try:
            async for frame in rag_service.stream_explain(RagExplainRequest(**payload)):
                frames.append(frame)
        finally:
            await client.aclose()
        return frames

    frames = asyncio.run(run())
    events = [line[7:] for f in frames for line in f.splitlines()
              if line.startswith("event: ")]
    text = "".join(frames)
    assert events.count("degraded") == 1, "整体超时后只应发唯一的降级事件"
    assert '"level": "L3"' in text
    assert "【学习卡】" in text, "超时后应回退到本地 L3 学习卡模板"
    assert "done" in events
