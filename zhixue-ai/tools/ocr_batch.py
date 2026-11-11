#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PDF 批量 OCR 工具（服务内容线，不生成题目/答案）。

流程：PDF 指定页码 → pdftoppm 渲染 PNG → RapidOCR 识别 → 按页输出 Markdown/JSON。
用途：教材/习题册扫描件无法用 pdftotext 取中文，内容线需要先得到“可人工核对”的
按页文本，再走 CSV/SQL 与人工签核。本工具只做 OCR，不推断题干、答案或知识点。

用法（在 zhixue-ai 目录执行）：
    python tools/ocr_batch.py --pdf 教材.pdf --pages 1-2 --out <输出目录>
    python tools/ocr_batch.py --pdf 教材.pdf --pages 1,3,5-8 --out <输出目录> --dpi 220

断点续跑：
    每页结果单独落盘在 <out>/pages/page_0001.json；再次执行会跳过已成功的页，
    失败页会重试。需要全部重跑时加 --force。

产出：
    <out>/images/page_0001.png   渲染图（便于人工对照）
    <out>/pages/page_0001.json   单页 OCR 结果（含逐行置信度）
    <out>/ocr.json               汇总 JSON（按页码）
    <out>/ocr.md                 汇总 Markdown（按页码分节）
    <out>/failures.json          失败清单（页码 + 原因），成功时为空数组

边界：只使用本地 RapidOCR，不联网、不调用大模型；不写数据库；不生成题目。
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

PAGE_PATTERN = re.compile(r"^\d+$")


def parse_pages(spec: str) -> list[int]:
    """解析 '1-2'、'1,3,5-8' 为页码列表（从 1 开始，去重升序）。"""
    pages: set[int] = set()
    for part in spec.split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            start_text, end_text = part.split("-", 1)
            if not PAGE_PATTERN.match(start_text.strip()) or not PAGE_PATTERN.match(end_text.strip()):
                raise argparse.ArgumentTypeError(f"页码区间非法: {part}")
            start, end = int(start_text), int(end_text)
            if start < 1 or end < start:
                raise argparse.ArgumentTypeError(f"页码区间非法: {part}")
            pages.update(range(start, end + 1))
        else:
            if not PAGE_PATTERN.match(part):
                raise argparse.ArgumentTypeError(f"页码非法: {part}")
            page = int(part)
            if page < 1:
                raise argparse.ArgumentTypeError(f"页码必须从 1 开始: {part}")
            pages.add(page)
    if not pages:
        raise argparse.ArgumentTypeError("--pages 不能为空")
    return sorted(pages)


def find_pdftoppm() -> str:
    binary = shutil.which("pdftoppm")
    if not binary:
        raise SystemExit("未找到 pdftoppm：请安装 Poppler 并把 bin 加入 PATH")
    return binary


def page_paths(out_dir: Path, page: int) -> tuple[Path, Path]:
    return (out_dir / "images" / f"page_{page:04d}.png",
            out_dir / "pages" / f"page_{page:04d}.json")


def read_json(path: Path) -> dict | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def write_json(path: Path, payload: object) -> None:
    """先写临时文件再替换，避免中断留下半截 JSON 破坏断点续跑。"""
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


def render_page(pdf: Path, page: int, png_path: Path, dpi: int, pdftoppm: str) -> None:
    png_path.parent.mkdir(parents=True, exist_ok=True)
    prefix = png_path.with_suffix("")
    command = [pdftoppm, "-f", str(page), "-l", str(page), "-r", str(dpi),
               "-png", "-singlefile", str(pdf), str(prefix)]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0 or not png_path.exists():
        detail = (result.stderr or result.stdout or "").strip()
        raise RuntimeError(f"pdftoppm 渲染失败（第 {page} 页）: {detail[:200]}")


def recognize_page(engine, png_path: Path) -> dict:
    from PIL import Image
    import numpy as np

    from app.services.ocr_quality import aggregate_confidence

    image = Image.open(png_path).convert("RGB")
    started = time.time()
    raw, _ = engine(np.array(image))
    lines = []
    for item in raw or []:
        text = str(item[1]).strip()
        if text:
            lines.append({"text": text, "score": round(float(item[2]), 4)})
    # 置信度族：mean 兼容旧口径；weighted/wilsonLower 为长度加权与 95% 下界
    stats = aggregate_confidence([(line["text"], line["score"]) for line in lines])
    return {
        "text": "\n".join(line["text"] for line in lines),
        "confidence": stats.mean,
        "weightedConfidence": stats.weighted,
        "wilsonLower": stats.wilson_lower,
        "nEff": stats.n_eff,
        # 脏样本：点估计 < 0.80，或证据不足（Wilson 下界 < 0.60）且非高置信
        "dirty": stats.weighted < 0.80
        or (stats.wilson_lower < 0.60 and stats.weighted < 0.90),
        "lines": lines,
        "elapsedMs": int((time.time() - started) * 1000),
    }


def write_aggregates(out_dir: Path, pages: list[int]) -> tuple[Path, Path]:
    records = []
    for page in pages:
        _, json_path = page_paths(out_dir, page)
        payload = read_json(json_path)
        if payload:
            records.append(payload)
    write_json(out_dir / "ocr.json", {"pages": records})
    sections = []
    for record in records:
        sections.append(f"## 第 {record['page']} 页\n\n{record['text']}\n")
    (out_dir / "ocr.md").write_text("\n".join(sections), encoding="utf-8")
    return out_dir / "ocr.json", out_dir / "ocr.md"


def main() -> int:
    parser = argparse.ArgumentParser(description="PDF 按页 OCR（本地 RapidOCR，不生成题目）")
    parser.add_argument("--pdf", required=True, help="PDF 文件路径")
    parser.add_argument("--pages", required=True, type=parse_pages, help="页码范围，如 1-2 或 1,3,5-8")
    parser.add_argument("--out", required=True, help="输出目录（由内容线指定）")
    parser.add_argument("--dpi", type=int, default=200, help="渲染 DPI（默认 200，教材扫描件建议 200—300）")
    parser.add_argument("--force", action="store_true", help="忽略已有单页结果，全部重跑")
    args = parser.parse_args()

    pdf = Path(args.pdf).expanduser().resolve()
    if not pdf.is_file():
        raise SystemExit(f"PDF 不存在: {pdf}")
    out_dir = Path(args.out).expanduser().resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "images").mkdir(exist_ok=True)
    (out_dir / "pages").mkdir(exist_ok=True)

    pdftoppm = find_pdftoppm()
    try:
        from rapidocr_onnxruntime import RapidOCR
    except ImportError as exc:
        raise SystemExit("未安装 RapidOCR：pip install -r requirements-ocr.txt") from exc
    engine = RapidOCR()

    failures = []
    started = time.time()
    for page in args.pages:
        png_path, json_path = page_paths(out_dir, page)
        if not args.force:
            cached = read_json(json_path)
            if cached and cached.get("page") == page:
                print(f"[skip] 第 {page} 页已有结果（断点续跑）")
                continue
        try:
            print(f"[ocr ] 第 {page} 页渲染与识别...")
            render_page(pdf, page, png_path, args.dpi, pdftoppm)
            payload = recognize_page(engine, png_path)
            payload.update({"page": page, "image": str(png_path.relative_to(out_dir)),
                            "pdf": pdf.name, "dpi": args.dpi})
            write_json(json_path, payload)
            print(f"[ok  ] 第 {page} 页 conf={payload['confidence']} chars={len(payload['text'])}")
        except Exception as exc:
            reason = f"{type(exc).__name__}: {exc}"
            failures.append({"page": page, "reason": reason[:300]})
            print(f"[fail] 第 {page} 页 {reason}")

    write_json(out_dir / "failures.json", failures)
    json_path, md_path = write_aggregates(out_dir, args.pages)
    elapsed = round(time.time() - started, 1)
    print(f"完成：成功 {len(args.pages) - len(failures)}/{len(args.pages)} 页，用时 {elapsed}s")
    print(f"汇总 JSON: {json_path}")
    print(f"汇总 Markdown: {md_path}")
    if failures:
        print(f"失败清单: {out_dir / 'failures.json'}")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
