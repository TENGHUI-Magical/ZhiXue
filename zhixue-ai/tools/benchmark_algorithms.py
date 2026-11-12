#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""算法基准测试：向量检索耗时 / 内存 / 排序一致性（重构前后对比用）。

测量口径
--------
1. 构建索引：N 个中文切片 embed（hash-bigram 模式）+ add 的耗时与 tracemalloc 峰值内存；
2. 检索：M 次查询的 mean / median / p95 耗时（time.perf_counter，单次查询计时）；
3. 排序一致性：固定种子查询的 top-5 (chunk_id, score) 快照，供 --compare 验证
   重构前后排序与分数一致性（容差 1e-3，search 本身 round 到 4 位小数）。

用法（在 zhixue-ai 目录执行）：
    python tools/benchmark_algorithms.py --label before
    python tools/benchmark_algorithms.py --label after
    python tools/benchmark_algorithms.py --compare <before.json> <after.json>

说明：bge 模型不在本机时 embed 走 hash-bigram，两版实现共用同一路径，
基准只衡量检索实现差异，不衡量 embedding 模型本身。
"""
from __future__ import annotations

import argparse
import json
import random
import statistics
import time
import tracemalloc
from pathlib import Path

import sys

AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))

from app.services import vector_index  # noqa: E402

# 确定性语料：真实数据库课程短语池，拼接成 300—500 字切片，贴近线上 chunk 分布
PHRASES = [
    "关系数据库的标准语言SQL支持数据定义、数据查询、数据操纵和数据控制",
    "关系模型由关系数据结构、关系操作集合和关系完整性约束三部分组成",
    "实体完整性要求主属性不能取空值，参照完整性要求外键要么为空要么引用存在的主键",
    "第三范式要求消除非主属性对码的传递函数依赖，BCNF要求每个决定因素都包含码",
    "数据库恢复的基本原理是利用存储在日志文件中的冗余数据来重建数据库",
    "事务的ACID特性包括原子性、一致性、隔离性和持久性",
    "两段锁协议保证事务调度的可串行性，但可能发生死锁",
    "B+树索引的所有关键字都出现在叶子节点，非叶节点仅作索引导向",
    "查询优化的目标是选择高效合理的等价查询表达式，代数优化先做选择投影再做连接",
    "视图是虚表，数据库只存放视图的定义而不存放视图对应的数据",
    "授权粒度包括数据库级、表级、属性列级和行级，GRANT语句可传递授权",
    "码是能唯一标识元组的属性或属性组，候选码是最小的超码",
    "函数依赖X决定Y意味着任意两个元组若X相等则Y必相等",
    "封锁粒度越粗并发度越低但系统开销越小，多粒度封锁引入意向锁",
    "日志文件必须先写日志后写数据库，检查点技术减少故障恢复时重做的事务数",
]


def make_corpus(n_chunks: int, seed: int = 20261112) -> list[str]:
    rng = random.Random(seed)
    texts = []
    for i in range(n_chunks):
        parts = [f"知识点{i}："]
        target = rng.randint(300, 500)
        while sum(len(p) for p in parts) < target:
            parts.append(rng.choice(PHRASES))
        texts.append("".join(parts))
    return texts


def make_queries(n_queries: int, seed: int = 991) -> list[str]:
    templates = [
        "什么是{0}",
        "{0}和{1}的区别",
        "为什么{0}",
        "请解释{0}的原理",
        "{0}怎么用",
    ]
    keys = ["事务的ACID特性", "B+树索引", "第三范式", "两段锁协议", "参照完整性",
            "查询优化", "视图", "日志文件", "函数依赖", "授权语句"]
    return [templates[i % len(templates)].format(keys[i % len(keys)],
                                                 keys[(i + 3) % len(keys)]) + f"第{i}个"
            for i in range(n_queries)]


def run_benchmark(label: str, n_chunks: int, n_queries: int, out_dir: Path) -> dict:
    corpus = make_corpus(n_chunks)
    queries = make_queries(n_queries)

    tracemalloc.start()
    build_start = time.perf_counter()
    index = vector_index.VectorIndex()
    chunks = [vector_index.Chunk(f"KP{i}#1", f"KP{i}", "", None, None, "", text)
              for i, text in enumerate(corpus)]
    vectors, mode = vector_index.embed_texts(corpus)
    index.add(chunks, vectors, mode)
    build_seconds = time.perf_counter() - build_start
    build_peak_mb = tracemalloc.get_traced_memory()[1] / 1024 / 1024
    tracemalloc.stop()

    # 预热（首次查询构建统计/缓存）
    index.search(queries[0], top_k=5, threshold=0.35)

    latencies = []
    top5_snapshot = []
    for query in queries:
        start = time.perf_counter()
        hits = index.search(query, top_k=5, threshold=0.35)
        latencies.append((time.perf_counter() - start) * 1000)
        top5_snapshot.append([[hit.chunk.chunk_id, hit.score] for hit in hits])

    result = {
        "label": label,
        "mode": mode,
        "n_chunks": n_chunks,
        "n_queries": n_queries,
        "index_size": index.size,
        "build_ms": round(build_seconds * 1000, 2),
        "build_peak_mb": round(build_peak_mb, 2),
        "search_mean_ms": round(statistics.mean(latencies), 3),
        "search_median_ms": round(statistics.median(latencies), 3),
        "search_p95_ms": round(statistics.quantiles(latencies, n=20)[18], 3),
        "top5_snapshot": top5_snapshot,
    }
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / f"benchmark_{label}.json"
    out_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "top5_snapshot"},
                     ensure_ascii=False, indent=2))
    print(f"已写入: {out_path}")
    return result


def compare(before_path: Path, after_path: Path) -> int:
    before = json.loads(before_path.read_text(encoding="utf-8"))
    after = json.loads(after_path.read_text(encoding="utf-8"))
    speedup = before["search_median_ms"] / after["search_median_ms"] \
        if after["search_median_ms"] > 0 else float("inf")
    print(f"median: {before['search_median_ms']}ms -> {after['search_median_ms']}ms "
          f"(加速比 x{speedup:.2f})")
    print(f"p95:    {before['search_p95_ms']}ms -> {after['search_p95_ms']}ms")
    print(f"mean:   {before['search_mean_ms']}ms -> {after['search_mean_ms']}ms")
    print(f"内存峰值: {before['build_peak_mb']}MB -> {after['build_peak_mb']}MB")

    mismatches = 0
    max_diff = 0.0
    for q_idx, (b_top, a_top) in enumerate(
            zip(before["top5_snapshot"], after["top5_snapshot"])):
        b_ids = [item[0] for item in b_top]
        a_ids = [item[0] for item in a_top]
        if b_ids != a_ids:
            mismatches += 1
            print(f"[排序不一致] 查询{q_idx}: before={b_ids} after={a_ids}")
        for (b_id, b_score), (a_id, a_score) in zip(b_top, a_top):
            max_diff = max(max_diff, abs(b_score - a_score))
    print(f"top-5 排序不一致查询数: {mismatches}/{len(before['top5_snapshot'])}")
    print(f"最大分数差: {max_diff:.6f}（容差 1e-3）")
    ok = mismatches == 0 and max_diff < 1e-3
    print("一致性: " + ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


def run_dedup_benchmark(n_records: int, out_dir: Path) -> dict:
    """去重候选枚举的内存/耗时：旧全量 n×n 距离矩阵 vs 新分块上三角枚举。"""
    import numpy as np
    from data_pipeline import dataset_builder as ds

    rng = np.random.default_rng(20261112)
    signatures = rng.integers(0, 2 ** 63, size=n_records, dtype=np.uint64)

    def old_full_matrix() -> None:
        table = ds._POPCOUNT_TABLE
        block = 1024
        distances = np.zeros((n_records, n_records), dtype=np.uint8)
        for start in range(0, n_records, block):
            chunk = signatures[start:start + block]
            xor = chunk[:, None] ^ signatures[None, :]
            distances[start:start + block] = table[
                xor.view(np.uint8).reshape(chunk.shape[0], n_records, 8)].sum(axis=2)
        np.argwhere(np.triu(distances <= 6, k=1))

    def timed(func):
        tracemalloc.start()
        start = time.perf_counter()
        func()
        elapsed = (time.perf_counter() - start) * 1000
        peak = tracemalloc.get_traced_memory()[1] / 1024 / 1024
        tracemalloc.stop()
        return round(elapsed, 2), round(peak, 2)

    old_ms, old_peak = timed(old_full_matrix)
    new_ms, new_peak = timed(
        lambda: ds._near_duplicate_pairs(signatures, max_hamming=6, block=256))
    result = {
        "label": "dedup",
        "n_records": n_records,
        "old_matrix_ms": old_ms,
        "old_peak_mb": old_peak,
        "new_blockwise_ms": new_ms,
        "new_peak_mb": new_peak,
        "peak_reduction_mb": round(old_peak - new_peak, 2),
    }
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "benchmark_dedup.json"
    out_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print(f"已写入: {out_path}")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="向量检索基准（重构前后对比）")
    parser.add_argument("--label", default=None, help="before / after")
    parser.add_argument("--chunks", type=int, default=200)
    parser.add_argument("--queries", type=int, default=50)
    parser.add_argument("--compare", nargs=2, metavar=("BEFORE", "AFTER"))
    parser.add_argument("--dedup", type=int, default=0,
                        help="额外跑去重候选枚举基准（参数为记录数）")
    parser.add_argument("--out-dir", default=str(
        AI_ROOT.parent.parent / "algorithm_and_data_opt" / "benchmarks"))
    args = parser.parse_args()

    if args.compare:
        return compare(Path(args.compare[0]), Path(args.compare[1]))
    if args.dedup:
        run_dedup_benchmark(args.dedup, Path(args.out_dir))
        return 0
    if not args.label:
        parser.error("需要 --label 或 --compare 或 --dedup")
    run_benchmark(args.label, args.chunks, args.queries, Path(args.out_dir))
    return 0


if __name__ == "__main__":
    sys.exit(main())
