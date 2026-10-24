"""OCR 路由：错题图像 → 文本（契约 2.2 节的识别执行端）。

任务表与轮询由 Java 侧管理；本路由保持无状态，只负责一次识别。
引擎：RapidOCR（PaddleOCR 模型的 ONNX 版，离线、轻量）；PaddleOCR 可替换接入。
"""
from __future__ import annotations

import io
import time
from typing import Any

import numpy as np
from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from PIL import Image, ImageOps

from app.schemas import OcrLine, OcrRecognizeOut

router = APIRouter()

MAX_IMAGE_BYTES = 5 * 1024 * 1024

_engine: Any = None


def _get_engine() -> Any:
    global _engine
    if _engine is None:
        from rapidocr_onnxruntime import RapidOCR

        _engine = RapidOCR()
    return _engine


def _recognize(data: bytes) -> OcrRecognizeOut:
    start = time.time()
    # 契约 V2 §4：先按 EXIF 旋转，再压到长边 ≤1600 后识别
    image = ImageOps.exif_transpose(Image.open(io.BytesIO(data))).convert("RGB")
    image.thumbnail((1600, 1600), Image.LANCZOS)
    result, _ = _get_engine()(np.array(image))
    lines: list[OcrLine] = []
    for item in result or []:
        # RapidOCR 返回 [box, text, score]
        text = str(item[1]).strip()
        if text:
            lines.append(OcrLine(text=text, score=round(float(item[2]), 4)))
    text = "\n".join(line.text for line in lines)
    confidence = round(sum(line.score for line in lines) / len(lines), 3) if lines else 0.0
    return OcrRecognizeOut(
        text=text,
        confidence=confidence,
        lines=lines,
        elapsedMs=int((time.time() - start) * 1000),
    )


@router.post("/recognize", response_model=OcrRecognizeOut)
async def recognize(image: UploadFile = File(..., description="错题图片，字段名 image")):
    """识别单张图片并返回原文 + 平均置信度。失败由 Java 侧映射 60002。"""
    data = await image.read()
    if not data:
        raise HTTPException(status_code=400, detail="图片为空")
    if len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="图片超过 5MB")
    try:
        return await run_in_threadpool(_recognize, data)
    except Exception as exc:  # 图片损坏、引擎异常等
        raise HTTPException(status_code=422, detail=f"OCR 识别失败: {exc}") from exc
