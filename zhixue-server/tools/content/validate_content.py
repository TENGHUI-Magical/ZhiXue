#!/usr/bin/env python3
"""智学领航内容校验：知识点/题库 CSV 导入前门禁。

用法：
  python validate_content.py --kp kp.csv --questions questions.csv [--strict]

严格模式（--strict，入库前必须）：
  - 知识点 conceptText >=150 字、misconception >=2 条、textbookPage 形如 P34
  - 每题解析 >=50 字；每个知识点 >=2 题
草稿模式（不加 --strict）：上述项降级为 WARNING，便于先攒初稿。
错误（ERROR）始终阻断。
"""
import argparse
import csv
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict

KP_HEADER = ["kpId", "chapterNo", "name", "conceptText", "misconception",
             "textbookPage", "dimensionKey", "sortNo"]
Q_HEADER = ["qId", "primaryKpId", "questionType", "questionText", "optionsJson",
            "answerText", "analysisText", "difficulty", "source", "variantHint"]
DIMENSIONS = {"concept", "sql", "schema", "transaction", "boundary"}
Q_TYPES = {"单选", "多选", "填空", "简答", "综合"}
CHOICE_TYPES = {"单选", "多选"}

errors, warnings = [], []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def read_csv(path, header):
    with open(path, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        missing = [h for h in header if h not in (reader.fieldnames or [])]
        if missing:
            err(f"{path} 缺少列: {missing}")
            return []
        return [row for row in reader if any((v or "").strip() for v in row.values())]


def load_kp(path, strict):
    rows = read_csv(path, KP_HEADER)
    seen = {}
    for i, row in enumerate(rows, start=2):
        kp = (row.get("kpId") or "").strip()
        where = f"{path}:{i} kpId={kp or '<空>'}"
        if not re.fullmatch(r"KP\d{4}", kp):
            err(f"{where} 编号必须形如 KP0401")
            continue
        if kp in seen:
            err(f"{where} 重复（首次在第 {seen[kp]} 行）")
        seen[kp] = i
        chapter = (row.get("chapterNo") or "").strip()
        if not chapter.isdigit() or not 1 <= int(chapter) <= 8:
            err(f"{where} chapterNo 必须为 1—8")
        if not (row.get("name") or "").strip():
            err(f"{where} name 不能为空")
        concept = (row.get("conceptText") or "").strip()
        mis = (row.get("misconception") or "").strip()
        mis_count = len([x for x in re.split(r"[|\n]", mis) if x.strip()])
        page = (row.get("textbookPage") or "").strip()
        dim = (row.get("dimensionKey") or "").strip()
        if dim not in DIMENSIONS:
            err(f"{where} dimensionKey 非法: {dim}")
        if not (row.get("sortNo") or "").strip().isdigit():
            err(f"{where} sortNo 必须为整数")
        if strict:
            if len(concept) < 150:
                err(f"{where} conceptText 不足 150 字（当前 {len(concept)}）")
            if mis_count < 2:
                err(f"{where} misconception 至少 2 条（当前 {mis_count}）")
            if not re.fullmatch(r"P\d+", page):
                err(f"{where} textbookPage 必须形如 P34（当前 {page or '<空>'}）")
        else:
            if len(concept) < 150:
                warn(f"{where} conceptText 不足 150 字（草稿模式）")
            if mis_count < 2:
                warn(f"{where} misconception 少于 2 条（草稿模式）")
            if not re.fullmatch(r"P\d+", page):
                warn(f"{where} textbookPage 待核对（草稿模式）")
    return seen


def load_questions(path, kp_ids, strict):
    rows = read_csv(path, Q_HEADER)
    seen = {}
    stems = {}
    per_kp = Counter()
    for i, row in enumerate(rows, start=2):
        qid = (row.get("qId") or "").strip()
        where = f"{path}:{i} qId={qid or '<空>'}"
        if not re.fullmatch(r"Q_\d{4}", qid):
            err(f"{where} 编号必须形如 Q_0401")
            continue
        if qid in seen:
            err(f"{where} 重复（首次在第 {seen[qid]} 行）")
        seen[qid] = i
        kp = (row.get("primaryKpId") or "").strip()
        if kp not in kp_ids:
            err(f"{where} primaryKpId 不存在于知识点表: {kp}")
        else:
            per_kp[kp] += 1
        qtype = (row.get("questionType") or "").strip()
        if qtype not in Q_TYPES:
            err(f"{where} questionType 非法: {qtype}")
        stem = (row.get("questionText") or "").strip()
        if not stem:
            err(f"{where} questionText 不能为空")
        else:
            h = hashlib.sha1(re.sub(r"\s+", "", stem).encode("utf-8")).hexdigest()
            if h in stems:
                err(f"{where} 题干与第 {stems[h]} 行重复")
            stems[h] = i
        options_raw = (row.get("optionsJson") or "").strip()
        options = []
        if qtype in CHOICE_TYPES:
            try:
                options = json.loads(options_raw) if options_raw else []
            except json.JSONDecodeError:
                err(f"{where} optionsJson 不是合法 JSON")
                options = []
            if len(options) < 4:
                err(f"{where} 选择题至少 4 个选项（当前 {len(options)}）")
            raw_keys = [str(o.get("key")).strip().upper() for o in options
                        if isinstance(o, dict) and str(o.get("key") or "").strip()]
            keys = set(raw_keys)
            if len(keys) != len(raw_keys):
                err(f"{where} 选项 key 重复")
            if any(not re.fullmatch(r"[A-Z]", key) for key in raw_keys):
                err(f"{where} 选项 key 必须为单个大写字母（A/B/C/D）")
            answer_raw = (row.get("answerText") or "").strip()
            answer = answer_raw.upper()
            if not answer:
                err(f"{where} 选择题 answerText 不能为空")
            elif answer_raw != answer:
                err(f"{where} 选择题 answerText 请使用大写选项 key（当前 {answer_raw}）")
            elif qtype == "多选":
                # 多选答案必须是大写 key 直接拼接（如 ABD），不接受逗号/空格
                if not re.fullmatch(r"[A-Z]+", answer):
                    err(f"{where} 多选题 answerText 必须为大写选项 key 拼接（如 ABD）")
                elif len(answer) != len(set(answer)):
                    err(f"{where} 多选题 answerText={answer_raw} 含重复选项 key")
                elif len(set(answer)) < 2:
                    err(f"{where} 多选题 answerText={answer_raw} 至少包含 2 个选项 key")
                elif not set(answer).issubset(keys):
                    err(f"{where} answerText={answer_raw} 含选项 key 之外的字符")
            elif answer not in keys:
                err(f"{where} answerText={answer_raw} 不在选项 key 内")
        analysis = (row.get("analysisText") or "").strip()
        if strict and len(analysis) < 50:
            err(f"{where} analysisText 不足 50 字（当前 {len(analysis)}）")
        elif not strict and len(analysis) < 50:
            warn(f"{where} analysisText 不足 50 字（草稿模式）")
        diff = (row.get("difficulty") or "").strip()
        if diff not in {"1", "2", "3"}:
            err(f"{where} difficulty 必须为 1/2/3")
    if strict:
        for kp in kp_ids:
            if per_kp.get(kp, 0) < 2:
                err(f"知识点 {kp} 题量不足 2（当前 {per_kp.get(kp, 0)}）")
    else:
        for kp in kp_ids:
            if per_kp.get(kp, 0) < 2:
                warn(f"知识点 {kp} 题量不足 2（草稿模式）")
    return per_kp


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    ap = argparse.ArgumentParser()
    ap.add_argument("--kp", required=True)
    ap.add_argument("--questions", required=True)
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args()

    kp_ids = load_kp(args.kp, args.strict)
    per_kp = load_questions(args.questions, set(kp_ids), args.strict)

    print(f"知识点: {len(kp_ids)} 个；题目: {sum(per_kp.values())} 道")
    print(f"模式: {'严格（入库）' if args.strict else '草稿'}")
    for w in warnings:
        print("WARNING:", w)
    for e in errors:
        print("ERROR:", e)
    if errors:
        print(f"\n[FAIL] 校验失败：{len(errors)} 个错误，{len(warnings)} 个警告")
        return 1
    print(f"\n[OK] 校验通过（{len(warnings)} 个警告）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
