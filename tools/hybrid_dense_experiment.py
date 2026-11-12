#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""阶段 2：bge-small-zh 语义通道 + 词法/语义 RRF 融合，实测五门禁。

设计要点
--------
1. BGE 用 CLS pooling + L2 归一，点积即余弦；模型与词表全部本地加载，不联网。
2. RRF 只负责「排序」（解决两通道分数尺度不兼容）；置信度另由两通道
   归一化分数加权产生，使其能走既有 decide（floor/auto/margin）。
3. 同时给出 dense-only、lexical-only、RRF 融合对照，全部同一 seed/题集/阈值。

用法：
    python tools/hybrid_dense_experiment.py
"""
from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch
from transformers import AutoModel, AutoTokenizer

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eval_mapping_offline as em  # noqa: E402

MODEL_DIR = Path(__file__).resolve().parents[1] / "zhixue-ai" / "data" / "models" / "bge-small-zh"


@dataclass
class DenseEncoder:
    tokenizer: object
    model: object
    device: object

    @classmethod
    def load(cls):
        tok = AutoTokenizer.from_pretrained(str(MODEL_DIR))
        model = AutoModel.from_pretrained(str(MODEL_DIR))
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        model.to(device).eval()
        return cls(tok, model, device)

    def encode(self, texts: list[str], batch_size: int = 16) -> np.ndarray:
        outs = []
        with torch.no_grad():
            for i in range(0, len(texts), batch_size):
                batch = texts[i:i + batch_size]
                enc = self.tokenizer(batch, padding=True, truncation=True,
                                     max_length=512, return_tensors="pt")
                enc = {k: v.to(self.device) for k, v in enc.items()}
                cls = self.model(**enc).last_hidden_state[:, 0]  # BGE: CLS
                cls = torch.nn.functional.normalize(cls, p=2, dim=1)
                outs.append(cls.cpu().numpy())
        return np.vstack(outs)


def dense_conf(cos: float) -> float:
    """把 dense 余弦映射到 0—1 置信度（BGE 余弦多在 0.2—0.95）。"""
    return float(np.clip((cos + 0.3) / 1.2, 0.0, 1.0))


def rrf_score(rank_lex: dict[str, int], rank_dense: dict[str, int],
              k: int = 60) -> dict[str, float]:
    ids = set(rank_lex) | set(rank_dense)
    out = {}
    for kp in ids:
        s = 0.0
        if kp in rank_lex:
            s += 1.0 / (k + rank_lex[kp])
        if kp in rank_dense:
            s += 1.0 / (k + rank_dense[kp])
        out[kp] = s
    return out


def evaluate(kps, questions, build_ranked, label):
    top1 = top3 = auto = auto_hit = failed = 0
    miss_rows = []
    for q in questions:
        ranked = build_ranked(q)  # list[(kp_id, conf)]
        ids = [kp_id for kp_id, _ in ranked]
        # 用统一 conf 构造 Hit 走同一 decide
        hits = [type("H", (), {"score": c, "raw": c})() for _, c in ranked]
        decision = em.decide(hits, em.CONFIRM_FLOOR)
        hit = bool(ids) and ids[0] == q.gold
        if hit:
            top1 += 1
        else:
            miss_rows.append((q.q_id, q.gold, ids[:3]))
        if q.gold in ids[:3]:
            top3 += 1
        if decision == "AUTO":
            auto += 1
            auto_hit += 1 if hit else 0
        elif decision == "FAILED":
            failed += 1
    n = len(questions)
    r = {"top1": top1 / n, "top3": top3 / n, "auto": auto,
         "auto_precision": (auto_hit / auto) if auto else 0.0,
         "coverage": (n - failed) / n, "failed": failed, "miss": miss_rows}
    print(f"===== {label} =====")
    print(f'top1 {r["top1"]:.1%} | top3 {r["top3"]:.1%} | AUTO {r["auto"]} '
          f'精确率 {r["auto_precision"]:.1%} | 覆盖 {r["coverage"]:.1%} | FAILED {r["failed"]}')
    for name, ok, val in [
        ("top1≥60%", r["top1"] >= 0.60, f'{r["top1"]:.1%}'),
        ("top3≥80%", r["top3"] >= 0.80, f'{r["top3"]:.1%}'),
        ("AUTO精确率≥90%", r["auto_precision"] >= 0.90, f'{r["auto_precision"]:.1%}'),
        ("覆盖≥86.7%", r["coverage"] >= 0.867, f'{r["coverage"]:.1%}'),
        ("FAILED≤8", r["failed"] <= 8, str(r["failed"]))]:
        print(f'  [{"PASS" if ok else "FAIL"}] {name} ({val})')
    print()
    return r


def main():
    kps = em.load_kps("all")
    questions = em.load_questions()
    questions, _ = em.filter_in_universe(questions, kps)
    kp_ids = [k.kp_id for k in kps]

    # 词法通道（误区语料）
    lex = em.Scorer(kps, use_misconception=True)
    # 语义通道
    enc = DenseEncoder.load()
    kp_texts = [em.corpus_of(k, True) for k in kps]
    kp_emb = enc.encode(kp_texts)
    q_emb = enc.encode([q.text for q in questions])

    # 预计算每题两通道排名与 dense 余弦
    per_q = []
    for qi, q in enumerate(questions):
        lex_hits = lex.rank(q.text)
        rank_lex = {h.kp.kp_id: pos for pos, h in enumerate(lex_hits, 1)}
        lex_score = {h.kp.kp_id: h.score for h in lex_hits}
        dcos = (q_emb[qi] @ kp_emb.T)
        order = np.argsort(-dcos)
        rank_dense = {kp_ids[j]: pos for pos, j in enumerate(order, 1)}
        dense_cos = {kp_ids[j]: float(dcos[j]) for j in order}
        per_q.append((q, rank_lex, lex_score, rank_dense, dense_cos))

    # 1) dense-only
    def build_dense(q):
        rec = next(x for x in per_q if x[0] is q)
        _, _, _, rank_dense, dense_cos = rec
        ordered = sorted(rank_dense, key=lambda k: rank_dense[k])
        return [(k, dense_conf(dense_cos[k])) for k in ordered]

    # 2) lexical-only（V2 口径，conf 用既有 score）
    def build_lex(q):
        rec = next(x for x in per_q if x[0] is q)
        _, rank_lex, lex_score, _, _ = rec
        ordered = sorted(rank_lex, key=lambda k: rank_lex[k])
        return [(k, lex_score[k]) for k in ordered]

    # 3) RRF 融合，置信度两通道加权
    def build_rrf(weight_lex=0.5):
        def _build(q):
            rec = next(x for x in per_q if x[0] is q)
            _, rank_lex, lex_score, rank_dense, dense_cos = rec
            rrf = rrf_score(rank_lex, rank_dense)
            ordered = sorted(rrf, key=lambda k: (-rrf[k], k))
            out = []
            for k in ordered:
                lc = lex_score.get(k, 0.0)
                dc = dense_conf(dense_cos.get(k, -0.3))
                conf = weight_lex * lc + (1 - weight_lex) * dc
                out.append((k, conf))
            return out
        return _build

    r_dense = evaluate(kps, questions, build_dense, "dense-only（bge-small-zh）")
    r_lex = evaluate(kps, questions, build_lex, "lexical-only（V2 词法）")
    r_rrf = evaluate(kps, questions, build_rrf(0.5), "RRF 融合（w_lex=0.5）")
    r_rrf2 = evaluate(kps, questions, build_rrf(0.4), "RRF 融合（w_lex=0.4）")

    # 救回/仍错对照
    print("RRF 融合仍错案例：")
    for row in r_rrf["miss"]:
        print(" ", row)


if __name__ == "__main__":
    main()
