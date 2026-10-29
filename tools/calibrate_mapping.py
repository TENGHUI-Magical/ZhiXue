#!/usr/bin/env python3
"""M1：映射选择性预测校准（风险-覆盖曲线 + 按教师产能选阈值）。

对应《算法高分路线与交接说明》M1 与《算法评审结论》#8 裁决：
把三态映射（AUTO/CONFIRM/FAILED）按"选择性预测"框架量化——
在标注集上画出"覆盖-精确率"曲线，回答两个运营问题：
1. 把 CONFIRM 下限从 0.40 调到 t，AUTO 精确率还能守住 p* 吗？覆盖能涨多少？
2. 教师每天最多复核 K 条、日错题量约 D 条时，哪个下限可行？

方法（Geifman & El-Yaniv 风险-覆盖框架；对齐 arXiv 2509.21514 选择性知识追踪）：
- 逐题取 top1 分数与是否命中，扫描下限 t ∈ [0, 1]；
- 每个标题下报：覆盖率、覆盖内精确率、AUTO 精确率、AUTO 率、人工处理率（CONFIRM+FAILED）；
- 另报"最优前缀曲线"（按分数排序取前 x%）：与三态阈值无关的上界参考；
- 给定 (p*, K, D) 输出可行下限区间。

数据与口径
----------
- 复用 eval_mapping_offline 的种子解析与 TF-IDF 复刻（不重复实现，避免口径漂移）；
- 章过滤口径：以 gold 章作为传入 chapterId（oracle 上限口径，见评审材料 §6.2 已声明的
  局限），只作参考，不作为线上结论；
- 全部为离线分析，60 题上 1 条题 ≈ 1.7pp，差异小于 1 条题的结论一律标"不显著"。

用法（在 zhixue 仓库根执行）
------------------------------
    python tools/calibrate_mapping.py
输出常量路径 JSON（tools/testdata/risk_coverage.json）供结项材料引用。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))

import eval_mapping_offline as em

OUT_JSON = TOOLS / "testdata" / "risk_coverage.json"

FLOORS = [0.0, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50]
DAILY_VOLUME = 40          # 假设：单课程日错题量（可按试用数据修正）
CAPACITIES = [10, 20, 40]  # 假设：教师日复核产能三档
TARGET_AUTO_PRECISION = 0.95


def sweep_floor(per_question: list[dict], floor: float) -> dict:
    """按三态规则对每个下限现算（AUTO/CONFIRM/FAILED 随 floor 变化）。"""
    n = len(per_question)
    auto = confirm = failed = 0
    covered_hits = 0
    auto_hits = 0
    for q in per_question:
        best, second, hit = q["best"], q["second"], q["hit"]
        if best < floor:
            failed += 1
        elif best >= em.AUTO_THRESHOLD and (second is None or best - second >= em.MARGIN):
            auto += 1
            auto_hits += 1 if hit else 0
        else:
            confirm += 1
        if best >= floor:
            covered_hits += 1 if hit else 0
    covered = n - failed
    return {
        "floor": floor,
        "coverage": covered / n,
        "precisionAboveFloor": covered_hits / covered if covered else 0.0,
        "autoRate": auto / n,
        "autoPrecision": auto_hits / auto if auto else 0.0,
        "confirmRate": confirm / n,
        "failedRate": failed / n,
        "humanRate": (confirm + failed) / n,
        "top1": sum(q["hit"] for q in per_question) / n,
    }


def prefix_curve(per_question: list[dict], covers=(1.0, 0.95, 0.9, 0.85, 0.8, 0.7, 0.6)) -> list[dict]:
    """最优风险-覆盖曲线：按分数排序取前 x%，与三态阈值无关（上界参考）。"""
    ordered = sorted(per_question, key=lambda q: (-q["best"], q["qId"]))
    n = len(ordered)
    rows = []
    for c in covers:
        k = max(1, round(n * c))
        prefix = ordered[:k]
        rows.append({
            "coverage": k / n,
            "precision": sum(q["hit"] for q in prefix) / k,
        })
    return rows


def sweep_auto(per_question: list[dict], floor: float = 0.30,
               auto_thresholds=(0.45, 0.50, 0.55, 0.60, 0.65, 0.70),
               margins=(0.05, 0.10, 0.15), score_key: str = "best",
               second_key: str = "second", hit_key: str = "hit") -> list[dict]:
    """AUTO 工作点扫描：AUTO 阈值与领先差才决定全自动比例；下限只转移 CONFIRM↔FAILED。"""
    rows = []
    for at in auto_thresholds:
        for margin in margins:
            auto = auto_hits = 0
            for q in per_question:
                best, second = q[score_key], q[second_key]
                if best < floor:
                    continue
                if best >= at and (second is None or best - second >= margin):
                    auto += 1
                    auto_hits += 1 if q[hit_key] else 0
            rows.append({
                "autoThreshold": at, "margin": margin,
                "autoRate": auto / len(per_question),
                "autoPrecision": auto_hits / auto if auto else 0.0,
            })
    return rows


def main() -> None:
    kps = em.load_kps()
    questions = em.load_questions()
    chapter_of = {kp.kp_id: kp.chapter for kp in kps}

    scorer = em.Scorer(kps)
    per_question: list[dict] = []
    for q in questions:
        ranked_all = scorer.rank(q.text)
        ranked_chapter = ([(kp, s) for kp, s in ranked_all
                           if chapter_of.get(kp.kp_id) == q.chapter] if q.chapter else [])
        ranked_chapter.sort(key=lambda item: (-item[1], item[0].kp_id))
        per_question.append({
            "qId": q.q_id,
            "best": ranked_all[0][1] if ranked_all else 0.0,
            "second": ranked_all[1][1] if len(ranked_all) > 1 else None,
            "hit": bool(ranked_all) and ranked_all[0][0].kp_id == q.gold,
            "chBest": ranked_chapter[0][1] if ranked_chapter else 0.0,
            "chSecond": ranked_chapter[1][1] if len(ranked_chapter) > 1 else None,
            "chapterHit": bool(ranked_chapter) and ranked_chapter[0][0].kp_id == q.gold,
            "chapterScore": ranked_chapter[0][1] if ranked_chapter else 0.0,
        })

    n = len(per_question)
    floor_rows = [sweep_floor(per_question, f) for f in FLOORS]

    # 可行下限：AUTO 精确率 ≥ p* 且 人工处理量 ≤ K
    scenarios = []
    for k in CAPACITIES:
        feasible = [r for r in floor_rows
                    if r["autoPrecision"] >= TARGET_AUTO_PRECISION
                    and r["humanRate"] * DAILY_VOLUME <= k]
        best = max(feasible, key=lambda r: r["coverage"]) if feasible else None
        scenarios.append({
            "capacityK": k,
            "dailyVolume": DAILY_VOLUME,
            "feasible": bool(feasible),
            "bestFloor": best["floor"] if best else None,
            "coverage": best["coverage"] if best else None,
            "autoPrecision": best["autoPrecision"] if best else None,
            "humanPerDay": best["humanRate"] * DAILY_VOLUME if best else None,
        })

    curve = prefix_curve(per_question)
    full_precision = curve[0]["precision"]
    at80 = next(r for r in curve if abs(r["coverage"] - 0.8) < 1e-9)

    auto_rows = sweep_auto(per_question)
    best_point = max((r for r in auto_rows if r["autoPrecision"] >= TARGET_AUTO_PRECISION),
                     key=lambda r: r["autoRate"], default=None)
    # 章先知口径（gold 章作传入章，oracle 上限口径）
    chapter_rows = sweep_auto(per_question, score_key="chBest", second_key="chSecond",
                              hit_key="chapterHit")
    ch_best_point = max((r for r in chapter_rows if r["autoPrecision"] >= TARGET_AUTO_PRECISION),
                        key=lambda r: r["autoRate"], default=None)

    print(f"题量 {n}（2 章 / 20 知识点；60 题上 1 条 ≈ 1.7pp，小差异一律视为不显著）")
    print("\n| 下限 | 覆盖 | 覆盖内精确率 | AUTO 率 | AUTO 精确率 | CONFIRM 率 | FAILED 率 | 人工处理率 |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for r in floor_rows:
        print(f"| {r['floor']:.2f} | {r['coverage']:.1%} | {r['precisionAboveFloor']:.1%} "
              f"| {r['autoRate']:.1%} | {r['autoPrecision']:.1%} | {r['confirmRate']:.1%} "
              f"| {r['failedRate']:.1%} | {r['humanRate']:.1%} |")

    print("\n最优前缀曲线（按分数排序，上界参考；对齐选择性预测文献口径）：")
    for r in curve:
        gain = r["precision"] - full_precision
        print(f"- 覆盖 {r['coverage']:.0%}：精确率 {r['precision']:.1%}"
              f"（{'+' if gain >= 0 else ''}{gain:.1%}）")
    print(f"\n弃答 20% 最不确定的题：精确率 {full_precision:.1%} → {at80['precision']:.1%}"
          f"（文献参考：选择性 KT 弃答 20% 提升 2.3—3.0pp，arXiv 2509.21514）")

    print(f"\n按教师产能选下限（假设日错题 {DAILY_VOLUME} 条、目标 AUTO 精确率 "
          f"{TARGET_AUTO_PRECISION:.0%}）：")
    for s in scenarios:
        if s["feasible"]:
            print(f"- K={s['capacityK']} 条/天：可选下限 {s['bestFloor']:.2f}，"
                  f"覆盖 {s['coverage']:.1%}，AUTO 精确率 {s['autoPrecision']:.1%}，"
                  f"人工 {s['humanPerDay']:.0f} 条/天")
        else:
            print(f"- K={s['capacityK']} 条/天：无可行下限（需放宽 p* 或加人）")

    print("\nAUTO 工作点扫描（floor=0.30；AUTO 阈值×领先差 决定全自动比例）：")
    print("| AUTO 阈值 | 领先差 | AUTO 率 | AUTO 精确率 |")
    print("| --- | --- | --- | --- |")
    for r in auto_rows:
        flag = " ←" if best_point is not None and r is best_point else ""
        print(f"| {r['autoThreshold']:.2f} | {r['margin']:.2f} "
              f"| {r['autoRate']:.1%} | {r['autoPrecision']:.1%}{flag} |")
    if best_point:
        daily_auto = best_point["autoRate"] * DAILY_VOLUME
        print(f"\n→ p*={TARGET_AUTO_PRECISION:.0%} 约束下最大全自动工作点："
              f"AUTO 阈值 {best_point['autoThreshold']:.2f} + 领先差 {best_point['margin']:.2f}，"
              f"AUTO 率 {best_point['autoRate']:.1%}（约 {daily_auto:.0f} 条/天免人工），"
              f"精确率 {best_point['autoPrecision']:.1%}")
        if ch_best_point and best_point:
            lift = ch_best_point["autoRate"] - best_point["autoRate"]
            print(f"→ 章先知口径（gold 章，上限参考）：同一约束下 AUTO 率 "
                  f"{ch_best_point['autoRate']:.1%}"
                  f"（+{lift:.1%}），阈值 {ch_best_point['autoThreshold']:.2f}/"
                  f"领先差 {ch_best_point['margin']:.2f}")
        print("→ 运营含义：CONFIRM 与 FAILED 只互相转移、人工总量不变；"
              "要降低教师负担只能提高 AUTO 率（打分更可分或校准分数）。")

    OUT_JSON.write_text(json.dumps({
        "n": n,
        "floorSweep": floor_rows,
        "autoSweep": auto_rows,
        "bestAutoPoint": best_point,
        "chapterAutoSweep": chapter_rows,
        "bestChapterAutoPoint": ch_best_point,
        "prefixCurve": curve,
        "capacityScenarios": scenarios,
        "assumptions": {
            "dailyVolume": DAILY_VOLUME,
            "targetAutoPrecision": TARGET_AUTO_PRECISION,
            "autoThreshold": em.AUTO_THRESHOLD,
            "margin": em.MARGIN,
            "note": "章过滤用 gold 章，oracle 上限口径；60 题无 holdout，小差异不显著",
        },
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n已写出：{OUT_JSON}")


if __name__ == "__main__":
    main()
