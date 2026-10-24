#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""教师双评一致性计算（Cohen's kappa）——17 号文第 4 节 / G5 门禁（kappa >= 0.6）。

输入 CSV（utf-8-sig）列：sample_id, rater_a_total, rater_b_total
                        [, rater_a_d1, rater_b_d1]（存在则执行 D1 一票否决）
口径：
- 主口径：合格二分（total >= 3 且 D1 >= 3）上的 Cohen's kappa
- 参考口径：1-5 分线性加权 kappa
- 区间：bootstrap 95%CI（默认 10000 次，seed 42）
自检：python tools/kappa.py --demo
安全：仅读写本脚本所在 tools/testdata 目录，拒绝 .. 路径段；无网络请求。
"""
import argparse
import csv
import json
import os
import random
import sys

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "testdata")


def safe_path(path):
    if ".." in os.path.normpath(path).replace("\\", "/").split("/"):
        raise SystemExit("[安全] 路径不允许包含 ..: " + path)
    resolved = os.path.realpath(os.path.abspath(path))
    root = os.path.realpath(DATA_DIR)
    if resolved != root and not resolved.startswith(root + os.sep):
        raise SystemExit("[安全] 路径必须位于 %s 内: %s" % (root, path))
    return resolved


def label(total, d1, use_d1):
    ok = total >= 3
    if use_d1 and d1 is not None:
        ok = ok and d1 >= 3
    return 1 if ok else 0


def kappa_binary(xs, ys):
    n = len(xs)
    if n == 0:
        return None
    po = sum(1 for x, y in zip(xs, ys) if x == y) / n
    values = set(xs) | set(ys)
    pe = sum((xs.count(v) / n) * (ys.count(v) / n) for v in values)
    return None if pe == 1 else (po - pe) / (1 - pe)


def kappa_linear(xs, ys, k=5):
    n = len(xs)
    if n == 0:
        return None
    num = den = 0.0
    for i in range(1, k + 1):
        for j in range(1, k + 1):
            w = abs(i - j) / (k - 1)
            o = sum(1 for x, y in zip(xs, ys) if x == i and y == j) / n
            e = (sum(1 for x in xs if x == i) / n) * (sum(1 for y in ys if y == j) / n)
            num += w * o
            den += w * e
    return None if den == 0 else 1 - num / den


def bootstrap_ci(pairs, fn, iters, seed):
    rng = random.Random(seed)
    stats = []
    n = len(pairs)
    for _ in range(iters):
        sample = [pairs[rng.randrange(n)] for _ in range(n)]
        v = fn([p[0] for p in sample], [p[1] for p in sample])
        if v is not None:
            stats.append(v)
    if not stats:
        return None, None
    stats.sort()
    return stats[int(0.025 * len(stats))], stats[int(0.975 * len(stats)) - 1]


def demo():
    rng = random.Random(7)
    pairs = []
    for _ in range(40):
        t = rng.randint(1, 5)
        b = t if rng.random() < 0.8 else max(1, min(5, t + rng.choice([-1, 1])))
        pairs.append((t, b))
    xs = [p[0] for p in pairs]
    ys = [p[1] for p in pairs]
    print("[demo] n=40 合成数据（约 80%% 完全一致，验证实现可运行）")
    print("  二分 kappa = %.3f" % kappa_binary([1 if x >= 3 else 0 for x in xs],
                                                [1 if y >= 3 else 0 for y in ys]))
    print("  加权 kappa = %.3f" % kappa_linear(xs, ys))
    return 0


def main():
    ap = argparse.ArgumentParser(description="双评 kappa 计算（本地工具）")
    ap.add_argument("--input", default="review_scores.csv")
    ap.add_argument("--bootstrap", type=int, default=10000)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--out", default=None)
    ap.add_argument("--demo", action="store_true")
    args = ap.parse_args()
    if args.demo:
        return demo()

    path = safe_path(args.input)
    with open(path, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))
    use_d1 = bool(rows) and rows[0].get("rater_a_d1") not in (None, "")
    pairs_bin, pairs_lin, skipped = [], [], []
    for r in rows:
        try:
            ta, tb = int(r["rater_a_total"]), int(r["rater_b_total"])
            da = int(r["rater_a_d1"]) if r.get("rater_a_d1") else None
            db = int(r["rater_b_d1"]) if r.get("rater_b_d1") else None
        except (TypeError, ValueError):
            skipped.append(r.get("sample_id"))
            continue
        pairs_bin.append((label(ta, da, use_d1), label(tb, db, use_d1)))
        pairs_lin.append((ta, tb))
    if len(pairs_bin) < 2:
        print("有效样本不足（n=%d），检查 %s" % (len(pairs_bin), path))
        return 2

    kb = kappa_binary([p[0] for p in pairs_bin], [p[1] for p in pairs_bin])
    kw = kappa_linear([p[0] for p in pairs_lin], [p[1] for p in pairs_lin])
    agree = sum(1 for a, b in pairs_bin if a == b) / len(pairs_bin)
    lo, hi = bootstrap_ci(pairs_bin, lambda x, y: kappa_binary(x, y), args.bootstrap, args.seed)
    lo2, hi2 = bootstrap_ci(pairs_lin, lambda x, y: kappa_linear(x, y), args.bootstrap, args.seed)

    print("n=%d  二分一致率=%.1f%%  剔除残缺=%d" % (len(pairs_bin), agree * 100, len(skipped)))
    print("二分 kappa=%.3f  95%%CI [%.3f, %.3f]   （G5 门禁 >= 0.600）" % (kb, lo, hi))
    print("加权 kappa=%.3f  95%%CI [%.3f, %.3f]" % (kw, lo2, hi2))
    verdict = "PASS" if (kb is not None and kb >= 0.6) else "BELOW_GATE"
    print("结论:", verdict)
    # 路径安全收口：报告写出抽到 _write_report（固定字面量路径），见文件末尾定义
    _write_report({"n": len(pairs_bin), "agreement": agree,
                   "kappa_binary": kb, "kappa_binary_ci": [lo, hi],
                   "kappa_weighted": kw, "kappa_weighted_ci": [lo2, hi2],
                   "verdict": verdict, "skipped": skipped})
    return 0


def _write_report(payload):
    """报告输出：路径为纯字面量（相对仓库根运行），不接收任何外部路径输入。"""
    from pathlib import Path
    out = Path("tools/testdata/last_kappa.json")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print("报告已写入:", out)


if __name__ == "__main__":
    sys.exit(main())
