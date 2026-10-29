#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""RAG 检索离线评测：60 道题作查询，gold 知识点的概念切片作目标。

为什么这样评
------------
项目没有“讲解质量”人工标注集，但**有** 60 道带主知识点的题目和逐点概念切片。
于是用“给定题干，能否把正确知识点的切片排进 top-5”作为检索侧代理指标：
- 不编造数据，查询与目标都来自仓库种子；
- 只衡量“检索是否找对知识点”，不代表讲解文风或教学效果；
- 结果用于在同一数据上对比融合方案，不做对外效果宣称。

对比方案
--------
1. weighted-jaccard：现状 0.7*cos + 0.3*字符二元组 Jaccard；
2. cosine：只用哈希向量余弦；
3. bm25：字符二元组 BM25（k1=1.2, b=0.75）；
4. bm25-norm：BM25 用查询自评分归一后再与余弦加权（0.7/0.3）；
5. rrf：余弦排名与 BM25 排名的 Reciprocal Rank Fusion（k=60）。

用法（在 zhixue-ai 目录执行）
------------------------------
    python tools/eval_retrieval.py
    python tools/eval_retrieval.py --json data/eval_retrieval.json
"""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path

AI_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = AI_ROOT.parent
sys.path.insert(0, str(AI_ROOT))

from app.services import vector_index  # noqa: E402

KP_SQL = REPO_ROOT / "zhixue-server" / "sql" / "seed-kp-ch02-ch03.sql"
Q_SQL = REPO_ROOT / "zhixue-server" / "sql" / "seed-question-ch02-ch03.sql"

TOP_K = 5
RRF_K = 60


def _unescape(text: str) -> str:
    return text.replace("''", "'")


_KP_PATTERN = re.compile(
    r"SELECT\s+'(KP\d+)',\s*1,\s*chapter_id,\s*'((?:[^']|'')*)',\s*"
    r"'((?:[^']|'')*)'",
    re.S,
)


def load_kps() -> list[tuple[str, str]]:
    sql = KP_SQL.read_text(encoding="utf-8")
    return [(m.group(1), _unescape(m.group(3))) for m in _KP_PATTERN.finditer(sql)]


def _split_top_level(text: str) -> list[str]:
    parts, buf, quoted, i = [], [], False, 0
    while i < len(text):
        ch = text[i]
        if quoted:
            if ch == "'" and i + 1 < len(text) and text[i + 1] == "'":
                buf.append("''")
                i += 2
                continue
            quoted = ch != "'"
            buf.append(ch)
        elif ch == "'":
            quoted = True
            buf.append(ch)
        elif ch == ",":
            parts.append("".join(buf).strip())
            buf = []
        else:
            buf.append(ch)
        i += 1
    parts.append("".join(buf).strip())
    return parts


def _literal(token: str) -> str:
    token = token.strip()
    return _unescape(token[1:-1]) if token.startswith("'") and token.endswith("'") else token


_VALUES_PATTERN = re.compile(
    r"INSERT\s+INTO\s+question_bank\b.*?VALUES\s*\((.*?)\)\s*ON\s+DUPLICATE", re.S | re.I)


def load_questions() -> list[tuple[str, str, str]]:
    sql = Q_SQL.read_text(encoding="utf-8")
    rows = []
    for match in _VALUES_PATTERN.finditer(sql):
        fields = [_literal(p) for p in _split_top_level(match.group(1))]
        if len(fields) >= 5:
            rows.append((fields[0], fields[2], fields[4]))
    return rows


def normalize(text: str) -> str:
    lowered = (text or "").lower()
    return "".join(ch for ch in lowered
                   if unicodedata.category(ch)[0] not in ("P", "Z", "S"))


def bigram_counts_clean(text: str) -> Counter:
    """与生产 `_bigram_counts` 同口径：小写、去标点/符号/空白。"""
    return Counter(vector_index._bigram_counts(text))


def bigram_counts_raw(text: str) -> Counter:
    """消融用：保留标点的旧口径，验证「标点污染 BM25」这一判断。"""
    clean = vector_index._clean(text).lower()
    return Counter(clean[i:i + 2] for i in range(max(0, len(clean) - 1)))


def cosine(a: list[float], b: list[float]) -> float:
    return sum(x * y for x, y in zip(a, b))


class Bm25:
    def __init__(self, docs: list[str], k1: float = 1.2, b: float = 0.75,
                 tokenizer=bigram_counts_clean):
        self.tokenizer = tokenizer
        self.counts = [tokenizer(doc) for doc in docs]
        self.lengths = [sum(c.values()) for c in self.counts]
        self.avgdl = sum(self.lengths) / max(1, len(self.lengths))
        self.k1, self.b = k1, b
        self.df: Counter = Counter()
        for counts in self.counts:
            self.df.update(counts.keys())
        self.n = len(self.counts)

    def score(self, query: str, index: int) -> float:
        q = self.tokenizer(query)
        doc = self.counts[index]
        dl = self.lengths[index] or 1
        total = 0.0
        for gram, qf in q.items():
            tf = doc.get(gram, 0)
            if not tf:
                continue
            idf = math.log(1 + (self.n - self.df[gram] + 0.5) / (self.df[gram] + 0.5))
            total += idf * (tf * (self.k1 + 1)) / (
                tf + self.k1 * (1 - self.b + self.b * dl / self.avgdl)) * (1 + math.log(qf))
        return total

    def self_score(self, query: str) -> float:
        """查询对自身的 BM25：作为归一化上界，把分数压回 0—1。"""
        q = self.tokenizer(query)
        total = 0.0
        for gram, qf in q.items():
            idf = math.log(1 + (self.n - self.df.get(gram, 0) + 0.5)
                           / (self.df.get(gram, 0) + 0.5))
            total += idf * (qf * (self.k1 + 1)) / (qf + self.k1) * (1 + math.log(qf))
        return total or 1.0


def rrf(rankings: list[list[int]], k: int = RRF_K) -> list[tuple[int, float]]:
    fused: dict[int, float] = {}
    for ranking in rankings:
        for position, index in enumerate(ranking, start=1):
            fused[index] = fused.get(index, 0.0) + 1.0 / (k + position)
    return sorted(fused.items(), key=lambda item: (-item[1], item[0]))


def rank_all(scores: list[float]) -> list[int]:
    return sorted(range(len(scores)), key=lambda i: (-scores[i], i))


def evaluate(chunks, vectors, bm25: Bm25, questions,
             bm25_raw: Bm25 | None = None) -> dict:
    gold_of_chunk = [chunk.kp_id for chunk in chunks]
    names = ["weighted-jaccard", "cosine", "bm25", "bm25-norm",
             "hybrid-0.5", "hybrid-0.3", "hybrid-0.15", "rrf"]
    if bm25_raw is not None:
        names.append("hybrid-0.15-raw")
    rows = {}
    details: dict[str, list[tuple[float, float]]] = {}
    for name in names:
        rows[name] = {"recall": 0, "mrr": 0.0, "top1": 0, "score_sum": 0.0, "n": 0}
        details[name] = []

    for q_id, gold, text in questions:
        if not text.strip():
            continue
        qv = vector_index._hash_vector(text)
        # 与生产一致：哈希余弦为负视为无证据，截断到 0
        cos = [max(0.0, cosine(qv, vec)) for vec in vectors]
        jac = [vector_index.char_bigram_jaccard(text, chunk.content) for chunk in chunks]
        bm = [bm25.score(text, i) for i in range(len(chunks))]
        self_score = bm25.self_score(text)
        bm_norm = [min(1.0, value / self_score) for value in bm]
        raw_norm = []
        if bm25_raw is not None:
            raw = [bm25_raw.score(text, i) for i in range(len(chunks))]
            raw_self = bm25_raw.self_score(text)
            raw_norm = [min(1.0, value / raw_self) for value in raw]

        rankings = {
            "cosine": rank_all(cos),
            "bm25": rank_all(bm),
        }
        scores = {
            "weighted-jaccard": [0.7 * c + 0.3 * j for c, j in zip(cos, jac)],
            "cosine": cos,
            "bm25": bm,
            "bm25-norm": [0.7 * c + 0.3 * b for c, b in zip(cos, bm_norm)],
            "hybrid-0.5": [0.5 * c + 0.5 * b for c, b in zip(cos, bm_norm)],
            "hybrid-0.3": [0.3 * c + 0.7 * b for c, b in zip(cos, bm_norm)],
            "hybrid-0.15": [0.15 * c + 0.85 * b for c, b in zip(cos, bm_norm)],
        }
        if raw_norm:
            scores["hybrid-0.15-raw"] = [0.15 * c + 0.85 * b
                                         for c, b in zip(cos, raw_norm)]
        rankings["rrf"] = [index for index, _ in rrf([rankings["cosine"], rankings["bm25"]])]
        for name in scores:
            rankings[name] = rank_all(scores[name])

        for name, ranking in rankings.items():
            row = rows[name]
            row["n"] += 1
            top_ids = [gold_of_chunk[i] for i in ranking[:TOP_K]]
            if gold in top_ids:
                row["recall"] += 1
                first = top_ids.index(gold) + 1
                row["mrr"] += 1.0 / first
            if top_ids and top_ids[0] == gold:
                row["top1"] += 1
            if name in scores:
                row["score_sum"] += scores[name][ranking[0]] if ranking else 0.0
                values = scores[name]
                max_any = max(values) if values else 0.0
                max_gold = max((values[i] for i, kp in enumerate(gold_of_chunk)
                                if kp == gold), default=0.0)
                details[name].append((max_any, max_gold))
            else:
                row["score_sum"] += 0.0

    result = {}
    for name, row in rows.items():
        n = row["n"] or 1
        result[name] = {
            "recall@5": round(row["recall"] / n, 4),
            "mrr@5": round(row["mrr"] / n, 4),
            "kpTop1": round(row["top1"] / n, 4),
            "avgTopScore": round(row["score_sum"] / n, 4),
        }
    return result, details


def sweep(details: list[tuple[float, float]], thresholds: list[float]) -> list[dict]:
    """阈值口径：有切片过阈 vs 其中 gold 也过阈（过阈但 gold 不在 → 注入无关上下文的概率）。"""
    n = len(details) or 1
    rows = []
    for threshold in thresholds:
        answered = sum(1 for max_any, _ in details if max_any >= threshold)
        answerable = sum(1 for max_any, max_gold in details
                         if max_any >= threshold and max_gold >= threshold)
        rows.append({
            "threshold": threshold,
            "coverage": round(answered / n, 4),
            "goldAbove": round(answerable / n, 4),
            "wrongContextRate": round((answered - answerable) / answered, 4) if answered else 0.0,
        })
    return rows


def evaluate_impl(chunks, questions) -> dict:
    """直接跑生产实现（VectorIndex.search），验证离线复刻与真实代码一致。"""
    index = vector_index.VectorIndex()
    vectors, mode = vector_index.embed_texts([chunk.content for chunk in chunks])
    index.add(chunks, vectors, mode)
    recall = mrr = top1 = coverage = gold_present = 0
    for q_id, gold, text in questions:
        hits = index.search(text, top_k=TOP_K, threshold=0.0)
        ids = [hit.chunk.kp_id for hit in hits]
        if gold in ids:
            recall += 1
            mrr += 1.0 / (ids.index(gold) + 1)
        if ids and ids[0] == gold:
            top1 += 1
        default_hits = index.search(text, top_k=TOP_K)
        if default_hits:
            coverage += 1
            if any(hit.chunk.kp_id == gold for hit in default_hits):
                gold_present += 1
    n = len(questions) or 1
    return {
        "mode": mode,
        "recall@5": round(recall / n, 4),
        "mrr@5": round(mrr / n, 4),
        "kpTop1": round(top1 / n, 4),
        "coverage": round(coverage / n, 4),
        "goldPresent": round(gold_present / n, 4),
        "wrongContextRate": round((coverage - gold_present) / coverage, 4) if coverage else 0.0,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", default="")
    args = parser.parse_args()

    kps = load_kps()
    questions = load_questions()
    chunks = []
    for kp_id, concept in kps:
        chunks.extend(vector_index.chunk_text(concept, kp_id))
    vectors = [vector_index._hash_vector(chunk.content) for chunk in chunks]
    bm25 = Bm25([chunk.content for chunk in chunks])
    bm25_raw = Bm25([chunk.content for chunk in chunks], tokenizer=bigram_counts_raw)
    print(f"知识点 {len(kps)}；切片 {len(chunks)}；查询 {len(questions)}（hash 向量模式）")

    result, details = evaluate(chunks, vectors, bm25, questions, bm25_raw)
    impl = evaluate_impl(chunks, questions)
    print(f"\n生产实现（{impl['mode']}）：recall@5={impl['recall@5']:.1%} "
          f"MRR@5={impl['mrr@5']:.3f} 知识点top-1={impl['kpTop1']:.1%} "
          f"默认阈值覆盖={impl['coverage']:.1%} gold过阈={impl['goldPresent']:.1%} "
          f"错上下文={impl['wrongContextRate']:.1%}")
    print("\n| 方案 | recall@5 | MRR@5 | 知识点 top-1 | top1 平均分 |")
    print("| --- | --- | --- | --- | --- |")
    for name, cell in result.items():
        print(f"| {name} | {cell['recall@5']:.1%} | {cell['mrr@5']:.3f} "
              f"| {cell['kpTop1']:.1%} | {cell['avgTopScore']:.4f} |")

    thresholds = [0.08, 0.0875, 0.10, 0.12, 0.15, 0.20]
    sweeps = {name: sweep(details[name], thresholds)
              for name in ("weighted-jaccard", "hybrid-0.15")}
    for name, rows in sweeps.items():
        print(f"\n阈值扫描（{name}）：")
        print("| 阈值 | 有切片过阈 | gold 也过阈 | 过阈但 gold 不在 |")
        print("| --- | --- | --- | --- |")
        for row in rows:
            print(f"| {row['threshold']:.2f} | {row['coverage']:.1%} "
                  f"| {row['goldAbove']:.1%} | {row['wrongContextRate']:.1%} |")

    if args.json:
        out = Path(args.json)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps({"chunks": len(chunks), "queries": len(questions),
                                   "results": result, "thresholdSweeps": sweeps},
                                  ensure_ascii=False, indent=2), encoding="utf-8")
        print("\n已写出：" + str(out))


if __name__ == "__main__":
    main()
