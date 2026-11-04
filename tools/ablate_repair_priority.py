#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PRP 补漏排序三因子消融对照（docs/05 §3.7）。

目的
----
为答辩/结项提供可复现证据：补漏评分 score = 先修权重 × 遗忘紧迫度 × 错因可修复性，
三个因子各自对排序质量的贡献是多少。删掉任一因子后指标下降，即证明该因子不是装饰。

口径（与 RepairPriorityService 逐项一致）
------------------------------------------
- 先修权重 prereqWeight：薄弱点自身 0.6、1 跳先修 1.0、2 跳 0.7、直接后继 0.5、其他 0.2
- 遗忘紧迫度 urgency = 1 - 0.5^(days/halfLife)，从未练习记 1.0
- 错因可修复性 repairability：概念混淆 1.0、方法缺失 0.9、步骤遗漏 0.8、边界 0.7、计算 0.5、其他 0.5
- 半衰 halfLife = 30 天

评估方法
--------
从真实种子图谱（ch01—ch05 先修边）取结构，对每个「薄弱点」（anchor）构造一个场景：
把该点标为最近答错、其余点为不同天数未练的干扰项。正确答案 = anchor 的 1—2 跳先修点
（教学上应优先补的前置）。指标：
- top1_recall：排名第 1 的是否落在正确集合
- top3_recall：前 3 是否命中正确集合
- mrr：正确集合中最好名次的倒数均值
- gap：正确项平均分 − 干扰项平均分（可分性）

用法
----
    python tools/ablate_repair_priority.py
    python tools/ablate_repair_priority.py --json tools/testdata/repair_priority_ablation.json

只用标准库；不连数据库；不依赖 Java 实现。
干扰项天数由 (anchor,kp) 的稳定哈希（md5 前 8 字节取模）生成，同一仓库状态下
多次运行输出一致，不依赖 PYTHONHASHSEED。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SQL_FILES = [
    ROOT / "zhixue-server" / "sql" / "seed-prereq-ch01.sql",
    ROOT / "zhixue-server" / "sql" / "seed-kp-ch02-ch03.sql",
    ROOT / "zhixue-server" / "sql" / "seed-prereq-ch04.sql",
    ROOT / "zhixue-server" / "sql" / "seed-prereq-ch05.sql",
]

HALF_LIFE = 30.0
MAX_PREREQ_DEPTH = 2

REPAIRABILITY = {
    "CONCEPT_CONFUSION": 1.0,
    "METHOD_MISSING": 0.9,
    "STEP_OMISSION": 0.8,
    "BOUNDARY_ERROR": 0.7,
    "CALCULATION_ERROR": 0.5,
    "UNCLASSIFIED": 0.5,
}
DEFAULT_REPAIRABILITY = 0.5

EDGE_PATTERN = re.compile(r"\('?(KP\d+)'?\s*,\s*'?(KP\d+)'?\)")


def load_edges() -> set[tuple[str, str]]:
    edges: set[tuple[str, str]] = set()
    for path in SQL_FILES:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        for a, b in EDGE_PATTERN.findall(text):
            if a != b:
                edges.add((b, a))  # (kp_id, prereq_kp_id)：b 的先修是 a
    return edges


def all_kps(edges: set[tuple[str, str]]) -> list[str]:
    kps: set[str] = set()
    for a, b in edges:
        kps.add(a)
        kps.add(b)
    return sorted(kps)


def distances_to_anchor(anchor: str, edges: set[tuple[str, str]], kp_set: set[str]) -> dict[str, int]:
    """anchor 距离 0；其先修 1 跳、先修的先修 2 跳（与 RepairPriorityService.distancesToAnchors 一致）。"""
    prereqs_of: dict[str, list[str]] = {}
    for kp, prereq in edges:
        if kp in kp_set and prereq in kp_set:
            prereqs_of.setdefault(kp, []).append(prereq)
    dist = {anchor: 0}
    queue = [anchor]
    while queue:
        cur = queue.pop(0)
        if dist[cur] >= MAX_PREREQ_DEPTH:
            continue
        for pre in prereqs_of.get(cur, []):
            if pre not in dist:
                dist[pre] = dist[cur] + 1
                queue.append(pre)
    return dist


def successors_of(anchor: str, edges: set[tuple[str, str]], kp_set: set[str]) -> set[str]:
    return {kp for kp, prereq in edges if prereq == anchor and kp in kp_set and kp != anchor}


def prereq_weight(distance: int, successor: bool, use_prereq: bool) -> float:
    if not use_prereq:
        return 1.0
    if distance == 1:
        return 1.0
    if distance == 2:
        return 0.7
    if distance == 0:
        return 0.6
    return 0.5 if successor else 0.2


def urgency(days: int | None, use_urgency: bool) -> float:
    if not use_urgency:
        return 1.0
    if days is None:
        return 1.0
    return 1.0 - (0.5 ** (max(0, days) / HALF_LIFE))


def repairability(error_type: str | None, use_repair: bool) -> float:
    if not use_repair:
        return 1.0
    if not error_type:
        return DEFAULT_REPAIRABILITY
    return REPAIRABILITY.get(error_type.strip().upper(), DEFAULT_REPAIRABILITY)


def score_all(anchor: str, edges: set[tuple[str, str]], kps: list[str],
              days_by_kp: dict[str, int | None], error_by_kp: dict[str, str | None],
              use_prereq: bool, use_urgency: bool, use_repair: bool) -> list[tuple[str, float]]:
    kp_set = set(kps)
    dist = distances_to_anchor(anchor, edges, kp_set)
    succ = successors_of(anchor, edges, kp_set)
    scored = []
    for kp in kps:
        d = dist.get(kp, -1)
        w = prereq_weight(d, kp in succ, use_prereq)
        u = urgency(days_by_kp.get(kp), use_urgency)
        r = repairability(error_by_kp.get(kp), use_repair)
        scored.append((kp, w * u * r))
    scored.sort(key=lambda x: (-x[1], x[0]))
    return scored


def evaluate(variant: str, use_prereq: bool, use_urgency: bool, use_repair: bool,
             edges: set[tuple[str, str]], kps: list[str], scenarios: list[dict]) -> dict:
    top1 = top3 = 0
    mrr_sum = 0.0
    gap_sum = 0.0
    for sc in scenarios:
        ranked = score_all(sc["anchor"], edges, kps, sc["days"], sc["errors"],
                           use_prereq, use_urgency, use_repair)
        truth = set(sc["truth"])
        rank_of_best = None
        for i, (kp, s) in enumerate(ranked, start=1):
            if kp in truth:
                rank_of_best = i
                break
        if rank_of_best == 1:
            top1 += 1
        if rank_of_best is not None and rank_of_best <= 3:
            top3 += 1
        mrr_sum += 0.0 if rank_of_best is None else 1.0 / rank_of_best
        truth_scores = [s for kp, s in ranked if kp in truth]
        other_scores = [s for kp, s in ranked if kp not in truth]
        if truth_scores and other_scores:
            gap_sum += sum(truth_scores) / len(truth_scores) - sum(other_scores) / len(other_scores)
    n = max(1, len(scenarios))
    return {
        "variant": variant,
        "n": len(scenarios),
        "top1_recall": round(top1 / n, 4),
        "top3_recall": round(top3 / n, 4),
        "mrr": round(mrr_sum / n, 4),
        "score_gap": round(gap_sum / n, 4),
    }


def build_scenarios(edges: set[tuple[str, str]], kps: list[str]) -> list[dict]:
    """每个有 1—2 跳先修的薄弱点造一个场景：正确答案 = 其 1—2 跳先修。"""
    kp_set = set(kps)
    scenarios = []
    for anchor in kps:
        dist = distances_to_anchor(anchor, edges, kp_set)
        truth = {kp for kp, d in dist.items() if d in (1, 2)}
        if not truth:
            continue
        days: dict[str, int | None] = {}
        errors: dict[str, str | None] = {}
        for kp in kps:
            if kp == anchor:
                days[kp] = 0
                errors[kp] = "CONCEPT_CONFUSION"
            else:
                # 干扰项天数：对 (anchor,kp) 做稳定哈希取模，跨进程一致；
                # 禁止用内置 hash()（PYTHONHASHSEED 加盐，逐次运行不同）
                days[kp] = (int.from_bytes(
                    hashlib.md5(f"{anchor}|{kp}".encode()).digest()[:8], "big") % 60) + 1
                errors[kp] = None
        scenarios.append({"anchor": anchor, "truth": sorted(truth),
                          "days": days, "errors": errors})
    return scenarios


def main() -> int:
    ap = argparse.ArgumentParser(description="PRP 三因子消融对照")
    ap.add_argument("--json", default="tools/testdata/repair_priority_ablation.json")
    args = ap.parse_args()

    edges = load_edges()
    kps = all_kps(edges)
    if len(kps) < 3:
        print("先修边过少，无法消融：", len(edges), "edges")
        return 2
    scenarios = build_scenarios(edges, kps)

    variants = [
        ("V0 全因子（先修×遗忘×错因）", True, True, True),
        ("V1 去先修权重", False, True, True),
        ("V2 去遗忘紧迫度", True, False, True),
        ("V3 去错因可修复性", True, True, False),
        ("V4 仅先修权重", True, False, False),
    ]
    results = [evaluate(name, p, u, r, edges, kps, scenarios) for name, p, u, r in variants]

    print("===== PRP 三因子消融（n=%d 场景，%d 知识点，%d 先修边）====="
          % (len(scenarios), len(kps), len(edges)))
    print("| 变体 | top1 | top3 | MRR | 分数差 |")
    print("| --- | --- | --- | --- | --- |")
    for r in results:
        print("| %s | %.1f%% | %.1f%% | %.3f | %.3f |" % (
            r["variant"], r["top1_recall"] * 100, r["top3_recall"] * 100,
            r["mrr"], r["score_gap"]))

    base = results[0]
    print("\n各因子贡献（相对全因子的 top1 降幅）：")
    for r in results[1:]:
        delta = base["top1_recall"] - r["top1_recall"]
        print("  %s：Δtop1 = %+.1f 个百分点" % (r["variant"], delta * 100))

    out = Path(args.json)
    if not out.is_absolute():
        out = ROOT / out
    out.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "formula": "score = prereqWeight × urgency × repairability (docs/05 §3.7)",
        "half_life_days": HALF_LIFE,
        "n_kp": len(kps), "n_edges": len(edges), "n_scenarios": len(scenarios),
        "results": results,
    }
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\n证据已写入:", out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
