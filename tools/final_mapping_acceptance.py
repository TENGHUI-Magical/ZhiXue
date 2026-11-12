#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""【历史快照 · 已废止 · 禁止引用为达标证据或答辩口径】（2026-11-08 标注）

本脚本及其输出 tools/testdata/final_mapping_acceptance.json 对应 2026-11-07 02:57 的
「RRF 五门禁全过」宣称，已由 2026-11-07 03:08 独立审计废止（台账 :3163 / :3200）：
同集调参、换尺度假象、环境不可复跑；主文档降级为「离线非确认原型，未过验收操作点」。
当前唯一验收口径为 U81-66 top1 53.0%（35/66），未达标；70% 结项线需 47/66。
本文件仅作溯源保留；结论不得引用、不得回写任何对外材料、不得当作最终验收产物。
配套警示：tools/testdata/final_mapping_acceptance.DEPRECATED.md。

以下为原始说明（历史内容，未改逻辑）：
最终映射验收脚本（固化版）：RRF(k=60, w_lex=0.4)，输出五门禁 + 逐题证据 + ECE。

参数选择依据（不是网格最优，而是可解释的稳健点）：
- k=60：RRF 原论文与业界标准默认值，避免「调 k 过拟合」质疑；
- w_lex=0.4 / w_dense=0.6：语义通道权重更高，AUTO 精确率余量更大（92.3%）。

输出：
- 控制台门禁报告；
- tools/testdata/final_mapping_acceptance.json（逐题明细 + 汇总，可归档证据）。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eval_mapping_offline as em  # noqa: E402
from hybrid_dense_experiment import DenseEncoder, dense_conf  # noqa: E402

K_RRF = 60
W_LEX = 0.4
EVIDENCE = Path(__file__).resolve().parent / "testdata" / "final_mapping_acceptance.json"


def fused(rank_lex, rank_dense, lex_score, dense_cos):
    ids = set(rank_lex) | set(rank_dense)
    rows = []
    for kp in ids:
        rrf = 0.0
        if kp in rank_lex:
            rrf += W_LEX * 1.0 / (K_RRF + rank_lex[kp])
        if kp in rank_dense:
            rrf += (1 - W_LEX) * 1.0 / (K_RRF + rank_dense[kp])
        lc = lex_score.get(kp, 0.0)
        dc = dense_conf(dense_cos.get(kp, -0.3))
        conf = W_LEX * lc + (1 - W_LEX) * dc
        rows.append((kp, rrf, conf))
    rows.sort(key=lambda x: (-x[1], x[0]))
    return rows


def adaptive_ece(conf_hit, bins=6):
    """等频（自适应）分箱 ECE：按置信度排序后每箱样本数近似相等。"""
    items = sorted(conf_hit, key=lambda x: x[0])
    n = len(items)
    ece = 0.0
    detail = []
    for i in range(bins):
        part = items[round(i * n / bins):round((i + 1) * n / bins)]
        if not part:
            continue
        avg_conf = sum(c for c, _ in part) / len(part)
        acc = sum(h for _, h in part) / len(part)
        ece += len(part) / n * abs(avg_conf - acc)
        detail.append({"n": len(part), "avg_conf": round(avg_conf, 3),
                       "acc": round(acc, 3)})
    return ece, detail


def main():
    kps = em.load_kps("all")
    questions = em.load_questions()
    questions, _ = em.filter_in_universe(questions, kps)
    kp_ids = [k.kp_id for k in kps]
    lex = em.Scorer(kps, use_misconception=True)
    enc = DenseEncoder.load()
    kp_emb = enc.encode([em.corpus_of(k, True) for k in kps])
    q_emb = enc.encode([q.text for q in questions])

    top1 = top3 = auto = auto_hit = failed = 0
    cases, conf_hit = [], []
    for qi, q in enumerate(questions):
        lex_hits = lex.rank(q.text)
        rank_lex = {h.kp.kp_id: p for p, h in enumerate(lex_hits, 1)}
        lex_score = {h.kp.kp_id: h.score for h in lex_hits}
        dcos = q_emb[qi] @ kp_emb.T
        order = np.argsort(-dcos)
        rank_dense = {kp_ids[j]: p for p, j in enumerate(order, 1)}
        dense_cos = {kp_ids[j]: float(dcos[j]) for j in order}

        rows = fused(rank_lex, rank_dense, lex_score, dense_cos)
        ordered = [kp for kp, _, _ in rows]
        conf = {kp: c for kp, _, c in rows}
        hits = [type("H", (), {"score": conf[x], "raw": conf[x]})()
                for x in ordered]
        decision = em.decide(hits, em.CONFIRM_FLOOR)
        hit = ordered[0] == q.gold
        top1 += hit
        top3 += q.gold in ordered[:3]
        if decision == "AUTO":
            auto += 1
            auto_hit += hit
        elif decision == "FAILED":
            failed += 1
        conf_hit.append((conf[ordered[0]], 1 if hit else 0))
        cases.append({"qId": q.q_id, "gold": q.gold, "top3": ordered[:3],
                      "top1_conf": round(conf[ordered[0]], 4),
                      "decision": decision, "hit": hit,
                      "gold_in_top3": q.gold in ordered[:3]})

    n = len(questions)
    # 显示/判定用未二次舍入的原始比例，避免先 round 再转百分比造成 65.1%/65.2% 口径漂移
    raw_top1, raw_top3 = top1 / n, top3 / n
    raw_auto_precision = (auto_hit / auto) if auto else 0.0
    raw_coverage = (n - failed) / n
    summary = {
        "top1": round(raw_top1, 4), "top3": round(raw_top3, 4),
        "auto": auto,
        "auto_precision": round(raw_auto_precision, 4),
        "coverage": round(raw_coverage, 4), "failed": failed,
    }
    ece, ece_detail = adaptive_ece(conf_hit)
    gates = [
        ("top1≥60%", raw_top1 >= 0.60, f'{raw_top1:.1%}'),
        ("top3≥80%", raw_top3 >= 0.80, f'{raw_top3:.1%}'),
        ("AUTO精确率≥90%", raw_auto_precision >= 0.90,
         f'{raw_auto_precision:.1%}'),
        ("覆盖≥86.7%", raw_coverage >= 0.867,
         f'{raw_coverage:.1%}'),
        ("FAILED≤8", summary["failed"] <= 8, str(summary["failed"])),
    ]
    print(f"RRF(k={K_RRF}, w_lex={W_LEX})  N={n}")
    for name, ok, val in gates:
        print(f'  [{"PASS" if ok else "FAIL"}] {name} ({val})')
    print(f"自适应分箱 ECE = {ece:.3f}")
    for d in ece_detail:
        print(" ", d)

    EVIDENCE.write_text(json.dumps(
        {"params": {"k": K_RRF, "w_lex": W_LEX}, "summary": summary,
         "ece": round(ece, 4), "ece_detail": ece_detail, "cases": cases},
        ensure_ascii=False, indent=2), encoding="utf-8")
    print("\n证据已写:", EVIDENCE)
    print("⚠ 本脚本已废止（见文件头），此输出仅作调试对照，不得引用为验收材料。")


if __name__ == "__main__":
    main()
