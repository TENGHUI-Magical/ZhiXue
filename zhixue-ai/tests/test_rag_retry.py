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


def _parse(frames: list[str]) -> list[tuple[str, dict]]:
    events: list[tuple[str, dict]] = []
    for frame in frames:
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


def _collect(payload: dict | None = None) -> list[tuple[str, dict]]:
    async def run() -> list[str]:
        frames = []
        async for frame in rag_service.stream_explain(RagExplainRequest(**(payload or PAYLOAD))):
            frames.append(frame)
        return frames

    return _parse(asyncio.run(run()))


def test_three_sections_are_generated_by_one_model_call(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "deepseek_api_key", "test-key")
    monkeypatch.setattr(settings, "glm_api_key", "")
    calls = {"count": 0}
    expected = (
        "【错因定位】\n你把候选码误当成了主码。\n"
        "【完整讲解】\n先检查候选码是否仍满足最小性。\n"
        "【自查】\n去掉任一属性后，它还能唯一确定元组吗？"
    )

    async def fake_stream(url, key, model, messages, usage, include_usage=False):
        calls["count"] += 1
        yield expected

    monkeypatch.setattr(rag_service, "_stream_openai", fake_stream)

    events = _collect()
    content = "".join(data["content"] for name, data in events if name == "chunk")
    done = [data for name, data in events if name == "done"][-1]

    assert calls["count"] == 1
    assert content == expected
    assert done["promptVersion"] == "v2"


def test_l3_emits_only_learning_card_with_at_most_three_misconceptions(
        monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "deepseek_api_key", "")
    monkeypatch.setattr(settings, "glm_api_key", "")

    events = _collect({**PAYLOAD, "misconception": "误区一\\n误区二\n误区三\n误区四"})
    content = "".join(data["content"] for name, data in events if name == "chunk")

    assert content == (
        "【学习卡】\n"
        "【知识点】关系模型；教材P34\n"
        "【常见误区】\n"
        "1. 误区一\n"
        "2. 误区二\n"
        "3. 误区三\n"
        "【练习提示】请完成下方同类练习，检验自己是否真正掌握。"
    )
    assert "概念原文" not in content
    assert "关系模型用二维表表示实体与实体间的联系" not in content
    assert "关系模型的三要素是什么" not in content
    assert "降级" not in content
    assert "模板" not in content
    assert "LLM" not in content


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
    assert degraded and degraded[0]["level"] == rag_service.LEVEL_L2
    assert degraded[0]["modelUsed"] == settings.llm_model_fallback, \
        "L2 必须携带实际备用模型名，供 Java 落库保留真实 model"
    done = [data for name, data in events if name == "done"][-1]
    assert done["degraded"] is True
    assert done["modelUsed"] == settings.llm_model_fallback


def test_meta_carries_primary_model_for_stream_break_attribution(
        monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "deepseek_api_key", "test-key")
    monkeypatch.setattr(settings, "glm_api_key", "")

    async def fake_stream(url, key, model, messages, usage, include_usage=False):
        yield "讲解正文。"

    monkeypatch.setattr(rag_service, "_stream_openai", fake_stream)
    events = _collect()
    meta = [data for name, data in events if name == "meta"][-1]
    assert meta["model"] == settings.llm_model_main, \
        "meta 需告知 Java 首个候选模型，流中断时才能保留真实 model 而不是 local-template"


def test_l1_partial_failure_raises_without_l3_template(
        monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "deepseek_api_key", "test-key")
    monkeypatch.setattr(settings, "glm_api_key", "")

    async def fake_stream(url, key, model, messages, usage, include_usage=False):
        yield "L1 第一段正文。"
        raise httpx.ReadTimeout("L1 中途断流")

    monkeypatch.setattr(rag_service, "_stream_openai", fake_stream)

    async def run() -> list[str]:
        frames = []
        try:
            async for frame in rag_service.stream_explain(RagExplainRequest(**PAYLOAD)):
                frames.append(frame)
        except httpx.ReadTimeout:
            return frames
        raise AssertionError("L1 中途断流应向上抛出，由 Java 记为 L2（部分产出）")

    events = _parse(asyncio.run(run()))
    names = [name for name, _ in events]
    assert "chunk" in names, "断流前应已输出部分正文"
    assert "done" not in names
    assert all(data.get("level") != rag_service.LEVEL_L3
               for name, data in events if name == "degraded"), \
        "部分产出不得发 L3，L3 只留给远程零产出后的本地模板"


def test_l2_partial_failure_does_not_append_template(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "deepseek_api_key", "test-key")
    monkeypatch.setattr(settings, "glm_api_key", "test-glm-key")

    async def fake_stream(url, key, model, messages, usage, include_usage=False):
        if "deepseek" in url:
            raise httpx.ConnectError("L1 持续失败")
        yield "GLM 第一段正文。"
        raise httpx.ReadTimeout("GLM 中途断流")

    monkeypatch.setattr(rag_service, "_stream_openai", fake_stream)

    async def run() -> list[str]:
        frames = []
        try:
            async for frame in rag_service.stream_explain(RagExplainRequest(**PAYLOAD)):
                frames.append(frame)
        except httpx.ReadTimeout:
            return frames
        raise AssertionError("L2 中途断流应向上抛出，由 Java 记为 L2（保留实际模型名）")

    events = _parse(asyncio.run(run()))
    names = [name for name, _ in events]
    assert "chunk" in names, "断流前应已输出部分 GLM 正文"
    levels = [data.get("level") for name, data in events if name == "degraded"]
    assert levels == [rag_service.LEVEL_L2], "不得再追加 L3 降级事件"
    assert "done" not in names, "不得再发送完整模板后的 done"


def test_page_whitelist_keeps_allowed_pages_and_ignores_kp_id() -> None:
    sanitize = getattr(rag_service, "sanitize_page_citations")
    text = sanitize("KP0203 见 P38 与第38页；P39 越界。", "P38")
    assert text == "KP0203 见 P38 与第38页；教材相关章节 越界。"


def test_page_whitelist_expands_allowed_ranges() -> None:
    sanitize = getattr(rag_service, "sanitize_page_citations")
    text = sanitize("P35 合法，P37 越界；第36页也合法。", "P34—36")
    assert text == "P35 合法，教材相关章节 越界；第36页也合法。"


def test_page_whitelist_does_not_duplicate_textbook_word() -> None:
    sanitize = getattr(rag_service, "sanitize_page_citations")
    assert sanitize("参见教材P999。", "P38") == "参见教材相关章节。"


def test_page_guard_sanitizes_citation_split_across_deltas() -> None:
    async def run() -> str:
        async def source():
            yield "见 P9"
            yield "99；保留 P38。"

        guard = getattr(rag_service, "_guard_pages")
        return "".join([delta async for delta in guard(source(), "P38")])

    assert asyncio.run(run()) == "见 教材相关章节；保留 P38。"


def test_stream_explain_sanitizes_model_pages_before_emitting(
        monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "deepseek_api_key", "test-key")
    monkeypatch.setattr(settings, "glm_api_key", "")

    async def fake_stream(url, key, model, messages, usage, include_usage=False):
        yield "越界 P9"
        yield "99；合法 P34。"

    monkeypatch.setattr(rag_service, "_stream_openai", fake_stream)
    events = _collect()
    content = "".join(data["content"] for name, data in events if name == "chunk")
    assert "P999" not in content
    assert "教材相关章节" in content
    assert "P34" in content


@pytest.mark.parametrize("sentence,answer,expected", [
    ("答案是B。", "B", True),
    ("正确选项为C项。", "C", True),
    ("选 D。", "D", True),
    ("正确答案为 B 项。", "B", True),
    ("DELETE  FROM 用于删数据。", "DELETE FROM", True),
    ("B 选项错在没考虑空值。", "B", False),
    ("不能选 B，因为它忽略空值。", "B", False),
    ("不应选B。", "B", False),
    ("答案不是 B，先别急着下结论。", "B", False),
    ("这里讨论的是 SQL 的 BETWEEN。", "B", False),
])
def test_answer_leak_controlled_samples(sentence: str, answer: str, expected: bool) -> None:
    detector = getattr(rag_service, "is_answer_leak")
    assert detector(sentence, answer) is expected


def test_output_guard_drops_only_leaking_sentences() -> None:
    async def run() -> str:
        async def source():
            yield "先看候选码的最小性。"
            yield "答案是B。"
            yield "再检查真子集。"

        guard = getattr(rag_service, "_guard_pages")
        return "".join([delta async for delta in guard(source(), "P38", "B")])

    assert asyncio.run(run()) == "先看候选码的最小性。再检查真子集。"


def test_stream_explain_falls_back_to_template_when_all_sentences_leak(
        monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "deepseek_api_key", "test-key")
    monkeypatch.setattr(settings, "glm_api_key", "")

    async def fake_stream(url, key, model, messages, usage, include_usage=False):
        yield "答案是B。"

    monkeypatch.setattr(rag_service, "_stream_openai", fake_stream)
    events = _collect({**PAYLOAD, "answerText": "B"})
    content = "".join(data["content"] for name, data in events if name == "chunk")
    levels = [data["level"] for name, data in events if name == "degraded"]
    done = [data for name, data in events if name == "done"][-1]
    assert levels == [rag_service.LEVEL_L3]
    assert done["modelUsed"] == rag_service.MODEL_LOCAL_TEMPLATE
    assert "答案是B" not in content
