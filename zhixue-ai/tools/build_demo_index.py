#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为演示库批量建 RAG 索引，并用 60 道题做在线检索验收。

为什么单独写脚本
----------------
`/api/rag/embed` 是教师/脚本显式入索引的唯一入口（不在服务启动时偷偷读库）。
演示库重建后，知识点从 20/30 个变成 41 个，必须把这 41 个 concept_text 全部
入索引，否则讲解只能引用旧切片。脚本只调用公开 HTTP 接口，不绕过业务逻辑。

用法（先启动 zhixue-ai）
------------------------
    python tools/build_demo_index.py --base-url http://127.0.0.1:8000
    python tools/build_demo_index.py --bench --json data/rag_index_report.json

口径
----
- 入索引：knowledge_point 中 course_id=1、is_active=1、concept_text 非空的全部知识点；
- 在线验收：question_bank 中 Q_02*/Q_03* 共 60 题，题干作 query，topK=5、threshold=0，
  统计 gold 知识点是否出现在返回的 5 条切片中（recall@5）与是否排第一（top1）；
- 只衡量“检索是否找对知识点”，不代表讲解质量；数据来自演示库，不是真实试用结果。
"""
from __future__ import annotations

import argparse
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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--dsn", default=settings.mysql_dsn)
    parser.add_argument("--course-id", type=int, default=1)
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--threshold", type=float, default=0.0)
    parser.add_argument("--timeout", type=float, default=15.0)
    parser.add_argument("--bench", action="store_true", help="入索引后跑 60 题在线检索")
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

        if args.bench:
            questions = load_questions(args.dsn, args.course_id)
            recall = top1 = 0
            retrieve_ms: list[float] = []
            empty = 0
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
                if item["primary_kp_id"] in ids:
                    recall += 1
                    if ids[0] == item["primary_kp_id"]:
                        top1 += 1
            n = len(questions) or 1
            report["bench"] = {
                "queries": len(questions),
                "topK": args.top_k,
                "threshold": args.threshold,
                "recallAt5": round(recall / n, 4),
                "top1": round(top1 / n, 4),
                "empty": empty,
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
    print(json.dumps(report, ensure_ascii=False, indent=2))
    print("\n已写出：" + str(output))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
