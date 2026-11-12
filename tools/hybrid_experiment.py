#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""达标组合实验平台（分阶段加入通道，全部可离线复跑）。

阶段 1（本文件）：真实章分类器（非 oracle）+ 置信门控回退，纯标准库。
阶段 2（hybrid_dense_experiment.py）：bge 语义通道 + RRF 融合。

口径：universe=all（81 点），题库 66 题；五门禁阈值与 eval_mapping_offline 一致。
"""
from __future__ import annotations

import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eval_mapping_offline as em  # noqa: E402


class ChapterClassifier:
    """章级 TF-IDF 文档分类：每章一个文档，余弦取 top1 + 置信度。"""

    def __init__(self, kps: list[em.Kp], use_misconception: bool = True):
        self.chapters = sorted({kp.chapter for kp in kps})
        # 章文档 = 该章所有 KP 语料（概念+名称×3+误区）拼接
        docs_by_ch = defaultdict(list)
        for kp in kps:
            docs_by_ch[kp.chapter].append(em.term_counts(em.corpus_of(kp, use_misconception)))
        self.ch_docs = {}
        for ch, counters in docs_by_ch.items():
            merged = Counter()
            for c in counters:
                merged.update(c)
            self.ch_docs[ch] = merged
        self.doc_freq = Counter()
        for counts in self.ch_docs.values():
            self.doc_freq.update(counts.keys())
        self.n_ch = len(self.ch_docs)
        self.vectors = {
            ch: em.vectorize(counts, self.doc_freq, self.n_ch, em.IDF_POWER)
            for ch, counts in self.ch_docs.items()
        }

    def predict(self, text: str) -> tuple[int, float, list[tuple[int, float]]]:
        qv = em.vectorize(em.term_counts(text), self.doc_freq, self.n_ch, em.IDF_POWER)
        scored = [(ch, em.cosine(qv, vec)) for ch, vec in self.vectors.items()]
        scored.sort(key=lambda x: (-x[1], x[0]))
        top1_ch, top1_s = scored[0]
        top2_s = scored[1][1] if len(scored) > 1 else 0.0
        return top1_ch, top1_s - top2_s, scored  # 置信度 = 领先差


def strategy_v2(scorer, text):
    return scorer.rank(text), "none"


def strategy_pred_chapter(scorer, clf, text, floor, ch_margin):
    """真实章分类 + 硬过滤 + 置信门控回退（不偷看 gold）。"""
    hits_all = scorer.rank(text)
    pred_ch, ch_conf, _ = clf.predict(text)
    # 章分类置信不足 → 直接用全量
    if ch_conf < ch_margin:
        return hits_all, "ch-lowconf-fallback"
    filtered = [h for h in hits_all if h.kp.chapter == pred_ch]
    if not filtered or filtered[0].score < floor:
        return hits_all, "kp-fallback"
    return filtered, f"hard-ch{pred_ch}"


def evaluate_strategy(kps, questions, scorer, strategy, **kw):
    chap = {kp.kp_id: kp.chapter for kp in kps}
    top1 = top3 = auto = auto_hit = failed = 0
    modes = Counter()
    rows = []
    for q in questions:
        if strategy is strategy_v2:
            ranked, mode = strategy(scorer, q.text)
        else:
            ranked, mode = strategy(scorer, kw["clf"], q.text,
                                    em.CONFIRM_FLOOR, kw["ch_margin"])
        modes[mode] += 1
        ids = [h.kp.kp_id for h in ranked]
        decision = em.decide(ranked, em.CONFIRM_FLOOR)
        hit = bool(ids) and ids[0] == q.gold
        if hit:
            top1 += 1
        if q.gold in ids[:3]:
            top3 += 1
        if decision == "AUTO":
            auto += 1
            auto_hit += 1 if hit else 0
        elif decision == "FAILED":
            failed += 1
        rows.append((q.q_id, hit, q.gold in ids[:3], decision, mode,
                     ids[0] if ids else "-"))
    n = len(questions)
    return {
        "n": n,
        "top1": top1 / n,
        "top3": top3 / n,
        "auto": auto,
        "auto_precision": (auto_hit / auto) if auto else 0.0,
        "coverage": (n - failed) / n,
        "failed": failed,
        "modes": modes,
        "rows": rows,
    }


def gates(r):
    """五门禁判定（同精度比较）。"""
    return [
        ("top1≥60%", r["top1"] >= 0.60, f'{r["top1"]:.1%}'),
        ("top3≥80%", r["top3"] >= 0.80, f'{r["top3"]:.1%}'),
        ("AUTO精确率≥90%", r["auto_precision"] >= 0.90, f'{r["auto_precision"]:.1%}'),
        ("覆盖≥86.7%", r["coverage"] >= 0.867, f'{r["coverage"]:.1%}'),
        ("FAILED≤8", r["failed"] <= 8, str(r["failed"])),
    ]


def main():
    kps = em.load_kps("all")
    questions = em.load_questions()
    questions, _ = em.filter_in_universe(questions, kps)
    scorer = em.Scorer(kps, use_misconception=True)
    clf = ChapterClassifier(kps, use_misconception=True)

    # 章分类器在门禁集 66 题上的准确率
    ch_correct = 0
    for q in questions:
        pred_ch, _, _ = clf.predict(q.text)
        ch_correct += pred_ch == q.chapter
    print(f"真实章分类器在 66 题上 top-1 准确率 = {ch_correct}/66 = {ch_correct/66:.1%}\n")

    configs = [
        ("V2 现状（无章）", strategy_v2, {}),
        ("真实章+门控回退 margin=0.03", strategy_pred_chapter, {"clf": clf, "ch_margin": 0.03}),
        ("真实章+门控回退 margin=0.06", strategy_pred_chapter, {"clf": clf, "ch_margin": 0.06}),
        ("真实章+门控回退 margin=0.10", strategy_pred_chapter, {"clf": clf, "ch_margin": 0.10}),
    ]
    for name, strat, kw in configs:
        r = evaluate_strategy(kps, questions, scorer, strat, **kw)
        print(f"===== {name} =====")
        print(f'top1 {r["top1"]:.1%} | top3 {r["top3"]:.1%} | '
              f'AUTO {r["auto"]} 精确率 {r["auto_precision"]:.1%} | '
              f'覆盖 {r["coverage"]:.1%} | FAILED {r["failed"]}')
        print("模式分布:", dict(r["modes"]))
        for gname, ok, val in gates(r):
            print(f'  [{"PASS" if ok else "FAIL"}] {gname} ({val})')
        print()


if __name__ == "__main__":
    main()
