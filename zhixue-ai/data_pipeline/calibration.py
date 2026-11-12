#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OCR 混淆参数拟合：训练对 → 字符混淆矩阵/编辑率 → 增广采样（闭环）。

为什么需要
----------
`dataset_builder` 的默认增广用人工整理的形近字表（CONFUSABLE），只能覆盖
已知错误模式。真实 OCR 训练对（`ocr/text` 与人工校对后的 `target`）携带了
本数据集、本扫描质量下的真实错误分布，应当反过来校准增广器：

    训练对 → 对齐 → 极大似然估计 + 拉普拉斯平滑 → 混淆矩阵 → 增广采样

数学定义
--------
1. 对齐：`difflib.SequenceMatcher` 的 opcode 给出三类编辑
   - replace：字符替换对 (observed, expected)；
   - insert：truth 有、OCR 无 → 漏识（delete 率）；
   - delete：OCR 有、truth 无 → 多识（insert 率）。
2. 平滑：$P(w\\mid c)=\\frac{n_{cw}+\\alpha}{\\sum_{w'}n_{cw'}+\\alpha(|V|+1)}$，
   $\\alpha=1$ 即加一平滑；$|V|+1$ 的 +1 留给未登录错误字符。
3. 编辑率（贝叶斯收缩）：直接用 $\\hat p=n/N$ 会在少量干净样本上得到 0，
   增广退化为"不扰动"。因此用 Beta 先验收缩到默认编辑率先验：
   $\\hat p_k=\\frac{n_k+\\pi_k\\cdot m}{N+m}$，$m$ 为等效先验样本量（默认 100 字符），
   $\\pi=(0.02,0.02,0.04)$ 为内置增广器的默认强度；样本越多先验影响越小。

用法（在 zhixue-ai 目录执行）
------------------------------
    python -m data_pipeline.calibration --input data/ocr_pairs.jsonl \
        --out data/calibration/confusion.json
"""
from __future__ import annotations

import argparse
import difflib
import json
import random
import sys
from pathlib import Path


def align_errors(predicted: str, truth: str) -> tuple[list[tuple[str, str]], int, int]:
    """对齐 OCR 输出与人工校对文本。

    返回 (替换对 [(observed, expected)], 漏识字符数, 多识字符数)。
    replace 长度不等时，余量分别计入漏识/多识。
    """
    substitutions: list[tuple[str, str]] = []
    dropped = added = 0
    matcher = difflib.SequenceMatcher(a=predicted, b=truth, autojunk=False)
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == "replace":
            for observed, expected in zip(predicted[i1:i2], truth[j1:j2]):
                if observed != expected:
                    substitutions.append((observed, expected))
            if (i2 - i1) > (j2 - j1):
                added += (i2 - i1) - (j2 - j1)
            elif (j2 - j1) > (i2 - i1):
                dropped += (j2 - j1) - (i2 - i1)
        elif tag == "delete":      # predicted 有、truth 无 → OCR 多识
            added += i2 - i1
        elif tag == "insert":      # truth 有、predicted 无 → OCR 漏识
            dropped += j2 - j1
    return substitutions, dropped, added


DEFAULT_RATES = {"p_delete": 0.02, "p_insert": 0.02, "p_substitute": 0.04}
DEFAULT_PRIOR_STRENGTH = 100.0


def fit(pairs: list[tuple[str, str]], *, alpha: float = 1.0,
        prior_strength: float = DEFAULT_PRIOR_STRENGTH,
        prior: dict[str, float] | None = None) -> dict:
    """从 (OCR 文本, 校对文本) 训练对估计混淆矩阵与三类编辑率。

    编辑率带 Beta 先验收缩（prior_strength 等效字符数），避免少量干净样本
    把扰动概率估成 0；样本量足够时先验自动被数据淹没。
    """
    prior = dict(prior or DEFAULT_RATES)
    substitutions: list[tuple[str, str]] = []
    dropped = added = chars = 0
    for predicted, truth in pairs:
        subs, d, a = align_errors(predicted or "", truth or "")
        substitutions.extend(subs)
        dropped += d
        added += a
        chars += len(truth or "")
    counts: dict[str, dict[str, int]] = {}
    for observed, expected in substitutions:
        row = counts.setdefault(expected, {})
        row[observed] = row.get(observed, 0) + 1
    vocab = {observed for observed, _ in substitutions}
    confusion: dict[str, dict[str, float]] = {}
    for expected, row in sorted(counts.items()):
        total = sum(row.values())
        denom = total + alpha * (len(vocab) + 1)
        confusion[expected] = {
            observed: round((count + alpha) / denom, 6)
            for observed, count in sorted(row.items())
        }
    denominator = chars + prior_strength
    rates = {
        name: (value + prior[name] * prior_strength) / denominator
        for name, value in (("p_delete", dropped), ("p_insert", added),
                            ("p_substitute", len(substitutions)))
    }
    return {
        "alpha": alpha,
        "priorStrength": prior_strength,
        "prior": prior,
        "pairCount": len(pairs),
        "charCount": chars,
        "substitutionCount": len(substitutions),
        "errorRates": {name: round(value, 6) for name, value in rates.items()},
        "confusion": confusion,
    }


def make_sampler(confusion: dict[str, dict[str, float]], rng: random.Random):
    """返回 sample(char) → 按 P(wrong|char) 采样的替换字符；无分布返回 None。"""
    def sample(char: str) -> str | None:
        row = confusion.get(char)
        if not row:
            return None
        return rng.choices(list(row), weights=list(row.values()), k=1)[0]
    return sample


def save_model(model: dict, path: Path) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(model, ensure_ascii=False, indent=1), encoding="utf-8")
    return path


def load_model(path: Path) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="OCR 混淆矩阵拟合（真实训练对 → 增广参数）")
    parser.add_argument("--input", required=True, help="JSONL，字段 input(OCR)/target(校对)")
    parser.add_argument("--out", required=True)
    parser.add_argument("--alpha", type=float, default=1.0, help="拉普拉斯平滑系数")
    args = parser.parse_args(argv)

    pairs: list[tuple[str, str]] = []
    with Path(args.input).open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            record = json.loads(line)
            if record.get("input") is not None and record.get("target") is not None:
                pairs.append((record["input"], record["target"]))
    if not pairs:
        raise SystemExit(f"无有效训练对（需要 input/target 字段）: {args.input}")
    model = fit(pairs, alpha=args.alpha)
    print(json.dumps({k: v for k, v in model.items() if k != "confusion"},
                     ensure_ascii=False, indent=2))
    print(f"混淆字符 {len(model['confusion'])} 个 → {save_model(model, Path(args.out))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
