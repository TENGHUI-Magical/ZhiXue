#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证 BGE 官方中文 query instruction 与语料口径对排序的影响。

BGE 官方建议：检索场景 query 加前缀「为这个句子生成表示以用于检索相关文章：」，
文档侧不加。对比 top1/top3 是否提升，决定是否纳入最终方案。
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eval_mapping_offline as em  # noqa: E402
from hybrid_dense_experiment import DenseEncoder  # noqa: E402

INSTR = "为这个句子生成表示以用于检索相关文章："


def run(kps, questions, enc, kp_texts, query_prefix):
    kp_emb = enc.encode(kp_texts)
    q_texts = [(query_prefix + q.text) if query_prefix else q.text
               for q in questions]
    q_emb = enc.encode(q_texts)
    kp_ids = [k.kp_id for k in kps]
    top1 = top3 = 0
    for qi, q in enumerate(questions):
        dcos = q_emb[qi] @ kp_emb.T
        order = list(np.argsort(-dcos))
        ordered = [kp_ids[j] for j in order]
        top1 += ordered[0] == q.gold
        top3 += q.gold in ordered[:3]
    n = len(questions)
    return top1 / n, top3 / n


def main():
    kps = em.load_kps("all")
    questions = em.load_questions()
    questions, _ = em.filter_in_universe(questions, kps)
    enc = DenseEncoder.load()

    # 两种 KP 语料口径
    kp_full = [em.corpus_of(k, True) for k in kps]      # 含误区
    kp_no_mis = [em.corpus_of(k, False) for k in kps]   # 不含误区

    print("KP语料 | query前缀 | top1 | top3")
    for name, kp_texts in [("含误区", kp_full), ("不含误区", kp_no_mis)]:
        for pname, pref in [("无前缀", ""), ("官方instruction", INSTR)]:
            t1, t3 = run(kps, questions, enc, kp_texts, pref)
            print(f"{name} | {pname} | {t1:.1%} | {t3:.1%}")


if __name__ == "__main__":
    main()
