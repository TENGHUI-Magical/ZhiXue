"""RAG 路由：检索 + 流式讲解（架构 2.1 节 /api/rag）。

路由层只做 HTTP 外壳；讲解编排、降级链与 SSE 帧生成在 app.services.rag_service。
"""
from __future__ import annotations

from fastapi import APIRouter, Depends
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import StreamingResponse

from app.deps import require_internal
from app.schemas import (RagEmbedRequest, RagEmbedResponse, RagExplainRequest, RagRetrieveRequest,
                         RagRetrieveResponse, RagSource)
from app.services import rag_service, vector_index

router = APIRouter()


@router.post("/explain", dependencies=[Depends(require_internal)])
async def explain(req: RagExplainRequest) -> StreamingResponse:
    """流式讲解（SSE）：meta → chunk* → done；合规拦截时发 error 60004（契约 §2）。"""
    return StreamingResponse(
        rag_service.stream_explain(req),
        media_type="text/event-stream",
        headers=rag_service.SSE_HEADERS,
    )


@router.post("/retrieve", response_model=RagRetrieveResponse, dependencies=[Depends(require_internal)])
async def retrieve(req: RagRetrieveRequest):
    """检索知识点切片（内部接口）：topK=5、阈值 0.35；无命中返回空 sources。"""
    query = (req.text or "").strip()
    if not query:
        return RagRetrieveResponse(sources=[])
    hits = await run_in_threadpool(
        vector_index.retrieve, query, req.topK, req.threshold, req.kpId, req.courseId
    )
    sources = [
        RagSource(
            kpId=hit.chunk.kp_id,
            name=hit.chunk.kp_name or hit.chunk.kp_id,
            ref=f"《数据库系统概论（第6版）》{hit.chunk.ref or '页码待确认'}",
            content=hit.chunk.content,
            score=hit.score,
        )
        for hit in hits
    ]
    return RagRetrieveResponse(sources=sources)


@router.post("/embed", response_model=RagEmbedResponse, dependencies=[Depends(require_internal)])
async def embed_document(req: RagEmbedRequest):
    """按 kpId 将文本切片（300—500 字、重叠 50）并写入 numpy 向量索引。"""
    if not req.text.strip():
        return RagEmbedResponse(kpId=req.kpId, chunks=0, mode="empty")
    count, mode = await run_in_threadpool(
        vector_index.embed_document, req.kpId, req.text, req.kpName,
        req.courseId, req.chapterId, req.ref,
    )
    return RagEmbedResponse(kpId=req.kpId, chunks=count, mode=mode)
