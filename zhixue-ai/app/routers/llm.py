"""LLM 路由：多模型网关 + 合规拦截器（规划书任务 58A，申报书合规承诺的技术落地）。"""
from __future__ import annotations

from fastapi import APIRouter

from app.config import settings
from app.schemas import ChatRequest, ChatResponse

router = APIRouter()

# 模型名配置驱动（改模型只改 .env，不改代码；全局规划冲突 #3/#5）
# 规划书 7.2 节模型分层：主力 DeepSeek-V3 / 兜底 GLM-4-Flash
# 合规拦截词表（骨架版）：正式版扩展至 Prompt 注入特征与代写意图识别，计入 ai_generation_log
BLOCK_KEYWORDS = ["直接给答案", "帮我写作业", "代写", "求出完整答案不要过程"]


def compliance_check(text: str) -> tuple[bool, str | None]:
    """命中代写意图 → 返回学术诚信提示，讲解定位为"思路点拨"而非"答案交付"。"""
    for kw in BLOCK_KEYWORDS:
        if kw in text:
            return True, "检测到代写请求倾向。本系统仅提供启发式讲解与思路引导，请独立完成作答。"
    return False, None


@router.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest):
    """通用对话（内部接口）。骨架期先落地合规拦截链路，模型调用下一阶段接入。"""
    last_user_text = next((m.content for m in reversed(req.messages) if m.role == "user"), "")
    block_flag, block_tip = compliance_check(last_user_text)
    if block_flag:
        return ChatResponse(modelUsed="-", content=block_tip or "", blockFlag=True)

    # TODO(P0 任务 55)：openai SDK（base_url 切换 DeepSeek/GLM）→ 超时(settings.llm_timeout_seconds)
    #        重试 1 次 → settings.llm_model_fallback 兜底 → 双失败时 Java 侧映射 60001，前端缓存解析 + 重试按钮
    model = req.model or settings.llm_model_main
    return ChatResponse(modelUsed=model, content="（骨架响应）模型调用通道待接入", blockFlag=False)