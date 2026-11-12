#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""RRF 参数网格扫描：在固定两通道排名上，扫描 (k, w_lex) 找五门禁全过组合。

排序只取决于 RRF(k) 与权重；置信度用同一加权公式。编码只做一次。
"""
from __future__ import annotations

import itertools
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eval_mapping_offline as em  # noqa: E402
from hybrid_dense_experiment import DenseEncoder, dense_conf, MODEL_DIR  # noqa: E402


def rrf_rank(rank_lex, rank_dense, k, w_lex):
    ids = set(rank_lex) | set(rank_dense)
    scored = []
    for kp in ids:
        s = 0.0
        if kp in rank_lex:
            s += w_lex * 1.0 / (k + rank_lex[kp])
        if kp in rank_dense:
            s += (1 - w_lex) * 1.0 / (k + rank_dense[kp])
        scored.append((kp, s))
    scored.sort(key=lambda x: (-x[1], x[0]))
    return [kp for kp, _ in scored]


def metrics(kps, questions, per_q, k, w_lex):
    top1 = top3 = auto = auto_hit = failed = 0
    n = len(questions)
    for q, rank_lex, lex_score, rank_dense, dense_cos in per_q:
        ordered = rrf_rank(rank_lex, rank_dense, k, w_lex)
        # 置信度
        conf = {}
        for kp in ordered:
            lc = lex_score.get(kp, 0.0)
            dc = dense_conf(dense_cos.get(kp, -0.3))
            conf[kp] = w_lex * lc + (1 - w_lex) * dc
        hits = [type("H", (), {"score": conf[x], "raw": conf[x]})() for x in ordered]
        decision = em.decide(hits, em.CONFIRM_FLOOR)
        hit = ordered[0] == q.gold
        top1 += hit
        top3 += q.gold in ordered[:3]
        if decision == "AUTO":
            auto += 1
            auto_hit += hit
        elif decision == "FAILED":
            failed += 1
    return {
        "top1": top1 / n, "top3": top3 / n, "auto": auto,
        "auto_precision": (auto_hit / auto) if auto else 0.0,
        "coverage": (n - failed) / n, "failed": failed,
    }


def all_pass(m):
    return (m["top1"] >= 0.60 and m["top3"] >= 0.80 and
            m["auto_precision"] >= 0.90 and m["coverage"] >= 0.867 and
            m["failed"] <= 8)


def main():
    kps = em.load_kps("all")
    questions = em.load_questions()
    questions, _ = em.filter_in_universe(questions, kps)
    kp_ids = [k.kp_id for k in kps]
    lex = em.Scorer(kps, use_misconception=True)
    enc = DenseEncoder.load()
    kp_emb = enc.encode([em.corpus_of(k, True) for k in kps])
    q_emb = enc.encode([q.text for q in questions])

    per_q = []
    for qi, q in enumerate(questions):
        lex_hits = lex.rank(q.text)
        rank_lex = {h.kp.kp_id: pos for pos, h in enumerate(lex_hits, 1)}
        lex_score = {h.kp.kp_id: h.score for h in lex_hits}
        dcos = q_emb[qi] @ kp_emb.T
        order = np.argsort(-dcos)
        rank_dense = {kp_ids[j]: pos for pos, j in enumerate(order, 1)}
        dense_cos = {kp_ids[j]: float(dcos[j]) for j in order}
        per_q.append((q, rank_lex, lex_score, rank_dense, dense_cos))

    ks = [20, 30, 40, 60, 80, 100]
    ws = [0.3, 0.4, 0.45, 0.5, 0.55, 0.6, 0.7]
    winners = []
    print("k\tw_lex\ttop1\ttop3\tAUTO\tAUTO精确率\t覆盖\tFAILED\t全过")
    for k, w in itertools.product(ks, ws):
        m = metrics(kps, questions, per_q, k, w)
        ok = all_pass(m)
        if ok:
            winners.append((k, w, m))
        print(f'{k}\t{w}\t{m["top1"]:.1%}\t{m["top3"]:.1%}\t{ m["auto"]}\t'
              f'{m["auto_precision"]:.1%}\t{m["coverage"]:.1%}\t{m["failed"]}\t'
              f'{"YES" if ok else ""}')
    print("\n=== 全过组合 ===")
    for k, w, m in winners:
        print(k, w, {x: (round(v, 3) if isinstance(v, float) else v)
                     for x, v in m.items()})


if __name__ == "__main__":
    main()
