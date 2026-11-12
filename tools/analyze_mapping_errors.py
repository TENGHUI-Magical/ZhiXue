#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""映射错例解剖（纯标准库，复用 eval_mapping_offline 的加载与打分）。

回答三个问题：
1. V2 / V11 口径下各有多少错例，错例里 gold 在 top-3（排序错）的比例；
2. 跨章错分有多少（章分类器可救），同章错分有多少（词法/语义问题）；
3. 哪些题在 V11 被救回、哪些仍错，给出逐题清单。

用法：
    python tools/analyze_mapping_errors.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eval_mapping_offline as em  # noqa: E402


def chapter_of(kps):
    return {kp.kp_id: kp.chapter for kp in kps}


def v11_ranked(scorer, text, gold_chapter, floor):
    """V11：误区语料 + 章硬过滤（oracle 章）+ 低于 floor 回退全量。"""
    hits_all = scorer.rank(text)
    filtered = [h for h in hits_all if h.kp.chapter == gold_chapter]
    if not filtered or filtered[0].score < floor:
        return hits_all, "fallback"
    return filtered, "hard"


def classify(ids, gold, chap, gold_in_top3):
    """错例类型：跨章 / 同章 / 检索不到（gold 不在 top3）。"""
    if not gold_in_top3:
        return "检索不到(gold不在top3)"
    top1_chapter = chap.get(ids[0])
    if top1_chapter != chap.get(gold):
        return "跨章错分"
    return "同章排序错"


def main():
    kps = em.load_kps("all")
    questions = em.load_questions()
    questions, dropped = em.filter_in_universe(questions, kps)
    chap = chapter_of(kps)
    floor = em.CONFIRM_FLOOR
    scorer = em.Scorer(kps, use_misconception=True)

    summary = {"V2": [], "V11": []}
    rescued, regressed = [], []
    for q in questions:
        # V2
        hits_v2 = scorer.rank(q.text)
        ids_v2 = [h.kp.kp_id for h in hits_v2]
        v2_hit = ids_v2[0] == q.gold if ids_v2 else False
        v2_in3 = q.gold in ids_v2[:3]
        if not v2_hit:
            kind = classify(ids_v2, q.gold, chap, v2_in3)
            summary["V2"].append((q.q_id, q.gold, chap[q.gold],
                                  ids_v2[0] if ids_v2 else "-",
                                  chap.get(ids_v2[0]) if ids_v2 else "-",
                                  round(hits_v2[0].score, 3) if hits_v2 else 0,
                                  v2_in3, kind))
        # V11
        hits_v11, mode = v11_ranked(scorer, q.text, q.chapter, floor)
        ids_v11 = [h.kp.kp_id for h in hits_v11]
        v11_hit = ids_v11[0] == q.gold if ids_v11 else False
        v11_in3 = q.gold in ids_v11[:3]
        if not v11_hit:
            kind = classify(ids_v11, q.gold, chap, v11_in3)
            summary["V11"].append((q.q_id, q.gold, chap[q.gold],
                                   ids_v11[0] if ids_v11 else "-",
                                   chap.get(ids_v11[0]) if ids_v11 else "-",
                                   round(hits_v11[0].score, 3) if hits_v11 else 0,
                                   v11_in3, mode, kind))
        if (not v2_hit) and v11_hit:
            rescued.append(q.q_id)
        if v2_hit and (not v11_hit):
            regressed.append(q.q_id)

    n = len(questions)
    print(f"题集 N={n}（universe=all，81 点）；filter 丢弃 {dropped} 题\n")
    from collections import Counter
    for tag, rows in summary.items():
        print(f"===== {tag} 错例 {len(rows)} 个 =====")
        kinds = Counter(r[-1] for r in rows)
        for k, c in kinds.most_common():
            print(f"  {k}: {c}")
        in3 = sum(1 for r in rows if r[6])
        cross = kinds.get("跨章错分", 0)
        same = kinds.get("同章排序错", 0)
        absent = kinds.get("检索不到(gold不在top3)", 0)
        print(f"  -- gold 在 top3（可重排/融合救）= {cross + same}（其中 in3 计数 {in3}）；"
              f"检索不到（需语义/语料）= {absent}\n")

    print(f"V11 相对 V2 救回 {len(rescued)} 题：{rescued}")
    print(f"V11 相对 V2 反噬 {len(regressed)} 题：{regressed}\n")

    header_v2 = ("qId", "gold", "gold章", "top1", "top1章", "top1分数", "gold在top3", "类型")
    header_v11 = ("qId", "gold", "gold章", "top1", "top1章", "top1分数", "gold在top3", "模式", "类型")
    print("===== V2 逐错例 =====")
    print(" | ".join(header_v2))
    for r in summary["V2"]:
        print(" | ".join(str(x) for x in r))
    print("\n===== V11 逐错例 =====")
    print(" | ".join(header_v11))
    for r in summary["V11"]:
        print(" | ".join(str(x) for x in r))


if __name__ == "__main__":
    main()
