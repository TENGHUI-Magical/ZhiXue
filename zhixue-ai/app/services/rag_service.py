"""RAG 讲解服务：检索上下文 → 流式讲解（架构 2.1 节 /api/rag）。

讲解降级链：L1 DeepSeek（失败重试 1 次）→ L2 GLM → L3 本地模板（本文件）→ L4 由 Java 侧映射 60001。
L3 模板只使用 Java 传入的 conceptText/misconception/页码，保证来源可追溯、不断章取义。
路由层只负责 HTTP 外壳，SSE 编排与降级全部在本服务内完成。
"""
from __future__ import annotations

import asyncio
import json
import logging
import time
import uuid
from typing import AsyncIterator

import httpx

from app.config import settings
from app.schemas import RagExplainRequest
from app.services.compliance import compliance_check

logger = logging.getLogger(__name__)

# Prompt 模板版本：随 _prompt() 结构变更递增，落 ai_generation_log.prompt_version（Git 可回溯）
PROMPT_VERSION = "v1"

# SSE 响应头：禁用代理缓冲，保持逐帧下发
SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no", "Connection": "keep-alive"}


def _sse(event: str, payload: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _first(text: str | None, limit: int) -> str:
    clean = (text or "").replace("\n", "；").strip()
    return clean[:limit]


def _prompt(req: RagExplainRequest) -> list[dict]:
    system = (
        "你是数据库原理课程的助教。只做思路点拨，不直接替学生写作业答案。"
        "必须依据给定概念切片讲解，不得编造教材页码或引用。"
        "用中文分 3—4 段，每段不超过 120 字。"
    )
    user = (
        f"知识点：{req.kpName}（{req.kpId}）\n"
        f"教材页码：{req.textbookPage or '未提供'}\n"
        f"概念切片：{req.conceptText}\n"
        f"常见误区：{req.misconception or '未提供'}\n"
        f"错因标签：{req.errorType or '未提供'}\n"
        f"学生题干：{req.questionText or '（只讲概念）'}\n"
        "请指出这道题考什么、学生最可能错在哪、下一步怎么自查。"
    )
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


async def _stream_openai(url: str, key: str, model: str, messages: list[dict],
                         usage: dict, include_usage: bool = False) -> AsyncIterator[str]:
    """OpenAI 兼容流式调用（DeepSeek / GLM 共用）。异常由调用方降级。"""
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    payload = {"model": model, "messages": messages, "stream": True, "temperature": 0.3}
    if include_usage:
        payload["stream_options"] = {"include_usage": True}
    timeout = httpx.Timeout(connect=3.0, read=float(settings.llm_timeout_seconds), write=10.0, pool=3.0)
    async with httpx.AsyncClient(timeout=timeout) as client:
        async with client.stream("POST", url, headers=headers, json=payload) as resp:
            resp.raise_for_status()
            async for line in resp.aiter_lines():
                if not line.startswith("data: "):
                    continue
                data = line[6:].strip()
                if data == "[DONE]":
                    break
                try:
                    obj = json.loads(data)
                except json.JSONDecodeError:
                    continue
                if obj.get("usage"):
                    usage.update(obj["usage"])
                delta = obj.get("choices", [{}])[0].get("delta", {}).get("content")
                if delta:
                    yield delta


async def _buffered(stream: AsyncIterator[str], size: int = 12) -> AsyncIterator[str]:
    """把模型逐字返回合并成 10—20 字的片段，减少 SSE 帧数、前端打字机更顺。"""
    buf = ""
    async for delta in stream:
        buf += delta
        if len(buf) >= size or delta.endswith(("。", "！", "？", "\n")):
            yield buf
            buf = ""
    if buf:
        yield buf


def _template_chunks(req: RagExplainRequest) -> list[str]:
    """L3 本地模板：只用已核对过的概念切片与误区，逐段给出思路。"""
    name = req.kpName or req.kpId
    concept = _first(req.conceptText, 160)
    mistake = _first(req.misconception, 90)
    question = _first(req.questionText, 70)
    page = req.textbookPage or "页码待确认"
    return [
        f"这道题对应知识点「{name}」。先抓定义：{concept}",
        f"常见误区：{mistake}" if mistake else "这个知识点暂时没有登记常见误区，请对照概念逐条自查。",
        f"回到题目：{question}" if question else "先用自己的话复述概念，再找一个反例检验边界条件。",
        f"自查顺序：定义 → 适用条件 → 反例。来源：《数据库系统概论（第6版）》{page}。",
    ]


async def stream_explain(req: RagExplainRequest) -> AsyncIterator[str]:
    """流式讲解（SSE）：meta → chunk* → done；合规拦截时发 error 60004（契约 §2）。"""
    start = time.time()
    # 契约 §1.2：traceId 由 Java 生成并透传，这里只回显，保证全链路一致
    trace_id = req.traceId or f"t-{time.strftime('%Y%m%d')}-{uuid.uuid4().hex[:8]}"
    sources = [{
        "kpId": req.kpId,
        "name": req.kpName or req.kpId,
        "ref": f"《数据库系统概论（第6版）》{req.textbookPage or '页码待确认'}",
    }]
    blocked, block_tip = compliance_check(req.questionText or "")

    yield _sse("meta", {"traceId": trace_id, "sources": sources})
    if blocked:
        # 契约 §2 60004：展示学术诚信提示，不展示讲解正文——不发 chunk/done，
        # 由 Java 转发 error 事件并落库 is_blocked=1
        yield _sse("error", {"code": 60004, "message": block_tip, "retryable": False})
        return

    messages = _prompt(req)
    usage: dict = {}
    if settings.deepseek_api_key:
        # 契约 §6：L1 超时/5xx/限流时重试 1 次，仍失败才降 L2。
        # 已向前端输出部分正文时不重试不降级（会造成正文重复），直接中断由 Java 标记 L3。
        delivered = False
        for attempt in (1, 2):
            try:
                async for delta in _buffered(_stream_openai(
                        f"{settings.deepseek_base_url.rstrip('/')}/chat/completions",
                        settings.deepseek_api_key, settings.llm_model_main, messages,
                        usage, include_usage=True)):
                    delivered = True
                    yield _sse("chunk", {"content": delta})
                if delivered:
                    yield _sse("done", {"degraded": False, "blocked": False,
                                        "modelUsed": settings.llm_model_main,
                                        "promptVersion": PROMPT_VERSION,
                                        "tokens": int(usage.get("completion_tokens", 0)),
                                        "promptTokens": int(usage.get("prompt_tokens", 0)),
                                        "elapsedMs": int((time.time() - start) * 1000)})
                    return
                logger.warning("L1 %s 返回空流（第 %s 次尝试）", settings.llm_model_main, attempt)
            except Exception as exc:
                if delivered:
                    logger.warning("L1 %s 流式中断且已输出正文，不再重试：%s",
                                   settings.llm_model_main, exc)
                    raise
                logger.warning("L1 %s 调用失败（第 %s 次尝试，共 2 次）：%s",
                               settings.llm_model_main, attempt, exc)
                usage.clear()

    if settings.glm_api_key:
        delivered = False
        try:
            yield _sse("degraded", {"level": "L2", "reason": "L1 调用失败"})
            async for delta in _buffered(_stream_openai(
                    f"{settings.glm_base_url.rstrip('/')}/chat/completions",
                    settings.glm_api_key, settings.llm_model_fallback, messages, usage)):
                delivered = True
                yield _sse("chunk", {"content": delta})
            yield _sse("done", {"degraded": True, "blocked": False,
                                "modelUsed": settings.llm_model_fallback,
                                "promptVersion": PROMPT_VERSION,
                                "tokens": int(usage.get("completion_tokens", 0)),
                                "promptTokens": int(usage.get("prompt_tokens", 0)),
                                "elapsedMs": int((time.time() - start) * 1000)})
            return
        except Exception as exc:
            if delivered:
                # 已输出部分 GLM 正文时直接中断，由 Java 标记 L3，不再追加模板造成混合正文
                logger.warning("L2 %s 流式中断且已输出正文，不再追加模板：%s",
                               settings.llm_model_fallback, exc)
                raise
            logger.warning("L2 %s 调用失败，降级本地模板：%s", settings.llm_model_fallback, exc)

    yield _sse("degraded", {"level": "L3", "reason": "L1/L2 均不可用，使用本地模板"})
    for chunk in _template_chunks(req):
        yield _sse("chunk", {"content": chunk})
        await asyncio.sleep(0.02)
    yield _sse("done", {"degraded": True, "blocked": False, "modelUsed": "local-template",
                        "promptVersion": PROMPT_VERSION,
                        "elapsedMs": int((time.time() - start) * 1000)})
