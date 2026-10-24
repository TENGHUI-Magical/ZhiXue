"""Pydantic 模型：与《前后端接口契约文档》一一对应（Java 侧经由网关调用的收口结构）。"""
from __future__ import annotations

from typing import Any, Literal, Optional

from pydantic import BaseModel, Field


# ===== /api/ocr =====
class OcrLine(BaseModel):
    """单行识别结果。"""
    text: str
    score: float


class OcrRecognizeOut(BaseModel):
    """OCR 识别结果：先只返回原文与置信度，结构化字段由前端人工确认。"""
    text: str
    confidence: float
    lines: list[OcrLine]
    elapsedMs: int


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
class RagExplainRequest(BaseModel):
    """RAG 讲解请求：Java 侧已从 MySQL 取出知识点上下文，Python 不再查库。"""
    kpId: str = Field(description="知识点编号，如 KP0201")
    kpName: str = Field(default="", description="知识点名称，用于 prompt 与模板")
    conceptText: str = Field(default="", description="300—500 字概念切片，L1 与 L3 共用")
    misconception: Optional[str] = Field(default=None, description="常见误区，换行分隔")
    textbookPage: Optional[str] = Field(default=None, description="教材页码，来源追溯")
    questionText: Optional[str] = Field(default=None, description="学生错题题干；为空时做概念讲解")
    errorType: Optional[str] = Field(default=None, description="契约五类错因英文 Key")
    courseId: int = Field(default=1, description="课程 ID")
    logId: Optional[str] = Field(default=None, description="错题记录编号；竖切阶段可为空")
    traceId: Optional[str] = Field(default=None, description="Java 生成的链路追踪 ID（契约 §1.2），优先回显")


class RagRetrieveRequest(BaseModel):
    """检索请求（内部接口，供 Java 诊断模块调用）。"""
    logId: str = Field(description="错题记录 EL_xxx")
    kpId: Optional[str] = None
    topK: int = Field(default=5, ge=3, le=8, description="检索切片数，算法文档默认 5")


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
    # 讲解场景固定低温度，保证同一错题多次生成结果稳定且可缓存（算法文档 4.3 节）
    temperature: float = Field(default=0.3, ge=0, le=2, description="采样温度，越界返回 422（契约 1.2 参数校验口径）")
    stream: bool = False


class ChatResponse(BaseModel):
    modelUsed: str
    content: str
    blockFlag: bool = Field(description="是否触发合规拦截（规划书任务 58A）")
    promptTokens: int = 0
    completionTokens: int = 0