#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""口语化查询复测：检验『BM25 主导』结论对词汇同源性的依赖。

背景
----
eval_retrieval.py 用 60 道题干作查询、概念切片作目标，二者出自同一本教材、
词汇高度同源，天然利好词法检索（BM25）。本脚本把同一批 60 题改写成学生口语
提问（queries_colloquial.json，LLM 合成、gold 不变），在同一索引上重跑各方案，
看结论是否翻转。这是评审问题 #3 的半代理验证：
- 合成改写 ≠ 真实学生提问，结论仍需试用数据确认；
- 但若词法方案在口语集上明显退化，即证明原结论有同源性偏置，bge 触发条件成立。

只读评测：不改生产代码，不写文件（结果以 stdout 输出，由人记录）。

用法（在 zhixue-ai 目录执行）
------------------------------
    python tools/eval_colloquial.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))
sys.path.insert(0, str(AI_ROOT / "tools"))
sys.path.insert(0, str(AI_ROOT / "app" / "services"))

import eval_retrieval as er  # noqa: E402
import vector_index  # noqa: E402

QUERIES_JSON = AI_ROOT / "tools" / "testdata" / "queries_colloquial.json"
THRESHOLDS = [0.08, 0.10, 0.12, 0.15, 0.20]


def main() -> None:
    data = json.loads(QUERIES_JSON.read_text(encoding="utf-8"))
    colloquial = [(q["qId"], q["gold"], q["colloquial"]) for q in data["queries"]]
    original = er.load_questions()

    kps = er.load_kps()
    chunks = []
    for kp_id, concept in kps:
        chunks.extend(vector_index.chunk_text(concept, kp_id))
    vectors = [vector_index._hash_vector(chunk.content) for chunk in chunks]
    bm25 = er.Bm25([chunk.content for chunk in chunks])

    res_orig, _ = er.evaluate(chunks, vectors, bm25, original)
    res_colo, det_colo = er.evaluate(chunks, vectors, bm25, colloquial)

    print(f"知识点 {len(kps)}；切片 {len(chunks)}；"
          f"题干集 {len(original)} vs 口语集 {len(colloquial)}（hash 模式）")
    print("说明：口语集为 LLM 合成改写（非真实学生提问），用于检验同源性偏置。\n")

    print("| 方案 | 题干 recall@5 | 口语 recall@5 | Δ | 题干 MRR | 口语 MRR | 题干 top1 | 口语 top1 |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for name in ["weighted-jaccard", "cosine", "bm25", "hybrid-0.3", "hybrid-0.15", "rrf"]:
        a, b = res_orig[name], res_colo[name]
        delta = b["recall@5"] - a["recall@5"]
        print(f"| {name} | {a['recall@5']:.1%} | {b['recall@5']:.1%} "
              f"| {delta:+.1%} | {a['mrr@5']:.3f} | {b['mrr@5']:.3f} "
              f"| {a['kpTop1']:.1%} | {b['kpTop1']:.1%} |")

    print("\n阈值扫描（hybrid-0.15，口语集）：gold 过阈率决定 60003 拒答率。")
    print("| 阈值 | 有切片过阈 | gold 也过阈 | 过阈但 gold 不在 |")
    print("| --- | --- | --- | --- |")
    for row in er.sweep(det_colo["hybrid-0.15"], THRESHOLDS):
        print(f"| {row['threshold']:.2f} | {row['coverage']:.1%} "
              f"| {row['goldAbove']:.1%} | {row['wrongContextRate']:.1%} |")

    print("\n判读：")
    bm_a = res_orig["bm25"]["recall@5"]
    bm_b = res_colo["bm25"]["recall@5"]
    cos_a = res_orig["cosine"]["recall@5"]
    cos_b = res_colo["cosine"]["recall@5"]
    print(f"- BM25 退化 {bm_a - bm_b:+.1%}，余弦变化 {cos_a - cos_b:+.1%}；"
          f"若 BM25 退化明显大于余弦，说明原结论含同源性偏置，"
          f"『bge 触发条件』得到数据支持。")
    print("- 若口语集 hybrid-0.15 的 gold 过阈率显著低于题干集，"
          "则固定阈值 0.10 会推高 60003 拒答率，应改为按模式/按集自适应阈值（P1 候选）。")


if __name__ == "__main__":
    main()
