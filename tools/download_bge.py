#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 ModelScope 下载 bge-small-zh-v1.5（稳定国内源），带进度与重试。

用法：
    python tools/download_bge.py --files small    # 仅配置/词表
    python tools/download_bge.py --files all      # 含模型权重（约 96MB）
"""
from __future__ import annotations

import argparse
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "zhixue-ai" / "data" / "models" / "bge-small-zh"
BASE = ("https://modelscope.cn/api/v1/models/BAAI/bge-small-zh-v1.5/"
        "repo?Revision=master&FilePath=")

SMALL_FILES = ["config.json", "vocab.txt", "tokenizer_config.json",
               "special_tokens_map.json"]
ALL_FILES = SMALL_FILES + ["pytorch_model.bin"]


def download_one(remote: str, dest: Path, retries: int = 3) -> tuple[bool, int]:
    url = BASE + remote
    for attempt in range(1, retries + 1):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                total = int(r.headers.get("Content-Length", 0))
                tmp = dest.with_suffix(dest.suffix + ".part")
                got = 0
                with open(tmp, "wb") as f:
                    while True:
                        chunk = r.read(1 << 16)
                        if not chunk:
                            break
                        f.write(chunk)
                        got += len(chunk)
                tmp.replace(dest)
                return True, got
        except Exception as e:  # 网络抖动重试
            if attempt == retries:
                print(f"FAIL {remote}: {type(e).__name__} {e}")
                return False, 0
            time.sleep(2 * attempt)
    return False, 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--files", choices=["small", "all"], default="small")
    args = ap.parse_args()
    DEST.mkdir(parents=True, exist_ok=True)
    files = ALL_FILES if args.files == "all" else SMALL_FILES
    for remote in files:
        local = DEST / remote
        if local.exists() and local.stat().st_size > 0:
            print(f"SKIP {remote} ({local.stat().st_size} B)")
            continue
        ok, size = download_one(remote, local)
        print(f'{"OK" if ok else "FAIL"} {remote} {size} B')


if __name__ == "__main__":
    main()
