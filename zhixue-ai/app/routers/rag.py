"""RAG 路由：检索 + 流式讲解（架构 2.1 节 /api/rag）。

路由层只做 HTTP 外壳；讲解编排、降级链与 SSE 帧生成在 app.services.rag_service。
"""
from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import StreamingResponse

from app.schemas import RagExplainRequest, RagRetrieveRequest, RagRetrieveResponse
from app.services import rag_service

router = APIRouter()


@router.post("/explain")
async def explain(req: RagExplainRequest) -> StreamingResponse:
    """流式讲解（SSE）：meta → chunk* → done；合规拦截时发 error 60004（契约 §2）。"""
    return StreamingResponse(
        rag_service.stream_explain(req),
        media_type="text/event-stream",
        headers=rag_service.SSE_HEADERS,
    )


@router.post("/retrieve", response_model=RagRetrieveResponse)
async def retrieve(req: RagRetrieveRequest):
    """检索知识点切片（内部接口，供 Java 诊断模块调用）。"""
    # TODO(P1)：numpy VectorIndex 检索 topK → 低于阈值时 Java 侧映射 60003
    # 未实现前显式返回 501，避免调用方把空结果当成"检索无匹配"（评审 C10）
    _ = req
    raise HTTPException(status_code=501, detail="RAG 检索尚未实现（P1）")


@router.post("/embed")
async def embed_document(kp_id: str = Query(..., alias="kpId", description="知识点编号，如 KP0201")):
    """教师端上传课程资料 → 切片(300-500字) → 向量化入库的触发桩。"""
    # TODO(P1 任务 60)：PPT/PDF 解析切片 → bge-small-zh-v1.5 Embedding → VectorIndex 持久化
    _ = kp_id
    raise HTTPException(status_code=501, detail="向量化入库尚未实现（P1）")
