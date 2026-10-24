"""RAG 路由：Embedding + 检索 + Prompt 组装（架构 2.1 节 /api/rag）。"""
from __future__ import annotations

from fastapi import APIRouter, Query

from app.schemas import RagRetrieveRequest, RagRetrieveResponse

router = APIRouter()


@router.post("/retrieve", response_model=RagRetrieveResponse)
async def retrieve(req: RagRetrieveRequest):
    """检索知识点切片（内部接口，供 Java 诊断模块调用）。

    TODO(P0)：numpy VectorIndex 检索 topK（默认 3）→ 低于阈值时 Java 侧映射 60003
              （向量库无匹配 → 提示人工选择知识点，见需求规格说明书 6.2 节降级规则）
    引用来源字段 ref 必须真实存在（教材页码/讲义段落），讲解页底部强制展示。
    """
    _ = req  # 骨架期占位
    return RagRetrieveResponse(sources=[])


@router.post("/embed")
async def embed_document(kp_id: str = Query(..., alias="kpId", description="知识点编号，如 KP0101")):
    """教师端上传课程资料 → 切片(300-500字) → 向量化入库的触发桩。

    参数名统一 camelCase（契约 1.4 命名约定）；TODO(P1 任务 60)：PPT/PDF 解析切片
    → bge-small-zh-v1.5 Embedding → VectorIndex 持久化。
    """
    return {"kpId": kp_id, "status": "PENDING"}