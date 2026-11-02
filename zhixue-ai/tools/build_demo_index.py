#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为演示库批量建 RAG 索引，并做在线检索验收。

为什么单独写脚本
----------------
`/api/rag/embed` 是教师/脚本显式入索引的唯一入口（不在服务启动时偷偷读库）。
演示库重建后，必须把当前全部 concept_text 入索引，否则讲解只能引用旧切片。
脚本只调用公开 HTTP 接口，不绕过业务逻辑。

用法（先启动 zhixue-ai）
------------------------
    python tools/build_demo_index.py --base-url http://127.0.0.1:8000
    python tools/build_demo_index.py --bench --json data/rag_index_report.json
    python tools/build_demo_index.py --bench --query-set tools/testdata/queries_student_style.json

口径
----
- 入索引：knowledge_point 中 course_id=1、is_active=1、concept_text 非空的全部知识点；
- 默认验收：question_bank 中 Q_02*/Q_03* 题干作 query；也可用 --query-set 指定
  带 category/gold/text 的学生式查询集。topK=5、threshold=0，统计 recall@5 与 top1；
- 只衡量“检索是否找对知识点”，不代表讲解质量；数据来自演示库，不是真实试用结果。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
import time
from datetime import datetime
from pathlib import Path

AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))

import httpx  # noqa: E402
from sqlalchemy import create_engine, text  # noqa: E402

from app.config import settings  # noqa: E402


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, math.ceil(p * len(ordered)) - 1))
    return round(ordered[index], 1)


def load_kps(dsn: str, course_id: int) -> list[dict]:
    engine = create_engine(dsn)
    sql = text("""
        SELECT kp_id, name, concept_text, chapter_id, textbook_page
        FROM knowledge_point
        WHERE course_id = :course_id AND is_active = 1
          AND concept_text IS NOT NULL AND concept_text <> ''
        ORDER BY kp_id
    """)
    with engine.connect() as conn:
        return [dict(row._mapping) for row in conn.execute(sql, {"course_id": course_id})]


def load_questions(dsn: str, course_id: int) -> list[dict]:
    engine = create_engine(dsn)
    sql = text("""
        SELECT q_id, primary_kp_id, question_text
        FROM question_bank
        WHERE course_id = :course_id
          AND (q_id LIKE 'Q_02%' OR q_id LIKE 'Q_03%')
        ORDER BY q_id
    """)
    with engine.connect() as conn:
        return [dict(row._mapping) for row in conn.execute(sql, {"course_id": course_id})]


def load_query_set(path: str) -> tuple[list[dict], dict[str, str]]:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    labels = data.get("categories") or {}
    rows = []
    for item in data.get("queries") or []:
        query = (item.get("text") or "").strip()
        gold = (item.get("gold") or "").strip()
        if not query or not gold:
            raise ValueError(f"查询缺少 text/gold：{item.get('qId')}")
        rows.append({
            "q_id": item.get("qId") or f"query-{len(rows) + 1}",
            "primary_kp_id": gold,
            "question_text": query,
            "category": item.get("category") or "uncategorized",
        })
    if not rows:
        raise ValueError("查询集为空")
    return rows, labels


def inspect_index(directory: str, expected_kp_ids: set[str]) -> dict:
    path = Path(directory).resolve()
    chunks = json.loads((path / "chunks.json").read_text(encoding="utf-8"))
    actual_kp_ids = {chunk["kp_id"] for chunk in chunks}
    files = {}
    for name in ("chunks.json", "meta.json", "vectors.npy", "vectors.json"):
        file = path / name
        if file.exists():
            files[name] = {
                "bytes": file.stat().st_size,
                "sha256": hashlib.sha256(file.read_bytes()).hexdigest(),
            }
    return {
        "directory": str(path),
        "chunks": len(chunks),
        "kpCount": len(actual_kp_ids),
        "missingKpIds": sorted(expected_kp_ids - actual_kp_ids),
        "unexpectedKpIds": sorted(actual_kp_ids - expected_kp_ids),
        "files": files,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--dsn", default=settings.mysql_dsn)
    parser.add_argument("--course-id", type=int, default=1)
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--threshold", type=float, default=0.0)
    parser.add_argument("--timeout", type=float, default=15.0)
    parser.add_argument("--bench", action="store_true", help="入索引后跑在线检索")
    parser.add_argument("--query-set", default="", help="配合 --bench 使用学生式查询 JSON")
    parser.add_argument("--index-dir", default="", help="核验本地索引覆盖率、文件大小与 SHA-256")
    parser.add_argument("--json", default="data/rag_index_report.json")
    args = parser.parse_args()

    base = args.base_url.rstrip("/")
    with httpx.Client(timeout=args.timeout) as client:
        health = client.get(base + "/health")
        health.raise_for_status()

        kps = load_kps(args.dsn, args.course_id)
        if not kps:
            raise SystemExit("没有查到可入索引的知识点；请先执行 seed-demo 脚本")
        print(f"待入索引知识点 {len(kps)} 个（courseId={args.course_id}）")

        chunks = 0
        embed_ms: list[float] = []
        modes: set[str] = set()
        for kp in kps:
            started = time.perf_counter()
            response = client.post(base + "/api/rag/embed", json={
                "kpId": kp["kp_id"],
                "text": kp["concept_text"],
                "kpName": kp["name"],
                "courseId": args.course_id,
                "chapterId": kp["chapter_id"],
                "ref": kp["textbook_page"] or "",
            })
            response.raise_for_status()
            body = response.json()
            chunks += int(body.get("chunks", 0))
            modes.add(str(body.get("mode", "")))
            embed_ms.append((time.perf_counter() - started) * 1000)
            print(f"  {kp['kp_id']} {kp['name']}: {body.get('chunks')} 切片 / {body.get('mode')}")

        report = {
            "generatedAt": datetime.now().astimezone().isoformat(timespec="seconds"),
            "baseUrl": base,
            "courseId": args.course_id,
            "kpCount": len(kps),
            "chunks": chunks,
            "mode": ",".join(sorted(modes)),
            "embedMs": {
                "p50": percentile(embed_ms, 0.50),
                "p95": percentile(embed_ms, 0.95),
                "total": round(sum(embed_ms), 1),
            },
        }
        if args.index_dir:
            report["index"] = inspect_index(args.index_dir, {kp["kp_id"] for kp in kps})
            if report["index"]["missingKpIds"] or report["index"]["unexpectedKpIds"]:
                raise SystemExit("索引知识点覆盖与主库不一致：" + json.dumps(report["index"],
                                                                      ensure_ascii=False))

        if args.bench:
            category_labels = {}
            if args.query_set:
                questions, category_labels = load_query_set(args.query_set)
            else:
                questions = load_questions(args.dsn, args.course_id)
            recall = top1 = 0
            retrieve_ms: list[float] = []
            empty = 0
            details = []
            category_stats: dict[str, dict[str, int]] = {}
            for item in questions:
                started = time.perf_counter()
                response = client.post(base + "/api/rag/retrieve", json={
                    "text": item["question_text"],
                    "courseId": args.course_id,
                    "topK": args.top_k,
                    "threshold": args.threshold,
                })
                response.raise_for_status()
                retrieve_ms.append((time.perf_counter() - started) * 1000)
                sources = response.json().get("sources") or []
                if not sources:
                    empty += 1
                ids = [source.get("kpId") for source in sources]
                gold = item["primary_kp_id"]
                recalled = gold in ids
                first = bool(ids and ids[0] == gold)
                recall += int(recalled)
                top1 += int(first)
                category = item.get("category") or "uncategorized"
                stat = category_stats.setdefault(category, {"queries": 0, "recall": 0, "top1": 0})
                stat["queries"] += 1
                stat["recall"] += int(recalled)
                stat["top1"] += int(first)
                details.append({
                    "qId": item["q_id"],
                    "category": category,
                    "gold": gold,
                    "rank": ids.index(gold) + 1 if recalled else None,
                    "hits": [{"kpId": source.get("kpId"), "ref": source.get("ref"),
                              "score": source.get("score")} for source in sources],
                })
            n = len(questions) or 1
            report["bench"] = {
                "querySet": args.query_set or "question_bank:Q_02*/Q_03*",
                "queries": len(questions),
                "topK": args.top_k,
                "threshold": args.threshold,
                "recallAt5": round(recall / n, 4),
                "top1": round(top1 / n, 4),
                "empty": empty,
                "byCategory": {
                    category: {
                        "label": category_labels.get(category, category),
                        "queries": stat["queries"],
                        "recallAt5": round(stat["recall"] / stat["queries"], 4),
                        "top1": round(stat["top1"] / stat["queries"], 4),
                    }
                    for category, stat in sorted(category_stats.items())
                },
                "details": details,
                "latencyMs": {
                    "p50": percentile(retrieve_ms, 0.50),
                    "p95": percentile(retrieve_ms, 0.95),
                    "total": round(sum(retrieve_ms), 1),
                },
            }

        output = Path(args.json)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print("\n结果：")
    summary = dict(report)
    if "bench" in summary:
        summary["bench"] = {key: value for key, value in report["bench"].items()
                            if key != "details"}
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print("\n已写出：" + str(output))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
