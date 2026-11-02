#!/usr/bin/env python3
"""智学领航内容校验：知识点/题库 CSV 导入前门禁。

用法：
  python validate_content.py --kp kp.csv --questions questions.csv [--strict]

严格模式（--strict，入库前必须）：
  - 知识点 conceptText >=150 字、misconception >=2 条、textbookPage 形如 P34
  - 每题解析 >=50 字；每个知识点 >=2 题
  - 章节质量规则（2026-11-03 扩展）：
      R1 综合题占比 >=10%（按题目总数）
      R2 难度 1/2/3 各档占比 >=20%（按题目总数）
      R3 单一 dimension 占比 <=60%（按知识点数）
      R4 解析字数按题型分档：单选/多选/填空 >=50 字，简答/综合 >=120 字
      R6 题源格式：资料名（教材/习题册）必须伴随印刷页号 P\\d+；
         只写资料名、只写 PDF 页号、页码范围残缺一律报警
         （背景：ch04/ch05 曾把习题册 PDF 号当印刷页号，ch01/ch02 只标"习题册"三字）
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
Q_TYPES = {"单选", "多选", "判断", "填空", "简答", "综合"}
CHOICE_TYPES = {"单选", "多选"}
JUDGMENT_ANSWERS = {"T", "F", "对", "错", "√", "×", "是", "否", "Y", "N"}

# ---- 章节级内容质量规则阈值（比例比较用整数交叉相乘，避免浮点误差；恰好等于阈值视为通过） ----
COMPREHENSIVE_MIN_PCT = 10   # R1：综合题占比 >=10%
DIFFICULTY_MIN_PCT = 20      # R2：难度 1/2/3 各档占比 >=20%
DIMENSION_MAX_PCT = 60       # R3：单章同一 dimension 占比 <=60%（按知识点数）
ANALYSIS_MIN_BY_TYPE = {"简答": 120, "综合": 120}  # R4：主观题解析 >=120 字
ANALYSIS_MIN_DEFAULT = 50                                   # 其余题型 >=50 字

# ---- R6：题源格式规则 ----
SOURCE_MATERIALS = ("教材", "习题册")
# 印刷页号：P 前不能是字母/数字，避免把 PDF73 误认成 P73
SOURCE_PAGE_RE = re.compile(r"(?<![A-Za-z0-9])P\d+")
# 页码范围：P 后跟范围符，范围符后的内容必须还是数字（可带 P 前缀）
SOURCE_RANGE_RE = re.compile(r"(?<![A-Za-z0-9])P\d+\s*[—\-~–]\s*([^，,；;）)]*)")

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
    dim_counts = Counter()
    for i, row in enumerate(rows, start=2):
        kp = (row.get("kpId") or "").strip()
        where = f"{path}:{i} kpId={kp or '<空>'}"
        if not re.fullmatch(r"KP\d{4}", kp):
            err(f"{where} 编号必须形如 KP0401")
            continue
        if kp in seen:
            err(f"{where} 重复（首次在第 {seen[kp]} 行）")
        else:
            # 维度按知识点去重后计数（重复行已在上面报错，不重复计入）
            if (row.get("dimensionKey") or "").strip() in DIMENSIONS:
                dim_counts[(row.get("dimensionKey") or "").strip()] += 1
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
    return seen, dim_counts


def load_questions(path, kp_ids, strict):
    rows = read_csv(path, Q_HEADER)
    seen = {}
    stems = {}
    per_kp = Counter()
    type_counts = Counter()
    diff_counts = Counter()
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
        options_loaded = False
        if options_raw:
            try:
                options = json.loads(options_raw)
                options_loaded = True
                if not isinstance(options, list):
                    err(f"{where} optionsJson 必须是选项数组")
                    options = []
            except json.JSONDecodeError:
                err(f"{where} optionsJson 不是合法 JSON")
                options = []
        if qtype == "判断":
            # 判断题允许无选项或恰好两个选项，不套用单选/多选的四选项规则。
            if len(options) not in {0, 2}:
                err(f"{where} 判断题应无选项或恰好 2 个选项（当前 {len(options)}）")
            answer_raw = (row.get("answerText") or "").strip()
            if answer_raw not in JUDGMENT_ANSWERS:
                err(f"{where} 判断题 answerText 只能为 T/F/对/错/√/×/是/否/Y/N（当前 {answer_raw or '<空>'}）")
        elif qtype in CHOICE_TYPES:
            if len(options) < 4:
                err(f"{where} 选择题至少 4 个选项（当前 {len(options)}）")
            raw_keys = [str(o.get("key")).strip() for o in options
                        if isinstance(o, dict) and str(o.get("key") or "").strip()]
            keys = set(raw_keys)
            if len(keys) != len(raw_keys):
                err(f"{where} 选项 key 重复")
            # 先校验原始 key，再比较答案；不允许先把 a 归一化成 A 而放过非法 key
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
        elif qtype in {"填空", "简答", "综合"} and options_loaded and options:
            err(f"{where} {qtype}题不应提供选项数组（当前 {len(options)} 个选项）")
        analysis = (row.get("analysisText") or "").strip()
        # R4：解析字数按题型分档（简答/综合 >=120，其余 >=50）
        min_len = ANALYSIS_MIN_BY_TYPE.get(qtype, ANALYSIS_MIN_DEFAULT)
        if len(analysis) < min_len:
            msg = (f"{where} analysisText 不足 {min_len} 字"
                   f"（{qtype}要求 ≥{min_len}，当前 {len(analysis)}）")
            if strict:
                err(msg)
            else:
                warn(msg + "（草稿模式）")
        diff = (row.get("difficulty") or "").strip()
        if diff not in {"1", "2", "3"}:
            err(f"{where} difficulty 必须为 1/2/3")
        else:
            diff_counts[diff] += 1
        if qtype in Q_TYPES:
            type_counts[qtype] += 1
        # R6：题源格式（资料名+印刷页号；PDF 号必须伴随印刷页；范围完整）
        for sm in check_source_format(row.get("source") or ""):
            msg = f"{where} 题源格式不规范：{sm}"
            if strict:
                err(msg)
            else:
                warn(msg + "（草稿模式）")
    if strict:
        for kp in kp_ids:
            if per_kp.get(kp, 0) < 2:
                err(f"知识点 {kp} 题量不足 2（当前 {per_kp.get(kp, 0)}）")
    else:
        for kp in kp_ids:
            if per_kp.get(kp, 0) < 2:
                warn(f"知识点 {kp} 题量不足 2（草稿模式）")
    return per_kp, type_counts, diff_counts


def check_source_format(source):
    """R6：题源格式规则（纯函数，便于单测）。返回违规描述列表。

    要求：出现"教材"/"习题册"资料名时必须伴随印刷页号 P\\d+；
    出现"PDF 页号"时必须同时给出印刷页号；页码范围后必须跟数字。
    合法样例：教材 P129 / 教材 P128—130 / 习题册 P42（PDF 57）/
              习题册 PDF73（印刷P58）/ 教材 P215—P217。
    """
    msgs = []
    s = (source or "").strip()
    if not s:
        return ["为空：必须为 资料名+印刷页号（如 教材 P129 / 习题册 P42（PDF 57））"]
    has_page = SOURCE_PAGE_RE.search(s) is not None
    for m in SOURCE_MATERIALS:
        if m in s and not has_page:
            msgs.append(f"只写了“{m}”，未标印刷页号（应形如 {m} P129 或 {m} P42（PDF 57））")
    if not has_page and not any(m in s for m in SOURCE_MATERIALS):
        msgs.append("缺少资料名（教材/习题册）且无印刷页号")
    if re.search(r"PDF\s*\d+", s) and not has_page:
        msgs.append("只写了 PDF 页号，缺印刷页号（应形如 习题册 P42（PDF 57））")
    for mm in SOURCE_RANGE_RE.finditer(s):
        rest = mm.group(1).strip()
        if not re.fullmatch(r"P?\d+", rest):
            msgs.append(f"页码范围格式不规范：…{mm.group(0)[:24]}…（应形如 P129—130 / P129—P130）")
    return msgs


def check_chapter_rules(type_counts, diff_counts, dim_counts, kp_total):
    """章节级质量规则（纯函数，便于单测）：返回违规消息列表。

    R1 综合题占比 >=10%；R2 难度 1/2/3 各 >=20%；R3 单一维度 <=60%（按知识点数）。
    比例比较全部用整数交叉相乘，避免浮点误差；恰好等于阈值视为通过。
    """
    msgs = []
    total = sum(type_counts.values())
    if total:
        comp = type_counts.get("综合", 0)
        if comp * 100 < total * COMPREHENSIVE_MIN_PCT:
            need = (total * COMPREHENSIVE_MIN_PCT + 99) // 100  # 向上取整
            msgs.append(f"[章节规则-综合占比] 综合题 {comp}/{total}"
                        f"（{comp * 100 // total}%），低于 {COMPREHENSIVE_MIN_PCT}%"
                        f"（至少 {need} 道）")
        for d in ("1", "2", "3"):
            n = diff_counts.get(d, 0)
            if n * 100 < total * DIFFICULTY_MIN_PCT:
                msgs.append(f"[章节规则-难度分布] 难度 {d} 档 {n}/{total}"
                            f"（{n * 100 // total}%），低于 {DIFFICULTY_MIN_PCT}%")
    if kp_total:
        for dim, n in dim_counts.most_common():
            if n * 100 > kp_total * DIMENSION_MAX_PCT:
                msgs.append(f"[章节规则-维度偏斜] dimension={dim} {n}/{kp_total}"
                            f"（{n * 100 // kp_total}%），超过 {DIMENSION_MAX_PCT}%")
    return msgs


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

    kp_ids, dim_counts = load_kp(args.kp, args.strict)
    per_kp, type_counts, diff_counts = load_questions(
        args.questions, set(kp_ids), args.strict)
    # 章节级质量规则：严格模式为 ERROR（阻断入库），草稿模式为 WARNING
    for m in check_chapter_rules(type_counts, diff_counts, dim_counts, len(kp_ids)):
        (err if args.strict else warn)(m)

    print(f"知识点: {len(kp_ids)} 个；题目: {sum(per_kp.values())} 道")
    if type_counts:
        print(f"题型: {dict(type_counts)}；难度: {dict(diff_counts)}；"
              f"维度: {dict(dim_counts)}")
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
