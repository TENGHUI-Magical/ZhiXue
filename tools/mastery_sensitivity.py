#!/usr/bin/env python3
"""掌握度参数敏感性分析（评审决议 #5：±20% 扰动下结论是否稳健）。

背景
----
MasteryCalculator 的参数（错因严重度 1.0/1.0/0.8/0.6/0.8、半衰 30 天）来自
教学设计语义，不是拟合结果。本脚本回答：参数扰动 ±20% 时，
"哪些知识点最薄弱"的排序会不会翻？——教师看到的是排序，不是数值本身。

方法
----
1. 按公式复刻：mastery = clamp01(1 - Σ(severity_i × 0.5^(d_i/H)) / max(1, N))，
   与 MasteryCalculator.java 逐项一致；
2. 合成事件集：每轮为 20 个知识点各生成随机事件序列（条数 2—30、错因按五种
   随机、发生时间 0—60 天前，固定种子可复现）。**合成序列用于刻画公式结构，
   不代表真实学生数据，禁止把结果当对外效果数字**；
3. 扰动：严重度全体 ×0.8 / ×1.2（上限截到 1.0），半衰 30→24 / 30→36；
4. 指标：扰动前后知识点掌握度排序的 Spearman ρ、最薄弱 top-3/top-5 集合的重合率。

判读
----
- ρ ≥ 0.95 且 top-5 重合 ≥ 90%：排序对参数稳健，"参数无数据依据"的风险可控；
- 否则：标出不稳定区间，结论改为"参数需在试用期用真实作答序列校准"。

用法（在 zhixue 仓库根执行）
------------------------------
    python tools/mastery_sensitivity.py
"""
from __future__ import annotations

import math
import random
import sys

KP_COUNT = 20
TRIALS = 2000
SEED = 20261029

BASE_SEVERITY = {
    "CONCEPT_CONFUSION": 1.0,
    "METHOD_MISSING": 1.0,
    "STEP_OMISSION": 0.8,
    "CALCULATION_ERROR": 0.6,
    "BOUNDARY_ERROR": 0.8,
}
ERROR_TYPES = list(BASE_SEVERITY)
BASE_HALF_LIFE = 30.0


def mastery(kp_events: list[tuple[str, int]], severity: dict[str, float],
            half_life: float) -> float:
    """复刻 MasteryCalculator：events=[(错因, 天数前)]。"""
    n = len(kp_events)
    if n == 0:
        return 0.0
    total = sum(severity[t] * math.pow(0.5, d / half_life) for t, d in kp_events)
    return max(0.0, min(1.0, 1.0 - total / max(1, n)))


def rank_order(values: list[float]) -> list[float]:
    """平均秩（并列取平均），返回升序秩。"""
    order = sorted(range(len(values)), key=lambda i: values[i])
    ranks = [0.0] * len(values)
    i = 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and values[order[j + 1]] == values[order[i]]:
            j += 1
        avg = (i + j) / 2 + 1
        for k in range(i, j + 1):
            ranks[order[k]] = avg
        i = j + 1
    return ranks


def spearman(a: list[float], b: list[float]) -> float:
    ra, rb = rank_order(a), rank_order(b)
    ma, mb = sum(ra) / len(ra), sum(rb) / len(rb)
    cov = sum((x - ma) * (y - mb) for x, y in zip(ra, rb))
    va = math.sqrt(sum((x - ma) ** 2 for x in ra))
    vb = math.sqrt(sum((y - mb) ** 2 for y in rb))
    return cov / (va * vb) if va and vb else 1.0


def gen_events(rng: random.Random) -> list[list[tuple[str, int]]]:
    return [
        [(rng.choice(ERROR_TYPES), rng.randint(0, 60))
         for _ in range(rng.randint(2, 30))]
        for _ in range(KP_COUNT)
    ]


def perturb_severity(scale: float) -> dict[str, float]:
    return {t: min(1.0, s * scale) for t, s in BASE_SEVERITY.items()}


VARIANTS = {
    "severity×0.8": {"severity": perturb_severity(0.8), "halfLife": BASE_HALF_LIFE},
    "severity×1.2(截1)": {"severity": perturb_severity(1.2), "halfLife": BASE_HALF_LIFE},
    "halfLife 24d(-20%)": {"severity": BASE_SEVERITY, "halfLife": 24.0},
    "halfLife 36d(+20%)": {"severity": BASE_SEVERITY, "halfLife": 36.0},
}


def main() -> None:
    # Windows GBK 控制台默认无法输出部分符号；显式 UTF-8，保证评审可重跑
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except (AttributeError, OSError):
        pass
    # 统计模拟必须可复现（评审要能重跑出同一组数），固定种子是需求不是缺陷；
    # 非密钥用途，不适用 secrets（扫描器若再报弱随机，以本注释为准）。
    rng = random.Random(SEED)
    stats = {name: {"rho": [], "top3": [], "top5": []} for name in VARIANTS}
    for _ in range(TRIALS):
        events = gen_events(rng)
        base = [mastery(kp, BASE_SEVERITY, BASE_HALF_LIFE) for kp in events]
        base_weak = set(sorted(range(KP_COUNT), key=lambda i: base[i])[:5])
        base_weak3 = set(sorted(range(KP_COUNT), key=lambda i: base[i])[:3])
        for name, cfg in VARIANTS.items():
            pert = [mastery(kp, cfg["severity"], cfg["halfLife"]) for kp in events]
            weak = set(sorted(range(KP_COUNT), key=lambda i: pert[i])[:5])
            weak3 = set(sorted(range(KP_COUNT), key=lambda i: pert[i])[:3])
            stats[name]["rho"].append(spearman(base, pert))
            stats[name]["top3"].append(len(base_weak3 & weak3) / 3)
            stats[name]["top5"].append(len(base_weak & weak) / 5)

    print(f"合成试验 {TRIALS} 轮 × {KP_COUNT} 个知识点（种子 {SEED}，事件数 2—30/点，0—60 天前）")
    print("口径：掌握度数值会变，教师看的是『谁最薄弱』的排序；看排序稳不稳。\n")
    print("| 扰动 | Spearman ρ（均值） | ρ 最差 1% | 最薄弱 top-3 重合 | top-5 重合 |")
    print("| --- | --- | --- | --- | --- |")
    for name, s in stats.items():
        rho_sorted = sorted(s["rho"])
        worst1 = rho_sorted[max(0, int(len(rho_sorted) * 0.01))]
        print(f"| {name} | {sum(s['rho']) / len(s['rho']):.4f} | {worst1:.4f} "
              f"| {sum(s['top3']) / len(s['top3']):.1%} | {sum(s['top5']) / len(s['top5']):.1%} |")

    worst = min(sum(s["top5"]) / len(s["top5"]) for s in stats.values())
    rho_min = min(sum(s["rho"]) / len(s["rho"]) for s in stats.values())
    print("\n判读：")
    if rho_min >= 0.95 and worst >= 0.90:
        print(f"- ±20% 扰动下 ρ≥{rho_min:.3f}、top-5 重合≥{worst:.1%}："
              "『谁最薄弱』的排序对参数稳健；参数标注『教学语义，非拟合』可辩护，"
              "试用期用真实序列复核即可。")
    else:
        print(f"- 存在不稳定区间（最低 ρ={rho_min:.3f}、top-5 重合最低 {worst:.1%}）："
              "参数需在试用期用真实作答序列校准；答辩引用时注明该前提。")
    print("- 已知局限：合成事件分布（均匀错因、均匀时间）不代表真实分布；"
          "本分析刻画的是公式结构对参数的敏感度，不是对学生的预测精度。")


if __name__ == "__main__":
    main()
