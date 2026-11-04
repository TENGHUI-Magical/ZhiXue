"""RAG 讲解服务：检索上下文 → 流式讲解（架构 2.1 节 /api/rag）。

讲解降级链：L1 DeepSeek（失败重试 1 次）→ L2 GLM → L3 本地模板（本文件）→ L4 由 Java 侧映射 60001。
L3 模板只使用 Java 传入的 conceptText/misconception/页码，保证来源可追溯、不断章取义。
路由层只负责 HTTP 外壳，SSE 编排与降级全部在本服务内完成。
"""
from __future__ import annotations

import asyncio
import json
import logging
import re
import time
import uuid
from typing import AsyncIterator

import httpx
from fastapi.concurrency import run_in_threadpool

from app.config import settings
from app.schemas import RagExplainRequest
from app.services.compliance import compliance_check

logger = logging.getLogger(__name__)

# Prompt 模板版本：随 _prompt() 结构变更递增，落 ai_generation_log.prompt_version（Git 可回溯）
PROMPT_VERSION = "v2"
PROMPT_TARGET_TOKENS = 3000
PROMPT_HARD_LIMIT_TOKENS = 4000
MAX_OUTPUT_TOKENS = 700

# 降级级别与模型名口径：与 Java 侧 RagExplainService 的 LEVEL_L2/LEVEL_L3/
# MODEL_LOCAL_TEMPLATE 常量一致，任一侧改动必须同步另一侧。
LEVEL_L2 = "L2"  # 有正文但非 L1 完整产出：备用模型完整产出，或流中断/超时截断
LEVEL_L3 = "L3"  # 远程零产出，完全回退本地模板
MODEL_LOCAL_TEMPLATE = "local-template"


def _model_hint() -> str:
    """meta 中告知 Java 侧首个候选模型名，供流中断时保留真实 model（内部字段，不下发前端）。"""
    if settings.deepseek_api_key:
        return settings.llm_model_main
    if settings.glm_api_key:
        return settings.llm_model_fallback
    return MODEL_LOCAL_TEMPLATE


# SSE 响应头：禁用代理缓冲，保持逐帧下发
SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no", "Connection": "keep-alive"}


def _sse(event: str, payload: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _first(text: str | None, limit: int) -> str:
    clean = (text or "").replace("\r", "").replace("\n", "；").strip()
    return clean[:limit]


def _misconceptions(text: str | None, limit: int) -> list[str]:
    """同时兼容真实换行与种子数据中的字面 \\n，最多返回指定条数。"""
    items = re.split(r"(?:\r?\n|\\n)", text or "")
    return [item.strip() for item in items if item.strip()][:limit]


def _prompt_tokens(messages: list[dict]) -> int:
    """无新增 tokenizer 依赖的保守预算：每字符按 1 token，另计每条消息 8 token。"""
    return sum(len(message["content"]) + 8 for message in messages)


def _retrieval_lines(req: RagExplainRequest, hits: list | None) -> list[str]:
    lines: list[str] = []
    budget = 800
    concept = (req.conceptText or "").strip()
    for hit in (hits or [])[:2]:
        content = (hit.chunk.content or "").strip()
        if not content or content in concept:
            continue
        content = content[:min(400, budget)]
        if not content:
            break
        lines.append(f"[{hit.chunk.kp_id}] {content}")
        budget -= len(content)
        if budget <= 0:
            break
    return lines


def _trim_tail(text: str, excess: int) -> str:
    return text[:max(0, len(text) - excess)].rstrip()


def _prompt(req: RagExplainRequest, hits: list | None = None) -> list[dict]:
    system = (
        "你是数据库原理课程的助教。只做思路点拨，不给最终答案或完整解题过程。"
        "必须依据给定教材证据讲解，不得编造教材页码或引用。"
        "有具体作答时才分析该作答；未提供具体作答时不得虚构学生步骤。"
    )
    question = _first(req.questionText, 1000) or "（只讲概念）"
    my_answer = _first(req.myAnswer, 300) or "未提供具体作答"
    concept = _first(req.conceptText, 600)
    mistakes = [_first(item, 125) for item in _misconceptions(req.misconception, 2)]
    prereq = req.prereqWeakness if req.prereqWeakness and req.prereqWeakness.sampleCount >= 3 else None
    prereq_concept = _first(prereq.conceptText, 200) if prereq else ""
    retrieval = _retrieval_lines(req, hits)

    def build() -> list[dict]:
        parts = [
            "[当前问题]",
            f"知识点：{req.kpName}（{req.kpId}）",
            f"错因标签：{req.errorType or '未提供'}",
            f"作答次数：第 {req.attemptCount} 次作答，其中第 {req.wrongCount} 次答错",
            "[学生证据]",
            f"题干：{question}",
            f"最近作答：{my_answer}",
            "[教材证据]",
            f"教材页码：{req.textbookPage or '未提供'}",
            f"概念切片：{concept or '未提供'}",
        ]
        if mistakes:
            parts.extend(["常见误区：", *[f"- {item}" for item in mistakes]])
        else:
            parts.append("常见误区：未提供")
        if prereq:
            parts.extend([
                "[先修弱项]",
                f"先修知识点：{prereq.kpName}（{prereq.kpId}）",
                f"掌握度：{prereq.mastery:.3f}；样本数：{prereq.sampleCount}",
            ])
            if prereq_concept:
                parts.append(f"概念提示：{prereq_concept}")
        if retrieval:
            parts.extend(["[检索切片]", *retrieval])
        parts.extend([
            "[输出格式]",
            "【错因定位】不超过 3 句，只分析这一步为什么错。",
            "【完整讲解】不超过 3 段，每段不超过 120 字。",
            "【自查】只提出 1 个能动手验证的问题。",
        ])
        return [
            {"role": "system", "content": system},
            {"role": "user", "content": "\n".join(parts)},
        ]

    messages = build()
    # 保留优先级：学生作答 > 作答次数 > 先修弱项 > 检索切片。
    # 实际裁撤反向进行：检索 → 先修概念 → 当前概念尾部 → 误区 → 题干尾部；
    # 仅 4000 硬上限仍无法满足时才最后裁学生作答，正常字段上限下不会走到该分支。
    for limit, allow_answer_trim in (
            (PROMPT_TARGET_TOKENS, False), (PROMPT_HARD_LIMIT_TOKENS, True)):
        while _prompt_tokens(messages) > limit:
            excess = _prompt_tokens(messages) - limit
            if retrieval:
                retrieval.pop()
            elif prereq_concept:
                prereq_concept = ""
            elif concept:
                concept = _trim_tail(concept, excess)
            elif mistakes:
                mistakes.pop()
            elif question != "（只讲概念）" and question:
                question = _trim_tail(question, excess)
            elif allow_answer_trim and my_answer != "未提供具体作答" and my_answer:
                my_answer = _trim_tail(my_answer, excess)
            else:
                break
            messages = build()
    return messages


def _request_payload(model: str, messages: list[dict], include_usage: bool) -> dict:
    payload = {
        "model": model,
        "messages": messages,
        "stream": True,
        "temperature": 0.3,
        "max_tokens": MAX_OUTPUT_TOKENS,
    }
    if include_usage:
        payload["stream_options"] = {"include_usage": True}
    return payload


async def _stream_openai(url: str, key: str, model: str, messages: list[dict],
                         usage: dict, include_usage: bool = False) -> AsyncIterator[str]:
    """OpenAI 兼容流式调用（DeepSeek / GLM 共用）。异常由调用方降级。"""
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    payload = _request_payload(model, messages, include_usage)
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


_PAGE_RE = re.compile(
    r"(?<![A-Za-z0-9])P\s*(\d+)(?:\s*[—–-]\s*(\d+))?"
    r"|第\s*(\d+)(?:\s*[—–-]\s*(\d+))?\s*页",
    re.IGNORECASE,
)
_SENTENCE_END_RE = re.compile(r"[。！？!?；;\n]")
_OPTION_LEAK_RE = re.compile(
    r"(?:正确答案|正确选项|答案|应选|选)\s*(?:是|为|选|：|:)?\s*([A-D])(?:\s*项)?(?![A-Za-z])",
    re.IGNORECASE,
)
_OPTION_ANSWER_RE = re.compile(r"^\s*([A-D])(?:\s*项)?\s*$", re.IGNORECASE)
_NEGATION_BEFORE_RE = re.compile(r"(?:不能|不应|不|别|排除)\s*$")


def _page_bounds(match: re.Match) -> tuple[int, int]:
    start = int(match.group(1) or match.group(3))
    end = int(match.group(2) or match.group(4) or start)
    return min(start, end), max(start, end)


def _pages(text: str) -> set[int]:
    allowed: set[int] = set()
    for match in _PAGE_RE.finditer(text or ""):
        start, end = _page_bounds(match)
        allowed.update(range(start, end + 1))
    return allowed


def sanitize_page_citations(text: str, textbook_page: str | None) -> str:
    """只保留知识点白名单内的页码；越界引用替换为不带页码的章节提示。"""
    allowed = _pages(textbook_page or "")

    def replace(match: re.Match) -> str:
        start, end = _page_bounds(match)
        if set(range(start, end + 1)) <= allowed:
            return match.group(0)
        prefix = match.string[:match.start()]
        return "相关章节" if prefix.endswith(("教材", "》")) else "教材相关章节"

    return _PAGE_RE.sub(replace, text or "")


def _normalized_answer(text: str) -> str:
    return "".join(ch.lower() for ch in (text or "") if ch.isalnum())


def is_answer_leak(sentence: str, answer_text: str | None) -> bool:
    """识别答案原文、归一化原文及选项泄露句式；解释性/否定引用不误判。"""
    answer = (answer_text or "").strip()
    if not answer or not sentence:
        return False
    option = _OPTION_ANSWER_RE.match(answer)
    if option:
        expected = option.group(1).upper()
        for match in _OPTION_LEAK_RE.finditer(sentence):
            if match.group(1).upper() != expected:
                continue
            if _NEGATION_BEFORE_RE.search(sentence[:match.start()]):
                continue
            return True
        return False
    if answer.lower() in sentence.lower():
        return True
    normalized = _normalized_answer(answer)
    return len(normalized) >= 4 and normalized in _normalized_answer(sentence)


async def _guard_pages(stream: AsyncIterator[str], textbook_page: str | None,
                       answer_text: str | None = None) -> AsyncIterator[str]:
    """逐句校验页码并删除答案泄露句；不缓冲整篇讲解。"""
    buf = ""
    async for delta in stream:
        buf += delta
        while True:
            boundary = _SENTENCE_END_RE.search(buf)
            if boundary is None:
                break
            end = boundary.end()
            sentence, buf = buf[:end], buf[end:]
            clean = sanitize_page_citations(sentence, textbook_page)
            if is_answer_leak(clean, answer_text):
                logger.warning("检测到答案泄露句，已删除该句")
            else:
                yield clean
    if buf:
        clean = sanitize_page_citations(buf, textbook_page)
        if not is_answer_leak(clean, answer_text):
            yield clean
        else:
            logger.warning("检测到答案泄露句，已删除该句")


def _template_chunks(req: RagExplainRequest) -> list[str]:
    """L3 学习卡：只给知识点定位、页码、最多三条误区与练习入口。"""
    name = req.kpName or req.kpId
    page = req.textbookPage or "页码待确认"
    mistakes = [_first(item, 90) for item in _misconceptions(req.misconception, 3)]
    parts = ["【学习卡】", f"【知识点】{name}；教材{page}", "【常见误区】"]
    if mistakes:
        parts.extend(f"{index}. {item}" for index, item in enumerate(mistakes, start=1))
    else:
        parts.append("暂未登记常见误区，请对照教材概念自查。")
    parts.append("【练习提示】请完成下方同类练习，检验自己是否真正掌握。")
    return ["\n".join(parts)]


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
    hits = []
    # 索引有内容时用检索命中的多来源增强；索引为空/失败时保持原有单来源，不影响降级链
    try:
        from app.services import vector_index
        # bge 首次加载可能耗时数秒，放到线程池，避免阻塞其他 SSE 流
        hits = await run_in_threadpool(
            vector_index.retrieve,
            req.questionText or req.conceptText,
            settings.rag_top_k,
            settings.rag_score_threshold,
            req.kpId,
            req.courseId,
        )
        if hits:
            # 保留当前知识点来源，再追加检索到的其他来源，避免把主来源替换掉
            seen = {req.kpId}
            for hit in hits:
                if hit.chunk.kp_id in seen:
                    continue
                seen.add(hit.chunk.kp_id)
                sources.append({
                    "kpId": hit.chunk.kp_id,
                    "name": hit.chunk.kp_name or hit.chunk.kp_id,
                    "ref": f"《数据库系统概论（第6版）》{hit.chunk.ref or '页码待确认'}",
                })
    except Exception as exc:  # 检索是增强项，不允许中断讲解
        logger.warning("RAG 检索增强失败，保持单来源：%s", exc)
    blocked, block_tip = compliance_check(req.questionText or "")

    yield _sse("meta", {"traceId": trace_id, "sources": sources, "model": _model_hint()})
    if blocked:
        # 契约 §2 60004：展示学术诚信提示，不展示讲解正文——不发 chunk/done，
        # 由 Java 转发 error 事件并落库 is_blocked=1
        yield _sse("error", {"code": 60004, "message": block_tip, "retryable": False})
        return

    messages = _prompt(req, hits)
    usage: dict = {}
    if settings.deepseek_api_key:
        # 契约 §6：L1 超时/5xx/限流时重试 1 次，仍失败才降 L2。
        # 已向前端输出部分正文时不重试不降级（会造成正文重复），直接中断由 Java 记为 L2（部分产出）。
        delivered = False
        for attempt in (1, 2):
            try:
                async for delta in _guard_pages(_buffered(_stream_openai(
                        f"{settings.deepseek_base_url.rstrip('/')}/chat/completions",
                        settings.deepseek_api_key, settings.llm_model_main, messages,
                        usage, include_usage=True)), req.textbookPage, req.answerText):
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
            yield _sse("degraded", {"level": LEVEL_L2, "reason": "L1 调用失败",
                                    "modelUsed": settings.llm_model_fallback})
            async for delta in _guard_pages(_buffered(_stream_openai(
                    f"{settings.glm_base_url.rstrip('/')}/chat/completions",
                    settings.glm_api_key, settings.llm_model_fallback, messages, usage)),
                    req.textbookPage, req.answerText):
                delivered = True
                yield _sse("chunk", {"content": delta})
            if delivered:
                yield _sse("done", {"degraded": True, "blocked": False,
                                    "modelUsed": settings.llm_model_fallback,
                                    "promptVersion": PROMPT_VERSION,
                                    "tokens": int(usage.get("completion_tokens", 0)),
                                    "promptTokens": int(usage.get("prompt_tokens", 0)),
                                    "elapsedMs": int((time.time() - start) * 1000)})
                return
            logger.warning("L2 输出全部被过滤，降级本地模板")
        except Exception as exc:
            if delivered:
                # 已输出部分 GLM 正文时直接中断，由 Java 记为 L2（保留 glm 模型名），不再追加模板造成混合正文
                logger.warning("L2 %s 流式中断且已输出正文，不再追加模板：%s",
                               settings.llm_model_fallback, exc)
                raise
            logger.warning("L2 %s 调用失败，降级本地模板：%s", settings.llm_model_fallback, exc)

    yield _sse("degraded", {"level": LEVEL_L3, "reason": "L1/L2 均不可用，使用本地模板"})
    for chunk in _template_chunks(req):
        yield _sse("chunk", {"content": sanitize_page_citations(chunk, req.textbookPage)})
        await asyncio.sleep(0.02)
    yield _sse("done", {"degraded": True, "blocked": False, "modelUsed": MODEL_LOCAL_TEMPLATE,
                        "promptVersion": PROMPT_VERSION,
                        "elapsedMs": int((time.time() - start) * 1000)})
