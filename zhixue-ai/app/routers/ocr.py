"""OCR 路由：错题图像 → 文本（契约 2.2 节的识别执行端）。

任务表与轮询由 Java 侧管理；本路由保持无状态，只负责一次识别。
引擎：RapidOCR（PaddleOCR 模型的 ONNX 版，离线、轻量）；PaddleOCR 可替换接入。
"""
from __future__ import annotations

import io
import logging
import time
from typing import Any

import numpy as np
from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from PIL import Image, ImageOps

from app.config import settings
from app.deps import require_internal
from app.schemas import OcrLine, OcrRecognizeOut
from app.services.ocr_quality import aggregate_confidence

router = APIRouter()
logger = logging.getLogger(__name__)

_engine: Any = None


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
async def recognize(image: UploadFile = File(..., description="错题图片，字段名 image")):
    """识别单张图片并返回原文 + 平均置信度。失败由 Java 侧映射 60002。"""
    # P1 修复：先读有限字节检查大小，避免全量 read() 后才查大小导致内存放大 DoS
    chunk = await image.read(settings.ocr_max_image_bytes + 1)
    if not chunk:
        raise HTTPException(status_code=400, detail="图片为空")
    if len(chunk) > settings.ocr_max_image_bytes:
        raise HTTPException(status_code=413, detail="图片超过大小上限")
    # 读剩余部分（如果文件刚好超过一个 chunk 边界）
    remaining = await image.read()
    data = chunk + remaining if remaining else chunk
    try:
        return await run_in_threadpool(_recognize, data)
    except Exception as exc:  # 图片损坏、引擎异常等
        logger.warning("OCR 识别失败 fileName=%s：%s", image.filename or "-", exc)
        raise HTTPException(status_code=422, detail=f"OCR 识别失败: {exc}") from exc
