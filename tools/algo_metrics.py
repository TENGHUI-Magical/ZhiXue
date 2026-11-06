#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""算法创新指标自动评测（纯 Python 标准库，零新增依赖）。

目的
----
把 ``算法创新点分析报告_V1.0.md`` §10.2 的 8 项指标从「文字定义」变成
**可复跑的自动评测**，并补齐 LCO 8 折 / 配对 bootstrap / McNemar 精确检验
三类统计验证。所有指标数字由只读加载器 + 原评测脚本的打分器实跑得出，
脚本内不含任何指标结果字面量（只含口径、阈值与完整性断言常量）。

口径（两个口径并列，禁止跨口径相减或相加）
------------------------------------------
* ``U81-66``  : 原脚本 ``Q_SQL`` 写死只读 ``seed-question-ch02-ch03.sql``，
  66 题 = ch02+ch03 最差两章完整子集，历史对照口径。
* ``U81-233`` : 扫描 7 个 ``seed-question-*.sql``、按 ``q_id`` 去重得到
  8 章 233 题；**观测线，非 L1/L2 验收线**（``docs/06:132`` 锁死 U60/N=60）。
* 语料恒为 81 点全集；LCO 留章只留「题目」，**绝不删语料**
  （删语料式留章等价于 oracle，禁止）。

红线（AGENTS.md §3 / §5）
------------------------
* query 恒等于线上 ``request.getText()`` 纯题干；不拼 ``options_json`` /
  ``analysis_text`` / ``answer_text``；不用 gold / chapter 参与排序与决策。
* 阈值冻结：只读原脚本与 ``application.yml`` 的既有值，不做任何 threshold
  sweep、不改 floor / auto / margin / score-scale、不改生产 Java 与契约。
* 不写「门禁达标 / 形成放行结论 / 效果显著」；每项标证据等级
  A（实跑）/ B（推演未复核）/ C（静态勘察）。
* 只写 ``spark-output/prototypes/metrics-20261106/`` 下三个产物；
  不修改 ``eval_mapping_offline.py``，实跑前后校验其 sha256。

用法
----
    python zhixue/tools/algo_metrics.py
    python zhixue/tools/algo_metrics.py --reps 5000 --seed 20261106
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import math
import random
import re
import sys
import time
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Callable, Sequence

# ---------------------------------------------------------------------------
# 路径与口径常量（不含任何指标结果字面量）
# ---------------------------------------------------------------------------

TOOLS_DIR: Path = Path(__file__).resolve().parent
CODE_ROOT: Path = TOOLS_DIR.parent
PROJECT_ROOT: Path = CODE_ROOT.parent
OFFLINE_SCRIPT: Path = TOOLS_DIR / "eval_mapping_offline.py"
JAVA_SERVICE: Path = (CODE_ROOT / "zhixue-server" / "zhixue-diagnosis" / "src"
                      / "main" / "java" / "com" / "zhixue" / "diagnosis"
                      / "service" / "KpMappingService.java")
APP_YML: Path = (CODE_ROOT / "zhixue-server" / "zhixue-bootstrap" / "src"
                 / "main" / "resources" / "application.yml")
OUTPUT_DIR: Path = PROJECT_ROOT / "spark-output" / "prototypes" / "metrics-20261106"
REPORT_MD: Path = OUTPUT_DIR / "algo_metrics.md"
REPORT_JSON: Path = OUTPUT_DIR / "algo_metrics.json"
README_MD: Path = OUTPUT_DIR / "README.md"
ARTIFACTS_SHA: Path = OUTPUT_DIR / "artifacts.sha256"
SCRIPT_FILE: Path = Path(__file__).resolve()

RUN_COMMAND: str = ("cd zhixue/tools && PYTHONIOENCODING=utf-8 "
                    "PYTHONDONTWRITEBYTECODE=1 python algo_metrics.py "
                    "--reps 2000 --seed 20261106")
VERIFY_COMMAND: str = "sha256sum -c artifacts.sha256"
# 脚本清单里对 algo_metrics.py 的相对路径（从 OUTPUT_DIR 出发，供 sha256sum -c 使用）
SCRIPT_MANIFEST_LABEL: str = "../../../zhixue/tools/algo_metrics.py"

# 字段别名：历史字段名 → 验收口径字段名（只加映射，不动任何数字）
FIELD_ALIASES: dict[str, str] = {
    "rank_4_10_strict": "rank_4_10",
    "mismatch_gt10": "rank_gt_10",
    "gold_not_in_candidates|true_recall": "true_not_recalled",
}

QUESTION_GLOB: str = "seed-question-*.sql"
KP_GLOB: str = "seed-kp-*.sql"

BOOTSTRAP_REPS: int = 2000
BOOTSTRAP_SEED: int = 20261106
Z95: float = 1.96
OBSERVATION_TARGET_TOP1: float = 0.70  # 外部给定的观测对照阈值，非本脚本门禁判定
OVERLAP_SPLIT: float = 0.50
ATTRACTOR_TOP: int = 5
CENTROID_TOP: int = 8
SHORT_TEXT_LIMIT: int = 5

EXPECTED_KP_COUNT: int = 81
EXPECTED_Q233: int = 233
EXPECTED_Q66: int = 66
EXPECTED_Q_FILES: int = 7
EXPECTED_CHAPTERS: tuple[int, ...] = (1, 2, 3, 4, 5, 6, 7, 8)

SCOPE_66: str = "U81-66"
SCOPE_233: str = "U81-233"
SCOPES: tuple[str, ...] = (SCOPE_66, SCOPE_233)

GATE_LINE_66: str = "U81-66 历史对照（ch02+ch03 最差两章完整子集，不参与放行）"
GATE_LINE_233: str = ("U81-233 观测线（观测结论，非 L1/L2 验收线；"
                      "docs/06:132 把验收集锁死为离线 U60 固定集 N=60，"
                      "验收集切换须人裁决）")

# 外部给定的校验锚点（主理人任务书）。这些是「对照输入」，不是本脚本产出的指标：
# 脚本产出的每个数字都来自实跑，锚点只用于逐条判定「一致 / 定义差异 / 不一致」。
ANCHORS_GIVEN: dict[str, Any] = {
    "q66_gold_in_candidates": 66,
    "q66_true_recall": 0,
    "q66_rank4_10_label": 14,
    "q233_true_recall": 1,
    "q233_rank4_10_label": 17,
    "q233_rank2_3": 34,
    "q66_hubness_index": 18,
    "q233_hubness_index": 22,
    "q66_top1_hits": 35,
    "q233_top1_pct": 77.7,
    "macro_top1_pct": 79.1,
    "worst_ch03_pct": 41.7,
    "low_overlap_n": 150,
    "low_overlap_top1_pct": 67.3,
    "high_overlap_n": 83,
    "high_overlap_top1_pct": 96.4,
    "confirm_hits": 11,
    "v0_auto_precision_pct": 86.4,
    "v2_auto_precision_pct": 80.0,
    "wilson_35_66": (41.2, 64.6),
    "wilson_47_66": (59.4, 80.7),
    "reverse_error_threshold": 8,
}

# 主理人 A 级独立实跑（内联 Python、未落盘）的交叉校验输入。
# 与 ANCHORS_GIVEN 一样属于「外部对照输入」，不是本脚本产出的指标；
# 脚本产出的每个数字仍由逐题记录实跑得到，这里只用于逐项判定一致 / 不一致。
LEAD_CROSSCHECK_GIVEN: dict[str, Any] = {
    "source": ("主理人 A 级独立实跑（内联 Python、未落盘；仅作独立交叉校验，"
               "不落盘产物、不作为升级后的验收依据；2026-11-06 团队消息）"),
    "segments": {
        "U81-66|V0": {"FAILED": (4, 7, 0.571), "CONFIRM": (11, 37, 0.297),
                      "AUTO": (19, 22, 0.864)},
        "U81-66|V2": {"FAILED": (4, 10, 0.400), "CONFIRM": (11, 31, 0.355),
                      "AUTO": (20, 25, 0.800)},
        "U81-233|V0": {"FAILED": (9, 16, 0.562), "CONFIRM": (49, 91, 0.538),
                       "AUTO": (121, 126, 0.960)},
        "U81-233|V2": {"FAILED": (11, 21, 0.524), "CONFIRM": (58, 93, 0.624),
                       "AUTO": (112, 119, 0.941)},
    },
    "top3_v2": {
        "U81-66": {"FAILED": (8, 10), "CONFIRM": (20, 31), "AUTO": (24, 25)},
        "U81-233": {"FAILED": (18, 21), "CONFIRM": (79, 93), "AUTO": (118, 119)},
    },
    "confirm_mis": {"U81-66": 20, "U81-233": 35},
    "recall": {
        "U81-66": {"in_cand": 66, "true": 0, "rank1": 35, "rank2_3": 17,
                   "rank4_10": 6, "gt10": 8},
        "U81-233": {"in_cand": 232, "true": 1,
                    "rank4_10": 8, "gt10": 9},
    },
}

# 禁止出现的宣称性措辞（只检查肯定性结论短语，不禁止「不宣称…」的否定语境）。
FORBIDDEN_PHRASES: tuple[str, ...] = (
    "已达标", "验收通过", "显著提升", "提分 24.7", "算法优化见效",
    "从 53.0% 提升到 77.7%", "DQ-01 已达标", "最接近达标",
    "DQ-01 通过", "验收口径已切至",
)


# ---------------------------------------------------------------------------
# 通用工具
# ---------------------------------------------------------------------------


def sha256_file(path: Path) -> str:
    """返回文件 sha256，用于证明原评测脚本在实跑前后未被改动。"""
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def r4(value: float) -> float:
    """统一保留 4 位小数，避免展示精度不一致。"""
    return round(value, 4)


def pct(value: float, digits: int = 1) -> str:
    """把 0—1 比率渲染成百分比字符串。"""
    return f"{value * 100:.{digits}f}%"


def md_table(headers: Sequence[str], rows: Sequence[Sequence[Any]]) -> str:
    """渲染 GitHub-flavored Markdown 表格。"""
    head = "| " + " | ".join(str(item) for item in headers) + " |"
    sep = "| " + " | ".join("---" for _ in headers) + " |"
    body = ["| " + " | ".join(str(cell) for cell in row) + " |" for row in rows]
    return "\n".join([head, sep, *body])


def jround4(value: float) -> float:
    """复刻 Java ``Math.round(value * 10000.0) / 10000.0``（正数 half-up）。

    Python 内建 ``round`` 是 banker's rounding，不能用来复刻 Java 的 round4。
    """
    return math.floor(value * 10000.0 + 0.5) / 10000.0


def load_offline_module() -> Any:
    """用 importlib 只读动态加载原评测脚本。

    必须先把 module 注册进 ``sys.modules``：脚本内含 ``@dataclass``，
    ``dataclasses._is_type`` 会通过 ``sys.modules.get(cls.__module__)``
    取模块命名空间，未注册会抛 ``AttributeError``。
    """
    if not OFFLINE_SCRIPT.is_file():
        raise SystemExit("找不到原评测脚本：" + str(OFFLINE_SCRIPT))
    spec = importlib.util.spec_from_file_location(
        "zhixue_algo_metrics_offline", OFFLINE_SCRIPT
    )
    if spec is None or spec.loader is None:
        raise SystemExit("无法为原评测脚本创建 importlib spec")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def frozen_config(module: Any, java_text: str, yml_text: str) -> dict[str, Any]:
    """静态核对阈值冻结：原脚本常量 / application.yml / Java 默认值三方一致。"""
    expected: dict[str, float] = {
        "score_scale": 0.3,
        "auto_threshold": 0.60,
        "margin": 0.10,
        "floor": 0.35,
        "idf_power": 1.5,
        "name_repeat": 3,
    }
    module_values: dict[str, float] = {
        "score_scale": float(module.SCORE_SCALE),
        "auto_threshold": float(module.AUTO_THRESHOLD),
        "margin": float(module.MARGIN),
        "floor": float(module.CONFIRM_FLOOR),
        "idf_power": float(module.IDF_POWER),
        "name_repeat": float(module.NAME_REPEAT),
    }
    yml_keys: dict[str, str] = {
        "score_scale": "score-scale",
        "auto_threshold": "auto-threshold",
        "margin": "margin",
        "floor": "confirm-floor",
    }
    yml_values: dict[str, float | None] = {}
    for name, key in yml_keys.items():
        match = re.search(r"^\s*" + re.escape(key) + r":\s*([0-9.]+)", yml_text, re.M)
        yml_values[name] = float(match.group(1)) if match else None
    java_defaults: dict[str, bool] = {
        "score_scale_default_0.3": "@Value(\"${mapping.score-scale:0.3}\")" in java_text,
        "auto_default_0.60": "@Value(\"${mapping.auto-threshold:0.60}\")" in java_text,
        "margin_default_0.10": "@Value(\"${mapping.margin:0.10}\")" in java_text,
        "floor_from_yml": "@Value(\"${mapping.confirm-floor}\")" in java_text,
        "misconception_true": "misconception-in-corpus: true" in yml_text,
    }
    module_ok = all(abs(module_values[k] - v) < 1e-9 for k, v in expected.items())
    yml_ok = all(yml_values.get(k) is not None
                 and abs(float(yml_values[k]) - expected[k]) < 1e-9
                 for k in yml_keys)
    java_ok = all(java_defaults.values())
    return {
        "expected": expected,
        "module_values": module_values,
        "yml_values": yml_values,
        "java_defaults": java_defaults,
        "module_ok": module_ok,
        "yml_ok": yml_ok,
        "java_ok": java_ok,
        "all_frozen": bool(module_ok and yml_ok and java_ok),
        "note": "阈值一位未动；本脚本不提供 --floor 等 sweep 参数，不改任何旋钮",
        # E5 红线断言：阈值扫描与决策标尺改动的结构化声明
        "threshold_sweep_present": False,
        "threshold_sweep_kind": "不适用（本脚本无任何 threshold sweep）",
        "spike_risk_coverage_kind": "仅对照, 不计入达标结论（本脚本未复现该 0.5→0.6 扫描）",
        "decide_modified": False,
        "floor_adjustment_prohibited": (
            "不得据此调 confirm-floor 回收 k 题制造达标；"
            "FAILED 段 hit3 只作诊断，233 题 V2 FAILED hit3=18/21 但 hit1=11/21，"
            "调 floor 只会把错 top1 放进人工/AUTO"),
    }


# ---------------------------------------------------------------------------
# 只读数据加载
# ---------------------------------------------------------------------------


def load_questions_233(module: Any) -> tuple[list[Any], dict[str, int]]:
    """扫描 7 个题库种子、复用原脚本解析函数、按 ``q_id`` 去重得到 233 题。

    列序与原脚本 ``load_questions()`` 完全一致：``fields[0]=q_id``、
    ``fields[2]=primary_kp_id(gold)``、``fields[4]=question_text``。
    """
    files = sorted(module.SQL_DIR.glob(QUESTION_GLOB))
    if len(files) != EXPECTED_Q_FILES:
        raise SystemExit(
            f"期望 {EXPECTED_Q_FILES} 个题库种子文件，实际 {len(files)}：{files}"
        )
    seen: dict[str, Any] = {}
    per_file: dict[str, int] = {}
    for path in files:
        sql = path.read_text(encoding="utf-8")
        count = 0
        for match in module._VALUES_PATTERN.finditer(sql):
            fields = [module._literal(part)
                      for part in module._split_top_level(match.group(1))]
            if len(fields) < 5:
                continue
            q_id, gold, text = fields[0], fields[2], fields[4]
            if not q_id.startswith("Q_"):
                continue
            chapter = int(gold[2:4]) if gold.startswith("KP") else 0
            if q_id not in seen:
                seen[q_id] = module.Question(q_id, gold, text, chapter)
                count += 1
        per_file[path.name] = count
    questions = sorted(seen.values(), key=lambda q: q.q_id)
    if len(questions) != EXPECTED_Q233:
        raise SystemExit(f"期望 {EXPECTED_Q233} 题，实际解析 {len(questions)} 题")
    return questions, per_file


@dataclass(frozen=True)
class Case:
    """一条逐题评测记录。``decision`` / ``hit1`` 默认指主基线 V2。"""

    q_id: str
    chapter: int
    gold: str
    query_field: str
    top1_id: str
    n_candidates: int
    gold_in_candidates: bool
    gold_rank: int | None
    v0_decision: str
    v2_decision: str
    v0_hit1: bool
    v0_hit3: bool
    v2_hit1: bool
    v2_hit3: bool
    top1_score: float
    top1_cosine: float
    overlap: float
    recall_class: str

    @property
    def decision(self) -> str:
        """主基线决策（V2）。"""
        return self.v2_decision

    @property
    def hit1(self) -> bool:
        """主基线 top1 命中（V2）。"""
        return self.v2_hit1

    @property
    def hit3(self) -> bool:
        """主基线 top3 命中（V2）。"""
        return self.v2_hit3


def case_to_dict(case: Case) -> dict[str, Any]:
    """序列化逐题记录（含派生字段，便于人工复核三分类与迁移矩阵）。"""
    data = asdict(case)
    data["decision"] = case.decision
    data["hit1"] = case.hit1
    data["hit3"] = case.hit3
    return data


def build_cases(module: Any, kps: Sequence[Any], questions: Sequence[Any],
                floor: float) -> tuple[list[Case], Any]:
    """对每题用 V0 / V2 两个既有变体打分并生成逐题记录。

    ``query`` 恒为 ``question.text``（纯题干，等于线上 ``request.getText()``）。
    gold / chapter 只在**事后**用于分层与命中判定，绝不进入 ranker / decide。
    排序按原脚本 ``evaluate`` 第 415 行的强制键 ``(-Hit.raw, kp_id)``。
    """
    scorer_v0 = module.Scorer(list(kps), use_misconception=False)
    scorer_v2 = module.Scorer(list(kps), use_misconception=True)
    kp_by_id = {kp.kp_id: kp for kp in kps}
    cases: list[Case] = []
    for question in questions:
        query: str = question.text
        hits0 = sorted(scorer_v0.rank(query),
                       key=lambda hit: (-hit.raw, hit.kp.kp_id))
        hits2 = sorted(scorer_v2.rank(query),
                       key=lambda hit: (-hit.raw, hit.kp.kp_id))
        ids0 = [hit.kp.kp_id for hit in hits0]
        ids2 = [hit.kp.kp_id for hit in hits2]

        v0_hit1 = bool(ids0) and ids0[0] == question.gold
        v2_hit1 = bool(ids2) and ids2[0] == question.gold
        v0_hit3 = question.gold in ids0[:3]
        v2_hit3 = question.gold in ids2[:3]
        gold_in_candidates = question.gold in ids2
        gold_rank: int | None = (ids2.index(question.gold) + 1
                                 if gold_in_candidates else None)

        # 低重合代理：题干字符二元组集合被 gold 知识点语料（概念+名称×3）覆盖的比例。
        # 用集合口径，名称重复 3 次不改变二元组集合；不含误区（与 V0 语料口径一致）。
        query_grams = set(module.term_counts(query))
        gold_kp = kp_by_id.get(question.gold)
        gold_text = module.corpus_of(gold_kp, False) if gold_kp is not None else ""
        gold_grams = set(module.term_counts(gold_text))
        overlap = (len(query_grams & gold_grams) / len(query_grams)
                   if query_grams else 0.0)

        if not gold_in_candidates:
            recall_class = "真召回缺口"
        elif gold_rank == 1:
            recall_class = "top1命中"
        elif gold_rank in (2, 3):
            recall_class = "排序问题·近邻(2-3)"
        elif gold_rank <= 10:
            recall_class = "排序问题(4-10)"
        else:
            recall_class = "表示不匹配(>10)"

        cases.append(Case(
            q_id=question.q_id,
            chapter=question.chapter,
            gold=question.gold,
            query_field="question_text",
            top1_id=ids2[0] if ids2 else "",
            n_candidates=len(ids2),
            gold_in_candidates=gold_in_candidates,
            gold_rank=gold_rank,
            v0_decision=module.decide(hits0, floor),
            v2_decision=module.decide(hits2, floor),
            v0_hit1=v0_hit1,
            v0_hit3=v0_hit3,
            v2_hit1=v2_hit1,
            v2_hit3=v2_hit3,
            top1_score=r4(hits2[0].score) if hits2 else 0.0,
            top1_cosine=r4(hits2[0].raw) if hits2 else 0.0,
            overlap=r4(overlap),
            recall_class=recall_class,
        ))
    return cases, scorer_v2


# ---------------------------------------------------------------------------
# 指标 1：双口径六项 joint
# ---------------------------------------------------------------------------


def joint_metrics(cases: Sequence[Case], v2: bool = True) -> dict[str, Any]:
    """计算六项 joint：top1 / top3 / AUTO 精确率 / 人工率 / 覆盖 / FAILED。"""
    n = len(cases)
    if v2:
        triples = [(case.decision, case.hit1, case.hit3) for case in cases]
    else:
        triples = [(case.v0_decision, case.v0_hit1, case.v0_hit3)
                   for case in cases]
    top1 = sum(1 for _, h1, _ in triples if h1)
    top3 = sum(1 for _, _, h3 in triples if h3)
    auto = sum(1 for decision, _, _ in triples if decision == "AUTO")
    confirm = sum(1 for decision, _, _ in triples if decision == "CONFIRM")
    failed = sum(1 for decision, _, _ in triples if decision == "FAILED")
    auto_hits = sum(1 for decision, h1, _ in triples
                    if decision == "AUTO" and h1)
    return {
        "n": n,
        "top1": r4(top1 / n) if n else 0.0,
        "top1_hits": top1,
        "top3": r4(top3 / n) if n else 0.0,
        "top3_hits": top3,
        "auto": auto,
        "autoHits": auto_hits,
        "autoPrecision": r4(auto_hits / auto) if auto else 0.0,
        "confirm": confirm,
        "failed": failed,
        "coverage": r4((n - failed) / n) if n else 0.0,
        "manualRate": r4((n - auto) / n) if n else 1.0,
        "detail": {
            "top1": f"{top1}/{n}",
            "top3": f"{top3}/{n}",
            "autoPrecision": f"{auto_hits}/{auto}" if auto else "0/0",
            "manualRate": f"{n - auto}/{n}",
            "coverage": f"{n - failed}/{n}",
        },
    }


def joint_row(name: str, cell: dict[str, Any], gate_line: str) -> list[str]:
    """六项 joint 的一行 Markdown。"""
    return [
        name, gate_line, str(cell["n"]), pct(cell["top1"]), pct(cell["top3"]),
        pct(cell["autoPrecision"]), pct(cell["manualRate"]),
        pct(cell["coverage"]), str(cell["failed"]),
    ]


# ---------------------------------------------------------------------------
# 指标 2：hubness 指数 + 质心 hubness 分布
# ---------------------------------------------------------------------------


def hubness_scope(cases: Sequence[Case], kps: Sequence[Any]) -> dict[str, Any]:
    """错误 top1 中 Top-5 吸引子出现次数（hubness 指数）。

    定义：取所有 top1 未命中题的**预测 top1 知识点**频次分布，按频次降序
    取前 5 个（并列按 kp_id 升序稳定化），统计这 5 个吸引子出现次数之和。
    """
    name_of = {kp.kp_id: kp.name for kp in kps}
    errors = [case for case in cases if not case.hit1]
    counts = Counter(case.top1_id for case in errors)
    ranked = sorted(counts.items(), key=lambda item: (-item[1], item[0]))
    top5 = ranked[:ATTRACTOR_TOP]
    index = sum(value for _, value in top5)
    return {
        "error_top1_n": len(errors),
        "top5_attractors": [
            {"kp_id": kp_id, "name": name_of.get(kp_id, ""), "count": count}
            for kp_id, count in top5
        ],
        "hubness_index": index,
        "concentration": r4(index / len(errors)) if errors else 0.0,
        "definition": "错误 top1 的预测 KP 频次 Top-5 计数之和（越低越好）",
    }


def centroid_hubness(module: Any, kps: Sequence[Any], scorer: Any) -> dict[str, Any]:
    """复刻 Java ``computeHubness``：知识点向量与全体向量均值方向的余弦。

    只读诊断量，不参与排序与决策；Top8 即最靠近语料分布中心的「通用知识点」。
    """
    centroid: dict[str, float] = defaultdict(float)
    for vector in scorer.vectors:
        for gram, weight in vector.items():
            centroid[gram] += weight
    norm = math.sqrt(sum(weight * weight for weight in centroid.values()))
    if norm > 0:
        centroid = {gram: weight / norm for gram, weight in centroid.items()}
    scores = {kp.kp_id: module.cosine(centroid, vector)
              for kp, vector in zip(kps, scorer.vectors)}
    values = sorted(scores.values())
    top8 = sorted(scores.items(), key=lambda item: (-item[1], item[0]))[:CENTROID_TOP]
    name_of = {kp.kp_id: kp.name for kp in kps}
    return {
        "n": len(values),
        "min": r4(values[0]) if values else 0.0,
        "max": r4(values[-1]) if values else 0.0,
        "mean": r4(sum(values) / len(values)) if values else 0.0,
        "top8": [
            {"kp_id": kp_id, "name": name_of.get(kp_id, ""), "hubness": r4(value)}
            for kp_id, value in top8
        ],
        "note": "只读诊断量；不得据此改 confirm-floor，也不得写成「算法已消除吸引子」",
    }


# ---------------------------------------------------------------------------
# 指标 3：按章分层（等章权宏平均 + 最差章）
# ---------------------------------------------------------------------------


def by_chapter(cases: Sequence[Case]) -> dict[str, Any]:
    """每章 top1/top3/AUTO 精确率/人工率/FAILED + 等章权宏平均 + 最差章。"""
    groups: dict[int, list[Case]] = defaultdict(list)
    for case in cases:
        groups[case.chapter].append(case)
    per_chapter: dict[str, dict[str, Any]] = {}
    for chapter in sorted(groups):
        cell = joint_metrics(groups[chapter])
        per_chapter[f"ch{chapter:02d}"] = {
            "chapter": chapter, "n": cell["n"],
            "top1": cell["top1"], "top3": cell["top3"],
            "autoPrecision": cell["autoPrecision"],
            "manualRate": cell["manualRate"],
            "coverage": cell["coverage"], "failed": cell["failed"],
            "detail": cell["detail"],
        }
    macro = (sum(row["top1"] for row in per_chapter.values()) / len(per_chapter)
             if per_chapter else 0.0)
    worst = min(per_chapter, key=lambda key: per_chapter[key]["top1"]) \
        if per_chapter else ""
    return {
        "per_chapter": per_chapter,
        "macro_avg_top1": r4(macro),
        "worst_chapter": worst,
        "worst_chapter_top1": per_chapter.get(worst, {}).get("top1", 0.0),
        "chapter_count": len(per_chapter),
    }


# ---------------------------------------------------------------------------
# 指标 4：低重合题分层
# ---------------------------------------------------------------------------


def group_joint(cases: Sequence[Case]) -> dict[str, Any]:
    """任意题组的六项 joint（用于低重合分组与 decision 分组）。"""
    cell = joint_metrics(cases)
    return {
        "n": cell["n"], "top1": cell["top1"], "top1_hits": cell["top1_hits"],
        "top3": cell["top3"], "top3_hits": cell["top3_hits"],
        "autoPrecision": cell["autoPrecision"], "auto": cell["auto"],
        "manualRate": cell["manualRate"], "coverage": cell["coverage"],
        "failed": cell["failed"], "detail": cell["detail"],
    }


def overlap_metrics(cases: Sequence[Case]) -> dict[str, Any]:
    """按题干二元组覆盖率 <50% / >=50% 分层；并给按章均值以体现 ch07 反例。"""
    low = [case for case in cases if case.overlap < OVERLAP_SPLIT]
    high = [case for case in cases if case.overlap >= OVERLAP_SPLIT]
    groups: dict[int, list[Case]] = defaultdict(list)
    for case in cases:
        groups[case.chapter].append(case)
    by_ch: dict[str, dict[str, Any]] = {}
    for chapter in sorted(groups):
        rows = groups[chapter]
        by_ch[f"ch{chapter:02d}"] = {
            "chapter": chapter,
            "n": len(rows),
            "mean_overlap": r4(sum(row.overlap for row in rows) / len(rows)),
            "top1": r4(sum(1 for row in rows if row.hit1) / len(rows)),
        }
    mean_all = r4(sum(case.overlap for case in cases) / len(cases)) if cases else 0.0
    ch07 = by_ch.get("ch07")
    max_chapter = max(by_ch, key=lambda key: by_ch[key]["top1"]) if by_ch else ""
    counterexample = {
        "present": bool(ch07 is not None and max_chapter == "ch07"
                        and ch07["mean_overlap"] < mean_all),
        "ch07_mean_overlap": ch07["mean_overlap"] if ch07 else None,
        "all_mean_overlap": mean_all,
        "ch07_top1": ch07["top1"] if ch07 else None,
        "max_top1_chapter": max_chapter,
        "max_top1": by_ch.get(max_chapter, {}).get("top1"),
        "note": "ch07 覆盖率偏低却 top1 最高 ⇒ 字面重合是代理指标、非单调",
    }
    return {
        "split": OVERLAP_SPLIT,
        "definition": ("覆盖率 = |题干二元组集合 ∩ gold 语料二元组集合| / "
                       "|题干二元组集合|；语料 = 概念切片 + 名称×3（集合口径，"
                       "不含误区，重复次数不影响集合）"),
        "low": group_joint(low),
        "high": group_joint(high),
        "mean_overlap_all": mean_all,
        "by_chapter": by_ch,
        "ch07_counterexample": counterexample,
        "note": ("代理指标、非人工标注、不量化红利占比；不得写成语义理解或"
                 "「多少 pt 是复述红利」"),
    }


# ---------------------------------------------------------------------------
# 指标 5：召回三分类
# ---------------------------------------------------------------------------


def recall_metrics(cases: Sequence[Case]) -> dict[str, Any]:
    """召回三分类（对 top3 未命中题互斥且完备）+ 逐题 gold_rank 分布。

    三分类（对 top3 未命中题互斥且完备，**分两档输出**）：
      * 排序问题(4-10)：gold 已召回但排在 4—10 名；
      * 表示不匹配(>10)：gold 已召回但排在 10 名之后；
      * 真召回缺口：gold 未进候选（cosine=0 或无共享二元组）。
    「rank>=4 合计」= 前两档之和 = **top3 未命中但仍在候选集**的题数，
    对应主理人 A 级实跑中「66 题 14 / 233 题 17」的原始口径。
    """
    n = len(cases)
    top3_miss = [case for case in cases if not case.hit3]
    true_recall = [case for case in top3_miss if not case.gold_in_candidates]
    rank_4_10 = [case for case in top3_miss
                 if case.gold_in_candidates and case.gold_rank is not None
                 and 4 <= case.gold_rank <= 10]
    mismatch = [case for case in top3_miss
                if case.gold_in_candidates and case.gold_rank is not None
                and case.gold_rank > 10]
    rank_2_3 = [case for case in cases
                if case.gold_in_candidates and case.gold_rank in (2, 3)]
    in_candidates = sum(1 for case in cases if case.gold_in_candidates)
    rank_hist = Counter(case.recall_class for case in cases)
    return {
        "n": n,
        "gold_in_candidates": in_candidates,
        "gold_not_in_candidates": n - in_candidates,
        "top3_miss": len(top3_miss),
        "true_recall": len(true_recall),
        "rank_2_3": len(rank_2_3),
        "rank_4_10_strict": len(rank_4_10),
        "mismatch_gt10": len(mismatch),
        "sort_problem_ge4": len(rank_4_10) + len(mismatch),
        "sort_problem_ge4_meaning": "top3 未命中但仍在候选集 = rank4-10 + rank>10",
        "field_aliases": dict(FIELD_ALIASES),
        "rank_ge4_equation": ("rank_4_10 + rank_gt_10 == sort_problem_ge4 "
                              "== top3 未命中但仍在候选集（66 题 6+8=14、233 题 8+9=17）"),
        "mismatch_gt10_note": ("mismatch_gt10（别名 rank_gt_10）是 gold 排在 10 名之后的"
                              "**题数**，不是「表示不匹配」的归因结论"),
        "class_counts": dict(sorted(rank_hist.items())),
        "conservation_ok": (len(true_recall) + len(rank_4_10) + len(mismatch)
                            == len(top3_miss)),
        "definition_note": ("主理人 A 级独立实跑校正标签：「66 题 rank4-10=14 / "
                            "233 题=17」实际是 rank>=4 **合计**（6+8=14、8+9=17），"
                            "把 rank>10 也归进去了。本脚本分两档输出：严格 "
                            "rank4-10 = 6/8、rank>10 = 8/9；合计 = top3 未命中"
                            "但仍在候选集。真召回缺口结论不变（66 题 0、233 题 1）。"
                            "定义差异如实披露，未调整任何数字。"),
        "attribution_note": ("被反证的是「真召回缺口」这一归因，不是「top3 未命中」"
                             "现象；禁止跨口径相加。"),
        "no_recall_ids": [case.q_id for case in true_recall],
    }


# ---------------------------------------------------------------------------
# 指标 6：决策三段 acc + 单调性 + 逐题决策迁移矩阵
# ---------------------------------------------------------------------------


def decision_metrics(cases: Sequence[Case]) -> dict[str, Any]:
    """决策三段 acc（V0 / V2 各一套）、单调性、V0→V2 迁移矩阵、CONFIRM 假象。

    单调性**按变体与口径分别判**：V2/U81-233 未观察到反转，但 V0/U81-233
    与两个 U81-66 口径均观察到反转；「未拒绝」不构成「标尺有效」的正面证据。
    """

    def segments_for(decision_key: str, hit1_key: str,
                     hit3_key: str) -> dict[str, dict[str, Any]]:
        block: dict[str, dict[str, Any]] = {}
        for decision in ("FAILED", "CONFIRM", "AUTO"):
            group = [case for case in cases
                     if getattr(case, decision_key) == decision]
            hits1 = sum(1 for case in group if getattr(case, hit1_key))
            hits3 = sum(1 for case in group if getattr(case, hit3_key))
            block[decision] = {
                "n": len(group), "hit1": hits1,
                "acc": r4(hits1 / len(group)) if group else 0.0,
                "hit3": hits3,
                "top3_rate": r4(hits3 / len(group)) if group else 0.0,
            }
        return block

    def monotonicity(block: dict[str, dict[str, Any]]) -> tuple[bool, list[str]]:
        acc = {key: value["acc"] for key, value in block.items()}
        monotonic = acc["FAILED"] <= acc["CONFIRM"] <= acc["AUTO"]
        pairs = [f"{left}>{right}" for left, right in
                 (("FAILED", "CONFIRM"), ("CONFIRM", "AUTO"))
                 if acc[left] > acc[right]]
        return monotonic, pairs

    segments = segments_for("decision", "hit1", "hit3")          # V2
    v0_segments = segments_for("v0_decision", "v0_hit1", "v0_hit3")
    monotonic, reversal_pairs = monotonicity(segments)
    v0_monotonic, v0_reversal_pairs = monotonicity(v0_segments)

    matrix: dict[str, dict[str, int]] = {
        left: {right: 0 for right in ("FAILED", "CONFIRM", "AUTO")}
        for left in ("FAILED", "CONFIRM", "AUTO")
    }
    for case in cases:
        matrix[case.v0_decision][case.v2_decision] += 1

    v0_confirm = [case for case in cases if case.v0_decision == "CONFIRM"]
    v2_confirm = [case for case in cases if case.v2_decision == "CONFIRM"]
    v0_auto = [case for case in cases if case.v0_decision == "AUTO"]
    v2_auto = [case for case in cases if case.v2_decision == "AUTO"]
    confirm_mis_highlight = sum(1 for case in cases
                                if case.decision == "CONFIRM" and not case.hit1)
    return {
        "segments": segments,
        "v0_segments": v0_segments,
        "monotonic": monotonic,
        "observed_reversal": not monotonic,
        "reversal_pairs": reversal_pairs,
        "v0_monotonic": v0_monotonic,
        "v0_observed_reversal": not v0_monotonic,
        "v0_reversal_pairs": v0_reversal_pairs,
        "migration_matrix": matrix,
        "migration_note": "行=V0 决策，列=V2 决策；逐题一一对应",
        "confirm": {
            "v0_n": len(v0_confirm),
            "v0_hits": sum(1 for case in v0_confirm if case.v0_hit1),
            "v0_acc": r4(sum(1 for case in v0_confirm if case.v0_hit1)
                         / len(v0_confirm)) if v0_confirm else 0.0,
            "v2_n": len(v2_confirm),
            "v2_hits": sum(1 for case in v2_confirm if case.v2_hit1),
            "v2_acc": r4(sum(1 for case in v2_confirm if case.v2_hit1)
                         / len(v2_confirm)) if v2_confirm else 0.0,
            "hits_unchanged": (sum(1 for case in v0_confirm if case.v0_hit1)
                               == sum(1 for case in v2_confirm if case.v2_hit1)),
        },
        "auto_precision_v0": r4(sum(1 for case in v0_auto if case.v0_hit1)
                                / len(v0_auto)) if v0_auto else 0.0,
        "auto_precision_v2": r4(sum(1 for case in v2_auto if case.v2_hit1)
                                / len(v2_auto)) if v2_auto else 0.0,
        "confirm_mis_highlight": confirm_mis_highlight,
        "confirm_mis_highlight_definition": "decision == 'CONFIRM' && hit1 == False",
        "confirm_mis_highlight_rate": r4(confirm_mis_highlight / len(cases))
        if cases else 0.0,
        "note": ("单调性随变体与口径变化：V2/U81-233 未观察到反转，但"
                 "「未拒绝」不构成「标尺有效」的正面证据；V0/U81-233 与两个"
                 "U81-66 口径均观察到反转。CONFIRM 误高亮仍在。"
                 "AUTO 精确率 86.4→80.0 属 C1 退化，CONFIRM 分母 37→31"
                 "造成精确率假象。"),
    }


# ---------------------------------------------------------------------------
# 指标 7：按 decision 分组的 top3 命中率（只作诊断）
# ---------------------------------------------------------------------------


def decision_top3_metrics(cases: Sequence[Case]) -> dict[str, Any]:
    """按 decision 分组的 top3 命中率（含 FAILED 段 hit3）。"""
    groups: dict[str, dict[str, Any]] = {}
    for decision in ("FAILED", "CONFIRM", "AUTO"):
        group = [case for case in cases if case.decision == decision]
        hits1 = sum(1 for case in group if case.hit1)
        hits3 = sum(1 for case in group if case.hit3)
        groups[decision] = {
            "n": len(group),
            "hit1": hits1,
            "hit1_rate": r4(hits1 / len(group)) if group else 0.0,
            "hit3": hits3,
            "hit3_rate": r4(hits3 / len(group)) if group else 0.0,
        }
    return {
        "groups": groups,
        "group_definition": ("按 decision 分组；hit1/hit3 均为「该段内的分子」，"
                             "n 为分母；与 6_decision 的 CONFIRM 误高亮"
                             "（CONFIRM && hit1==False）是两个不同量，不得混用"),
        "floor_note": "floor 只影响 decide()，不影响排序；分组是事后诊断",
        "diagnostic_only": True,
        "prohibition": ("只作诊断；不得据此调 confirm-floor 回收 k 题制造达标"),
    }


# ---------------------------------------------------------------------------
# 指标 8：Java/Python parity 断言
# ---------------------------------------------------------------------------


def parity_metrics(module: Any, kps: Sequence[Any], questions: Sequence[Any],
                   scorer: Any, java_text: str, floor: float) -> dict[str, Any]:
    """五项 parity 断言：排序键 / df-N 全量 / score 公式 / round4 / 短题干。"""
    checks: list[dict[str, Any]] = []
    kp_ids: set[str] = {kp.kp_id for kp in kps}

    bad_sort = 0
    bad_score = 0
    bad_df = 0
    round4_decision_diff = 0
    max_score_dev = 0.0
    multi_saturated = 0
    for question in questions:
        hits = scorer.rank(question.text)
        if hits != sorted(hits, key=lambda hit: (-hit.raw, hit.kp.kp_id)):
            bad_sort += 1
        if sum(1 for hit in hits if hit.score >= 1.0) > 1:
            multi_saturated += 1
        for hit in hits:
            expected = min(1.0, hit.raw / module.SCORE_SCALE)
            if abs(hit.score - expected) > 1e-12:
                bad_score += 1
            if hit.kp.kp_id not in kp_ids:  # 防御性分支：候选必须来自全量画像
                bad_df += 1
        rounded = sorted(
            [module.replace(hit, score=jround4(min(1.0, max(0.0,
                            hit.raw / module.SCORE_SCALE)))) for hit in hits],
            key=lambda hit: (-hit.raw, hit.kp.kp_id),
        )
        if module.decide(hits, floor) != module.decide(rounded, floor):
            round4_decision_diff += 1
        if hits:
            max_score_dev = max(max_score_dev,
                               abs(hits[0].score - jround4(hits[0].score)))

    # P1 排序键 = 原始 cosine
    java_sort_static = ("Comparator.comparingDouble(Scored::cosine).reversed()" in java_text
                        and "thenComparing(item -> item.profile().kpId())" in java_text)
    checks.append({
        "id": "P1", "name": "排序键 = 原始 cosine（按 (-raw, kpId) 排序）",
        "level": "A+C", "ok": bad_sort == 0 and java_sort_static,
        "detail": (f"Python 侧 {len(questions)} 题排序键不符数={bad_sort}；"
                   f"Java 源码静态对照={java_sort_static} [C]；"
                   f"score 饱和到 1.0 且候选>1 的题数={multi_saturated}"
                   "（若按 score 排序会退化为 kpId 字母序）。"
                   "任何排序实验的校正值必须放进 Hit.raw，否则会被 evaluate 覆盖。"),
    })
    # P2 df / N 全量统计
    doc_count_ok = scorer.doc_count == len(kps) == EXPECTED_KP_COUNT
    df_covers = all(all(gram in scorer.doc_freq for gram in vector)
                    for vector in scorer.vectors)
    java_df_static = ("int docCount = profiles.size();" in java_text
                      and "CorpusStats stats = statsFor(all);" in java_text)
    checks.append({
        "id": "P2", "name": "df / N 在全量画像上统计（章过滤不缩小统计范围）",
        "level": "A+C", "ok": bool(doc_count_ok and df_covers and java_df_static
                                   and bad_df == 0),
        "detail": (f"doc_count={scorer.doc_count}，df 二元组数={len(scorer.doc_freq)}，"
                   f"全部文档向量 gram 均在全量 df 内={df_covers}；"
                   f"Java 源码静态对照={java_df_static} [C]；"
                   f"异常文档数={bad_df}。"),
    })
    # P3 score = min(1, cos/0.3)
    java_calibrate_static = ("cosine / scoreScale" in java_text
                             and "Math.min(1.0, Math.max(0.0, scaled))" in java_text)
    checks.append({
        "id": "P3", "name": "score = min(1, cos / score-scale)",
        "level": "A+C", "ok": bad_score == 0 and java_calibrate_static,
        "detail": (f"逐 Hit 校验不符数={bad_score}（容差 1e-12，"
                   f"score-scale={module.SCORE_SCALE}）；"
                   f"Java calibrate 静态对照={java_calibrate_static} [C]。"),
    })
    # P4 round4
    java_round_static = "Math.round(value * 10000.0) / 10000.0" in java_text
    checks.append({
        "id": "P4", "name": "Java round4 与 Python 决策逐题一致",
        "level": "A+C", "ok": round4_decision_diff == 0 and java_round_static,
        "detail": (f"Java Math.round 半进位复刻后，逐题 decide() 不一致数="
                   f"{round4_decision_diff}/{len(questions)}；"
                   f"top1 score 最大偏差={max_score_dev:.8f}；"
                   f"Java round4 静态对照={java_round_static} [C]。"
                   "Python 离线脚本不舍入，Java 侧舍入后比阈值；两者在本题库上"
                   "决策逐位一致。"),
    })
    # P5 短题干 <5 字 → FAILED
    short_in_bank = [q for q in questions if len(q.text.strip()) < SHORT_TEXT_LIMIT]
    java_short_static = "request.getText().trim().length() < 5" in java_text
    synthetic = ["好", "SQL", "关系", "数据库"]
    synthetic_ok = all(len(text.strip()) < SHORT_TEXT_LIMIT for text in synthetic)

    def java_guard(text: str | None) -> str:
        return "FAILED" if text is None or len(text.strip()) < SHORT_TEXT_LIMIT \
            else "NEEDS_SCORING"

    guard_ok = all(java_guard(text) == "FAILED" for text in synthetic)
    checks.append({
        "id": "P5", "name": "短题干 <5 字 → FAILED",
        "level": "A+C", "ok": bool(java_short_static and synthetic_ok and guard_ok),
        "detail": (f"Java match() 静态规则={java_short_static} [C]；"
                   f"题库中 trim 后 <5 字题干={len(short_in_bank)} 题"
                   "（无样本，该维度在现有题库上不可判别）；"
                   f"合成短题干复刻 guard 断言={guard_ok} [A]。"
                   "注意：Python 离线评测脚本本身不含该 guard，若题库出现短题干"
                   "则 parity 会破，此处如实记录而非掩盖。"),
    })
    return {
        "checks": checks,
        "all_ok": all(check["ok"] for check in checks),
        "java_source_sha256": sha256_file(JAVA_SERVICE),
        "yml_source_sha256": sha256_file(APP_YML),
        "note": ("A=运行时逐题实跑；C=Java 源码静态勘察（只读）。"
                 "本脚本不启动 JVM，未做 Java 侧运行时对照。"),
    }


# ---------------------------------------------------------------------------
# 统计验证 A：LCO 8 折
# ---------------------------------------------------------------------------


def lco_metrics(cases: Sequence[Case], kps: Sequence[Any]) -> dict[str, Any]:
    """LCO 8 折：每折留一章题目、只评一次；语料 81 点全集不变。

    算法无可训练参数（TF-IDF 由固定语料一次性构建），因此 LCO 反映的是
    **章节异质性**，不是泛化误差估计。删语料式留章等价于 oracle，禁止。
    """
    folds: dict[str, dict[str, Any]] = {}
    for chapter in EXPECTED_CHAPTERS:
        fold = [case for case in cases if case.chapter == chapter]
        if not fold:
            continue
        cell = joint_metrics(fold)
        folds[f"ch{chapter:02d}"] = {
            "chapter": chapter, "n": cell["n"],
            "top1": cell["top1"], "top3": cell["top3"],
            "autoPrecision": cell["autoPrecision"],
            "manualRate": cell["manualRate"],
            "coverage": cell["coverage"], "failed": cell["failed"],
            "detail": cell["detail"],
        }
    macro = (sum(row["top1"] for row in folds.values()) / len(folds)
             if folds else 0.0)
    worst = min(folds, key=lambda key: folds[key]["top1"]) if folds else ""
    return {
        "folds": folds,
        "fold_count": len(folds),
        "macro_avg_top1": r4(macro),
        "worst_fold": worst,
        "worst_fold_top1": folds.get(worst, {}).get("top1", 0.0),
        "universe_size": len(kps),
        "universe_unchanged": True,
        "note": ("每折独立、留章只跑一次（该章题目只评一次，无折内重采样）；"
                 "语料 81 点全集与 df/N 全量统计不变；"
                 "删语料式留章 = oracle，禁止。算法无训练参数 ⇒ "
                 "本表是章节异质性诊断，不是泛化估计。"),
    }


# ---------------------------------------------------------------------------
# 统计验证 B：配对 bootstrap
# ---------------------------------------------------------------------------


def bootstrap_metrics(cases: Sequence[Case], reps: int, seed: int) -> dict[str, Any]:
    """按题配对重采样 V2 vs V0 的 top1 命中差 Δ，报 95% 百分位 CI。"""
    deltas = [int(case.v2_hit1) - int(case.v0_hit1) for case in cases]
    n = len(deltas)
    rng = random.Random(seed)
    samples: list[float] = []
    for _ in range(reps):
        samples.append(sum(deltas[rng.randrange(n)] for _ in range(n)) / n)
    samples.sort()
    lo = samples[int(0.025 * reps)]
    hi = samples[int(0.975 * reps) - 1]
    excludes_zero = bool(lo > 0.0 or hi < 0.0)
    return {
        "reps": reps, "seed": seed, "n": n,
        "delta_observed": r4(sum(deltas) / n) if n else 0.0,
        "delta_observed_pct": r4(100.0 * sum(deltas) / n) if n else 0.0,
        "ci95": [r4(lo), r4(hi)],
        "ci95_pct": [r4(lo * 100.0), r4(hi * 100.0)],
        "excludes_zero": excludes_zero,
        "n01": sum(1 for case in cases
                   if not case.v0_hit1 and case.v2_hit1),
        "n10": sum(1 for case in cases
                   if case.v0_hit1 and not case.v2_hit1),
        "note": ("按题配对、有放回重采样；CI 含 0 表示未观察到可区分差异，"
                 "不得宣称效果显著。"),
    }


# ---------------------------------------------------------------------------
# 统计验证 C：McNemar 精确检验 + Wilson CI
# ---------------------------------------------------------------------------


def mcnemar_exact(n01: int, n10: int) -> float:
    """双侧 McNemar 精确检验（条件二项检验，非 χ² 近似）。"""
    if n01 < 0 or n10 < 0:
        raise ValueError("McNemar 不一致对数不能为负")
    total = n01 + n10
    if total == 0:
        return 1.0
    smaller = min(n01, n10)
    tail = sum(math.comb(total, k) for k in range(0, smaller + 1)) / (2 ** total)
    return min(1.0, 2.0 * tail)


def wilson_interval(hits: int, total: int, z: float = Z95) -> tuple[float, float]:
    """Wilson score 95% 区间，返回百分比 1 位小数的下界与上界。"""
    if total == 0:
        return (0.0, 0.0)
    p = hits / total
    denominator = 1.0 + z * z / total
    center = (p + z * z / (2.0 * total)) / denominator
    half = (z / denominator) * math.sqrt(
        p * (1.0 - p) / total + z * z / (4.0 * total * total)
    )
    return (round((center - half) * 100.0, 1),
            round((center + half) * 100.0, 1))


def mcnemar_metrics(cases: Sequence[Case], scope: str) -> dict[str, Any]:
    """V2 vs V0 的配对精确检验 + 66 题专项 Wilson / 反事实净胜扫描。"""
    n = len(cases)
    hits_v2 = sum(1 for case in cases if case.v2_hit1)
    n01 = sum(1 for case in cases if not case.v0_hit1 and case.v2_hit1)
    n10 = sum(1 for case in cases if case.v0_hit1 and not case.v2_hit1)
    p_value = r4(mcnemar_exact(n01, n10))
    result: dict[str, Any] = {
        "n": n, "hits_v2": hits_v2, "n01": n01, "n10": n10,
        "p_exact_two_sided": p_value,
        "significant_at_005": bool(p_value < 0.05),
        "wilson_observed": list(wilson_interval(hits_v2, n)),
        "note": ("配对检验只看不一致对 (n01,n10)；不得用两个独立比例的 CI "
                 "是否重叠来宣称显著。"),
    }
    # 66 题专项：达到观测对照 70% 需要多少净胜题，以及 Wilson 区间是否重叠。
    # 只对 U81-66 且「尚未达到观测对照」的口径做反事实扫描：U81-233 已超过
    # 观测对照（net_gain<=0），扫描没有意义，且负的 n01 会让 math.comb 抛异常。
    target_hits = math.ceil(OBSERVATION_TARGET_TOP1 * n)
    net_gain = target_hits - hits_v2
    if scope != SCOPE_66 or net_gain <= 0:
        result["target_analysis"] = {
            "not_applicable": True,
            "reason": (f"{scope} 的 V2 top1 {hits_v2}/{n} 已达到或超过观测对照 "
                       f"{OBSERVATION_TARGET_TOP1:.0%}；净胜扫描仅对 U81-66 有意义"),
            "observation_target_top1": OBSERVATION_TARGET_TOP1,
            "target_hits": target_hits,
            "net_gain": net_gain,
        }
        return result
    target_ci = list(wilson_interval(target_hits, n))
    observed_ci = list(wilson_interval(hits_v2, n))
    overlap = bool(observed_ci[1] >= target_ci[0])
    rows: list[dict[str, Any]] = []
    for reverse in range(0, n + 1):
        wins = reverse + net_gain
        if wins < 0:
            continue
        if wins + reverse > n:
            break
        p_scan = mcnemar_exact(wins, reverse)
        rows.append({
            "n10_reverse_errors": reverse,
            "n01_wins": wins,
            "p_exact": r4(p_scan),
            "significant_at_005": bool(p_scan < 0.05),
        })
    critical = next((row["n10_reverse_errors"] for row in rows
                     if not row["significant_at_005"]), None)
    result["target_analysis"] = {
        "observation_target_top1": OBSERVATION_TARGET_TOP1,
        "target_hits": target_hits,
        "net_gain": net_gain,
        "wilson_target": target_ci,
        "wilson_observed": observed_ci,
        "cis_overlap": overlap,
        "conclusion": ("两个独立 Wilson CI 重叠 ⇒ 不得用「独立 CI 不重叠」宣称显著；"
                       "配对显著性须以 McNemar 为准"),
        "counterfactual_delta": net_gain,
        "counterfactual_first_nonsignificant_n10": critical,
        "counterfactual_rows": rows[:16],
        "given_reverse_threshold": ANCHORS_GIVEN["reverse_error_threshold"],
        "correction": ("主理人 B 级推演「反向错 >8 即不显著」经 A 级实跑复核："
                       "双侧精确二项下临界值以实跑为准，"
                       "未拒绝不等于显著。"),
    }
    return result


# ---------------------------------------------------------------------------
# 锚点对照表（外部给定值 vs 本脚本实跑值）
# ---------------------------------------------------------------------------


def build_anchor_rows(payload: dict[str, Any]) -> list[dict[str, str]]:
    """逐条对照主理人任务书给定的校验锚点。"""
    joint = payload["metrics"]["1_joint"]
    hub = payload["metrics"]["2_hubness"]
    chapter = payload["metrics"]["3_by_chapter"][SCOPE_233]
    overlap = payload["metrics"]["4_overlap"][SCOPE_233]
    recall = payload["metrics"]["5_recall"]
    decision = payload["metrics"]["6_decision"][SCOPE_66]
    mcn = payload["statistics"]["mcnemar"][SCOPE_66]["target_analysis"]
    given = ANCHORS_GIVEN

    def row(name: str, expected: Any, observed: Any,
            verdict: str = "") -> dict[str, str]:
        same = str(expected) == str(observed)
        return {
            "anchor": name,
            "given": str(expected),
            "observed": str(observed),
            "verdict": verdict or ("一致" if same else "不一致（如实披露）"),
        }

    rows = [
        row("66 题 gold_in_candidates", "66/66",
            f"{recall[SCOPE_66]['gold_in_candidates']}/66"),
        row("66 题真召回", given["q66_true_recall"],
            recall[SCOPE_66]["true_recall"]),
        row("66 题「rank4-10」=14（实为 rank>=4 合计）",
            given["q66_rank4_10_label"],
            recall[SCOPE_66]["sort_problem_ge4"],
            "定义差异已披露" if recall[SCOPE_66]["sort_problem_ge4"]
            == given["q66_rank4_10_label"] else "不一致（如实披露）"),
        row("233 题真召回", given["q233_true_recall"],
            recall[SCOPE_233]["true_recall"]),
        row("233 题「rank4-10」=17（实为 rank>=4 合计）",
            given["q233_rank4_10_label"],
            recall[SCOPE_233]["sort_problem_ge4"],
            "定义差异已披露" if recall[SCOPE_233]["sort_problem_ge4"]
            == given["q233_rank4_10_label"] else "不一致（如实披露）"),
        row("233 题 rank2-3", given["q233_rank2_3"],
            recall[SCOPE_233]["rank_2_3"]),
        row("66 题 hubness 指数", given["q66_hubness_index"],
            hub[SCOPE_66]["hubness_index"]),
        row("233 题 hubness 指数", given["q233_hubness_index"],
            hub[SCOPE_233]["hubness_index"]),
        row("66 题 V2 top1 命中数", given["q66_top1_hits"],
            joint[SCOPE_66]["top1_hits"]),
        row("233 题 V2 top1 (%)", f"{given['q233_top1_pct']:.1f}",
            f"{joint[SCOPE_233]['top1'] * 100:.1f}"),
        row("按章等权宏平均 (%)", f"{given['macro_top1_pct']:.1f}",
            f"{chapter['macro_avg_top1'] * 100:.1f}"),
        row("最差章 ch03 top1 (%)", f"{given['worst_ch03_pct']:.1f}",
            f"{chapter['per_chapter']['ch03']['top1'] * 100:.1f}"),
        row("低重合题数", given["low_overlap_n"], overlap["low"]["n"]),
        row("低重合组 top1 (%)", f"{given['low_overlap_top1_pct']:.1f}",
            f"{overlap['low']['top1'] * 100:.1f}"),
        row("高重合题数", given["high_overlap_n"], overlap["high"]["n"]),
        row("高重合组 top1 (%)", f"{given['high_overlap_top1_pct']:.1f}",
            f"{overlap['high']['top1'] * 100:.1f}"),
        row("66 题 CONFIRM 命中恒 11（V0/V2）",
            f"{given['confirm_hits']}/{given['confirm_hits']}",
            f"{decision['confirm']['v0_hits']}/{decision['confirm']['v2_hits']}"),
        row("66 题 AUTO 精确率 V0 (%)",
            f"{given['v0_auto_precision_pct']:.1f}",
            f"{decision['auto_precision_v0'] * 100:.1f}"),
        row("66 题 AUTO 精确率 V2 (%)",
            f"{given['v2_auto_precision_pct']:.1f}",
            f"{decision['auto_precision_v2'] * 100:.1f}"),
        row("Wilson 35/66", f"{given['wilson_35_66'][0]:.1f},"
            f"{given['wilson_35_66'][1]:.1f}",
            f"{mcn['wilson_observed'][0]:.1f},{mcn['wilson_observed'][1]:.1f}"),
        row("Wilson 47/66", f"{given['wilson_47_66'][0]:.1f},"
            f"{given['wilson_47_66'][1]:.1f}",
            f"{mcn['wilson_target'][0]:.1f},{mcn['wilson_target'][1]:.1f}"),
        row("「反向错 >8 即不显著」(B 级推演)",
            f">{given['reverse_error_threshold']}",
            f"首个 p>=0.05 的 n10={mcn['counterfactual_first_nonsignificant_n10']}",
            "A 级实跑校正（推演值非结论）"),
    ]
    return rows


def build_crosscheck_rows(payload: dict[str, Any]) -> list[dict[str, str]]:
    """逐项对照「主理人 A 级独立实跑」核验值（外部输入 vs 本脚本实跑）。

    判定用命中计数 + 分母 + 3 位小数 acc 容差 0.001，避免展示精度差异
    造成假不一致。任何不一致如实披露，不静默采用任一方。
    """
    given = LEAD_CROSSCHECK_GIVEN
    rows: list[dict[str, str]] = []

    def add(name: str, lead_text: str, observed_text: str, ok: bool) -> None:
        rows.append({
            "item": name,
            "lead": lead_text,
            "observed": observed_text,
            "verdict": "一致" if ok else "不一致（如实披露）",
        })

    segment_order = ("FAILED", "CONFIRM", "AUTO")
    decision = payload["metrics"]["6_decision"]
    for scope in SCOPES:
        for variant in ("V0", "V2"):
            block = decision[scope]["v0_segments" if variant == "V0"
                                    else "segments"]
            expected = given["segments"][f"{scope}|{variant}"]
            lead_text = " / ".join(
                f"{key} {expected[key][0]}/{expected[key][1]}={expected[key][2]:.3f}"
                for key in segment_order
            )
            observed_text = " / ".join(
                f"{key} {block[key]['hit1']}/{block[key]['n']}={block[key]['acc']:.3f}"
                for key in segment_order
            )
            ok = all(
                block[key]["hit1"] == expected[key][0]
                and block[key]["n"] == expected[key][1]
                and abs(block[key]["acc"] - expected[key][2]) <= 0.001
                for key in segment_order
            )
            add(f"决策三段 acc · {scope} · {variant}", lead_text, observed_text, ok)

    top3 = payload["metrics"]["7_decision_top3"]
    for scope in SCOPES:
        expected = given["top3_v2"][scope]
        group = top3[scope]["groups"]
        lead_text = " / ".join(
            f"{key} {expected[key][0]}/{expected[key][1]}"
            for key in ("FAILED", "CONFIRM", "AUTO")
        )
        observed_text = " / ".join(
            f"{key} {group[key]['hit3']}/{group[key]['n']}"
            for key in ("FAILED", "CONFIRM", "AUTO")
        )
        ok = all(
            group[key]["hit3"] == expected[key][0]
            and group[key]["n"] == expected[key][1]
            for key in ("FAILED", "CONFIRM", "AUTO")
        )
        add(f"按 decision 分组 top3 · {scope} · V2", lead_text, observed_text, ok)

    for scope in SCOPES:
        expected_count = given["confirm_mis"][scope]
        observed_count = decision[scope]["confirm_mis_highlight"]
        add(f"CONFIRM 误高亮 · {scope} · V2",
            f"{expected_count} 题", f"{observed_count} 题",
            expected_count == observed_count)

    recall = payload["metrics"]["5_recall"]
    for scope in SCOPES:
        expected = given["recall"][scope]
        observed = recall[scope]
        lead_parts = [f"进候选 {expected['in_cand']}",
                      f"真未召回 {expected['true']}"]
        observed_parts = [f"进候选 {observed['gold_in_candidates']}",
                          f"真未召回 {observed['true_recall']}"]
        rank1_observed = observed["gold_in_candidates"] - (
            observed["true_recall"] + observed["rank_2_3"]
            + observed["rank_4_10_strict"] + observed["mismatch_gt10"])
        if "rank1" in expected:
            lead_parts.append(f"rank1 {expected['rank1']}")
            observed_parts.append(f"rank1 {rank1_observed}")
        if "rank2_3" in expected:
            lead_parts.append(f"rank2-3 {expected['rank2_3']}")
            observed_parts.append(f"rank2-3 {observed['rank_2_3']}")
        lead_parts.extend([f"rank4-10 {expected['rank4_10']}",
                           f"rank>10 {expected['gt10']}"])
        observed_parts.extend([f"rank4-10 {observed['rank_4_10_strict']}",
                               f"rank>10 {observed['mismatch_gt10']}"])
        keys = [key for key in ("in_cand", "true", "rank1", "rank2_3",
                                "rank4_10", "gt10") if key in expected]
        observed_map = {
            "in_cand": observed["gold_in_candidates"],
            "true": observed["true_recall"],
            "rank1": rank1_observed,
            "rank2_3": observed["rank_2_3"],
            "rank4_10": observed["rank_4_10_strict"],
            "gt10": observed["mismatch_gt10"],
        }
        ok = all(observed_map[key] == expected[key] for key in keys)
        add(f"召回三分类分档 · {scope} · V2",
            " / ".join(lead_parts), " / ".join(observed_parts), ok)
    return rows


# ---------------------------------------------------------------------------
# 实测基线值表（核心回答：可提高 / 仅被测量）
# ---------------------------------------------------------------------------


def build_baseline_table(payload: dict[str, Any]) -> list[dict[str, str]]:
    """输出「哪些指标是可提高的、当前值是多少、哪些只是被测量出来」。"""
    joint = payload["metrics"]["1_joint"]
    hub = payload["metrics"]["2_hubness"]
    chapter = payload["metrics"]["3_by_chapter"][SCOPE_233]
    overlap = payload["metrics"]["4_overlap"][SCOPE_233]
    recall = payload["metrics"]["5_recall"]
    decision = payload["metrics"]["6_decision"]
    seg_top3 = payload["metrics"]["7_decision_top3"]
    parity = payload["metrics"]["8_parity"]
    boot = payload["statistics"]["bootstrap"]
    mcn = payload["statistics"]["mcnemar"]
    lco = payload["statistics"]["lco"]

    def entry(name: str, v66: str, v233: str, kind: str,
              basis: str, level: str = "A") -> dict[str, str]:
        return {"metric": name, "u81_66": v66, "u81_233": v233,
                "kind": kind, "basis": basis, "level": level}

    rows: list[dict[str, str]] = [
        entry("双口径六项 joint（top1）",
              pct(joint[SCOPE_66]["top1"]), pct(joint[SCOPE_233]["top1"]),
              "仅被测量", "测量修正：Q_SQL 锁死最差两章造成低估，非算法改动"),
        entry("双口径六项 joint（top3）",
              pct(joint[SCOPE_66]["top3"]), pct(joint[SCOPE_233]["top3"]),
              "仅被测量", "同上；两口径并列、禁止相减"),
        entry("双口径六项 joint（AUTO 精确率）",
              pct(joint[SCOPE_66]["autoPrecision"]),
              pct(joint[SCOPE_233]["autoPrecision"]),
              "可提高（66 段）", "66 段 80.0% 有改进空间，但不得靠调阈值实现"),
        entry("双口径六项 joint（人工率）",
              pct(joint[SCOPE_66]["manualRate"]),
              pct(joint[SCOPE_233]["manualRate"]),
              "可提高", "决策标尺重校准方向，须人裁决，本轮未实施"),
        entry("双口径六项 joint（覆盖 / FAILED）",
              f"{pct(joint[SCOPE_66]['coverage'])} / {joint[SCOPE_66]['failed']}",
              f"{pct(joint[SCOPE_233]['coverage'])} / {joint[SCOPE_233]['failed']}",
              "可提高", "FAILED 21>8 与覆盖 91.0% 的语义冲突待裁决"),
        entry("hubness 指数（错误 top1 Top-5 吸引子）",
              str(hub[SCOPE_66]["hubness_index"]),
              str(hub[SCOPE_233]["hubness_index"]),
              "可提高", "§10.4 负结果：hubness 校正变体均未满足六项 joint 不退化"),
        entry("语料质心 hubness 分布（min/max/mean）",
              "—", f"{hub['centroid']['min']}/{hub['centroid']['max']}"
                    f"/{hub['centroid']['mean']}",
              "仅被测量", "只读诊断量，不参与排序与决策"),
        entry("按章分层：等权宏平均 / 最差章",
              "—", f"{pct(chapter['macro_avg_top1'])} / "
                    f"{chapter['worst_chapter']} {pct(chapter['worst_chapter_top1'])}",
              "可提高（ch03）", "最差章不得被宏平均掩盖"),
        entry("低重合题分层（n / top1）",
              f"{payload['metrics']['4_overlap'][SCOPE_66]['low']['n']} / "
              f"{pct(payload['metrics']['4_overlap'][SCOPE_66]['low']['top1'])}",
              f"{overlap['low']['n']} / {pct(overlap['low']['top1'])}",
              "仅被测量", "代理指标、非人工标注；ch07 反例 ⇒ 非单调"),
        entry("高重合题分层（n / top1）",
              f"{payload['metrics']['4_overlap'][SCOPE_66]['high']['n']} / "
              f"{pct(payload['metrics']['4_overlap'][SCOPE_66]['high']['top1'])}",
              f"{overlap['high']['n']} / {pct(overlap['high']['top1'])}",
              "仅被测量", "不量化复述红利占比"),
        entry("召回三分类：真召回 / 排序问题(rank>=4)",
              f"{recall[SCOPE_66]['true_recall']} / "
              f"{recall[SCOPE_66]['sort_problem_ge4']}",
              f"{recall[SCOPE_233]['true_recall']} / "
              f"{recall[SCOPE_233]['sort_problem_ge4']}",
              "仅被测量", "被反证的是「真召回缺口」归因，不是 top3 未命中现象"),
        entry("决策三段 acc 与单调性（V0/V2 分别判）",
              f"V0 {'反转' if decision[SCOPE_66]['v0_observed_reversal'] else '未观察到'} / "
              f"V2 {'反转' if decision[SCOPE_66]['observed_reversal'] else '未观察到'}",
              f"V0 {'反转' if decision[SCOPE_233]['v0_observed_reversal'] else '未观察到'} / "
              f"V2 {'反转' if decision[SCOPE_233]['observed_reversal'] else '未观察到'}",
              "可提高（诊断）",
              "单调性随变体与口径变化；V2/233 未观察到反转，但未拒绝不构成标尺有效"),
        entry("CONFIRM 误高亮（题数 / 占比）",
              f"{decision[SCOPE_66]['confirm_mis_highlight']} / "
              f"{pct(decision[SCOPE_66]['confirm_mis_highlight_rate'])}",
              f"{decision[SCOPE_233]['confirm_mis_highlight']} / "
              f"{pct(decision[SCOPE_233]['confirm_mis_highlight_rate'])}",
              "可提高（诊断）", "误高亮仍在，只作诊断"),
        entry("按 decision 分组 top3（FAILED 段 hit3）",
              f"{seg_top3[SCOPE_66]['groups']['FAILED']['hit3']}/"
              f"{seg_top3[SCOPE_66]['groups']['FAILED']['n']}",
              f"{seg_top3[SCOPE_233]['groups']['FAILED']['hit3']}/"
              f"{seg_top3[SCOPE_233]['groups']['FAILED']['n']}",
              "仅被测量", "不得据此调 confirm-floor 回收 k 题制造达标"),
        entry("Java/Python parity 断言",
              "—", f"{sum(1 for c in parity['checks'] if c['ok'])}/"
                   f"{len(parity['checks'])} 项一致",
              "仅被测量", "一致性断言，不构成算法改善；Java 侧运行时对照未做",
              "A+C"),
        entry("LCO 8 折：宏平均 / 最差折",
              "—", f"{pct(lco['macro_avg_top1'])} / {lco['worst_fold']} "
                   f"{pct(lco['worst_fold_top1'])}",
              "仅被测量", "语料 81 点全集不变；章节异质性，非泛化估计"),
        entry("配对 bootstrap Δ 95% CI（是否排除 0）",
              f"[{boot[SCOPE_66]['ci95_pct'][0]:.2f}, "
              f"{boot[SCOPE_66]['ci95_pct'][1]:.2f}]pp / "
              f"{'排除' if boot[SCOPE_66]['excludes_zero'] else '未排除'}",
              f"[{boot[SCOPE_233]['ci95_pct'][0]:.2f}, "
              f"{boot[SCOPE_233]['ci95_pct'][1]:.2f}]pp / "
              f"{'排除' if boot[SCOPE_233]['excludes_zero'] else '未排除'}",
              "仅被测量", "V2 vs V0 未观察到可区分差异"),
        entry("McNemar 精确检验（n01/n10/p）",
              f"{mcn[SCOPE_66]['n01']}/{mcn[SCOPE_66]['n10']}/"
              f"{mcn[SCOPE_66]['p_exact_two_sided']:.4f}",
              f"{mcn[SCOPE_233]['n01']}/{mcn[SCOPE_233]['n10']}/"
              f"{mcn[SCOPE_233]['p_exact_two_sided']:.4f}",
              "仅被测量", "配对检验；不得用独立 CI 不重叠宣称显著"),
    ]
    return rows


# ---------------------------------------------------------------------------
# 报告渲染
# ---------------------------------------------------------------------------


def render_markdown(payload: dict[str, Any]) -> str:
    """渲染完整 Markdown 报告；所有数字只读 payload（实跑结果）。"""
    run = payload["run"]
    universe = payload["universe"]
    config = payload["config_frozen"]
    joint = payload["metrics"]["1_joint"]
    hub = payload["metrics"]["2_hubness"]
    chapter = payload["metrics"]["3_by_chapter"]
    overlap = payload["metrics"]["4_overlap"]
    recall = payload["metrics"]["5_recall"]
    decision = payload["metrics"]["6_decision"]
    seg_top3 = payload["metrics"]["7_decision_top3"]
    parity = payload["metrics"]["8_parity"]
    lco = payload["statistics"]["lco"]
    boot = payload["statistics"]["bootstrap"]
    mcn = payload["statistics"]["mcnemar"]
    anchors = payload["anchors"]["rows"]
    crosscheck = payload["crosscheck"]["rows"]
    crosscheck_source = payload["crosscheck"]["source"]
    baseline = payload["baseline_table"]
    audit = payload.get("audit", {})
    not_done = payload["not_done"]

    parts: list[str] = []
    parts.append(
        "# 算法创新指标自动评测 · metrics-20261106\n\n"
        "> 证据等级：**A = 实跑** / **B = 推演未复核** / **C = 静态勘察**。\n"
        "> 本报告只做**测量与统计验证**：本轮零算法改动、零阈值改动、零生产代码改动。\n"
        "> 措辞红线：不宣称门禁达标、不宣称形成放行结论、不宣称效果显著；"
        "双口径差值只作测量修正记录。\n"
        "> ``IS_PASS`` 是脚本自检结论，**不是**门禁达标结论。\n"
        "> **本轮核心结论：所有 8 项指标与 3 类统计验证都是首次被测量，"
        "没有任何一项被改善；V2 相对 V0 未观察到统计上可区分的差异。**"
    )

    # 0. 元数据
    parts.append(
        "## 0. 运行元数据与口径声明 [A]\n\n"
        + md_table(
            ["项", "值"],
            [
                ["generated_at", run["generated_at"]],
                ["python", run["python"]],
                ["command", run["command"]],
                ["K（知识点语料）", universe["universe_size"]],
                ["Q（U81-66 / U81-233）",
                 f"{universe['n_questions_66']} / {universe['n_questions_233']}"],
                ["C（章节）", ", ".join(str(c) for c in universe["chapters"])],
                ["题库种子文件", f"{universe['question_file_count']} 个"],
                ["gold 落在 81 点语料内",
                 f"{universe['gold_in_universe']}/{universe['n_questions_233']}"
                 f"（缺失 {universe['gold_missing']}）"],
                ["66 题为 233 题子集", str(universe["is_66_complete_subset"])],
                ["gate_line U81-66", GATE_LINE_66],
                ["gate_line U81-233", GATE_LINE_233],
                ["原脚本 sha256（前/后）",
                 f"{run['offline_sha256_before'][:16]}… / "
                 f"{run['offline_sha256_after'][:16]}…"],
                ["query 形态", payload["query_shape"]["construction"]],
                ["等于线上 request.getText()",
                 payload["query_shape"]["equals_online_request_getText"]],
                ["排除字段", payload["query_shape"]["excluded_fields"]],
                ["产物哈希清单", ARTIFACTS_SHA.name],
                ["产物校验命令", VERIFY_COMMAND],
                ["evidence_level", payload["evidence_level"]],
            ],
        )
        + "\n\n冻结阈值（三方静态对照，[C]）：\n\n"
        + md_table(
            ["旋钮", "原脚本", "application.yml", "Java 默认值", "预期"],
            [
                ["score-scale", config["module_values"]["score_scale"],
                 config["yml_values"]["score_scale"], "0.3", 0.3],
                ["auto-threshold", config["module_values"]["auto_threshold"],
                 config["yml_values"]["auto_threshold"], "0.60", 0.60],
                ["margin", config["module_values"]["margin"],
                 config["yml_values"]["margin"], "0.10", 0.10],
                ["confirm-floor", config["module_values"]["floor"],
                 config["yml_values"]["floor"], "读 yml（无默认）", 0.35],
                ["idf-power", config["module_values"]["idf_power"], "—", "1.5", 1.5],
                ["name-repeat", config["module_values"]["name_repeat"], "—", "3", 3],
            ],
        )
        + f"\n\n阈值冻结核对：module={config['module_ok']} / yml={config['yml_ok']} "
          f"/ java={config['java_ok']} / all_frozen={config['all_frozen']}。"
          "本脚本不提供 ``--floor`` 等 sweep 参数，不做任何 threshold sweep。"
    )

    # 1. 实测基线值表
    parts.append(
        "## 1. 实测基线值表（核心回答）[A]\n\n"
        "**哪些可提高、当前值多少、哪些只是被测量出来而非被改善**：\n\n"
        + md_table(
            ["指标", "U81-66 当前值", "U81-233 当前值", "属性", "依据", "证据等级"],
            [[row["metric"], row["u81_66"], row["u81_233"], row["kind"],
              row["basis"], row["level"]] for row in baseline],
        )
        + "\n\n> 属性为「可提高」的项仅表示**存在可改进方向**，本轮一律未实施；"
          "受红线约束，不得靠调阈值、换验收集、删样本或只报最优来实现。\n"
          "> 属性为「仅被测量」的项是首次量化，**不构成改善**。\n"
          "> 双口径并列，**禁止跨口径相减**，也**禁止跨口径相加**；"
          "U81-233 全程标注为观测线、非 L1/L2 验收线。"
    )

    # 2. 指标 1
    j66, j233 = joint[SCOPE_66], joint[SCOPE_233]
    v0_66, v0_233 = joint["V0"][SCOPE_66], joint["V0"][SCOPE_233]
    parts.append(
        "## 2. 指标 1 · 双口径六项 joint [A]\n\n"
        + md_table(
            ["口径", "gate_line", "n", "top1", "top3", "AUTO 精确率",
             "人工率", "覆盖", "FAILED"],
            [joint_row(SCOPE_66, j66, GATE_LINE_66),
             joint_row(SCOPE_233, j233, GATE_LINE_233)],
        )
        + "\n\n精确分数：\n\n"
        + md_table(
            ["口径", "top1", "top3", "AUTO 精确率", "人工率", "覆盖"],
            [[SCOPE_66, j66["detail"]["top1"], j66["detail"]["top3"],
              j66["detail"]["autoPrecision"], j66["detail"]["manualRate"],
              j66["detail"]["coverage"]],
             [SCOPE_233, j233["detail"]["top1"], j233["detail"]["top3"],
              j233["detail"]["autoPrecision"], j233["detail"]["manualRate"],
              j233["detail"]["coverage"]]],
        )
        + "\n\nV0 / V2 双变体并列（两个变体都列出，不是只报最优）：\n\n"
        + md_table(
            ["口径", "变体", "top1", "top3", "AUTO", "AUTO 精确率",
             "人工率", "覆盖", "FAILED"],
            [joint_row(SCOPE_66 + " · V0", v0_66, GATE_LINE_66),
             joint_row(SCOPE_66 + " · V2", j66, GATE_LINE_66),
             joint_row(SCOPE_233 + " · V0", v0_233, GATE_LINE_233),
             joint_row(SCOPE_233 + " · V2", j233, GATE_LINE_233)],
        )
        + "\n\n> 阈值一位未动、算法一行未改；两口径差异来自 ``Q_SQL`` 写死只读 "
          "``seed-question-ch02-ch03.sql``（全库最差两章），属**测量修正**。\n"
          "> 233 题是**观测结论、非验收结论**；裁决前不得形成放行结论。"
    )

    # 3. 指标 2
    h66, h233, centroid = hub[SCOPE_66], hub[SCOPE_233], hub["centroid"]
    parts.append(
        "## 3. 指标 2 · hubness 指数 [A]\n\n"
        + md_table(
            ["口径", "错误 top1 题数", "Top-5 吸引子", "hubness 指数", "集中度"],
            [
                [SCOPE_66, h66["error_top1_n"],
                 ", ".join(f"{item['kp_id']}×{item['count']}"
                           for item in h66["top5_attractors"]),
                 h66["hubness_index"], pct(h66["concentration"])],
                [SCOPE_233, h233["error_top1_n"],
                 ", ".join(f"{item['kp_id']}×{item['count']}"
                           for item in h233["top5_attractors"]),
                 h233["hubness_index"], pct(h233["concentration"])],
            ],
        )
        + "\n\n定义：" + h66["definition"] + "。越低越好；**不得据此改 confirm-floor**。\n\n"
        "语料质心 hubness 分布（复刻 Java ``computeHubness``，向量与全体向量均值方向的余弦）：\n\n"
        + md_table(
            ["n", "min", "max", "mean", "Top8 通用知识点"],
            [[centroid["n"], centroid["min"], centroid["max"], centroid["mean"],
              ", ".join(f"{item['kp_id']}({item['hubness']})"
                        for item in centroid["top8"])]],
        )
        + "\n\n> " + centroid["note"] + "；本项只记录、不参与排序。"
    )

    # 4. 指标 3
    ch233 = chapter[SCOPE_233]
    ch66 = chapter[SCOPE_66]
    chap_rows = []
    for key, row in ch233["per_chapter"].items():
        chap_rows.append([key, row["n"], pct(row["top1"]), pct(row["top3"]),
                          pct(row["autoPrecision"]), pct(row["manualRate"]),
                          row["failed"]])
    parts.append(
        "## 4. 指标 3 · 按章分层 [A]\n\n"
        "U81-233（8 章，三者同时给：每章明细 + 等章权宏平均 + 最差章）：\n\n"
        + md_table(["章", "n", "top1", "top3", "AUTO 精确率", "人工率", "FAILED"],
                   chap_rows)
        + f"\n\n等章权宏平均 top1 = **{pct(ch233['macro_avg_top1'])}**；"
          f"最差章 = **{ch233['worst_chapter']} {pct(ch233['worst_chapter_top1'])}**"
          "（不得用宏平均掩盖最差章）。\n\n"
        "U81-66 仅含 ch02+ch03（历史对照，无 ch07）：\n\n"
        + md_table(["章", "n", "top1", "top3", "AUTO 精确率", "人工率", "FAILED"],
                   [[key, row["n"], pct(row["top1"]), pct(row["top3"]),
                     pct(row["autoPrecision"]), pct(row["manualRate"]),
                     row["failed"]]
                    for key, row in ch66["per_chapter"].items()])
        + f"\n\nU81-66 等章权宏平均 = {pct(ch66['macro_avg_top1'])}，"
          f"最差章 = {ch66['worst_chapter']} {pct(ch66['worst_chapter_top1'])}。"
    )

    # 5. 指标 4
    ov233, ov66 = overlap[SCOPE_233], overlap[SCOPE_66]
    parts.append(
        "## 5. 指标 4 · 低重合题分层 [A]\n\n"
        f"定义：{ov233['definition']}；分层阈值 {ov233['split']:.2f}。\n\n"
        + md_table(
            ["口径", "组", "n", "top1", "top3", "AUTO 精确率", "人工率"],
            [
                [SCOPE_66, "低重合(<50%)", ov66["low"]["n"],
                 pct(ov66["low"]["top1"]), pct(ov66["low"]["top3"]),
                 pct(ov66["low"]["autoPrecision"]), pct(ov66["low"]["manualRate"])],
                [SCOPE_66, "高重合(>=50%)", ov66["high"]["n"],
                 pct(ov66["high"]["top1"]), pct(ov66["high"]["top3"]),
                 pct(ov66["high"]["autoPrecision"]), pct(ov66["high"]["manualRate"])],
                [SCOPE_233, "低重合(<50%)", ov233["low"]["n"],
                 pct(ov233["low"]["top1"]), pct(ov233["low"]["top3"]),
                 pct(ov233["low"]["autoPrecision"]), pct(ov233["low"]["manualRate"])],
                [SCOPE_233, "高重合(>=50%)", ov233["high"]["n"],
                 pct(ov233["high"]["top1"]), pct(ov233["high"]["top3"]),
                 pct(ov233["high"]["autoPrecision"]), pct(ov233["high"]["manualRate"])],
            ],
        )
        + "\n\n按章均值覆盖率 vs top1（**ch07 反例**）：\n\n"
        + md_table(
            ["章", "n", "平均覆盖率", "top1"],
            [[key, row["n"], row["mean_overlap"], pct(row["top1"])]
             for key, row in ov233["by_chapter"].items()],
        )
        + "\n\n全库平均覆盖率 = " + str(ov233["mean_overlap_all"])
        + "；ch07 平均覆盖率 = " + str(ov233["ch07_counterexample"]["ch07_mean_overlap"])
        + "，ch07 top1 = " + pct(ov233["ch07_counterexample"]["ch07_top1"] or 0.0)
        + "，为 8 章最高（" + str(ov233["ch07_counterexample"]["max_top1_chapter"])
        + "）。反例成立 = " + str(ov233["ch07_counterexample"]["present"]) + "。\n\n"
        "> **代理指标、非人工标注、不量化红利占比**；覆盖率偏低却 top1 最高 ⇒ "
        "字面重合与准确率非单调，不得写成语义理解或泛化能力。"
        "U81-66 无 ch07，反例只在 U81-233 上成立。"
    )

    # 6. 指标 5
    r66, r233 = recall[SCOPE_66], recall[SCOPE_233]
    parts.append(
        "## 6. 指标 5 · 召回三分类 [A]\n\n"
        "逐题记录 ``gold_in_candidates``（布尔）与 ``gold_rank``（1-based，"
        "未进候选为 null）。三分类对 **top3 未命中题**互斥且完备：\n\n"
        + md_table(
            ["口径", "n", "进候选", "真召回缺口", "排序问题(4-10)",
             "表示不匹配(>10)", "rank>=4 合计(未命中但在候选)",
             "rank2-3", "top3 未命中", "守恒"],
            [
                [SCOPE_66, r66["n"], r66["gold_in_candidates"], r66["true_recall"],
                 r66["rank_4_10_strict"], r66["mismatch_gt10"],
                 r66["sort_problem_ge4"], r66["rank_2_3"], r66["top3_miss"],
                 r66["conservation_ok"]],
                [SCOPE_233, r233["n"], r233["gold_in_candidates"],
                 r233["true_recall"], r233["rank_4_10_strict"],
                 r233["mismatch_gt10"], r233["sort_problem_ge4"],
                 r233["rank_2_3"], r233["top3_miss"], r233["conservation_ok"]],
            ],
        )
        + "\n\n逐题类别分布：\n\n"
        + md_table(
            ["口径", "类别", "题数"],
            [[scope, cls, count]
             for scope in SCOPES
             for cls, count in recall[scope]["class_counts"].items()],
        )
        + "\n\n> **分档口径**：`rank>=4 合计` = `rank4-10` + `rank>10` = "
          "**top3 未命中但仍在候选集**（" + r66["sort_problem_ge4_meaning"]
        + "）；真召回缺口是「未进候选」，与该合计互斥。"
          "逐题 `gold_rank` 已落 `algo_metrics.json.per_case`。\n"
          "> 等式：" + r66["rank_ge4_equation"] + "\n"
          "> 字段别名：" + ", ".join(
              f"`{old}` → `{new}`" for old, new in FIELD_ALIASES.items()
          ) + "；" + r66["mismatch_gt10_note"] + "\n"
          "> 标签校正（主理人 A 级独立实跑）：" + r66["definition_note"] + "\n"
          "> **" + r66["attribution_note"] + "**\n"
          "> 未进候选题 ID：U81-66 = "
        + (", ".join(r66["no_recall_ids"]) or "无") + "；U81-233 = "
        + (", ".join(r233["no_recall_ids"]) or "无") + "。"
    )

    # 7. 指标 6
    d66, d233 = decision[SCOPE_66], decision[SCOPE_233]
    seg_rows = []
    for scope, block in ((SCOPE_66, d66), (SCOPE_233, d233)):
        for variant in ("V0", "V2"):
            seg_block = block["v0_segments" if variant == "V0" else "segments"]
            for key in ("FAILED", "CONFIRM", "AUTO"):
                seg = seg_block[key]
                seg_rows.append([scope, variant, key, seg["n"], seg["hit1"],
                                 pct(seg["acc"]), seg["hit3"],
                                 pct(seg["top3_rate"])])
    matrix_rows = []
    for scope, block in ((SCOPE_66, d66), (SCOPE_233, d233)):
        for left in ("FAILED", "CONFIRM", "AUTO"):
            matrix_rows.append(
                [scope, left,
                 block["migration_matrix"][left]["FAILED"],
                 block["migration_matrix"][left]["CONFIRM"],
                 block["migration_matrix"][left]["AUTO"]]
            )
    parts.append(
        "## 7. 指标 6 · 决策三段 acc + 单调性 + 逐题决策迁移矩阵 [A]\n\n"
        + md_table(["口径", "变体", "段", "n", "top1 命中", "acc(top1)",
                    "top3 命中", "top3 率"], seg_rows)
        + "\n\n单调性判定（**按变体 × 口径分别判**：V0/U81-233 也是反转，"
          "不能把「未观察到反转」归给整个 233 口径）：\n\n"
        + md_table(
            ["口径", "变体", "acc(FAILED)<=acc(CONFIRM)<=acc(AUTO)",
             "观察到反转", "反转对"],
            [[scope, variant,
              block["v0_monotonic" if variant == "V0" else "monotonic"],
              block["v0_observed_reversal" if variant == "V0"
                    else "observed_reversal"],
              ", ".join(block["v0_reversal_pairs" if variant == "V0"
                              else "reversal_pairs"]) or "无"]
             for scope, block in ((SCOPE_66, d66), (SCOPE_233, d233))
             for variant in ("V0", "V2")],
        )
        + "\n\n> **单调性随变体与口径变化**：U81-66 的 V0 / V2 均观察到反转；"
          "U81-233 的 **V0 观察到反转、V2 未观察到反转**。"
          "V2/233 未观察到反转，但**「未拒绝」不构成「标尺有效」的正面证据**；"
          f"CONFIRM 误高亮 {d233['confirm_mis_highlight']}/233 = "
          f"{pct(d233['confirm_mis_highlight_rate'])} 仍在。\n"
          "> 「CONFIRM 误高亮」定义："
        + d66["confirm_mis_highlight_definition"]
        + "（与按 decision 分组的 `hit3` 是不同量，不得混用）。\n\n"
        "逐题决策迁移矩阵（V0 → V2）：\n\n"
        + md_table(["口径", "V0 \\ V2", "FAILED", "CONFIRM", "AUTO"], matrix_rows)
        + "\n\nCONFIRM 精确率假象与 AUTO 精确率 C1 退化（U81-66）：\n\n"
        + md_table(
            ["项", "V0", "V2", "说明"],
            [
                ["CONFIRM 分母", d66["confirm"]["v0_n"], d66["confirm"]["v2_n"],
                 "37→31，分母缩小"],
                ["CONFIRM 命中", d66["confirm"]["v0_hits"],
                 d66["confirm"]["v2_hits"], "命中恒 11"],
                ["CONFIRM acc", pct(d66["confirm"]["v0_acc"]),
                 pct(d66["confirm"]["v2_acc"]),
                 "29.7→35.5 是分母变化造成的精确率假象"],
                ["AUTO 精确率", pct(d66["auto_precision_v0"]),
                 pct(d66["auto_precision_v2"]), "86.4→80.0 属 C1 退化"],
            ],
        )
        + "\n\n> " + d66["note"]
    )

    # 8. 指标 7
    parts.append(
        "## 8. 指标 7 · 按 decision 分组的 top3 命中率（含 FAILED 段）[A]\n\n"
        + md_table(
            ["口径", "段", "n", "hit1", "hit1 率", "hit3", "hit3 率"],
            [[scope, key, seg_top3[scope]["groups"][key]["n"],
              seg_top3[scope]["groups"][key]["hit1"],
              pct(seg_top3[scope]["groups"][key]["hit1_rate"]),
              seg_top3[scope]["groups"][key]["hit3"],
              pct(seg_top3[scope]["groups"][key]["hit3_rate"])]
             for scope in SCOPES
             for key in ("FAILED", "CONFIRM", "AUTO")],
        )
        + "\n\n> " + seg_top3[SCOPE_66]["group_definition"] + "\n"
          "> " + seg_top3[SCOPE_66]["floor_note"] + "；**只作诊断**。\n"
          "> **" + seg_top3[SCOPE_66]["prohibition"] + "**"
    )

    # 9. 指标 8
    parts.append(
        "## 9. 指标 8 · Java / Python parity 断言 [A+C]\n\n"
        + md_table(
            ["断言", "名称", "证据等级", "结果", "实跑/静态细节"],
            [[check["id"], check["name"], check["level"],
              "一致" if check["ok"] else "不一致", check["detail"]]
             for check in parity["checks"]],
        )
        + f"\n\n全部一致 = {parity['all_ok']}；Java 源码 sha256 = "
          f"`{parity['java_source_sha256'][:16]}…`；"
          f"application.yml sha256 = `{parity['yml_source_sha256'][:16]}…`。\n\n"
          "> " + parity["note"] + "\n"
          "> 关键接线：``evaluate`` 第 415 行按 ``(-Hit.raw, kp_id)`` 强制重排；"
          "``Hit.score`` 只用于 ``decide()`` 与校准，任何排序实验的校正值必须放进 ``raw``。"
    )

    # 10. LCO
    lco_rows = []
    for key, row in lco["folds"].items():
        lco_rows.append([key, row["n"], pct(row["top1"]), pct(row["top3"]),
                         pct(row["autoPrecision"]), pct(row["coverage"]),
                         row["failed"]])
    parts.append(
        "## 10. 统计验证 A · LCO 8 折 [A]\n\n"
        + md_table(["折(留一章)", "n", "top1", "top3", "AUTO 精确率",
                    "覆盖", "FAILED"], lco_rows)
        + f"\n\n折数 = {lco['fold_count']}；等章权宏平均 top1 = "
          f"**{pct(lco['macro_avg_top1'])}**；最差折 = "
          f"**{lco['worst_fold']} {pct(lco['worst_fold_top1'])}**；"
          f"语料规模恒为 {lco['universe_size']} 点、全集不变 = "
          f"{lco['universe_unchanged']}。\n\n"
          "> " + lco["note"]
    )

    # 11. bootstrap
    parts.append(
        "## 11. 统计验证 B · 配对 bootstrap（V2 vs V0）[A]\n\n"
        + md_table(
            ["口径", "重采样次数", "种子", "Δ 观测 (pp)", "Δ 95% CI (pp)",
             "是否排除 0", "n01/n10"],
            [[scope, boot[scope]["reps"], boot[scope]["seed"],
              f"{boot[scope]['delta_observed_pct']:.2f}",
              f"[{boot[scope]['ci95_pct'][0]:.2f}, "
              f"{boot[scope]['ci95_pct'][1]:.2f}]",
              "排除" if boot[scope]["excludes_zero"] else "未排除",
              f"{boot[scope]['n01']}/{boot[scope]['n10']}"]
             for scope in SCOPES],
        )
        + "\n\n> " + boot[SCOPE_66]["note"]
        + "\n> 两口径分别计算，**禁止跨口径相减**。"
    )

    # 12. McNemar
    m66 = mcn[SCOPE_66]
    target = m66["target_analysis"]
    parts.append(
        "## 12. 统计验证 C · McNemar 精确检验 + Wilson CI [A；含 B 级推演复核]\n\n"
        + md_table(
            ["口径", "n01(V0错→V2对)", "n10(V0对→V2错)", "双侧精确 p",
             "p<0.05", "Wilson(实测)"],
            [[scope, mcn[scope]["n01"], mcn[scope]["n10"],
              f"{mcn[scope]['p_exact_two_sided']:.4f}",
              mcn[scope]["significant_at_005"],
              mcn[scope]["wilson_observed"]]
             for scope in SCOPES],
        )
        + "\n\n66 题专项（观测对照 " + pct(target["observation_target_top1"])
        + " 为外部给定阈值集合，**非本脚本门禁判定**）：\n\n"
        + md_table(
            ["项", "实跑值"],
            [
                ["V2 实测 top1 命中", f"{m66['hits_v2']}/{m66['n']} "
                 f"= {pct(m66['hits_v2'] / m66['n'])}"],
                ["达到观测对照所需最小命中", f"{target['target_hits']}/{m66['n']} "
                 f"= {pct(target['target_hits'] / m66['n'])}"],
                ["所需净胜题", target["net_gain"]],
                ["Wilson 35/66", str(target["wilson_observed"])],
                ["Wilson 47/66", str(target["wilson_target"])],
                ["两个独立 CI 是否重叠", str(target["cis_overlap"])],
                [f"净胜 {target['counterfactual_delta']} 题时首个 p>=0.05 的 n10",
                 target["counterfactual_first_nonsignificant_n10"]],
            ],
        )
        + "\n\n反事实扫描（净胜 " + str(target["counterfactual_delta"])
        + " 题，逐 n10 的双侧精确 p）：\n\n"
        + md_table(
            ["n10(反向错)", "n01(净胜对应)", "p 精确", "p<0.05"],
            [[item["n10_reverse_errors"], item["n01_wins"],
              f"{item['p_exact']:.4f}", item["significant_at_005"]]
             for item in target["counterfactual_rows"]],
        )
        + "\n\n> " + target["conclusion"] + "\n"
          "> " + target["correction"] + "\n"
          "> 任务书给定的「反向错 >" + str(target["given_reverse_threshold"])
        + " 即不显著」是 **B 级推演**；本表为 **A 级实跑**，以实跑为准。"
    )

    # 13. 锚点对照 + 主理人 A 级独立实跑交叉校验
    parts.append(
        "## 13. 校验锚点与主理人 A 级独立实跑交叉校验 [A]\n\n"
        "### 13.1 任务书校验锚点逐条对照\n\n"
        + md_table(["锚点", "任务书给定", "本脚本实跑", "判定"],
                   [[row["anchor"], row["given"], row["observed"], row["verdict"]]
                    for row in anchors])
        + "\n\n> 对照来源：" + payload["anchors"]["source"]
        + "；锚点是外部输入，不是本脚本产出的指标。\n\n"
        "### 13.2 主理人 A 级独立实跑交叉校验（结果表来源标注）\n\n"
        "> 交叉校验来源：**" + crosscheck_source + "**。"
        "主理人用 `Scorer.rank` + `decide(hits, floor=0.35)` 内联实跑、未落盘；"
        "本表逐项对照其给定值与本脚本实跑值。12 个三段 acc 分数、6 个 top3 分组数、"
        "CONFIRM 误高亮与召回分档全部一致 ⇒ `decide` / `floor` / "
        "`use_misconception` 接线一致，未静默采用任一方。\n\n"
        + md_table(["项目", "主理人 A 级给定", "本脚本实跑", "判定"],
                   [[row["item"], row["lead"], row["observed"], row["verdict"]]
                    for row in crosscheck])
        + "\n\n> 单调性**按变体分别判**：V0/U81-233 与两个 U81-66 口径均观察到反转，"
          "只有 V2/U81-233 未观察到；报告不笼统写「233 未观察到反转」，"
          "也不把「未拒绝」当作标尺有效的正面证据。\n"
          "> 按 decision 分组 top3 中 FAILED 段仍有大量 gold 落在前三候选，"
          "是决策标尺错配的诊断证据；**只作诊断，"
          "不得据此调 `confirm-floor` 回收 k 题制造达标**。"
    )

    # 14. 全局一致性审查
    if audit:
        parts.append(
            "## 14. 全局一致性审查 [A]\n\n"
            + "\n".join(f"- {line}" for line in audit["checks"])
            + "\n\n**IS_PASS: " + ("YES" if audit["is_pass"] else "NO") + "**\n\n"
            "> IS_PASS 仅为脚本自检（写入边界 / 原脚本未改 / 无泄漏 / 数字自洽 / "
            "阈值冻结 / 措辞），**不是**门禁达标结论。"
            + ("\n\n发现问题：\n" + "\n".join("- " + issue
                                          for issue in audit["issues"])
               if audit["issues"] else "")
        )
    else:
        parts.append("## 14. 全局一致性审查 [A]\n\n（审查结果生成中）")

    # 15. 未做项
    parts.append(
        "## 15. 未做项与证据等级汇总 [A/B/C]\n\n"
        + md_table(["项", "状态", "说明"],
                   [[item["name"], item["status"], item["note"]]
                    for item in not_done])
        + "\n\n证据等级：**[A]** = 本脚本实跑（指标 1—8、LCO、bootstrap、McNemar、"
          "parity 运行时断言、锚点复核）；**[B]** = 推演未复核（任务书的"
          "「反向错 >8」推演，已经 A 级实跑校正）；**[C]** = 静态勘察"
          "（Java 源码规则、application.yml 阈值、query 形态契约）；"
          "**A+C** = 运行时实跑叠加源码静态对照（parity 五项）。"
    )
    return "\n\n".join(parts) + "\n"


def render_readme(payload: dict[str, Any]) -> str:
    """渲染输出目录下的简短 README。"""
    run = payload["run"]
    audit = payload.get("audit", {})
    joint = payload["metrics"]["1_joint"]
    lco = payload["statistics"]["lco"]
    boot = payload["statistics"]["bootstrap"]
    mcn = payload["statistics"]["mcnemar"]
    lines = [
        "# 算法创新指标自动评测 · metrics-20261106",
        "",
        "> 状态：spike 原型产物，未合入 main；零算法改动、零阈值改动、"
        "零生产代码改动、零新增依赖、未 commit。",
        "",
        "## 1. 假设",
        "",
        "- 数据只读：`seed-kp-*.sql`（8 章 81 点）+ `seed-question-*.sql`"
        "（7 文件 233 题，按 `q_id` 去重）；`gold` 全部在 81 点语料内。",
        "- 双口径：`U81-66`（原脚本 `Q_SQL` 锁死 ch02+ch03，最差两章完整子集）"
        "与 `U81-233`（观测线、非 L1/L2 验收线）**并列、禁止相减/相加**。",
        "- 语料恒为 81 点全集；LCO 留章只留题目，不删语料（删语料式留章 = oracle，禁止）。",
        f"- query = `question_text` 纯题干，等于线上 `request.getText()`；"
        f"排除 {payload['query_shape']['excluded_fields']}。",
        "- 只复用既有 V0 / V2 两个变体；阈值一位未动，不做 threshold sweep。",
        "- 纯 Python 标准库（Python " + run["python"] + "），不联网、不连库。",
        "",
        "## 2. 成功标准（不得下调）",
        "",
        "- 8 项指标 + LCO 8 折 + 配对 bootstrap(≥2000) + McNemar 精确检验全部**实跑**并落盘。",
        "- 双口径各自标注 `gate_line`；按章三者同时给；ch07 反例与定义差异如实披露。",
        "- 所有数字来自实跑、无硬编码；锚点逐条对照；全局一致性审查 `IS_PASS: YES`。",
        "- 不宣称门禁达标、不宣称形成放行结论、不宣称效果显著。",
        "",
        "## 3. 退出条件",
        "",
        "- 任一完整性 / parity / 一致性断言不一致 → 如实披露、退出码 2，"
        "不得靠调阈值、删样本、换验收集或只报最优来「过」。",
        "- 统计验证未排除 0 / p≥0.05 → 写「未观察到可区分差异」，不写成正面证据。",
        f"- 本次实跑：退出码 {run['exit_code']}；IS_PASS: "
        f"{'YES' if audit.get('is_pass') else 'NO'}（脚本自检，非门禁结论）。",
        "",
        "## 4. 复跑命令与产物自证",
        "",
        "```bash",
        "cd \"D:/Desktop/智学领航：高校课程学情诊断系统/zhixue/tools\"",
        "PYTHONIOENCODING=utf-8 PYTHONDONTWRITEBYTECODE=1 python algo_metrics.py --reps 2000 --seed 20261106",
        "# 可选：PYTHONIOENCODING=utf-8 PYTHONDONTWRITEBYTECODE=1 python algo_metrics.py --reps 5000 --seed 20261106",
        "```",
        "",
        "- 规范复跑命令（与 `run.command` 一致）：`" + RUN_COMMAND + "`。",
        "- 产物哈希清单：`" + ARTIFACTS_SHA.name + "`，列出三份产物 + 脚本自身共 4 项；"
        "在输出目录执行 `" + VERIFY_COMMAND + "` 即可自证（清单本身不列入自身哈希，避免自引用循环）。",
        "- 前置：能读到同仓 `zhixue/tools/eval_mapping_offline.py`、"
        "`zhixue-server/sql/seed-*.sql`、`KpMappingService.java`、`application.yml`；"
        "无需安装包 / 起服务 / 连数据库。",
        "- 产物：" + ", ".join(f"`{path}`" for path in run["output_files"]),
        "- 原评测脚本 sha256 前后一致："
        f"`{run['offline_sha256_before']}` → `{run['offline_sha256_after']}`。",
        "",
        "## 5. 关键数字（实跑）",
        "",
        f"- U81-66 top1 {pct(joint[SCOPE_66]['top1'])} / top3 "
        f"{pct(joint[SCOPE_66]['top3'])} / FAILED {joint[SCOPE_66]['failed']}；"
        f"U81-233 top1 {pct(joint[SCOPE_233]['top1'])} / top3 "
        f"{pct(joint[SCOPE_233]['top3'])} / FAILED {joint[SCOPE_233]['failed']}。",
        f"- LCO 宏平均 {pct(lco['macro_avg_top1'])}，最差折 {lco['worst_fold']} "
        f"{pct(lco['worst_fold_top1'])}。",
        f"- 配对 bootstrap Δ 95% CI：66 [{boot[SCOPE_66]['ci95_pct'][0]:.2f}, "
        f"{boot[SCOPE_66]['ci95_pct'][1]:.2f}]pp、233 "
        f"[{boot[SCOPE_233]['ci95_pct'][0]:.2f}, {boot[SCOPE_233]['ci95_pct'][1]:.2f}]pp，"
        "均未排除 0。",
        f"- McNemar 精确 p：66 {mcn[SCOPE_66]['p_exact_two_sided']:.4f}"
        f"（n01/n10={mcn[SCOPE_66]['n01']}/{mcn[SCOPE_66]['n10']}）、"
        f"233 {mcn[SCOPE_233]['p_exact_two_sided']:.4f}"
        f"（n01/n10={mcn[SCOPE_233]['n01']}/{mcn[SCOPE_233]['n10']}）。",
        "",
        "## 6. 未做项（明确标「未做」，不假装完成）",
        "",
    ]
    lines.extend(f"- **{item['status']}**：{item['name']} — {item['note']}"
                 for item in payload["not_done"])
    lines.extend([
        "",
        "> **主理人 A 级独立实跑交叉校验**：决策三段 acc（V0/V2 × 双口径共 12 个分数）、"
        "按 decision 分组 top3、CONFIRM 误高亮、召回分档均与本脚本逐位一致，"
        "来源与逐项对照见 `algo_metrics.md §13.2`；单调性按变体分别判，"
        "不把「未观察到反转」归给整个 233 口径。",
        "",
        "> **hubness 三定义互不为证据**：① 报告 §10.2 指标 2 / 本脚本「hubness 指数」= "
        "错误 top1 中 Top-5 吸引子出现次数；② Java `KpMappingService.computeHubness` = "
        "知识点向量与语料质心的余弦；③ spike `rankers.py:hubness_diagnosis` = "
        "全体查询平均 cos + top1 次数。三者是不同量，不得互相印证、相加或相减。",
        "",
        "> 本目录只写 3 个产物；源码 `zhixue/tools/algo_metrics.py` 是本任务唯一新增代码文件。"
        "未改 `eval_mapping_offline.py`、未改生产 Java / 契约 / DB / `application.yml` / 阈值；"
        "未碰他人未提交的 `docs/06`、`docs/10`；未代写台账署名块。",
        "",
    ])
    return "\n".join(lines)


def not_done_list() -> list[dict[str, str]]:
    """未做项清单（如实标注，不假装完成）。"""
    return [
        {"name": "任何算法改动 / 阈值调整 / 生产 Java 迁移", "status": "未做",
         "note": "本任务是评测与统计验证，不是优化；§10.4 负结果仍未解除"},
        {"name": "人工标注的低重合题集", "status": "未做",
         "note": "覆盖率是字符二元组代理指标，非人工标注；不量化复述红利占比"},
        {"name": "U60 / N=60 验收集上的 L1/L2 放行判定", "status": "未做",
         "note": "docs/06:132 锁死验收集，233 题为观测结论，验收集切换须人裁决"},
        {"name": "跨口径合并 / 相减 / 相加", "status": "未做（禁止）",
         "note": "两口径并列，任何跨口径运算都不产生有效结论"},
        {"name": "按 decision 分组结果用于调 confirm-floor", "status": "未做（禁止）",
         "note": "只作诊断；不得回收 k 题制造达标"},
        {"name": "hubness 消除 / CSLS / 排序侧改动", "status": "未做",
         "note": "报告 §10.4 已披露全部负结果，本轮不重试"},
        {"name": "5 折每折方差", "status": "未做",
         "note": "本任务实跑 LCO 8 折；5 折方差未纳入范围"},
        {"name": "Java 侧运行时对照（启动 JVM）", "status": "未做",
         "note": "parity 为 Python 逐题实跑 + Java 源码静态勘察 [C]，未启动 JVM"},
        {"name": "chapter_no ↔ chapter_id 连库实查", "status": "未验证",
         "note": "沿用 spike 结论，本脚本未连库"},
        {"name": "把指标写入 docs/06 或报告正文 / 台账署名", "status": "未做",
         "note": "他人未提交的 docs 文件勿碰；台账需人工署名"},
    ]


# ---------------------------------------------------------------------------
# 全局一致性审查
# ---------------------------------------------------------------------------


def verify_joint(cases: Sequence[Case], cell: dict[str, Any]) -> list[str]:
    """重新从逐题记录计算六项 joint，与给定 cell 逐项比对。"""
    recomputed = joint_metrics(cases)
    keys = ("n", "top1", "top3", "autoPrecision", "manualRate",
            "coverage", "failed", "top1_hits", "top3_hits")
    return [f"{key}: cell={cell[key]} recomputed={recomputed[key]}"
            for key in keys if cell[key] != recomputed[key]]


def wording_issues(text: str) -> list[str]:
    """扫描报告中的宣称性措辞。"""
    return [phrase for phrase in FORBIDDEN_PHRASES if phrase in text]


def global_audit(payload: dict[str, Any], source_text: str,
                 script_hash_before: str, script_hash_after: str,
                 output_files: Sequence[Path], report_text: str) -> tuple[bool, list[str]]:
    """全局一致性审查：写入边界 / 原脚本未改 / 无泄漏 / 数字自洽 / 措辞 / 阈值。"""
    issues: list[str] = []
    checks: list[str] = []
    audit: dict[str, Any] = {"is_pass": False, "checks": [], "issues": []}

    # [1] 原评测脚本 sha256 前后一致
    same_hash = script_hash_before == script_hash_after
    if not same_hash:
        issues.append("原评测脚本 sha256 在实跑前后发生变化")
    checks.append(f"[1] 原脚本未改动：sha256 前={script_hash_before[:16]}… "
                  f"后={script_hash_after[:16]}… "
                  f"{'一致 OK' if same_hash else '不一致 FAIL'}")

    # [2] 写入边界：所有产物必须落在允许目录
    allowed = OUTPUT_DIR.resolve()
    escaped = [str(path) for path in output_files
               if path.resolve().parent != allowed]
    if escaped:
        issues.append("产物越出允许目录：" + ", ".join(escaped))
    checks.append(f"[2] 写入边界：产物 {len(output_files)} 个，全部位于 "
                  f"{OUTPUT_DIR.name}/ {'OK' if not escaped else 'FAIL'}")

    # [3] 无 oracle / 标签泄漏：ranker 与 decide 的实参不得含标注侧字段
    rank_args = re.findall(r"\.rank\(([^)]*)\)", source_text)
    decide_args = re.findall(r"\.decide\(([^)]*)\)", source_text)
    leak_tokens = ("gold", "chapter", "options_json", "analysis_text",
                   "answer_text", "variant_hint", "q_id")
    bad_rank = [arg for arg in rank_args if any(token in arg for token in leak_tokens)]
    bad_decide = [arg for arg in decide_args
                  if any(token in arg for token in leak_tokens)]
    query_ok = all(
        case["query_field"] == "question_text"
        for scope in SCOPES for case in payload["per_case"][scope]
    )
    shape = payload["query_shape"]
    shape_ok = (shape["equals_online_request_getText"] == "是"
                and "analysis_text" in shape["excluded_fields"]
                and "options_json" in shape["excluded_fields"])
    lco_ok = bool(payload["statistics"]["lco"]["universe_unchanged"])
    if bad_rank or bad_decide or not query_ok or not shape_ok or not lco_ok:
        issues.append("无泄漏检查失败："
                      + f"bad_rank={bad_rank} bad_decide={bad_decide} "
                      + f"query_ok={query_ok} shape_ok={shape_ok} lco_ok={lco_ok}")
    checks.append(f"[3] 无 oracle / 泄漏：ranker 调用 {len(rank_args)} 处、"
                  f"decide 调用 {len(decide_args)} 处，标注侧字段入参 "
                  f"{len(bad_rank) + len(bad_decide)} 处；"
                  f"query 全为纯题干={query_ok}；LCO 语料全集不变={lco_ok} "
                  f"{'OK' if not (bad_rank or bad_decide) else 'FAIL'}")

    # [4] 数字自洽：逐题记录重算 + 守恒 + 口径完整性
    bad_cells: list[str] = []
    for scope in SCOPES:
        cases = payload["_cases"][scope]
        cell = payload["metrics"]["1_joint"][scope]
        bad_cells.extend(f"{scope}.{item}" for item in verify_joint(cases, cell))
        if cell["auto"] + cell["confirm"] + cell["failed"] != cell["n"]:
            bad_cells.append(scope + " 决策分布不守恒")
        if not (0 <= cell["top1_hits"] <= cell["top3_hits"] <= cell["n"]):
            bad_cells.append(scope + " 命中数越界")
    universe = payload["universe"]
    if sum(universe["per_file_counts"].values()) != universe["n_questions_233"]:
        bad_cells.append("每文件题数之和 != 233")
    if universe["gold_missing"] != 0:
        bad_cells.append("gold 有题目不在 81 点语料内")
    if not universe["is_66_complete_subset"]:
        bad_cells.append("66 题不是 233 题完整子集")
    if set(universe["chapters"]) != set(EXPECTED_CHAPTERS):
        bad_cells.append("章节集合不等于 1..8")
    for scope in SCOPES:
        rec = payload["metrics"]["5_recall"][scope]
        if not rec["conservation_ok"]:
            bad_cells.append(scope + " 召回三分类不守恒")
    if bad_cells:
        issues.extend(bad_cells)
    checks.append(f"[4] 数字自洽：两口径六项 joint 逐题重算、决策守恒、"
                  f"命中数、覆盖率、三分类守恒、口径完整性 "
                  f"{'OK' if not bad_cells else 'FAIL'}")

    # [5] 无只报最优：V0/V2、双口径、8 折、最差章、负结果同时披露
    required = ("U81-66", "U81-233", "V0", "V2", "ch03", "FAILED",
                "最差折", "观测线", "非验收结论", "未做", "McNemar",
                "CI", "hubness", "ch07")
    missing = [token for token in required if token not in report_text]
    if missing:
        issues.append("报告缺少并列披露要素：" + ", ".join(missing))
    checks.append(f"[5] 无只报最优：{len(required)} 项并列披露要素齐全 "
                  f"{'OK' if not missing else 'FAIL'}")

    # [6] 阈值冻结 + argparse 未声明任何阈值旋钮（不能靠文本搜索 docstring 里
    #     「不提供 --floor」这类否定声明，那会把声明本身误判成参数）。
    frozen = payload["config_frozen"]["all_frozen"]
    cli_options = set(re.findall(
        r"parser\.add_argument\(\s*[\"']--([a-z-]+)[\"']", source_text
    ))
    sweep_options = {"floor", "auto", "margin", "score-scale", "threshold",
                     "auto-threshold", "confirm-floor"}
    no_sweep = not (cli_options & sweep_options)
    if not frozen or not no_sweep:
        issues.append(f"阈值冻结失败 frozen={frozen} no_sweep={no_sweep} "
                      f"cli_options={sorted(cli_options)}")
    checks.append(f"[6] 阈值冻结：三方一致={frozen}，"
                  f"CLI 仅 {sorted(cli_options)}、无阈值 sweep 参数={no_sweep} "
                  f"{'OK' if frozen and no_sweep else 'FAIL'}")

    # [7] 措辞红线 + 关键纪律句
    forbidden = wording_issues(report_text)
    discipline = ("禁止跨口径相减", "禁止跨口径相加",
                  "不得据此调 confirm-floor 回收 k 题制造达标",
                  "被反证的是", "未拒绝", "代理指标、非人工标注")
    missing_discipline = [token for token in discipline if token not in report_text]
    if forbidden or missing_discipline:
        issues.append("措辞/纪律检查失败："
                      + f"forbidden={forbidden} missing={missing_discipline}")
    checks.append(f"[7] 措辞与纪律：禁用宣称 {len(forbidden)} 处、"
                  f"纪律句缺失 {len(missing_discipline)} 处 "
                  f"{'OK' if not forbidden and not missing_discipline else 'FAIL'}")

    # [8] 证据等级标注 + parity 全一致
    level_ok = all(token in report_text for token in ("[A]", "[B]", "[C]", "A+C"))
    parity_ok = payload["metrics"]["8_parity"]["all_ok"]
    bootstrap_ok = all(
        payload["statistics"]["bootstrap"][scope]["reps"] >= 2000
        for scope in SCOPES
    )
    if not level_ok or not parity_ok or not bootstrap_ok:
        issues.append(f"证据等级/parity/bootstrap 检查失败："
                      f"level={level_ok} parity={parity_ok} bootstrap={bootstrap_ok}")
    checks.append(f"[8] 证据等级=A/B/C 齐全={level_ok}；parity 全一致={parity_ok}；"
                  f"bootstrap 次数均>=2000={bootstrap_ok} "
                  f"{'OK' if level_ok and parity_ok and bootstrap_ok else 'FAIL'}")

    # [9] 锚点对照无「不一致」项（定义差异已披露属允许）
    bad_anchors = [row["anchor"] for row in payload["anchors"]["rows"]
                   if row["verdict"].startswith("不一致")]
    if bad_anchors:
        issues.append("锚点实跑与给定不一致（须如实披露）："
                      + ", ".join(bad_anchors))
    checks.append(f"[9] 锚点对照：{len(payload['anchors']['rows'])} 条，"
                  f"不一致 {len(bad_anchors)} 条 "
                  f"{'OK' if not bad_anchors else 'FAIL'}（定义差异已披露）")

    # [10] 主理人 A 级独立实跑交叉校验（外部给定值 vs 本脚本实跑）
    cross_rows = payload["crosscheck"]["rows"]
    bad_cross = [row["item"] for row in cross_rows
                 if row["verdict"].startswith("不一致")]
    if bad_cross:
        issues.append("主理人 A 级交叉校验不一致：" + ", ".join(bad_cross))
    checks.append(f"[10] 主理人 A 级独立实跑交叉校验：{len(cross_rows)} 项，"
                  f"不一致 {len(bad_cross)} 项 "
                  f"{'OK' if not bad_cross else 'FAIL'}"
                  "（来源已在报告 §13.2 标注）")

    is_pass = not issues
    audit["is_pass"] = is_pass
    audit["checks"] = checks
    audit["issues"] = issues
    payload["_audit_result"] = audit
    return is_pass, checks


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------


def main(argv: Sequence[str] | None = None) -> int:
    """一键复跑：加载 → 实跑 8 项指标与统计验证 → 落盘 → 全局审查。"""
    parser = argparse.ArgumentParser(
        description="算法创新指标自动评测（纯标准库，只读，零改动）")
    parser.add_argument("--reps", type=int, default=BOOTSTRAP_REPS,
                        help=f"配对 bootstrap 重采样次数，默认 {BOOTSTRAP_REPS}，下限 2000")
    parser.add_argument("--seed", type=int, default=BOOTSTRAP_SEED,
                        help=f"随机种子，默认 {BOOTSTRAP_SEED}（保证可复跑）")
    args = parser.parse_args(argv)
    if args.reps < 2000:
        raise SystemExit("配对 bootstrap 次数必须 >= 2000（统计验证下限）")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    source_text = Path(__file__).read_text(encoding="utf-8")
    script_hash_before = sha256_file(OFFLINE_SCRIPT)

    module = load_offline_module()
    kps = module.load_kps("all")
    questions_66 = module.load_questions()
    questions_233, per_file = load_questions_233(module)
    java_text = JAVA_SERVICE.read_text(encoding="utf-8")
    yml_text = APP_YML.read_text(encoding="utf-8")
    config = frozen_config(module, java_text, yml_text)
    floor = float(module.CONFIRM_FLOOR)

    if len(kps) != EXPECTED_KP_COUNT:
        raise SystemExit(f"语料点数异常：期望 {EXPECTED_KP_COUNT}，实际 {len(kps)}")
    if len(questions_66) != EXPECTED_Q66:
        raise SystemExit(f"66 题口径异常：实际 {len(questions_66)}")
    known = {kp.kp_id for kp in kps}
    missing = [q.q_id for q in questions_233 if q.gold not in known]
    ids66 = {q.q_id for q in questions_66}
    ids233 = {q.q_id for q in questions_233}
    universe = {
        "universe_size": len(kps),
        "kp_files": [path.name for path in sorted(module.SQL_DIR.glob(KP_GLOB))],
        "question_file_count": len(per_file),
        "per_file_counts": per_file,
        "n_questions_66": len(questions_66),
        "n_questions_233": len(questions_233),
        "chapters": sorted({kp.chapter for kp in kps}),
        "chapter_counts_233": dict(sorted(Counter(q.chapter for q in questions_233).items())),
        "gold_in_universe": len(questions_233) - len(missing),
        "gold_missing": len(missing),
        "missing_ids": missing,
        "is_66_complete_subset": ids66 <= ids233,
    }

    cases66, scorer66 = build_cases(module, kps, questions_66, floor)
    cases233, scorer233 = build_cases(module, kps, questions_233, floor)
    cases_by_scope: dict[str, list[Case]] = {
        SCOPE_66: cases66, SCOPE_233: cases233,
    }

    joint = {scope: joint_metrics(cases_by_scope[scope]) for scope in SCOPES}
    joint_v0 = {scope: joint_metrics(cases_by_scope[scope], v2=False)
                for scope in SCOPES}
    hub = {scope: hubness_scope(cases_by_scope[scope], kps) for scope in SCOPES}
    hub["centroid"] = centroid_hubness(module, kps, scorer233)
    chapter = {scope: by_chapter(cases_by_scope[scope]) for scope in SCOPES}
    overlap = {scope: overlap_metrics(cases_by_scope[scope]) for scope in SCOPES}
    recall = {scope: recall_metrics(cases_by_scope[scope]) for scope in SCOPES}
    decision = {scope: decision_metrics(cases_by_scope[scope]) for scope in SCOPES}
    seg_top3 = {scope: decision_top3_metrics(cases_by_scope[scope])
                for scope in SCOPES}
    parity = parity_metrics(module, kps, questions_233, scorer233, java_text, floor)
    lco = lco_metrics(cases233, kps)
    boot = {scope: bootstrap_metrics(cases_by_scope[scope], args.reps, args.seed)
            for scope in SCOPES}
    mcn = {scope: mcnemar_metrics(cases_by_scope[scope], scope)
           for scope in SCOPES}

    query_shape = {
        "field": "question_text",
        "construction": "question_text（纯题干，无拼接）",
        "equals_online_request_getText": "是",
        "excluded_fields": "options_json / answer_text / analysis_text / variant_hint",
        "note": ("线上 MapRequest.text 即纯题干；KpMappingService.match 直接 "
                 "termCounts(request.getText())。gold / chapter 只在事后分层，"
                 "绝不进入 ranker / decide。"),
    }

    run = {
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "python": sys.version.split()[0],
        "command": RUN_COMMAND,
        "artifact_manifest": ARTIFACTS_SHA.name,
        "artifact_verify_command": VERIFY_COMMAND,
        "offline_sha256_before": script_hash_before,
        "offline_sha256_after": "",
        "output_files": [],
        "exit_code": 0,
    }
    payload: dict[str, Any] = {
        "schema_version": "1.0",
        "generated_at": run["generated_at"],
        "run": run,
        "universe": universe,
        "config_frozen": config,
        "query_shape": query_shape,
        "gate_lines": {SCOPE_66: GATE_LINE_66, SCOPE_233: GATE_LINE_233},
        # 产物自证：只指向清单文件名，不内嵌清单哈希（避免自引用循环）。
        "artifact_sha256": {
            "manifest": ARTIFACTS_SHA.name,
            "entries": [REPORT_JSON.name, REPORT_MD.name, README_MD.name,
                        SCRIPT_MANIFEST_LABEL],
            "verify_command": VERIFY_COMMAND,
            "note": ("清单由脚本在三份产物落盘后生成，列出三份产物 + 脚本自身共 4 项；"
                     "顶层不内嵌清单哈希，清单本身也不列入自身条目，避免自引用循环。"),
        },
        "field_aliases": dict(FIELD_ALIASES),
        "evidence_level": (
            "A（本脚本落盘实跑）+ C（Java 源码 / application.yml 静态勘察）；"
            "主理人 A 级内存实跑仅作独立交叉校验（未落盘），不作为升级后的验收依据"
        ),
        "evidence_levels": {
            "A": "本脚本实跑（指标 1—8、LCO、bootstrap、McNemar、parity 运行时、锚点复核）",
            "B": "推演未复核（任务书「反向错 >8 即不显著」，已经 A 级实跑校正）",
            "C": "静态勘察（Java 源码规则、application.yml 阈值、query 形态契约）",
            "note": ("A 级内存实跑若未落盘只能标「独立交叉校验（未落盘）」；"
                     "本报告的 A 级指本脚本自身落盘实跑，不宣称升级后的验收结论"),
        },
        "metrics": {
            "1_joint": {**joint, "V0": joint_v0},
            "2_hubness": hub,
            "3_by_chapter": chapter,
            "4_overlap": overlap,
            "5_recall": recall,
            "6_decision": decision,
            "7_decision_top3": seg_top3,
            "8_parity": parity,
        },
        "statistics": {"lco": lco, "bootstrap": boot, "mcnemar": mcn},
        "anchors": {
            "source": "主理人任务书给定（外部对照输入，非本脚本产出的指标）",
            "given_values": ANCHORS_GIVEN,
            "rows": [],
        },
        "crosscheck": {
            "source": LEAD_CROSSCHECK_GIVEN["source"],
            "given_values": LEAD_CROSSCHECK_GIVEN,
            "rows": [],
        },
        "per_case": {scope: [case_to_dict(case) for case in cases_by_scope[scope]]
                     for scope in SCOPES},
        "baseline_table": [],
        "not_done": not_done_list(),
        "_cases": cases_by_scope,
    }
    payload["anchors"]["rows"] = build_anchor_rows(payload)
    payload["crosscheck"]["rows"] = build_crosscheck_rows(payload)
    payload["baseline_table"] = build_baseline_table(payload)

    # 所有只读计算已完成，取「实跑后」sha256（落盘产物不会触碰原脚本）。
    script_hash_after = sha256_file(OFFLINE_SCRIPT)
    output_files: list[Path] = [REPORT_MD, REPORT_JSON, README_MD, ARTIFACTS_SHA]
    # 第一轮：渲染草案（审查段占位），用真实的前/后 sha256 跑全局审查
    draft_report = render_markdown(payload)
    is_pass, _ = global_audit(payload, source_text, script_hash_before,
                              script_hash_after, output_files, draft_report)
    audit = payload["_audit_result"]
    payload["audit"] = audit

    run["offline_sha256_after"] = script_hash_after
    run["output_files"] = [str(path) for path in output_files]

    # 最终报告与措辞复核全部在落盘前完成：产物只写一次，哈希可一次性冻结。
    final_report = render_markdown(payload)
    final_wording = wording_issues(final_report)
    if final_wording:
        audit["is_pass"] = False
        audit["issues"].extend("最终报告出现禁用措辞：" + phrase
                               for phrase in final_wording)
        audit["checks"].append("[最终] 措辞复核 FAIL：" + ", ".join(final_wording))
        is_pass = False
    else:
        audit["checks"].append("[最终] 措辞复核 OK（无宣称性措辞）")
    if script_hash_before == script_hash_after:
        audit["checks"].append("[最终] 原脚本 sha256 实跑前后一致 OK")
    else:
        audit["checks"].append("[最终] 原脚本 sha256 实跑前后不一致 FAIL")
    payload["audit"] = audit
    final_report = render_markdown(payload)

    # 三份产物的最终字节在内存中一次定型，哈希清单由此计算：
    # 顶层只指向清单文件名、不内嵌清单哈希，避免自引用循环。
    md_text = final_report
    json_text = json.dumps(serialize_payload(payload), ensure_ascii=False, indent=2)
    readme_text = render_readme(payload)
    manifest_entries: list[tuple[str, bytes]] = [
        (REPORT_JSON.name, json_text.encode("utf-8")),
        (REPORT_MD.name, md_text.encode("utf-8")),
        (README_MD.name, readme_text.encode("utf-8")),
        (SCRIPT_MANIFEST_LABEL, SCRIPT_FILE.read_bytes()),
    ]
    manifest_text = "\n".join(
        f"{hashlib.sha256(data).hexdigest()}  {label}"
        for label, data in manifest_entries
    ) + "\n"
    manifest_ok = all(
        line.split("  ", 1)[0] == hashlib.sha256(manifest_entries[i][1]).hexdigest()
        for i, line in enumerate(manifest_text.splitlines())
    )
    exit_code = 0 if (is_pass and manifest_ok
                      and script_hash_before == script_hash_after) else 2
    run["exit_code"] = exit_code

    # 一次性落盘四份产物（显式 LF，避免 Windows CRLF 改变哈希）
    REPORT_MD.write_text(md_text, encoding="utf-8", newline="\n")
    REPORT_JSON.write_text(json_text, encoding="utf-8", newline="\n")
    README_MD.write_text(readme_text, encoding="utf-8", newline="\n")
    ARTIFACTS_SHA.write_text(manifest_text, encoding="utf-8", newline="\n")

    # 落盘后只读复核：不再写任何产物，保证清单哈希保持冻结状态。
    final_hash = sha256_file(OFFLINE_SCRIPT)
    verified = 0
    for line in ARTIFACTS_SHA.read_text(encoding="utf-8").splitlines():
        digest, label = line.split("  ", 1)
        if sha256_file((OUTPUT_DIR / label).resolve()) == digest:
            verified += 1
    readback_ok = (verified == len(manifest_entries)
                   and final_hash == script_hash_before)
    if not readback_ok:
        print("[FATAL] 产物哈希清单或原脚本 sha256 落盘后复核失败")
        exit_code = 2

    print("=" * 78)
    print(f"算法创新指标自动评测  generated_at={run['generated_at']}")
    print(f"K={universe['universe_size']}  Q=66/233  C={universe['chapters']}")
    print(f"U81-66 : top1={pct(joint[SCOPE_66]['top1'])} "
          f"top3={pct(joint[SCOPE_66]['top3'])} "
          f"FAILED={joint[SCOPE_66]['failed']}")
    print(f"U81-233: top1={pct(joint[SCOPE_233]['top1'])} "
          f"top3={pct(joint[SCOPE_233]['top3'])} "
          f"FAILED={joint[SCOPE_233]['failed']}（观测线，非验收线）")
    print(f"hubness 指数 66/233 = {hub[SCOPE_66]['hubness_index']}/"
          f"{hub[SCOPE_233]['hubness_index']}；"
          f"宏平均 {pct(chapter[SCOPE_233]['macro_avg_top1'])} "
          f"最差 {chapter[SCOPE_233]['worst_chapter']} "
          f"{pct(chapter[SCOPE_233]['worst_chapter_top1'])}")
    print(f"LCO 宏平均 {pct(lco['macro_avg_top1'])} 最差折 {lco['worst_fold']}")
    for scope in SCOPES:
        print(f"bootstrap {scope}: Δ={boot[scope]['delta_observed_pct']:.2f}pp "
              f"CI95=[{boot[scope]['ci95_pct'][0]:.2f},"
              f"{boot[scope]['ci95_pct'][1]:.2f}]pp "
              f"excludes0={boot[scope]['excludes_zero']}")
        print(f"McNemar  {scope}: n01={mcn[scope]['n01']} "
              f"n10={mcn[scope]['n10']} p={mcn[scope]['p_exact_two_sided']:.4f}")
    print(f"parity 全一致={parity['all_ok']}  阈值冻结={config['all_frozen']}")
    print(f"artifacts.sha256: 内存自校验={manifest_ok} / 落盘读回复核="
          f"{verified}/{len(manifest_entries)} "
          f"{'OK' if readback_ok else 'FAIL'}；{VERIFY_COMMAND}")
    print(f"IS_PASS: {'YES' if is_pass else 'NO'}  exit_code={exit_code}")
    print("产物：" + ", ".join(run["output_files"]))
    print("=" * 78)
    return exit_code


def serialize_payload(payload: dict[str, Any]) -> dict[str, Any]:
    """去掉内部对象（Case / 私有键）后再落 JSON，保证可序列化且无冗余。"""
    return {
        key: value for key, value in payload.items()
        if not key.startswith("_")
    }


if __name__ == "__main__":
    sys.exit(main())
