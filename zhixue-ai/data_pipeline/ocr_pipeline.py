#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OCR 提取引擎：矩阵预处理 + 多引擎降级链 + 置信度统计过滤。

降级链（全部懒加载，按顺序取第一个可用引擎）：
    RapidOCR（本项目生产标准，ONNX 离线）
      → PaddleOCR → EasyOCR → Tesseract（pytesseract，中文需 chi_sim 语言包）

预处理（可选，data_pipeline.image_math）：
    灰度化 → 可分离高斯降噪 → Otsu 二值化 → 行投影方差倾斜校正 → 仿射回正

置信度过滤（app.services.ocr_quality）：
    行级：score < line_floor 直接剔除（默认 0.5）；
    页级：Wilson 95% 下界 < page_floor 判为脏样本（默认 0.60），
    单行高置信无法伪装整页合格（n_eff 修正）。

用法（在 zhixue-ai 目录执行）：
    python -m data_pipeline.ocr_pipeline --images data/upload/ --out data/ocr_out.jsonl
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

import numpy as np
from PIL import Image

from app.services.ocr_quality import aggregate_confidence


def load_engine() -> tuple[str, object]:
    """按降级链取第一个可用的 OCR 引擎，返回 (引擎名, 实例)。"""
    errors: list[str] = []
    try:
        from rapidocr_onnxruntime import RapidOCR
        return "rapidocr", RapidOCR()
    except Exception as exc:
        errors.append(f"rapidocr: {exc}")
    try:
        from paddleocr import PaddleOCR
        return "paddleocr", PaddleOCR(use_angle_cls=True, lang="ch", show_log=False)
    except Exception as exc:
        errors.append(f"paddleocr: {exc}")
    try:
        import easyocr
        return "easyocr", easyocr.Reader(["ch_sim", "en"], verbose=False)
    except Exception as exc:
        errors.append(f"easyocr: {exc}")
    try:
        import pytesseract
        return "tesseract", pytesseract
    except Exception as exc:
        errors.append(f"tesseract: {exc}")
    raise SystemExit("无可用 OCR 引擎：\n  " + "\n  ".join(errors))


def _engine_lines(engine_name: str, engine: object, image: np.ndarray
                  ) -> list[tuple[str, float]]:
    """统一各引擎输出为 [(text, score), ...]。"""
    if engine_name == "rapidocr":
        raw, _ = engine(image)
        return [(str(item[1]).strip(), float(item[2])) for item in raw or []
                if str(item[1]).strip()]
    if engine_name == "paddleocr":
        raw = engine.ocr(image, cls=True)
        result = []
        for page in raw or []:
            for item in page or []:
                result.append((str(item[1][0]).strip(), float(item[1][1])))
        return [(t, s) for t, s in result if t]
    if engine_name == "easyocr":
        return [(str(item[1]).strip(), float(item[2]))
                for item in engine.readtext(image) if str(item[1]).strip()]
    # tesseract：无行级置信度时用 image_to_data 的 conf 列
    import pytesseract
    data = pytesseract.image_to_data(
        image, lang="chi_sim+eng", output_type=pytesseract.Output.DICT)
    lines = []
    for text, conf in zip(data["text"], data["conf"]):
        text = str(text).strip()
        if text and float(conf) >= 0:
            lines.append((text, float(conf) / 100.0))
    return lines


def preprocess(image: np.ndarray, *, denoise_sigma: float | None = 1.0,
               correct_skew: bool = True) -> tuple[np.ndarray, float]:
    """矩阵预处理；denoise_sigma=None 跳过高斯降噪。返回 (处理图, 倾斜角)。"""
    from data_pipeline.image_math import deskew, gaussian_blur, to_grayscale
    gray = to_grayscale(image)
    angle = 0.0
    if correct_skew:
        gray, angle = deskew(gray, sigma=denoise_sigma or 1.0)
    elif denoise_sigma is not None:
        gray = gaussian_blur(gray, denoise_sigma)
    return gray, angle


def run_ocr(image_path: Path, engine_name: str, engine: object, *,
            denoise_sigma: float | None = 1.0, correct_skew: bool = True,
            line_floor: float = 0.5, page_floor: float = 0.60) -> dict:
    """单图 OCR 全流程：预处理 → 识别 → 行级过滤 → 页级 Wilson 门禁。

    脏样本判定（两条件取或）：
    1. 加权置信度 < 0.80：点估计本身不合格，无论证据多少；
    2. Wilson 下界 < page_floor 且加权 < 0.90：证据不足且非高置信——
       短页（5 行 0.99）不误伤，"证据少 + 质量平庸"的页才转人工。
    """
    started = time.time()
    image = np.array(Image.open(image_path).convert("RGB"))
    processed, skew_angle = preprocess(
        image, denoise_sigma=denoise_sigma, correct_skew=correct_skew)
    raw_lines = _engine_lines(engine_name, engine, processed)

    kept = [(text, score) for text, score in raw_lines if score >= line_floor]
    stats = aggregate_confidence(kept)
    dirty = (stats.weighted < 0.80
             or (stats.wilson_lower < page_floor and stats.weighted < 0.90))
    return {
        "file": str(image_path),
        "engine": engine_name,
        "skewAngle": round(skew_angle, 2),
        "text": "\n".join(text for text, _ in kept),
        "lines": [{"text": t, "score": round(s, 4)} for t, s in kept],
        "droppedLines": len(raw_lines) - len(kept),
        "meanConfidence": stats.mean,
        "weightedConfidence": stats.weighted,
        "wilsonLower": stats.wilson_lower,
        "nEff": stats.n_eff,
        "dirty": dirty,  # 脏样本：剔除出训练集，转人工
        "elapsedMs": int((time.time() - started) * 1000),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="图像 → OCR 结构化 JSONL（置信度过滤）")
    parser.add_argument("--images", required=True, help="图片目录或单图路径")
    parser.add_argument("--out", required=True, help="输出 JSONL 路径")
    parser.add_argument("--no-denoise", action="store_true", help="跳过高斯降噪")
    parser.add_argument("--no-deskew", action="store_true", help="跳过倾斜校正")
    parser.add_argument("--line-floor", type=float, default=0.5, help="行级置信度下限")
    parser.add_argument("--page-floor", type=float, default=0.60,
                        help="页级 Wilson 下界下限（低于判脏）")
    args = parser.parse_args(argv)

    engine_name, engine = load_engine()
    root = Path(args.images)
    files = ([root] if root.is_file() else
             sorted(p for p in root.iterdir()
                    if p.suffix.lower() in {".png", ".jpg", ".jpeg", ".bmp", ".webp"}))
    if not files:
        raise SystemExit(f"目录无图片: {root}")

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    dirty = 0
    with out_path.open("a", encoding="utf-8") as handle:
        for path in files:
            record = run_ocr(path, engine_name, engine,
                             denoise_sigma=None if args.no_denoise else 1.0,
                             correct_skew=not args.no_deskew,
                             line_floor=args.line_floor, page_floor=args.page_floor)
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")
            dirty += int(record["dirty"])
            print(f"[{'脏' if record['dirty'] else 'ok'}] {path.name} "
                  f"wilson={record['wilsonLower']} lines={len(record['lines'])}")
    print(f"完成：{len(files)} 图，脏样本 {dirty} → {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
