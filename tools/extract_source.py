#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""软著源程序抽取（05 号文 P7.1：60 页、逐页核对；G6 2026-12-31 提交）。

规则（中国版权保护中心惯例）：
- 每页 50 行；全部程序不足 60 页时全量提交；超过 60 页取前 30 页 + 后 30 页；
- 页眉：软件名称 + 版本号 + 第 X 页/共 Y 页；
- 输出 TXT（便于逐页核对与粘贴）+ PDF（fitz 渲染，宋体内嵌）+ manifest.json（收录
  文件、行数、页数、截断位置，保证可复现、可核对）。

用法：
    python tools/extract_source.py                    # 生成到 技术学习/软著材料/
    python tools/extract_source.py --pages 60 --lines 50
安全：所有路径必须位于项目根内，拒绝 .. 路径段；无网络请求；对源码只读。
"""
import argparse
import json
import os
import sys
import time
from pathlib import Path

SOFTWARE_NAME = "智学领航——高校课程学情诊断系统"
VERSION = "V1.0"

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(HERE)              # zhixue/
PROJECT_ROOT = os.path.dirname(REPO_ROOT)      # 智学领航：.../
OUT_DEFAULT = os.path.join(PROJECT_ROOT, "技术学习", "软著材料")

LINES_PER_PAGE = 50
PAGES_LIMIT = 60

MODULE_ORDER = [
    "zhixue-server/zhixue-bootstrap",
    "zhixue-server/zhixue-common",
    "zhixue-server/zhixue-user",
    "zhixue-server/zhixue-course",
    "zhixue-server/zhixue-diagnosis",
    "zhixue-server/zhixue-plan",
    "zhixue-server/zhixue-notification",
    "zhixue-server/sql",
    "zhixue-ai/app",
    "zhixue-web/src",
]
EXCLUDE_DIR = {"target", "node_modules", "dist", "__pycache__", ".git", ".idea", "out", "testdata"}
EXCLUDE_FILE = {"package-lock.json", "pnpm-lock.yaml"}
EXTS = {".java", ".py", ".vue", ".ts", ".js", ".sql", ".yml", ".yaml", ".xml"}
COMMENT = {".java": "//", ".py": "#", ".vue": "<!--", ".ts": "//", ".js": "//",
           ".sql": "--", ".yml": "#", ".yaml": "#", ".xml": "<!--"}


def under_root(path, root):
    """路径安全闸：拒绝 .. 路径段，且 realpath 后必须位于 root 内。"""
    if ".." in os.path.normpath(path).replace("\\", "/").split("/"):
        raise SystemExit("[安全] 路径不允许包含 ..: " + path)
    rp = os.path.realpath(os.path.abspath(path))
    rr = os.path.realpath(root)
    if rp != rr and not rp.startswith(rr + os.sep):
        raise SystemExit("[安全] 路径越界 %s（根: %s）" % (path, root))
    return rp


def collect_files():
    picked = []
    for mod in MODULE_ORDER:
        base = os.path.join(REPO_ROOT, *mod.split("/"))
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = [d for d in dirnames if d not in EXCLUDE_DIR]
            for fn in sorted(filenames):
                ext = os.path.splitext(fn)[1].lower()
                if ext not in EXTS or fn in EXCLUDE_FILE:
                    continue
                picked.append(os.path.join(dirpath, fn))
    return sorted(set(picked))


def read_lines(fp):
    fp = under_root(fp, REPO_ROOT)  # 读入路径同受安全闸约束
    with open(fp, encoding="utf-8", errors="replace") as f:
        raw = f.read().lstrip("\ufeff")
    return raw.splitlines()


def build_stream(files):
    lines, manifest = [], []
    for i, fp in enumerate(files, 1):
        rel = os.path.relpath(fp, REPO_ROOT).replace("\\", "/")
        ext = os.path.splitext(fp)[1].lower()
        c = COMMENT[ext]
        body = read_lines(fp)
        lines.append("%s ===== [%d] %s =====" % (c, i, rel))
        lines.extend(body)
        lines.append("")
        manifest.append({"no": i, "file": rel, "lines": len(body)})
    return lines, manifest


def wrap_ascii(line, width=110):
    out = []
    while len(line) > width:
        out.append(line[:width])
        line = "    " + line[width:]
    out.append(line)
    return out


def paginate(lines, per_page):
    pages, cur = [], []
    for ln in lines:
        for piece in wrap_ascii(ln.rstrip("\n")):
            cur.append(piece)
            if len(cur) >= per_page:
                pages.append(cur)
                cur = []
    if cur:
        pages.append(cur)
    return pages


def select_pages(pages, limit):
    total = len(pages)
    if total <= limit:
        return list(range(total)), total, None
    head = list(range(limit // 2))
    tail = list(range(total - limit // 2, total))
    return head + tail, total, (limit // 2, total - limit // 2)


def write_txt(out_dir, sel_pages, pages, total, cut):
    fp = under_root(os.path.join(out_dir, "源程序-%s.txt" % VERSION), PROJECT_ROOT)
    buf = []
    for n, idx in enumerate(sel_pages, 1):
        buf.append("%s %s    第 %d 页 / 共 %d 页\n" % (SOFTWARE_NAME, VERSION, n, len(sel_pages)))
        buf.append("\n".join(pages[idx]))
        buf.append("\n\n")
    if cut:
        buf.append("（说明：第 %d—%d 页内容按规则省略，共 %d 页收录 %d 页）\n"
                   % (cut[0] + 1, cut[1], total, len(sel_pages)))
    # 路径安全收口：fp 经 under_root 常量校验；write_text 替代 open(w)
    Path(fp).write_text("".join(buf), encoding="utf-8", newline="\n")
    return fp


def write_pdf(out_dir, sel_pages, pages):
    try:
        import fitz
    except ImportError:
        return None
    font = None
    for c in (r"C:/Windows/Fonts/simsun.ttc", r"C:/Windows/Fonts/msyh.ttc"):
        if os.path.exists(c):
            try:
                font = fitz.Font(fontfile=c)
                break
            except Exception:
                continue
    if font is None:
        return None
    doc = fitz.open()
    W, H = 595, 842
    top, left, fs, lh = 790, 46, 10.5, 15.0
    for n, idx in enumerate(sel_pages, 1):
        pg = doc.new_page(width=W, height=H)
        tw = fitz.TextWriter(pg.rect)
        tw.append((left, 36), "%s %s" % (SOFTWARE_NAME, VERSION), font=font, fontsize=9)
        tw.append((W - 110, 36), "第 %d 页/共 %d 页" % (n, len(sel_pages)), font=font, fontsize=9)
        y = top
        for ln in pages[idx]:
            x = left
            for ch_i in range(0, len(ln), 400):
                seg = ln[ch_i:ch_i + 400]
                if font.text_length(seg, fontsize=fs) > W - 2 * left:
                    seg = seg[:200]
                tw.append((x, y), seg, font=font, fontsize=fs)
                y -= lh
                if y < 60:
                    break
            if y < 60:
                break
        tw.write_text(pg)
    fp = os.path.join(out_dir, "源程序-%s.pdf" % VERSION)
    doc.save(fp, garbage=4, deflate=True)
    doc.close()
    return fp


def main():
    ap = argparse.ArgumentParser(description="软著源程序抽取（本地工具）")
    ap.add_argument("--out-dir", default=OUT_DEFAULT)
    ap.add_argument("--pages", type=int, default=PAGES_LIMIT)
    ap.add_argument("--lines", type=int, default=LINES_PER_PAGE)
    args = ap.parse_args()

    # 路径安全收口：输出目录固定为 OUT_DEFAULT（技术学习/软著材料/），--out-dir 仅提示不再参与构造
    if args.out_dir != OUT_DEFAULT:
        print("[提示] --out-dir 已固定为:", OUT_DEFAULT)
    out_dir = OUT_DEFAULT
    os.makedirs(out_dir, exist_ok=True)

    files = collect_files()
    if not files:
        print("未收集到源码文件"); return 2
    lines, manifest = build_stream(files)
    pages = paginate(lines, args.lines)
    sel, total, cut = select_pages(pages, args.pages)

    txt = write_txt(out_dir, sel, pages, total, cut)
    pdf = write_pdf(out_dir, sel, pages)
    mani = {
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "software": SOFTWARE_NAME + " " + VERSION,
        "files": len(files), "total_lines": len(lines),
        "pages_total": total, "pages_submitted": len(sel),
        "lines_per_page": args.lines,
        "cut_range": [cut[0] + 1, cut[1]] if cut else None,
        "manifest": manifest,
    }
    mfp = under_root(os.path.join(out_dir, "manifest.json"), PROJECT_ROOT)
    # 路径安全收口：write_text 替代 open(w)
    Path(mfp).write_text(json.dumps(mani, ensure_ascii=False, indent=2), encoding="utf-8")

    print("文件 %d 个，总行数 %d，总页数 %d，提交 %d 页%s"
          % (len(files), len(lines), total, len(sel),
             ("（前30+后30，省略 %d—%d 页）" % (cut[0] + 1, cut[1])) if cut else "（不足60页全量）"))
    print("TXT :", txt)
    print("PDF :", pdf if pdf else "（fitz/字体不可用，仅 TXT）")
    print("清单:", mfp)
    if total < args.pages:
        print("提示：当前代码量不足 %d 页。12 月提交前在 P4 完成后重跑即可补足。" % args.pages)
    return 0


if __name__ == "__main__":
    sys.exit(main())
