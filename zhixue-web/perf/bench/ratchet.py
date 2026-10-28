#!/usr/bin/env python3
"""闪电.skill 棘轮：测出来的数只许降不许升。

用法：
  python3 ratchet.py init  --result perf/bench/base.json --ceilings timing
  python3 ratchet.py check --result perf/bench/now.json  [--ceilings bundle|timing] [--update]

--ceilings 接受别名 bundle（默认，体积棘轮 ceilings.json）或 timing（登录时延基线
timing-ceilings.json），也兼容传文件名；上限文件只允许这两个常量路径，CLI 值仅作选择器。
默认比较结果里版本 A 的「能用」p75；--metric 可换成 lcp、requests、bytes_total 等。
check 时超过上限（加容差）就以退出码 1 失败；更低且带 --update 时把上限往下调。
"""
import argparse
import json
import sys
from pathlib import Path

_BENCH_DIR = Path(__file__).resolve().parent
# 写盘只允许这两个常量路径（扫描门禁要求），--ceilings 仅作选择器
CEILINGS_PATH = _BENCH_DIR / "ceilings.json"
TIMING_CEILINGS_PATH = _BENCH_DIR / "timing-ceilings.json"
_CEILINGS_ALIASES = {
    "bundle": CEILINGS_PATH,
    "timing": TIMING_CEILINGS_PATH,
    "ceilings.json": CEILINGS_PATH,
    "timing-ceilings.json": TIMING_CEILINGS_PATH,
}


def _ceilings_file(name):
    key = "ceilings.json" if name is None else Path(name).name
    if key in _CEILINGS_ALIASES:
        return _CEILINGS_ALIASES[key]
    raise SystemExit("不支持的上限文件：只允许 ceilings.json（bundle）或 timing-ceilings.json（timing）")


def _repo_path(path_str):
    # 结果文件是读入的 CLI 路径，只许落在仓库内
    p = Path(path_str).resolve()
    if not p.is_relative_to(_BENCH_DIR.parents[1]):
        raise SystemExit(f"路径必须在 zhixue-web 仓库内：{p}")
    return p


def read_value(result_path, version, metric):
    with open(_repo_path(result_path), encoding="utf-8") as fh:
        data = json.load(fh)
    s = data["results"][version]["summary"]
    return s[metric]["p75"], data["results"][version]["url"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("action", choices=["init", "check"])
    ap.add_argument("--result", required=True)
    ap.add_argument("--ceilings", help="上限文件：bundle（默认）或 timing，也兼容传文件名")
    ap.add_argument("--label", help="上限的名字，默认用结果里的 URL")
    ap.add_argument("--version", default="A")
    ap.add_argument("--metric", default="ready")
    ap.add_argument("--tolerance", type=float, default=0.05, help="真实耗时有噪音，默认允许 5%% 波动；计数类指标建议设 0")
    ap.add_argument("--update", action="store_true")
    args = ap.parse_args()

    ceilings_path = _ceilings_file(args.ceilings)
    value, url = read_value(args.result, args.version, args.metric)
    if value is None:
        print("结果里没有这个指标的有效数据，先看 bench 输出里的失败原因", file=sys.stderr)
        sys.exit(2)
    label = args.label or url
    key = f"{label}::{args.metric}_p75"

    try:
        ceilings = json.loads(ceilings_path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        ceilings = {}

    if args.action == "init":
        ceilings[key] = value
        print(f"上限已记下：{key} = {value}")
    else:
        if key not in ceilings:
            print(f"没有 {key} 的上限，先跑 init", file=sys.stderr)
            sys.exit(2)
        limit = ceilings[key]
        allowed = limit * (1 + args.tolerance)
        if value > allowed:
            print(f"✗ 变差了：{key} = {value}，上限 {limit}（容差内最多 {allowed:.1f}）")
            sys.exit(1)
        print(f"✓ {key} = {value}，上限 {limit}")
        if value < limit and args.update:
            ceilings[key] = value
            print(f"  上限下调到 {value}")

    ceilings_path.write_text(json.dumps(ceilings, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
