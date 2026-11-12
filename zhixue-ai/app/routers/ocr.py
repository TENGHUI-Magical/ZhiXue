"""OCR 路由：错题图像 → 文本（契约 2.2 节的识别执行端）。

任务表与轮询由 Java 侧管理；本路由保持无状态，只负责一次识别。
引擎：RapidOCR（PaddleOCR 模型的 ONNX 版，离线、轻量）；PaddleOCR 可替换接入。
"""
from __future__ import annotations

import asyncio
import io
import logging
import time
from typing import Any

import numpy as np
from fastapi import APIRouter, Depends, File, Header, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from PIL import Image, ImageOps

from app.config import settings
from app.deps import require_internal
from app.schemas import OcrLine, OcrRecognizeOut
from app.services.ocr_quality import aggregate_confidence

router = APIRouter()
logger = logging.getLogger(__name__)

_engine: Any = None

# ---- P1-1：上传类型白名单 / magic bytes / 像素上限 / 并发上限 ----
_ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp"}
_MAGIC_JPEG = b"\xff\xd8\xff"
_MAGIC_PNG = b"\x89PNG\r\n\x1a\n"
# 防 decompression bomb：显式设比 PIL 默认更保守的上限（默认仅告警不拦截）
Image.MAX_IMAGE_PIXELS = 20_000_000
# 泛化文案：底层库异常（路径/版本/栈片段）只写服务端日志，不回显给调用方
_OCR_ERROR_DETAIL = "OCR 识别失败，请重试或更换图片"

# OCR 是重 CPU 推理：限制并发，避免打满 anyio 线程池拖慢 /api/rag/explain
_OCR_SEMAPHORE: asyncio.Semaphore | None = None
_OCR_SEMAPHORE_LOOP = None
_MAX_CONCURRENT_OCR = 4


def _ocr_semaphore() -> asyncio.Semaphore:
    """惰性创建并发信号量；按事件循环重建，兼容 TestClient/多 loop 场景。"""
    global _OCR_SEMAPHORE, _OCR_SEMAPHORE_LOOP
    loop = asyncio.get_running_loop()
    if _OCR_SEMAPHORE is None or _OCR_SEMAPHORE_LOOP is not loop:
        _OCR_SEMAPHORE = asyncio.Semaphore(_MAX_CONCURRENT_OCR)
        _OCR_SEMAPHORE_LOOP = loop
    return _OCR_SEMAPHORE


def _valid_magic(data: bytes) -> bool:
    """不信任客户端声明的 content_type，用文件头 magic bytes 兜底校验。"""
    if data.startswith(_MAGIC_JPEG) or data.startswith(_MAGIC_PNG):
        return True
    # WEBP：RIFF + 4 字节长度 + WEBP
    return len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP"


def _get_engine() -> Any:
    global _engine
    if _engine is None:
        from rapidocr_onnxruntime import RapidOCR

        _engine = RapidOCR()
    return _engine


def _recognize(data: bytes) -> OcrRecognizeOut:
    start = time.time()
    # 契约 V2 §4：先按 EXIF 旋转，再压到长边 ≤ 上限（配置化）后识别
    image = ImageOps.exif_transpose(Image.open(io.BytesIO(data))).convert("RGB")
    side = settings.ocr_max_side
    image.thumbnail((side, side), Image.LANCZOS)
    result, _ = _get_engine()(np.array(image))
    lines: list[OcrLine] = []
    for item in result or []:
        # RapidOCR 返回 [box, text, score]
        text = str(item[1]).strip()
        if text:
            lines.append(OcrLine(text=text, score=round(float(item[2]), 4)))
    text = "\n".join(line.text for line in lines)
    # 置信度升级（docs/05 口径）：字符长度加权均值替代行数算术平均——
    # 每个字符是一次识别试验，长行证据权重更高；契约字段与 0.75 告警阈值不变
    stats = aggregate_confidence([(line.text, line.score) for line in lines])
    logger.info("OCR 置信度 stats weighted=%.3f wilsonLower=%.3f nEff=%.1f lines=%d",
                stats.weighted, stats.wilson_lower, stats.n_eff, stats.line_count)
    return OcrRecognizeOut(
        text=text,
        confidence=round(stats.weighted, 3),
        lines=lines,
        elapsedMs=int((time.time() - start) * 1000),
    )


@router.post("/recognize", response_model=OcrRecognizeOut, dependencies=[Depends(require_internal)])
async def recognize(
    image: UploadFile = File(..., description="错题图片，字段名 image"),
    x_trace_id: str = Header(default="", alias="X-Trace-Id"),
):
    """识别单张图片并返回原文 + 平均置信度。失败由 Java 侧映射 60002。

    docs/01 §1.2：traceId 由 Java 侧经 X-Trace-Id 透传，这里只回显到日志做全链路留痕；
    可选 header，缺省不影响既有调用，OcrRecognizeOut 契约字段保持不变。
    """
    # 截断防超长/日志注入，缺省回显 "-" 与 RAG 链路口径一致
    trace = (x_trace_id or "-")[:64]
    # P1 修复：先读有限字节检查大小，避免全量 read() 后才查大小导致内存放大 DoS
    chunk = await image.read(settings.ocr_max_image_bytes + 1)
    if not chunk:
        raise HTTPException(status_code=400, detail="图片为空")
    if len(chunk) > settings.ocr_max_image_bytes:
        raise HTTPException(status_code=413, detail="图片超过大小上限")
    # 声明的 content_type 先做快速拒绝（真正类型仍以 magic bytes 为准）
    ctype = (image.content_type or "").lower()
    if ctype not in _ALLOWED_CONTENT_TYPES:
        raise HTTPException(status_code=415, detail="不支持的图片类型")
    # 读剩余部分（如果文件刚好超过一个 chunk 边界）
    remaining = await image.read()
    data = chunk + remaining if remaining else chunk
    if not _valid_magic(data):
        raise HTTPException(status_code=415, detail="图片内容校验失败")
    async with _ocr_semaphore():
        try:
            out = await run_in_threadpool(_recognize, data)
        except Exception as exc:  # 图片损坏、引擎异常等
            # P1-4：异常详情只写服务端日志，422 回显固定泛化文案，避免泄露内部信息
            logger.warning("OCR 识别失败 traceId=%s fileName=%s：%s",
                           trace, image.filename or "-", exc)
            raise HTTPException(status_code=422, detail=_OCR_ERROR_DETAIL) from exc
    logger.info("OCR 识别完成 traceId=%s confidence=%s elapsedMs=%s",
                trace, out.confidence, out.elapsedMs)
    return out
