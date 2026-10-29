#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""映射算法离线评测（不依赖后端/数据库，直接读种子 SQL）。

目的
----
1. 复刻 `KpMappingService` 的字符二元组 TF-IDF 余弦，验证 15 号文档记录的
   60 题 top-1 73.3% 基线；
2. 在同一 60 题集上对比候选改进（章节先验、误区入语料、BM25、RRF），
   用数据决定是否改 Java 实现，避免拍脑袋调参。

数据来源（只读、仓库内）
- 知识点与概念切片：`zhixue-server/sql/seed-kp-ch02-ch03.sql`
- 60 题与主知识点：`zhixue-server/sql/seed-question-ch02-ch03.sql`
- 32 题补充标注（可选）：`tools/testdata/mapping_cases.csv`

口径
----
- 打分公式与 Java 保持一致：tf = 1 + ln(count)，idf = (ln((N+1)/(df+1))+1)^1.5，
  L2 归一化后余弦；概念切片 + 名称重复 3 次；score = min(1, cos/0.3)。
- 章先知情的两种口径分开报：
  * hard  —— 调用方明确传入 chapterId 时只在该章候选内排序（真实可用）；
  * oracle —— 用 gold 所属章过滤，是上限参考，不代表线上效果。
- 只报可复现的相对比较，不把离线结果当成线上验收数据。

用法
----
    python tools/eval_mapping_offline.py
    python tools/eval_mapping_offline.py --json tools/testdata/mapping_offline.json
"""
from __future__ import annotations

import argparse
import json
import math
import re
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KP_SQL = ROOT / "zhixue-server" / "sql" / "seed-kp-ch02-ch03.sql"
Q_SQL = ROOT / "zhixue-server" / "sql" / "seed-question-ch02-ch03.sql"

IDF_POWER = 1.5
NAME_REPEAT = 3
SCORE_SCALE = 0.3
AUTO_THRESHOLD = 0.60
MARGIN = 0.10
CONFIRM_FLOOR = 0.40


@dataclass(frozen=True)
class Kp:
    kp_id: str
    name: str
    concept: str
    misconception: str
    chapter: int


@dataclass(frozen=True)
class Question:
    q_id: str
    gold: str
    text: str
    chapter: int


# ---------- 种子 SQL 解析 ----------

_KP_PATTERN = re.compile(
    r"SELECT\s+'(KP\d+)',\s*1,\s*chapter_id,\s*'((?:[^']|'')*)',\s*"
    r"'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*'([^']*)',\s*'[^']*',\s*\d+,\s*\d+\s*"
    r"FROM\s+course_chapter\s+WHERE\s+course_id\s*=\s*1\s+AND\s+chapter_no\s*=\s*(\d+)",
    re.S,
)


def _unescape(text: str) -> str:
    return text.replace("''", "'")


def load_kps() -> list[Kp]:
    sql = KP_SQL.read_text(encoding="utf-8")
    kps = [
        Kp(m.group(1), _unescape(m.group(2)), _unescape(m.group(3)),
           _unescape(m.group(4)), int(m.group(6)))
        for m in _KP_PATTERN.finditer(sql)
    ]
    if not kps:
        raise SystemExit("未从种子 SQL 解析出知识点：" + str(KP_SQL))
    return kps


def _split_top_level(text: str) -> list[str]:
    """按顶层逗号切分 SQL VALUES 元组，忽略引号内逗号，支持 '' 转义。"""
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
    if token.startswith("'") and token.endswith("'"):
        return _unescape(token[1:-1])
    return token


_VALUES_PATTERN = re.compile(r"INSERT\s+INTO\s+question_bank\b.*?VALUES\s*\((.*?)\)\s*ON\s+DUPLICATE",
                             re.S | re.I)


def load_questions() -> list[Question]:
    sql = Q_SQL.read_text(encoding="utf-8")
    questions = []
    for match in _VALUES_PATTERN.finditer(sql):
        fields = [_literal(part) for part in _split_top_level(match.group(1))]
        # 列序：q_id, course_id, primary_kp_id, question_type, question_text, ...
        if len(fields) < 5:
            continue
        q_id, gold, text = fields[0], fields[2], fields[4]
        chapter = int(gold[2:4]) if gold.startswith("KP") else 0
        questions.append(Question(q_id, gold, text, chapter))
    if not questions:
        raise SystemExit("未从种子 SQL 解析出题目：" + str(Q_SQL))
    return questions


def load_extra_cases() -> list[Question]:
    path = ROOT / "tools" / "testdata" / "mapping_cases.csv"
    if not path.exists():
        return []
    import csv

    rows = list(csv.DictReader(path.read_text(encoding="utf-8-sig").splitlines()))
    result = []
    for row in rows:
        gold = (row.get("true_kp_id") or "").strip()
        text = (row.get("question_text") or "").strip()
        if not gold or not text:
            continue
        result.append(Question(row.get("case_id") or "MC", gold, text,
                               int(row.get("chapter") or 0)))
    return result


# ---------- 打分（与 Java 同公式） ----------


def normalize(text: str) -> str:
    lowered = (text or "").lower()
    return "".join(ch for ch in lowered
                   if unicodedata.category(ch)[0] not in ("P", "Z", "S"))


def term_counts(text: str) -> Counter:
    clean = normalize(text)
    return Counter(clean[i:i + 2] for i in range(max(0, len(clean) - 1)))


def corpus_of(kp: Kp, use_misconception: bool) -> str:
    parts = [kp.concept or ""]
    parts.extend([kp.name or ""] * NAME_REPEAT)
    if use_misconception:
        parts.append(kp.misconception or "")
    return " ".join(parts)


def vectorize(counts: Counter, doc_freq: Counter, doc_count: int, power: float) -> dict:
    vector = {}
    for gram, count in counts.items():
        df = doc_freq.get(gram)
        if df is None:
            continue
        idf = (math.log((doc_count + 1.0) / (df + 1.0)) + 1.0) ** power
        vector[gram] = (1.0 + math.log(count)) * idf
    norm = math.sqrt(sum(v * v for v in vector.values()))
    return {k: v / norm for k, v in vector.items()} if norm else {}


def cosine(a: dict, b: dict) -> float:
    if len(a) > len(b):
        a, b = b, a
    return sum(v * b.get(k, 0.0) for k, v in a.items())


class Scorer:
    def __init__(self, kps: list[Kp], use_misconception: bool = False,
                 power: float = IDF_POWER):
        self.kps = kps
        self.use_misconception = use_misconception
        self.power = power
        self.docs = [term_counts(corpus_of(kp, use_misconception)) for kp in kps]
        self.doc_freq = Counter()
        for counts in self.docs:
            self.doc_freq.update(counts.keys())
        self.vectors = [vectorize(c, self.doc_freq, len(kps), power) for c in self.docs]

    def rank(self, text: str) -> list[tuple[Kp, float]]:
        qv = vectorize(term_counts(text), self.doc_freq, len(self.kps), self.power)
        scored = []
        for kp, vector in zip(self.kps, self.vectors):
            cos = cosine(qv, vector)
            if cos > 0:
                scored.append((kp, min(1.0, cos / SCORE_SCALE)))
        scored.sort(key=lambda item: (-item[1], item[0].kp_id))
        return scored


def bm25_rank(kps: list[Kp], texts: list[str], query: str,
              k1: float = 1.2, b: float = 0.75) -> list[tuple[Kp, float]]:
    """字符二元组 BM25：词频饱和 + 文档长度归一，替代 Jaccard 集合重叠。"""
    docs = [term_counts(text) for text in texts]
    avgdl = sum(sum(d.values()) for d in docs) / max(1, len(docs))
    df = Counter()
    for d in docs:
        df.update(d.keys())
    n = len(docs)
    q = term_counts(query)
    scored = []
    for kp, doc in zip(kps, docs):
        dl = sum(doc.values()) or 1
        score = 0.0
        for gram, qf in q.items():
            tf = doc.get(gram, 0)
            if not tf:
                continue
            idf = math.log(1 + (n - df[gram] + 0.5) / (df[gram] + 0.5))
            score += idf * (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * dl / avgdl)) * (1 + math.log(qf))
        if score > 0:
            scored.append((kp, score))
    scored.sort(key=lambda item: (-item[1], item[0].kp_id))
    return scored


def rrf_fuse(rankings: list[list[tuple[Kp, float]]], k: int = 60) -> list[tuple[Kp, float]]:
    fused: dict[str, float] = defaultdict(float)
    kp_by_id = {}
    for ranking in rankings:
        for position, (kp, _) in enumerate(ranking, start=1):
            fused[kp.kp_id] += 1.0 / (k + position)
            kp_by_id[kp.kp_id] = kp
    items = [(kp_by_id[kp_id], score) for kp_id, score in fused.items()]
    items.sort(key=lambda item: (-item[1], item[0].kp_id))
    return items


# ---------- 指标 ----------


_BINS = ((0.0, 0.4), (0.4, 0.6), (0.6, 0.8), (0.8, 1.01))


def evaluate(name: str, kps: list[Kp], questions: list[Question],
             ranker, chapter_mode: str = "none", penalty: float = 0.0,
             floor: float = CONFIRM_FLOOR, fallback_below_floor: bool = False) -> dict:
    chapter_of = {kp.kp_id: kp.chapter for kp in kps}
    top1 = top3 = 0
    auto = confirm = failed = auto_hits = usable_hits = 0
    bins = Counter()
    bin_hits = Counter()
    misses = []
    for q in questions:
        ranked_all = ranker(q.text)
        ranked = ranked_all
        if chapter_mode in ("hard", "oracle") and q.chapter:
            ranked = [(kp, s) for kp, s in ranked
                      if chapter_of.get(kp.kp_id) == q.chapter]
        elif chapter_mode == "consistency" and penalty:
            # 真实可用的软先验：以 top-1 所属章为准，跨章候选降权（不偷看 gold）
            if ranked:
                top_chapter = chapter_of.get(ranked[0][0].kp_id)
                ranked = [(kp, s - penalty if chapter_of.get(kp.kp_id) != top_chapter else s)
                          for kp, s in ranked]
        elif chapter_mode == "soft" and q.chapter and penalty:
            ranked = [(kp, s - penalty if chapter_of.get(kp.kp_id) != q.chapter else s)
                      for kp, s in ranked]
        if fallback_below_floor and (not ranked or ranked[0][1] < floor):
            # 章过滤后置信不足时回退全量：避免“章选错/跨章题”直接判 FAILED
            ranked = ranked_all
        ranked.sort(key=lambda item: (-item[1], item[0].kp_id))
        ids = [kp.kp_id for kp, _ in ranked]
        hit = bool(ids) and ids[0] == q.gold
        if hit:
            top1 += 1
        else:
            misses.append({"qId": q.q_id, "gold": q.gold,
                           "top3": ids[:3], "chapter": q.chapter})
        if q.gold in ids[:3]:
            top3 += 1
        # 决策分布
        if not ranked or ranked[0][1] < floor:
            failed += 1
        elif ranked[0][1] >= AUTO_THRESHOLD and (
                len(ranked) < 2 or ranked[0][1] - ranked[1][1] >= MARGIN):
            auto += 1
            auto_hits += 1 if hit else 0
            usable_hits += 1 if hit else 0
        else:
            confirm += 1
            usable_hits += 1 if hit else 0
        if ranked:
            score = ranked[0][1]
            for low, high in _BINS:
                if low <= score < high:
                    label = f"{low:.1f}-{min(high, 1.0):.1f}"
                    bins[label] += 1
                    bin_hits[label] += 1 if hit else 0
                    break
    n = len(questions)
    return {
        "variant": name,
        "n": n,
        "top1": round(top1 / n, 4),
        "top3": round(top3 / n, 4),
        "auto": auto, "autoPrecision": round(auto_hits / auto, 4) if auto else 0.0,
        "confirm": confirm, "failed": failed,
        "coverage": round((n - failed) / n, 4),
        "precisionAboveFloor": round(usable_hits / (n - failed), 4) if n > failed else 0.0,
        "calibration": {label: {"n": bins[label],
                                "acc": round(bin_hits[label] / bins[label], 4)}
                        for label in sorted(bins)},
        "misses": misses[:20],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", type=str, default="")
    args = parser.parse_args()

    kps = load_kps()
    questions = load_questions()
    extra = load_extra_cases()
    print(f"知识点 {len(kps)} 个；题库 {len(questions)} 题；补充标注 {len(extra)} 题")

    baseline = Scorer(kps)
    mis = Scorer(kps, use_misconception=True)
    results = [
        evaluate("V0 baseline（TF-IDF, IDF^1.5）", kps, questions, baseline.rank),
        evaluate("V1 IDF^2.0", kps, questions, Scorer(kps, power=2.0).rank),
        evaluate("V2 误区入语料", kps, questions, mis.rank),
        evaluate("V3 章一致性 -0.03", kps, questions, baseline.rank,
                 chapter_mode="consistency", penalty=0.03),
        evaluate("V4 章一致性 -0.06", kps, questions, baseline.rank,
                 chapter_mode="consistency", penalty=0.06),
        evaluate("V5 章一致性 -0.10", kps, questions, baseline.rank,
                 chapter_mode="consistency", penalty=0.10),
        evaluate("V6 章硬过滤（传 chapterId）", kps, questions, baseline.rank,
                 chapter_mode="hard"),
        evaluate("V7 误区 + 章硬过滤", kps, questions, mis.rank,
                 chapter_mode="hard"),
        evaluate("V10 章硬过滤 + 低置信回退", kps, questions, baseline.rank,
                 chapter_mode="hard", fallback_below_floor=True),
    ]

    bm25_docs = [corpus_of(kp, False) for kp in kps]
    bm25 = lambda text: bm25_rank(kps, bm25_docs, text)  # noqa: E731
    results.append(evaluate("V8 BM25（字符二元组）", kps, questions, bm25))
    results.append(evaluate("V9 RRF(TF-IDF, BM25)", kps, questions,
                            lambda text: rrf_fuse([baseline.rank(text), bm25(text)])))

    print("\n| 方案 | top-1 | top-3 | AUTO | AUTO 精确率 | CONFIRM | FAILED |")
    print("| --- | --- | --- | --- | --- | --- | --- |")
    for item in results:
        print(f"| {item['variant']} | {item['top1']:.1%} | {item['top3']:.1%} "
              f"| {item['auto']} | {item['autoPrecision']:.1%} "
              f"| {item['confirm']} | {item['failed']} |")

    print("\n确认下限敏感性（baseline，只影响 FAILED 与 CONFIRM 分界）：")
    print("| floor | 覆盖（非 FAILED） | 下限以上 top-1 精确率 |")
    print("| --- | --- | --- |")
    for floor in (0.20, 0.30, 0.35, 0.40):
        cell = evaluate(f"floor={floor:.2f}", kps, questions, baseline.rank, floor=floor)
        print(f"| {floor:.2f} | {cell['coverage']:.1%} | {cell['precisionAboveFloor']:.1%} |")

    print("\n基线校准（top1 分数区间 → 实际正确率）：")
    for label, cell in results[0]["calibration"].items():
        print(f"  {label}: n={cell['n']}, acc={cell['acc']:.1%}")

    print("\n基线错分（前 10）：")
    for miss in results[0]["misses"][:10]:
        print(f"  {miss['qId']} gold={miss['gold']} top3={miss['top3']}")

    if args.json:
        out = Path(args.json)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps({"results": results, "baseline_misses": results[0]["misses"]},
                                  ensure_ascii=False, indent=2), encoding="utf-8")
        print("\n已写出：" + str(out))


if __name__ == "__main__":
    main()
