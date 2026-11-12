#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""映射算法离线评测（不依赖后端/数据库，直接读种子 SQL）。

目的
----
1. 复刻 `KpMappingService` 的字符二元组 TF-IDF 余弦 + 决策三段，作为算法回归门禁；
2. 在同一题库上对比候选改进（章节先验、误区入语料、低置信回退、BM25、RRF），
   用数据决定是否改 Java 实现，避免拍脑袋调参。

数据来源（只读、仓库内）
- 知识点与概念切片：`zhixue-server/sql/seed-kp-*.sql`
- 60 题与主知识点：`zhixue-server/sql/seed-question-ch02-ch03.sql`
- 32 题补充标注（补充观察集，**不作门禁**）：`tools/testdata/mapping_cases.csv`

语料口径（两个口径不可混引，报告必须打印 N = universe_size）
----------------------------------------------------------------
- ``--universe ch23``（U20）：只读 `seed-kp-ch02-ch03.sql`，N=20。历史权威口径。
- ``--universe all``（U60，**默认**）：扫描 `seed-kp-*.sql` 全部（ch01—ch06），N=60。
  生产 `KnowledgePointService.listMatchProfiles` 返回课程**全部**启用知识点，
  当前种子就是 60 个，因此 ``all`` 才是线上语义。

口径与忠实性
------------
- 打分公式与 Java 一致：tf = 1 + ln(count)，idf = (ln((N+1)/(df+1))+1)^1.5，
  L2 归一化后余弦；语料 = 概念切片 + 名称重复 3 次（+ 可选误区文本）；
  score = min(1, cos / score-scale)，Java 侧 round4 后再比阈值（本脚本不舍入，
  60 题集上两者逐位一致，见 docs/design/mapping-optimization-2026-11-03.md §2.2）。
- **排序键用原始 cosine，绝不用校准后的 score**（D3）。cos ≥ score-scale 时多个
  候选会饱和到 score = 1.0，若按 score 排序就会退化成按 kpId 字母序。
- df / N 始终在**全量画像**上统计；章过滤只缩小"参与排序的候选集合"（D2）。
  生产 Java 已按此修正，因此本脚本的章过滤变体与 Java 语义一致。
- 章过滤变体的诚实声明：
  * ``hard`` 用 gold 所属章作为"调用方传入的章"，**假设调用方传对了章**，
    是上限参考；当前生产调用链**从不传 chapterId**（见设计文档 §4），线上不可达。
  * ``soft`` / ``oracle`` 直接用 gold 所属章，是 oracle 口径，
    **禁止**用于任何线上效果声明。

用法
----
    python tools/eval_mapping_offline.py                       # 默认 --universe all
    python tools/eval_mapping_offline.py --universe ch23
    python tools/eval_mapping_offline.py --json tools/testdata/mapping_eval_offline_all_universe.json
    python tools/eval_mapping_offline.py --skip-extra           # 不跑 32 题补充集
"""
from __future__ import annotations

import argparse
import json
import math
import re
import time
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, replace
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SQL_DIR = ROOT / "zhixue-server" / "sql"
KP_SQL_CH23 = SQL_DIR / "seed-kp-ch02-ch03.sql"
KP_SQL_ALL_GLOB = "seed-kp-*.sql"
Q_SQL = SQL_DIR / "seed-question-ch02-ch03.sql"

IDF_POWER = 1.5
NAME_REPEAT = 3
SCORE_SCALE = 0.3
AUTO_THRESHOLD = 0.60
MARGIN = 0.10

# 与 zhixue-bootstrap/src/main/resources/application.yml 的 mapping.confirm-floor 保持同值。
# 历史口径是 0.40（V0 现状）；随"误区入语料"下调到 0.35，见设计文档 §6.2。
CONFIRM_FLOOR = 0.35


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


@dataclass(frozen=True)
class Hit:
    """一条候选。`raw` 是唯一排序键，`score` 只用于决策与分档。"""

    kp: Kp
    score: float
    raw: float


# ---------- 种子 SQL 解析 ----------

# 列序：'kp_id', 1, chapter_id, 'name', 'concept_text', 'misconception',
#       'textbook_page', 'dimension_key', sort_no, created_by
# 注意：ch02-ch03 的 sort_no 写成 1，ch01/ch04/ch05/ch06 写成 '1'（带引号），
# 因此数值列必须允许可选引号，否则会静默漏掉 ch01/ch04+（旧版 `\d+` 的缺陷）。
_KP_PATTERN = re.compile(
    r"SELECT\s+'(KP\d+)'\s*,\s*1\s*,\s*chapter_id\s*,\s*'((?:[^']|'')*)'\s*,\s*"
    r"'((?:[^']|'')*)'\s*,\s*'((?:[^']|'')*)'\s*,\s*'([^']*)'\s*,\s*'[^']*'\s*,\s*"
    r"'?\d+'?\s*,\s*'?\d+'?\s*"
    r"FROM\s+course_chapter\s+WHERE\s+course_id\s*=\s*1\s+AND\s+chapter_no\s*=\s*(\d+)",
    re.S,
)


def _unescape(text: str) -> str:
    return text.replace("''", "'")


def _parse_kp_file(path: Path) -> list[Kp]:
    sql = path.read_text(encoding="utf-8")
    return [
        Kp(m.group(1), _unescape(m.group(2)), _unescape(m.group(3)),
           _unescape(m.group(4)), int(m.group(6)))
        for m in _KP_PATTERN.finditer(sql)
    ]


def kp_sql_files(universe: str) -> list[Path]:
    """按 universe 口径返回参与语料构建的种子文件（按文件名升序）。"""
    if universe == "ch23":
        return [KP_SQL_CH23]
    if universe == "all":
        files = sorted(SQL_DIR.glob(KP_SQL_ALL_GLOB))
        if not files:
            raise SystemExit("未找到知识点种子：" + str(SQL_DIR / KP_SQL_ALL_GLOB))
        return files
    raise SystemExit("未知 universe 口径：" + str(universe))


def load_kps(universe: str = "all") -> list[Kp]:
    """解析知识点语料；按 kp_id 去重（ch02-ch03 同时覆盖第 2、3 章）。"""
    kps: list[Kp] = []
    seen: set[str] = set()
    for path in kp_sql_files(universe):
        for kp in _parse_kp_file(path):
            if kp.kp_id in seen:
                continue
            seen.add(kp.kp_id)
            kps.append(kp)
    if not kps:
        raise SystemExit("未从种子 SQL 解析出知识点：universe=" + universe)
    kps.sort(key=lambda item: item.kp_id)
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
    """32 题补充观察集（习题册选择题原文，含选项文本噪声，不作门禁）。"""
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


def filter_in_universe(cases: list[Question], kps: list[Kp]) -> tuple[list[Question], int]:
    """丢掉 gold 不在当前语料内的用例（否则 top1 永远不可能命中，指标失真）。"""
    known = {kp.kp_id for kp in kps}
    kept = [q for q in cases if q.gold in known]
    return kept, len(cases) - len(kept)


# ---------- 打分（与 Java 同公式） ----------


def normalize(text: str) -> str:
    lowered = (text or "").lower()
    return "".join(ch for ch in lowered
                   if unicodedata.category(ch)[0] not in ("P", "Z", "S"))


def term_counts(text: str) -> Counter:
    clean = normalize(text)
    return Counter(clean[i:i + 2] for i in range(max(0, len(clean) - 1)))


def corpus_of(kp: Kp, use_misconception: bool) -> str:
    """语料 = 概念切片 + 名称 ×3（+ 误区一次，不重复——重复会变成新旋钮）。"""
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
    """全量画像上建一次 df / N / 文档向量；查询时按原始 cosine 排序（D3）。"""

    def __init__(self, kps: list[Kp], use_misconception: bool = False,
                 power: float = IDF_POWER):
        self.kps = kps
        self.use_misconception = use_misconception
        self.power = power
        self.docs = [term_counts(corpus_of(kp, use_misconception)) for kp in kps]
        self.doc_freq = Counter()
        for counts in self.docs:
            self.doc_freq.update(counts.keys())
        self.doc_count = len(kps)
        self.vectors = [vectorize(c, self.doc_freq, self.doc_count, power) for c in self.docs]

    def rank(self, text: str) -> list[Hit]:
        qv = vectorize(term_counts(text), self.doc_freq, self.doc_count, self.power)
        hits = []
        for kp, vector in zip(self.kps, self.vectors):
            cos = cosine(qv, vector)
            if cos > 0:
                hits.append(Hit(kp, min(1.0, cos / SCORE_SCALE), cos))
        # ★ 排序键必须是原始 cosine；按 score 排会在饱和后退化为按 kpId 排（D3）
        hits.sort(key=lambda hit: (-hit.raw, hit.kp.kp_id))
        return hits


def bm25_rank(kps: list[Kp], texts: list[str], query: str,
              k1: float = 1.2, b: float = 0.75) -> list[Hit]:
    """字符二元组 BM25：词频饱和 + 文档长度归一，替代 Jaccard 集合重叠。

    原始 BM25 分不在 0—1 标度上，故 score 直接沿用原始分（与历史版本口径一致，
    该变体已被否决，仅作对照）。
    """
    docs = [term_counts(text) for text in texts]
    avgdl = sum(sum(d.values()) for d in docs) / max(1, len(docs))
    df = Counter()
    for d in docs:
        df.update(d.keys())
    n = len(docs)
    q = term_counts(query)
    hits = []
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
            hits.append(Hit(kp, score, score))
    hits.sort(key=lambda hit: (-hit.raw, hit.kp.kp_id))
    return hits


def rrf_fuse(rankings: list[list[Hit]], k: int = 60) -> list[Hit]:
    fused: dict[str, float] = defaultdict(float)
    kp_by_id: dict[str, Kp] = {}
    for ranking in rankings:
        for position, hit in enumerate(ranking, start=1):
            fused[hit.kp.kp_id] += 1.0 / (k + position)
            kp_by_id[hit.kp.kp_id] = hit.kp
    hits = [Hit(kp_by_id[kp_id], score, score) for kp_id, score in fused.items()]
    hits.sort(key=lambda hit: (-hit.raw, hit.kp.kp_id))
    return hits


# ---------- 指标 ----------


_BINS = ((0.0, 0.4), (0.4, 0.6), (0.6, 0.8), (0.8, 1.01))


def decide(ranked: list[Hit], floor: float) -> str:
    """与 Java `KpMappingService.decide` 同规则：floor / auto / margin 全读配置。"""
    if not ranked or ranked[0].score < floor:
        return "FAILED"
    if ranked[0].score >= AUTO_THRESHOLD and (
            len(ranked) < 2 or ranked[0].score - ranked[1].score >= MARGIN):
        return "AUTO"
    return "CONFIRM"


def evaluate(name: str, kps: list[Kp], questions: list[Question],
             ranker, chapter_mode: str = "none", penalty: float = 0.0,
             floor: float = CONFIRM_FLOOR, fallback_below_floor: bool = False,
             keep_cases: bool = False, note: str = "",
             use_misconception: bool = False) -> dict:
    """在给定 ranker 上算 top1 / top3 / 决策分布 / 校准 / 逐题明细。

    ``use_misconception`` 必须由调用方显式传入该变体的真实取值：ranker 常是
    ``Scorer.rank`` 这种绑定方法，``getattr`` 拿不到开关状态（会静默报 false）。

    ``chapter_mode`` 说明（口径必须如实标注，见模块 docstring）：
      - "none"        : 不启用章信息，等价线上（当前生产调用链不传 chapterId）。
      - "hard"        : 只保留「gold 所属章」的候选，**假设调用方传对了章**；
                        线上不可达，仅作上限参考。
      - "consistency" : 以 top1 所属章为准做跨章降权，不偷看 gold，真实可用。
      - "soft"/"oracle": 直接用 gold 所属章降权 —— **oracle 口径，
                        禁止用于任何线上效果声明**。
    """
    if chapter_mode in ("soft", "oracle"):
        note = (note + " ").strip() + "[oracle 口径：用 gold 所属章，禁止用于线上效果声明]"

    chapter_of = {kp.kp_id: kp.chapter for kp in kps}
    top1 = top3 = 0
    auto = confirm = failed = auto_hits = usable_hits = 0
    bins = Counter()
    bin_hits = Counter()
    misses: list[dict] = []
    cases: list[dict] = []

    for q in questions:
        ranked_all = ranker(q.text)
        ranked = ranked_all
        if chapter_mode in ("hard", "oracle") and q.chapter:
            ranked = [hit for hit in ranked_all if chapter_of.get(hit.kp.kp_id) == q.chapter]
        elif chapter_mode == "consistency" and penalty:
            # 真实可用的软先验：以 top-1 所属章为准，跨章候选降权（不偷看 gold）
            if ranked:
                top_chapter = chapter_of.get(ranked[0].kp.kp_id)
                ranked = [
                    replace(hit, score=hit.score - penalty, raw=hit.raw - penalty)
                    if chapter_of.get(hit.kp.kp_id) != top_chapter else hit
                    for hit in ranked
                ]
        elif chapter_mode == "soft" and q.chapter and penalty:
            # oracle：直接看 gold 所属章，仅作上限参考
            ranked = [
                replace(hit, score=hit.score - penalty, raw=hit.raw - penalty)
                if chapter_of.get(hit.kp.kp_id) != q.chapter else hit
                for hit in ranked
            ]
        if fallback_below_floor and (not ranked or ranked[0].score < floor):
            # 章过滤后置信不足时回退全量：避免"章选错/跨章题"直接判 FAILED
            ranked = ranked_all
        ranked = sorted(ranked, key=lambda hit: (-hit.raw, hit.kp.kp_id))

        ids = [hit.kp.kp_id for hit in ranked]
        decision = decide(ranked, floor)
        hit = bool(ids) and ids[0] == q.gold
        if hit:
            top1 += 1
        else:
            misses.append({"qId": q.q_id, "gold": q.gold,
                           "top3": ids[:3], "chapter": q.chapter})
        hit3 = q.gold in ids[:3]
        if hit3:
            top3 += 1

        if decision == "FAILED":
            failed += 1
        elif decision == "AUTO":
            auto += 1
            auto_hits += 1 if hit else 0
            usable_hits += 1 if hit else 0
        else:
            confirm += 1
            usable_hits += 1 if hit else 0

        if ranked:
            score = ranked[0].score
            for low, high in _BINS:
                if low <= score < high:
                    label = f"{low:.1f}-{min(high, 1.0):.1f}"
                    bins[label] += 1
                    bin_hits[label] += 1 if hit else 0
                    break

        if keep_cases:
            cases.append({
                "qId": q.q_id,
                "gold": q.gold,
                "ranked_top3": ids[:3],
                "score": round(ranked[0].score, 4) if ranked else 0.0,
                "cosine": round(ranked[0].raw, 4) if ranked else 0.0,
                "decision": decision,
                "hit1": hit,
                "hit3": hit3,
            })

    n = len(questions)
    result = {
        "variant": name,
        "n": n,
        "floor": floor,
        "chapter_mode": chapter_mode,
        "use_misconception": bool(use_misconception),
        "top1": round(top1 / n, 4) if n else 0.0,
        "top3": round(top3 / n, 4) if n else 0.0,
        "top1_hits": top1,
        "top3_hits": top3,
        "auto": auto,
        "autoHits": auto_hits,
        "autoPrecision": round(auto_hits / auto, 4) if auto else 0.0,
        "confirm": confirm,
        "failed": failed,
        "coverage": round((n - failed) / n, 4) if n else 0.0,
        "precisionAboveFloor": round(usable_hits / (n - failed), 4) if n > failed else 0.0,
        "calibration": {label: {"n": bins[label],
                                "acc": round(bin_hits[label] / bins[label], 4)}
                        for label in sorted(bins)},
        "misses": misses[:20],
        "note": note,
    }
    if keep_cases:
        result["per_case"] = cases
    return result


# ---------- 门禁（docs/design/mapping-optimization-2026-11-03.md §8.5） ----------

# 口径提示（2026-11-08，仅注释）：`label` 是历史遗留的按「语料知识点数」命名
# （ch23 曾为 20 点 -> "U20"；all 曾为 60 点 -> "U60"）。当前 `--universe all`
# 实为 81 点语料 / 66 题，故 label 既不等于点数 60，也不等于题数 60。
# 该 label 会被打印进 CLI 表头并写进结果 JSON，复制输出当证据时会出现
# 「U60 口径，N=81」式自相矛盾。登记映射数字一律用五列：
#   语料点数(universe_size) / 验收集题数(n) / 口径名+gate_line /
#   冻结日期+seed SHA / 可引用性（五类枚举）。
# 详见 `spark-output/defense/证据链一致性口径卡_2026-11-06.md` §8。
# 本注释不改动判定逻辑；label 与阈值的变更需 D1 签字后另行处置。

GATES = {
    "ch23": {"top1": 0.733, "top3": 0.850, "autoPrecision": 0.900,
             "coverage": 0.867, "failed": 8, "label": "U20"},
    "all": {"top1": 0.600, "top3": 0.800, "autoPrecision": 0.900,
            "coverage": 0.867, "failed": 8, "label": "U60"},
}

# 门禁阈值以"百分比 1 位小数"给出（73.3 / 85.0 / 90.0 / 86.7），指标报告也是这个精度。
_PERCENT_DECIMALS = 1


def _same_precision(actual: float, required: float) -> bool:
    """门禁**同精度**比较：实测值与阈值都按报告精度（百分比 1 位小数）舍入后再比。

    为什么不能直接比浮点：覆盖率 52/60 = 0.866666…，报告值是 86.7%，与阈值 86.7%
    属同一精度；直接比浮点会得到 `0.8667 < 0.867` 的**假 FAIL**（2026-11-03 实跑遇到）。

    为什么不是放宽容差：容差会让"报告值 86.6%"也蒙混过关。这里只对齐精度，
    **阈值一位不动**——舍入后 < 86.7 的实测值仍然 FAIL（86.64% → 86.6% → FAIL）。
    等价表述：接受边界是 `阈值 − 0.05pp`（半个报告刻度），这就是"按报告精度比较"
    的数学后果，而不是人为下调阈值。精确分数（52/60=86.67%）始终随门禁表一起打印，
    便于人工复核。
    """
    scale = 10 ** _PERCENT_DECIMALS
    return round(actual * 100, _PERCENT_DECIMALS) >= round(required * 100, _PERCENT_DECIMALS)


def check_gates(cell: dict, universe: str) -> list[dict]:
    """逐条核对门禁，返回带精确分数的明细，供打印与 JSON 落盘。"""
    gate = GATES[universe]
    n = cell["n"]
    hit1, hit3 = cell["top1_hits"], cell["top3_hits"]
    auto, auto_hits = cell["auto"], cell["autoHits"]
    failed = cell["failed"]
    usable = n - failed

    def ratio_row(name: str, required: float, hits: int, total: int,
                  required_text: str) -> dict:
        actual = hits / total if total else 0.0
        return {
            "name": name,
            "required": required_text,
            "actual": round(actual, 4),
            "detail": (f"{hits}/{total}={actual * 100:.2f}%" if total else "0/0=n/a"),
            "pass": _same_precision(actual, required),
        }

    return [
        ratio_row("top1", gate["top1"], hit1, n, f"≥ {gate['top1']:.1%}"),
        ratio_row("top3", gate["top3"], hit3, n, f"≥ {gate['top3']:.1%}"),
        ratio_row("AUTO 精确率", gate["autoPrecision"], auto_hits, auto,
                  f"≥ {gate['autoPrecision']:.1%}"),
        ratio_row("覆盖（非 FAILED）", gate["coverage"], usable, n,
                  f"≥ {gate['coverage']:.1%}"),
        {
            "name": "FAILED 题数",
            "required": f"≤ {gate['failed']}",
            "actual": failed,
            "detail": f"{failed} 题",
            "pass": failed <= gate["failed"],
        },
    ]


# ---------- 主流程 ----------


def main() -> None:
    parser = argparse.ArgumentParser(
        description="映射算法离线评测（两个语料口径，默认 all=线上语义）")
    parser.add_argument("--universe", choices=("ch23", "all"), default="all",
                        help="语料口径：ch23=仅第2/3章（N=20，历史权威口径）；"
                             "all=全部种子（N=60，线上语义，默认）")
    parser.add_argument("--floor", type=float, default=CONFIRM_FLOOR,
                        help=f"确认下限，默认 {CONFIRM_FLOOR}（与 application.yml 同值）")
    parser.add_argument("--json", type=str, default="", help="把完整结果写入该 JSON 路径")
    parser.add_argument("--skip-extra", dest="include_extra", action="store_false",
                        help="跳过 32 题补充观察集")
    parser.add_argument("--no-per-case", dest="per_case", action="store_false",
                        help="JSON 中不落逐题明细")
    parser.set_defaults(include_extra=True, per_case=True)
    args = parser.parse_args()

    kps = load_kps(args.universe)
    questions = load_questions()
    universe_size = len(kps)
    extra_all = load_extra_cases() if args.include_extra else []
    extra, extra_skipped = filter_in_universe(extra_all, kps)

    print("=" * 78)
    print(f"语料口径 universe={args.universe}  N(universe_size)={universe_size}  "
          f"章节={sorted({kp.chapter for kp in kps})}")
    print(f"题库 {len(questions)} 题；补充观察集 {len(extra)} 题"
          + (f"（{extra_skipped} 题 gold 不在本语料内已跳过）" if extra_skipped else "")
          + f"；确认下限 floor={args.floor:.2f}")
    print("⚠ 两个口径的指标不可混引：引用 73.3% 时必须写明 N=20。")
    print("=" * 78)

    baseline = Scorer(kps)
    mis = Scorer(kps, use_misconception=True)

    results = [
        evaluate("V0 baseline（TF-IDF, IDF^1.5, 语料=概念+名称）",
                 kps, questions, baseline.rank, floor=args.floor, keep_cases=args.per_case,
                 use_misconception=False,
                 note="V0 = 现状语义（误区不入语料）"),
        evaluate("V1 IDF^2.0", kps, questions, Scorer(kps, power=2.0).rank, floor=args.floor,
                 use_misconception=False),
        evaluate("V2 误区入语料（推荐）",
                 kps, questions, mis.rank, floor=args.floor, keep_cases=args.per_case,
                 use_misconception=True,
                 note="推荐方案：misconception-in-corpus=true"),
        evaluate("V3 章一致性 -0.03", kps, questions, baseline.rank,
                 chapter_mode="consistency", penalty=0.03, floor=args.floor,
                 use_misconception=False),
        evaluate("V4 章一致性 -0.06", kps, questions, baseline.rank,
                 chapter_mode="consistency", penalty=0.06, floor=args.floor,
                 use_misconception=False),
        evaluate("V5 章一致性 -0.10", kps, questions, baseline.rank,
                 chapter_mode="consistency", penalty=0.10, floor=args.floor,
                 use_misconception=False),
        evaluate("V6 章硬过滤（假设调用方传对章）", kps, questions, baseline.rank,
                 chapter_mode="hard", floor=args.floor, use_misconception=False,
                 note="线上不可达：生产调用链不传 chapterId（设计文档 §4）"),
        evaluate("V7 误区 + 章硬过滤", kps, questions, mis.rank,
                 chapter_mode="hard", floor=args.floor, use_misconception=True,
                 note="线上不可达：生产调用链不传 chapterId（设计文档 §4）"),
        evaluate("V10 章硬过滤 + 低置信回退", kps, questions, baseline.rank,
                 chapter_mode="hard", fallback_below_floor=True, floor=args.floor,
                 use_misconception=False,
                 note="线上不可达：生产调用链不传 chapterId（设计文档 §4）"),
        evaluate("V11 误区 + 章硬过滤 + 低置信回退", kps, questions, mis.rank,
                 chapter_mode="hard", fallback_below_floor=True, floor=args.floor,
                 use_misconception=True,
                 note="首次评测组合；线上不可达：生产调用链不传 chapterId（设计文档 §4）"),
    ]

    bm25_docs = [corpus_of(kp, False) for kp in kps]

    def bm25_ranker(text: str) -> list[Hit]:
        return bm25_rank(kps, bm25_docs, text)

    results.append(evaluate("V8 BM25（字符二元组）", kps, questions, bm25_ranker,
                            floor=args.floor, use_misconception=False))
    results.append(evaluate("V9 RRF(TF-IDF, BM25)", kps, questions,
                            lambda text: rrf_fuse([baseline.rank(text), bm25_ranker(text)]),
                            floor=args.floor, use_misconception=False))

    if extra:
        # 32 题补充集真正纳入评测（历史版本只 print 数量，从未参与任何变体）
        results.append(evaluate("V0-MC32 补充集（误区关）", kps, extra, baseline.rank,
                                floor=args.floor, use_misconception=False,
                                note="补充观察集，含选项文本噪声，不作门禁"))
        results.append(evaluate("V2-MC32 补充集（误区开）", kps, extra, mis.rank,
                                floor=args.floor, use_misconception=True,
                                note="补充观察集，含选项文本噪声，不作门禁"))

    print("\n### 语料口径 universe=%s，N=%d，floor=%.2f，题库 %d 题"
          % (args.universe, universe_size, args.floor, len(questions)))
    print("\n| 方案 | top-1 | top-3 | AUTO | AUTO 精确率 | CONFIRM | FAILED | 覆盖 |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for item in results:
        print(f"| {item['variant']} | {item['top1']:.1%} | {item['top3']:.1%} "
              f"| {item['auto']} | {item['autoPrecision']:.1%} "
              f"| {item['confirm']} | {item['failed']} | {item['coverage']:.1%} |")

    print("\n确认下限敏感性（V0 baseline；只影响 FAILED 与 CONFIRM 分界）：")
    print("| floor | 覆盖（非 FAILED） | 下限以上 top-1 精确率 |")
    print("| --- | --- | --- |")
    for floor in (0.30, 0.35, 0.40):
        cell = evaluate(f"floor={floor:.2f}", kps, questions, baseline.rank, floor=floor)
        print(f"| {floor:.2f} | {cell['coverage']:.1%} | {cell['precisionAboveFloor']:.1%} |")

    print("\n基线校准（V0 top1 分数区间 → 实际正确率）：")
    for label, cell in results[0]["calibration"].items():
        print(f"  {label}: n={cell['n']}, acc={cell['acc']:.1%}")

    print("\n基线错分（前 10）：")
    for miss in results[0]["misses"][:10]:
        print(f"  {miss['qId']} gold={miss['gold']} top3={miss['top3']}")

    gate_rows = check_gates(results[2], args.universe)
    gate_pass = all(row["pass"] for row in gate_rows)
    print(f"\n### 门禁核对（{GATES[args.universe]['label']} 口径，V2 推荐方案，N={universe_size}）")
    print("| 门禁 | 要求 | 实测（精确分数） | 同精度值 | 结果 |")
    print("| --- | --- | --- | --- | --- |")
    for row in gate_rows:
        shown = (f"{row['actual'] * 100:.1f}%" if row["name"] != "FAILED 题数"
                 else str(row["actual"]))
        print(f"| {row['name']} | {row['required']} | {row['detail']} "
              f"| {shown} | {'PASS' if row['pass'] else 'FAIL'} |")
    print("\n说明：门禁按**同精度**比较——实测值与阈值都保留到百分比 1 位小数后再比。"
          "阈值一位未下调。")
    print(f"\nV2 @floor={args.floor:.2f} 门禁结论：{'PASS' if gate_pass else 'FAIL'}")

    if extra:
        print("\n补充观察集（32 题，不作门禁）：V0 top1=%.1f%% / top3=%.1f%%；"
              "V2 top1=%.1f%% / top3=%.1f%%"
              % (results[-2]["top1"] * 100, results[-2]["top3"] * 100,
                 results[-1]["top1"] * 100, results[-1]["top3"] * 100))

    if args.json:
        out = Path(args.json)
        out.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
            "universe": args.universe,
            "universe_size": universe_size,
            "chapters": sorted({kp.chapter for kp in kps}),
            "n_questions": len(questions),
            "n_extra": len(extra),
            # config 只放**所有变体共用**的阈值旋钮，供复算；与变体相关的开关
            # （misconception）不放在这里，避免"config 说开、per_case 却是 V0"的歧义。
            "config": {
                "score_scale": SCORE_SCALE,
                "auto": AUTO_THRESHOLD,
                "floor": args.floor,
                "margin": MARGIN,
                "idf_power": IDF_POWER,
                "name_repeat": NAME_REPEAT,
                "recommended_use_misconception": results[2]["use_misconception"],
            },
            "recommended": {
                "variant": results[2]["variant"],
                "use_misconception": results[2]["use_misconception"],
                "floor": args.floor,
            },
            "gate": {
                "label": GATES[args.universe]["label"],
                "comparison": "同精度比较：实测值与阈值均保留百分比 1 位小数后再比，阈值未下调",
                "rows": gate_rows,
                "pass": gate_pass,
            },
            "results": results,
            "baseline_misses": results[0]["misses"],
            # 逐题明细来自 V0（误区关）：键名显式带上口径，避免误当成推荐方案明细
            "per_case": results[0].get("per_case", []),
            "per_case_variant": results[0]["variant"],
            "per_case_use_misconception": results[0]["use_misconception"],
            "per_case_note": "顶层 per_case 是 V0（误区关）的逐题明细；"
                             "推荐方案 V2 的逐题明细见 results[2].per_case",
        }
        # 显式 LF：Windows 下默认会写 CRLF，导致证据 JSON 的 diff 变成整文件重写
        out.write_text(json.dumps(payload, ensure_ascii=False, indent=2),
                       encoding="utf-8", newline="\n")
        print("\n已写出：" + str(out))


if __name__ == "__main__":
    main()
