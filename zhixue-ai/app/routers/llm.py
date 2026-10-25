"""LLM 路由：多模型网关 + 合规拦截器（规划书任务 58A，申报书合规承诺的技术落地）。"""
from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.config import settings
from app.schemas import ChatRequest, ChatResponse
from app.services.compliance import compliance_check

router = APIRouter()

# 模型名配置驱动（改模型只改 .env，不改代码；全局规划冲突 #3/#5）
# 规划书 7.2 节模型分层：主力 DeepSeek-V3 / 兜底 GLM-4-Flash


@router.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest):
    """通用对话（内部接口）。骨架期先落地合规拦截链路，模型调用下一阶段接入。"""
    last_user_text = next((m.content for m in reversed(req.messages) if m.role == "user"), "")
    block_flag, block_tip = compliance_check(last_user_text)
    if block_flag:
        return ChatResponse(modelUsed="-", content=block_tip or "", blockFlag=True)

    # TODO(P0 任务 4.7)：openai SDK（base_url 切换 DeepSeek/GLM）→ 首字节 10s 超时
    #        重试 1 次 → settings.llm_model_fallback 兜底 → 双失败时 Java 侧映射 60001
    # 未接入前显式 501，禁止返回伪造正文（评审 C10）
    _ = (req, settings)
    raise HTTPException(status_code=501, detail="模型调用通道尚未接入（P0 任务 4.7）")