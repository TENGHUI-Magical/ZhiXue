#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""训练集构建器：去重 → 异常清洗 → 分层抽样 → 蒙特卡洛增广 → 标准格式输出。

数学内核
--------
1. SimHash 近重复检测（Charikar 2002）：字符二元组为特征、词频为权，
   64 位分段签名；海明距离 ≤ 6 视为候选，再用精确 Jaccard 复核：
   $J(A,B)=|B_g(A)\\cap B_g(B)|/|B_g(A)\\cup B_g(B)| \\ge 0.85$ 判重复。
   复杂度：签名 O(Σ|d|)；候选对按块向量化 XOR + popcount，
   峰值内存 O(block·n) 而非 O(n²)，不随样本数平方膨胀。
2. IQR 异常清洗：字段 x 的界为 $[Q_1-1.5\\,\\mathrm{IQR},\\ Q_3+1.5\\,\\mathrm{IQR}]$。
3. 分层抽样：按标签分组内先 shuffle 再按比例切分（最大余数法配平整数配额），
   保证 train/val/test 标签分布一致（避免分布漂移）。
4. 蒙特卡洛增广（OCR 后处理训练对）：对干净文本按几何分布施加
   删字 p_d / 插字 p_i / 形近替换 p_s 三种算子，生成 (损坏文本, 干净文本)
   序列对；确定性由种子保证，可复现。

用法（在 zhixue-ai 目录执行）：
    python -m data_pipeline.dataset_builder --input data/crawled.jsonl \
        --out-dir data/datasets --label-key source --augment 5000
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import random
import sys
import unicodedata
from pathlib import Path

import numpy as np

from data_pipeline.calibration import make_sampler

# ---------- 文本归一化与二元组（与 app/services/vector_index._bigram_counts 同口径） ----------

def _normalize(text: str) -> str:
    return "".join(ch for ch in (text or "").lower()
                   if unicodedata.category(ch)[0] not in ("P", "Z", "S"))


def bigram_set(text: str) -> set[str]:
    clean = _normalize(text)
    if len(clean) < 2:
        return {clean} if clean else set()
    return {clean[i:i + 2] for i in range(len(clean) - 1)}


def bigram_counts(text: str) -> dict[str, int]:
    clean = _normalize(text)
    if len(clean) < 2:
        return {clean: 1} if clean else {}
    counts: dict[str, int] = {}
    for i in range(len(clean) - 1):
        gram = clean[i:i + 2]
        counts[gram] = counts.get(gram, 0) + 1
    return counts


# ---------- 1. SimHash 去重 ----------

def simhash(text: str) -> int:
    """加权 SimHash：Σ count(g)·sign_64(hash(g)) 逐位取符号。"""
    counts = bigram_counts(text)
    bits = [0] * 64
    for gram, weight in counts.items():
        digest = hashlib.blake2b(gram.encode("utf-8"), digest_size=8).digest()
        value = int.from_bytes(digest, "big")
        for b in range(64):
            bits[b] += weight if (value >> b) & 1 else -weight
    signature = 0
    for b in range(64):
        if bits[b] > 0:
            signature |= 1 << b
    return signature


_POPCOUNT_TABLE = np.array([bin(i).count("1") for i in range(256)], dtype=np.uint8)


def _stable_bucket(text: str) -> int:
    """跨进程稳定的分桶：不用内置 hash()（受 PYTHONHASHSEED 随机化，换进程即漂移）。"""
    digest = hashlib.blake2b(text.encode("utf-8"), digest_size=4).digest()
    return int.from_bytes(digest, "big") % 1000


def _near_duplicate_pairs(signatures: np.ndarray, max_hamming: int = 6,
                          block: int = 256) -> list[tuple[int, int]]:
    """分块枚举海明距离 ≤ max_hamming 的候选对 (i, j)，i < j。

    只保留块内上三角区域，峰值内存 O(block·n·8B) 而非 O(n²)：
    1 万条记录时距离矩阵需 100MB，分块 256 只需约 20MB（且不再存整张矩阵）。
    """
    n = signatures.shape[0]
    if n < 2:
        return []
    pairs: list[tuple[int, int]] = []
    for start in range(0, n, block):
        stop = min(n, start + block)
        xor = signatures[start:stop, None] ^ signatures[None, start:]
        bits = _POPCOUNT_TABLE[
            xor.view(np.uint8).reshape(stop - start, n - start, 8)].sum(axis=2)
        rows, cols = np.nonzero(bits <= max_hamming)
        for row, col in zip(rows.tolist(), cols.tolist()):
            i, j = start + row, start + col
            if j > i:
                pairs.append((i, j))
    return pairs


def dedup(records: list[dict], *, threshold: float = 0.85,
          max_hamming: int = 6) -> tuple[list[dict], list[dict]]:
    """SimHash 候选 + 精确 Jaccard 复核的近重复去重。

    海明距离 ≤ 6 视为候选（随机无关文本对期望距离 ≈ 32，6 已高度选择性）；
    Jaccard ≥ 0.85 判重复（覆盖 OCR 单字符噪声场景，实测 1 字扰动 ≈ 0.87）。

    返回 (保留, 重复)。保留规则：同组重复中保留 text 最长者（信息量最大），
    其余移除；精确内容（哈希相同）直接归组。
    """
    if len(records) <= 1:
        return list(records), []
    digests = [hashlib.sha256(_normalize(r.get("text", "")).encode("utf-8")).hexdigest()
               for r in records]
    representative: dict[str, int] = {}
    for i, digest in enumerate(digests):
        representative.setdefault(digest, i)

    parent = list(range(len(records)))

    def find(i: int) -> int:
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    def union(i: int, j: int) -> None:
        parent[find(i)] = find(j)

    # 精确重复（归一化后同文）先归组：同组文本完全相同，后续近重复只需看代表
    for i, digest in enumerate(digests):
        if representative[digest] != i:
            union(i, representative[digest])

    # 近重复只在「非空文本的代表记录」之间枚举：若对全部 n 条枚举，
    # k 条同文（或空文本，SimHash 全 0）会产出 C(k,2) 个候选对，
    # 内存重新退化为 O(n²)——这正是分块枚举要消除的情形。
    candidates = [i for i in sorted(representative.values())
                  if bigram_set(records[i].get("text", ""))]
    if len(candidates) >= 2:
        hashes = np.array([simhash(records[i].get("text", "")) for i in candidates],
                          dtype=np.uint64)
        grams = {i: bigram_set(records[i].get("text", "")) for i in candidates}
        for a, b in _near_duplicate_pairs(hashes, max_hamming=max_hamming):
            i, j = candidates[a], candidates[b]
            if find(i) == find(j):
                continue
            left, right = grams[i], grams[j]
            jaccard = len(left & right) / len(left | right)
            if jaccard >= threshold:
                union(i, j)

    groups: dict[int, list[int]] = {}
    for i in range(len(records)):
        groups.setdefault(find(i), []).append(i)
    kept, removed = [], []
    for members in groups.values():
        best = max(members, key=lambda i: len(records[i].get("text", "")))
        kept.append(records[best])
        removed.extend(records[i] for i in members if i != best)
    # 保持原相对顺序
    keep_ids = {id(r) for r in kept}
    ordered = [r for r in records if id(r) in keep_ids]
    return ordered, removed


# ---------- 2. IQR 异常清洗 ----------

def iqr_filter(records: list[dict], *, field: str = "textLen",
               k: float = 1.5) -> tuple[list[dict], list[dict]]:
    """四分位距准则清洗数值字段；字段缺失的记录保留（无证据不剔除）。"""
    values = [r.get(field) for r in records if isinstance(r.get(field), (int, float))]
    if len(values) < 4:
        return list(records), []
    q1, q3 = np.percentile(values, [25, 75])
    iqr = q3 - q1
    low, high = q1 - k * iqr, q3 + k * iqr
    kept, outliers = [], []
    for r in records:
        v = r.get(field)
        if isinstance(v, (int, float)) and (v < low or v > high):
            outliers.append(r)
        else:
            kept.append(r)
    return kept, outliers


# ---------- 3. 分层抽样 ----------

def stratified_split(records: list[dict], *, label_key: str = "label",
                     ratios: tuple[float, float, float] = (0.8, 0.1, 0.1),
                     seed: int = 42) -> tuple[list[dict], list[dict], list[dict]]:
    """组内 shuffle + 最大余数法配额；无标签记录归入 "UNKNOWN" 层。"""
    if abs(sum(ratios) - 1.0) > 1e-9 or any(r < 0 for r in ratios):
        raise ValueError(f"ratios 必须非负且和为 1，得到 {ratios}")
    groups: dict[str, list[dict]] = {}
    for r in records:
        groups.setdefault(str(r.get(label_key, "UNKNOWN")), []).append(r)
    train, val, test = [], [], []
    for label in sorted(groups):
        members = sorted(groups[label], key=lambda r: json.dumps(r, ensure_ascii=False,
                                                                 sort_keys=True))
        random.Random(seed + _stable_bucket(label)).shuffle(members)
        n = len(members)
        raw = [r * n for r in ratios]
        counts = [int(x) for x in raw]
        order = sorted(range(3), key=lambda i: raw[i] - counts[i], reverse=True)
        for i in order:  # 最大余数法补齐
            if sum(counts) < n:
                counts[i] += 1
        train.extend(members[:counts[0]])
        val.extend(members[counts[0]:counts[0] + counts[1]])
        test.extend(members[counts[0] + counts[1]:])
    return train, val, test


# ---------- 4. 蒙特卡洛增广（OCR 后处理训练对） ----------

# 常见 OCR 形近混淆对（低分辨率扫描 + 衬线字体的典型错误模式）
CONFUSABLE = {
    "0": "oO", "o": "0", "1": "lI", "l": "1", "I": "1", "|": "1",
    "rn": "m", "m": "rn", "vv": "w", "w": "vv", "cl": "d", "nn": "m",
    "己": "已巳", "已": "己巳", "巳": "已己", "未": "末", "末": "未",
    "土": "士", "士": "土", "日": "曰", "曰": "日", "干": "千于",
    "千": "干于", "子": "子", "析": "折", "折": "析", "使": "便",
    "便": "使", "锻": "锹", "简": "簿", "练": "炼", "炼": "练",
}
_INSERT_CHARS = "。，、；：mn土士日干l1o0"


def corrupt_text(text: str, rng: random.Random, *, p_delete: float = 0.02,
                 p_insert: float = 0.02, p_substitute: float = 0.04,
                 sampler=None) -> str:
    """字符级扰动：独立伯努利算子 删除/插入/形近替换。

    E[编辑数] = |text|·(p_d+p_i+p_s)，默认 8%，模拟中等质量扫描件 OCR 输出。
    sampler 为校准后的混淆采样器（`calibration.make_sampler`）；为 None 时
    退回内置 CONFUSABLE 表（默认行为与旧版逐位一致）。
    """
    out: list[str] = []
    i = 0
    while i < len(text):
        ch = text[i]
        u = rng.random()
        if u < p_delete:
            i += 1
            continue
        if u < p_delete + p_insert:
            out.append(rng.choice(_INSERT_CHARS))
            out.append(ch)
            i += 1
            continue
        if u < p_delete + p_insert + p_substitute:
            pair = text[i:i + 2] if i + 1 < len(text) else ch
            options = CONFUSABLE.get(pair)
            if options:
                out.append(rng.choice(options))
                i += 2
                continue
            if sampler is not None:
                replacement = sampler(ch)
                out.append(ch if replacement is None else replacement)
                i += 1
                continue
            options = CONFUSABLE.get(ch)
            out.append(rng.choice(options) if options else ch)
            i += 1
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def monte_carlo_augment(records: list[dict], n: int, *, seed: int = 42,
                        label_key: str = "label",
                        calibration: dict | None = None) -> list[dict]:
    """自举采样 + 字符级蒙特卡洛扰动，产出 (corrupt, clean) 序列对。

    calibration 为 `calibration.fit` 的输出：有则用拟合出的编辑率与混淆矩阵
    采样（真实 OCR 错误分布），无则用内置默认参数（向后兼容）。
    用途：OCR 后处理纠错模型的监督训练数据；synthetic=true 标记来源，
    下游入库前必须人工抽检（AGENTS.md 红线 1）。
    """
    if n <= 0 or not records:
        return []
    rng = random.Random(seed)
    pool = [r for r in records if r.get("text")]
    if not pool:
        return []
    rates = (calibration or {}).get("errorRates", {})
    p_delete = rates.get("p_delete", 0.02)
    p_insert = rates.get("p_insert", 0.02)
    p_substitute = rates.get("p_substitute", 0.04)
    sampler = make_sampler(calibration["confusion"], rng) if calibration else None
    augmented = []
    for i in range(n):
        base = pool[int(rng.random() * len(pool))]
        clean = base["text"]
        record = {
            "id": f"synth-{seed}-{i}",
            "input": corrupt_text(clean, rng, p_delete=p_delete, p_insert=p_insert,
                                  p_substitute=p_substitute, sampler=sampler),
            "target": clean,
            "label": base.get(label_key, "SYNTHETIC"),
            "synthetic": True,
            "source": "monte-carlo",
        }
        if calibration:
            record["calibrated"] = True
        augmented.append(record)
    return augmented


# ---------- 5. 输出 ----------

def write_outputs(name: str, records: list[dict], out_dir: Path,
                  formats: tuple[str, ...] = ("jsonl", "csv")) -> list[Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    written = []
    if not records:
        return written
    if "jsonl" in formats:
        path = out_dir / f"{name}.jsonl"
        with path.open("w", encoding="utf-8") as handle:
            for r in records:
                handle.write(json.dumps(r, ensure_ascii=False) + "\n")
        written.append(path)
    if "csv" in formats:
        path = out_dir / f"{name}.csv"
        keys = sorted({k for r in records for k in r})
        with path.open("w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=keys, extrasaction="ignore")
            writer.writeheader()
            writer.writerows(records)
        written.append(path)
    if "parquet" in formats:
        try:
            import pandas as pd
            path = out_dir / f"{name}.parquet"
            pd.DataFrame(records).to_parquet(path, index=False)
            written.append(path)
        except ImportError:
            print("[跳过] parquet 需要 pandas + pyarrow，当前环境未安装")
    return written


def build(input_paths: list[Path], out_dir: Path, *, label_key: str = "source",
          dedup_threshold: float = 0.90, augment: int = 0, seed: int = 42,
          ratios: tuple[float, float, float] = (0.8, 0.1, 0.1),
          calibration: dict | None = None) -> dict:
    records: list[dict] = []
    for path in input_paths:
        with path.open(encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if line:
                    records.append(json.loads(line))
    for r in records:
        r.setdefault("textLen", len(r.get("text", "")))
    stats = {"input": len(records)}

    records, duplicates = dedup(records, threshold=dedup_threshold)
    stats["duplicates_removed"] = len(duplicates)
    records, outliers = iqr_filter(records)
    stats["iqr_outliers_removed"] = len(outliers)

    synthetic = monte_carlo_augment(records, augment, seed=seed, label_key=label_key,
                                    calibration=calibration)
    all_records = records + synthetic
    train, val, test = stratified_split(all_records, label_key=label_key,
                                        ratios=ratios, seed=seed)

    outputs = []
    for name, split in (("train", train), ("val", val), ("test", test)):
        outputs.extend(write_outputs(name, split, out_dir))
    stats.update({"synthetic_generated": len(synthetic), "train": len(train),
                  "val": len(val), "test": len(test), "outputs":
                  [str(p) for p in outputs]})
    return stats


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="训练集构建：去重/清洗/分层/增广")
    parser.add_argument("--input", nargs="+", required=True, help="输入 JSONL 文件")
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--label-key", default="source")
    parser.add_argument("--dedup-threshold", type=float, default=0.90)
    parser.add_argument("--augment", type=int, default=0, help="蒙特卡洛增广条数")
    parser.add_argument("--calibration", default=None,
                        help="混淆矩阵 JSON（data_pipeline.calibration 输出）")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args(argv)
    calibration = None
    if args.calibration:
        from data_pipeline.calibration import load_model
        calibration = load_model(Path(args.calibration))
    stats = build([Path(p) for p in args.input], Path(args.out_dir),
                  label_key=args.label_key, dedup_threshold=args.dedup_threshold,
                  augment=args.augment, seed=args.seed, calibration=calibration)
    print(json.dumps(stats, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
