"""Pydantic 模型：与《前后端接口契约文档》一一对应（Java 侧经由网关调用的收口结构）。"""
from __future__ import annotations

from typing import Any, Literal, Optional

from pydantic import BaseModel, Field


# ===== /api/ocr =====
class OcrTaskOut(BaseModel):
    """OCR 任务状态（契约 2.2 节轮询结构）。"""
    taskId: str
    status: Literal["PENDING", "RUNNING", "SUCCESS", "FAILED"]
    confidence: Optional[float] = None
    fields: Optional[dict[str, Any]] = Field(
        default=None,
        description="题干/选项/答案分块及题型难度预判，置信度需人工校正时前端高亮",
    )


# ===== /api/rag =====
class RagRetrieveRequest(BaseModel):
    """检索请求（内部接口，供 Java 诊断模块调用）。"""
    logId: str = Field(description="错题记录 EL_xxx")
    kpId: Optional[str] = None
    topK: int = 3


class RagSource(BaseModel):
    kpId: str
    name: str
    ref: str = Field(description="教材页码/讲义章节，讲解引用来源（禁止无来源输出）")
    content: str
    score: float


class RagRetrieveResponse(BaseModel):
    sources: list[RagSource]


# ===== /api/llm =====
class ChatMessage(BaseModel):
    role: Literal["system", "user", "assistant"]
    content: str


class ChatRequest(BaseModel):
    model: Optional[str] = Field(default=None, description="缺省走主力 DeepSeek-V3")
    messages: list[ChatMessage]
    temperature: float = Field(default=0.7, ge=0, le=2, description="采样温度，越界返回 422（契约 1.2 参数校验口径）")
    stream: bool = False


class ChatResponse(BaseModel):
    modelUsed: str
    content: str
    blockFlag: bool = Field(description="是否触发合规拦截（规划书任务 58A）")
    promptTokens: int = 0
    completionTokens: int = 0