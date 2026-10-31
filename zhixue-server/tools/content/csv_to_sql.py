#!/usr/bin/env python3
"""由已校验的 CSV 生成 seed SQL（upsert，可重复执行）。

用法：
  python csv_to_sql.py --kp kp.csv --questions questions.csv \
      --chapter-no 4 --chapter-title 数据库安全性 --sort-no 3 --outdir ./out

前置：先跑 validate_content.py --strict 通过。
"""
import argparse
import csv
import os
import re
import sys

KP_HEADER = ["kpId", "chapterNo", "name", "conceptText", "misconception",
             "textbookPage", "dimensionKey", "sortNo"]
Q_HEADER = ["qId", "primaryKpId", "questionType", "questionText", "optionsJson",
            "answerText", "analysisText", "difficulty", "source", "variantHint"]


def sql_str(value):
    if value is None:
        return "NULL"
    text = str(value).strip()
    if not text:
        return "NULL"
    return "'" + text.replace("'", "''") + "'"


def read_csv(path, header):
    with open(path, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        missing = [h for h in header if h not in (reader.fieldnames or [])]
        if missing:
            print(f"缺少列 {missing}: {path}", file=sys.stderr)
            sys.exit(1)
        return [row for row in reader if any((v or "").strip() for v in row.values())]


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    ap = argparse.ArgumentParser()
    ap.add_argument("--kp", required=True)
    ap.add_argument("--questions", required=True)
    ap.add_argument("--chapter-no", type=int, required=True)
    ap.add_argument("--chapter-title", required=True)
    ap.add_argument("--sort-no", type=int, required=True)
    ap.add_argument("--outdir", required=True)
    args = ap.parse_args()

    kp_rows = read_csv(args.kp, KP_HEADER)
    q_rows = read_csv(args.questions, Q_HEADER)
    for i, row in enumerate(kp_rows, start=2):
        if (row.get("chapterNo") or "").strip() != str(args.chapter_no):
            print(f"{args.kp}:{i} chapterNo 与 --chapter-no 不一致", file=sys.stderr)
            sys.exit(1)

    os.makedirs(args.outdir, exist_ok=True)
    ch = args.chapter_no
    kp_path = os.path.join(args.outdir, f"seed-kp-ch{ch:02d}.sql")
    q_path = os.path.join(args.outdir, f"seed-question-ch{ch:02d}.sql")

    with open(kp_path, "w", encoding="utf-8") as f:
        f.write(f"-- 智学领航 · 第{ch}章知识点种子（由 csv_to_sql.py 生成）\n")
        f.write("-- 前置：init-v2.sql；可重复执行（upsert）。\n")
        f.write("USE zhixue;\nSET NAMES utf8mb4;\n\n")
        f.write("INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES\n")
        f.write(f"(1, {ch}, {sql_str(args.chapter_title)}, {args.sort_no})\n")
        f.write("ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);\n\n")
        for row in kp_rows:
            mis = "\\n".join(x.strip() for x in re.split(r"[|\n]", (row.get("misconception") or "")) if x.strip())
            f.write(
                "INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, "
                "misconception, textbook_page, dimension_key, sort_no, created_by)\n"
                "SELECT " + ", ".join([
                    sql_str(row.get("kpId")), "1", "chapter_id", sql_str(row.get("name")),
                    sql_str(row.get("conceptText")), sql_str(mis), sql_str(row.get("textbookPage")),
                    sql_str(row.get("dimensionKey")), sql_str(row.get("sortNo")), "1"
                ]) + "\nFROM course_chapter WHERE course_id = 1 AND chapter_no = " + str(ch) + "\n"
                "ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), "
                "misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), "
                "dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);\n\n"
            )

    with open(q_path, "w", encoding="utf-8") as f:
        f.write(f"-- 智学领航 · 第{ch}章题库种子（由 csv_to_sql.py 生成）\n")
        f.write("-- 前置：init-v2.sql、seed-kp-ch%02d.sql；可重复执行（upsert）。\n" % ch)
        f.write("USE zhixue;\nSET NAMES utf8mb4;\n\n")
        for row in q_rows:
            f.write(
                "INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, "
                "question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)\n"
                "VALUES (" + ", ".join([
                    sql_str(row.get("qId")), "1", sql_str(row.get("primaryKpId")),
                    sql_str(row.get("questionType")), sql_str(row.get("questionText")),
                    sql_str(row.get("optionsJson")), sql_str(row.get("answerText")),
                    sql_str(row.get("analysisText")), sql_str(row.get("difficulty")),
                    sql_str(row.get("source")), sql_str(row.get("variantHint"))
                ]) + ")\n"
                "ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), "
                "question_type = VALUES(question_type), question_text = VALUES(question_text), "
                "options_json = VALUES(options_json), answer_text = VALUES(answer_text), "
                "analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), "
                "source = VALUES(source), variant_hint = VALUES(variant_hint);\n\n"
            )

    print(f"生成: {kp_path} ({len(kp_rows)} 知识点)")
    print(f"生成: {q_path} ({len(q_rows)} 题)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
