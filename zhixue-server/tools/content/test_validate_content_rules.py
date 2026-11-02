#!/usr/bin/env python3
"""validate_content.py 四类章节质量规则的单元测试。

覆盖：R1 综合题占比 >=10%、R2 难度 1/2/3 各 >=20%、
R3 单维度 <=60%、R4 解析字数按题型分档（选择/填空 >=50，简答/综合 >=120）。
每条规则构造"能过 + 过不了 + 边界"三类样例，保证规则本身不误报。

运行：
  cd zhixue\\zhixue-server\\tools\\content
  D:\\Python3.12.10\\python.exe -m unittest test_validate_content_rules -v
"""
import csv
import io
import os
import shutil
import sys
import tempfile
import unittest
from contextlib import redirect_stdout

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import validate_content as vc  # noqa: E402


def kp_row(idx, dim):
    return {
        "kpId": f"KP80{idx:02d}",
        "chapterNo": "8",
        "name": f"测试知识点{idx}",
        "conceptText": "概念" * 80,  # 160 字，满足 >=150
        "misconception": "误区一|误区二|误区三",
        "textbookPage": "P100",
        "dimensionKey": dim,
        "sortNo": str(idx),
    }


def q_row(i, kp_count, qtype="填空", difficulty="1", analysis_len=130,
          answerText=None, optionsJson=None):
    default_options = ('[{"key":"A","text":"甲"},{"key":"B","text":"乙"},'
                       '{"key":"C","text":"丙"},{"key":"D","text":"丁"}]'
                       if qtype in vc.CHOICE_TYPES else "")
    default_answer = "A" if qtype == "单选" else ("AB" if qtype == "多选" else "参考答案")
    return {
        "qId": f"Q_80{i:02d}",
        "primaryKpId": f"KP80{(i - 1) % kp_count + 1:02d}",
        "questionType": qtype,
        "questionText": f"第{i}题：{qtype}题干，请根据教材内容作答（编号{i}）",
        "optionsJson": default_options if optionsJson is None else optionsJson,
        "answerText": default_answer if answerText is None else answerText,
        "analysisText": "解" * analysis_len,
        "difficulty": difficulty,
        "source": "教材 P100",
        "variantHint": "无",
    }


def tagged(msgs, tag):
    return [m for m in msgs if m.startswith(f"[章节规则-{tag}]")]


class ChapterRuleTestBase(unittest.TestCase):
    """构造临时章节 CSV 并跑校验管线的公共基类。"""

    def setUp(self):
        vc.errors.clear()
        vc.warnings.clear()
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp, True)

    def write_chapter(self, dims, questions):
        kp_path = os.path.join(self.tmp, "kp.csv")
        q_path = os.path.join(self.tmp, "q.csv")
        with open(kp_path, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=vc.KP_HEADER)
            w.writeheader()
            for idx, dim in enumerate(dims, start=1):
                w.writerow(kp_row(idx, dim))
        with open(q_path, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=vc.Q_HEADER)
            w.writeheader()
            for i, spec in enumerate(questions, start=1):
                w.writerow(q_row(i, len(dims), **spec))
        return kp_path, q_path

    def run_rules(self, kp_path, q_path, strict=True):
        kp_ids, dim_counts = vc.load_kp(kp_path, strict)
        _, type_counts, diff_counts = vc.load_questions(
            q_path, set(kp_ids), strict)
        msgs = vc.check_chapter_rules(
            type_counts, diff_counts, dim_counts, len(kp_ids))
        return msgs, list(vc.errors)

    def run_main(self, kp_path, q_path, strict):
        argv = ["validate_content.py", "--kp", kp_path, "--questions", q_path]
        if strict:
            argv.append("--strict")
        old_argv = sys.argv
        sys.argv = argv
        try:
            with redirect_stdout(io.StringIO()):
                rc = vc.main()
        finally:
            sys.argv = old_argv
        return rc


def balanced_diffs(total, tiers=(1, 3, 1)):
    """生成难度序列，默认 1:3:1（各 20%/60%/20%）。"""
    seq = []
    for _ in range(total // 5):
        seq += ["1", "2", "2", "2", "3"]
    seq += ["2"] * (total - len(seq))  # 余数补中档，不破坏各档 >=20%
    return seq


# ---------------- R1 综合题占比 >=10% ----------------

class TestR1ComprehensiveRatio(ChapterRuleTestBase):

    def test_pass_at_exact_threshold(self):
        """30 题含 3 道综合（恰好 10%）应通过，不误报。"""
        qs = [dict(qtype="综合", difficulty=d) for d in ("1", "2", "3")]
        qs += [dict(qtype="填空", difficulty=d)
               for d in balanced_diffs(27)]
        kp, q = self.write_chapter(["concept"] * 5, qs)
        msgs, _ = self.run_rules(kp, q)
        self.assertEqual(tagged(msgs, "综合占比"), [])

    def test_fail_below_threshold(self):
        """25 题仅 2 道综合（8%）应被拦下。"""
        qs = [dict(qtype="综合", difficulty="2") for _ in range(2)]
        qs += [dict(qtype="填空", difficulty=d)
               for d in balanced_diffs(23)]
        kp, q = self.write_chapter(["concept"] * 5, qs)
        msgs, _ = self.run_rules(kp, q)
        hits = tagged(msgs, "综合占比")
        self.assertEqual(len(hits), 1)
        self.assertIn("综合题 2/25", hits[0])
        self.assertIn("至少 3 道", hits[0])  # ceil(25*10%)=3

    def test_fail_zero_comprehensive(self):
        """ch01 场景：25 题 0 综合应被拦下。"""
        qs = [dict(qtype="填空", difficulty=d)
              for d in balanced_diffs(25)]
        kp, q = self.write_chapter(["concept"] * 5, qs)
        msgs, _ = self.run_rules(kp, q)
        hits = tagged(msgs, "综合占比")
        self.assertEqual(len(hits), 1)
        self.assertIn("综合题 0/25", hits[0])


# ---------------- R2 难度 1/2/3 各 >=20% ----------------

class TestR2DifficultyDistribution(ChapterRuleTestBase):

    def test_pass_balanced(self):
        """25 题 5/15/5（各档 >=20%）应通过。"""
        diffs = ["1"] * 5 + ["2"] * 15 + ["3"] * 5
        qs = [dict(qtype="填空", difficulty=d) for d in diffs]
        kp, q = self.write_chapter(["concept"] * 5, qs)
        msgs, _ = self.run_rules(kp, q)
        self.assertEqual(tagged(msgs, "难度分布"), [])

    def test_fail_missing_tier(self):
        """25 题 4/16/5：难度 1 档 16% 应被拦下，且只报 1 档。"""
        diffs = ["1"] * 4 + ["2"] * 16 + ["3"] * 5
        qs = [dict(qtype="填空", difficulty=d) for d in diffs]
        kp, q = self.write_chapter(["concept"] * 5, qs)
        msgs, _ = self.run_rules(kp, q)
        hits = tagged(msgs, "难度分布")
        self.assertEqual(len(hits), 1)
        self.assertIn("难度 1 档 4/25", hits[0])

    def test_pass_exact_20pct(self):
        """30 题 6/18/6：三档恰好 20% 应通过（边界不误报）。"""
        diffs = ["1"] * 6 + ["2"] * 18 + ["3"] * 6
        qs = [dict(qtype="填空", difficulty=d) for d in diffs]
        kp, q = self.write_chapter(["concept"] * 5, qs)
        msgs, _ = self.run_rules(kp, q)
        self.assertEqual(tagged(msgs, "难度分布"), [])


# ---------------- R3 单维度 <=60%（按知识点数） ----------------

class TestR3DimensionSkew(ChapterRuleTestBase):

    def test_pass_at_exact_60pct(self):
        """10 个知识点 6 schema + 4 concept（恰好 60%）应通过。"""
        dims = ["schema"] * 6 + ["concept"] * 4
        qs = [dict(qtype="填空", difficulty=d)
              for d in balanced_diffs(20)]
        kp, q = self.write_chapter(dims, qs)
        msgs, _ = self.run_rules(kp, q)
        self.assertEqual(tagged(msgs, "维度偏斜"), [])

    def test_fail_concentrated(self):
        """ch05 场景：9 个知识点 8 schema（89%）应被拦下。"""
        dims = ["schema"] * 8 + ["concept"]
        qs = [dict(qtype="填空", difficulty=d)
              for d in balanced_diffs(18)]
        kp, q = self.write_chapter(dims, qs)
        msgs, _ = self.run_rules(kp, q)
        hits = tagged(msgs, "维度偏斜")
        self.assertEqual(len(hits), 1)
        self.assertIn("dimension=schema 8/9", hits[0])

    def test_pass_mixed_dimensions(self):
        """10 个知识点分散在 5 个维度应通过。"""
        dims = ["concept", "sql", "schema", "transaction", "boundary"] * 2
        qs = [dict(qtype="填空", difficulty=d)
              for d in balanced_diffs(20)]
        kp, q = self.write_chapter(dims, qs)
        msgs, _ = self.run_rules(kp, q)
        self.assertEqual(tagged(msgs, "维度偏斜"), [])


# ---------------- R4 解析字数按题型分档 ----------------

class TestR4AnalysisLength(ChapterRuleTestBase):

    def analysis_errors(self):
        return [e for e in vc.errors if "analysisText 不足" in e]

    def test_choice_50_boundary(self):
        """单选解析恰好 50 字通过、49 字被拦。"""
        qs = [dict(qtype="单选", difficulty="1", analysis_len=50),
              dict(qtype="单选", difficulty="2", analysis_len=49)]
        kp, q = self.write_chapter(["concept"], qs)
        self.run_rules(kp, q)
        errs = self.analysis_errors()
        self.assertEqual(len(errs), 1)
        self.assertIn("不足 50 字", errs[0])
        self.assertIn("Q_8002", errs[0])  # 只有 49 字那题被拦

    def test_short_answer_120_boundary(self):
        """简答解析恰好 120 字通过、119 字被拦（阈值 120 而非 50）。"""
        qs = [dict(qtype="简答", difficulty="2", analysis_len=120),
              dict(qtype="简答", difficulty="3", analysis_len=119)]
        kp, q = self.write_chapter(["concept"], qs)
        self.run_rules(kp, q)
        errs = self.analysis_errors()
        self.assertEqual(len(errs), 1)
        self.assertIn("不足 120 字", errs[0])
        self.assertIn("Q_8002", errs[0])

    def test_comprehensive_below_120_fails(self):
        """综合题 100 字解析（旧门槛 50 能过）应被新门槛拦下。"""
        qs = [dict(qtype="综合", difficulty="3", analysis_len=100),
              dict(qtype="综合", difficulty="3", analysis_len=130)]
        kp, q = self.write_chapter(["schema"], qs)
        self.run_rules(kp, q)
        errs = self.analysis_errors()
        self.assertEqual(len(errs), 1)
        self.assertIn("综合要求 ≥120", errs[0])

    def test_fill_80_still_passes(self):
        """填空 80 字（>=50）不应被误报为不足 120。"""
        qs = [dict(qtype="填空", difficulty="2", analysis_len=80),
              dict(qtype="填空", difficulty="2", analysis_len=60)]
        kp, q = self.write_chapter(["concept"], qs)
        self.run_rules(kp, q)
        self.assertEqual(self.analysis_errors(), [])


# ---------------- 判断题与 options_json ----------------

class TestJudgementAndOptions(ChapterRuleTestBase):

    def test_valid_judgement_passes(self):
        """判断题无选项或两个选项均可，不套用至少四选项规则。"""
        two_options = '[{"key":"A","text":"对"},{"key":"B","text":"错"}]'
        qs = [dict(qtype="判断", answerText="对", difficulty="1"),
              dict(qtype="判断", answerText="F", difficulty="2", optionsJson=two_options)]
        kp, q = self.write_chapter(["concept"], qs)
        _, errors = self.run_rules(kp, q)
        self.assertEqual(errors, [])

    def test_judgement_numeric_answer_rejected(self):
        """判断题 answerText=1 不在判定值白名单，必须拒绝。"""
        qs = [dict(qtype="判断", answerText="1", difficulty="1"),
              dict(qtype="判断", answerText="对", difficulty="2")]
        kp, q = self.write_chapter(["concept"], qs)
        _, errors = self.run_rules(kp, q)
        self.assertTrue(any("判断题 answerText" in e and "Q_8001" in e for e in errors))

    def test_judgement_five_options_rejected(self):
        """判断题给 5 个选项必须拒绝，不能按选择题放行。"""
        five_options = ('[{"key":"A","text":"1"},{"key":"B","text":"2"},'
                        '{"key":"C","text":"3"},{"key":"D","text":"4"},'
                        '{"key":"E","text":"5"}]')
        qs = [dict(qtype="判断", answerText="T", difficulty="1", optionsJson=five_options),
              dict(qtype="判断", answerText="对", difficulty="2")]
        kp, q = self.write_chapter(["concept"], qs)
        _, errors = self.run_rules(kp, q)
        self.assertTrue(any("恰好 2 个选项" in e and "Q_8001" in e for e in errors))


# ---------------- 严格/草稿模式接线 ----------------

class TestModeWiring(ChapterRuleTestBase):

    def test_strict_mode_blocks_import(self):
        """严格模式下章节规则违规应产生 ERROR 并以退出码 1 阻断。"""
        qs = [dict(qtype="填空", difficulty=d)
              for d in balanced_diffs(25)]  # 0 综合，触发 R1
        kp, q = self.write_chapter(["concept"] * 5, qs)
        rc = self.run_main(kp, q, strict=True)
        self.assertEqual(rc, 1)
        self.assertTrue(any("[章节规则-综合占比]" in e for e in vc.errors))

    def test_draft_mode_downgrades_to_warning(self):
        """草稿模式下章节规则降级为 WARNING，不阻断（退出码 0）。"""
        qs = [dict(qtype="填空", difficulty=d)
              for d in balanced_diffs(25)]
        kp, q = self.write_chapter(["concept"] * 5, qs)
        rc = self.run_main(kp, q, strict=False)
        self.assertEqual(rc, 0)
        self.assertFalse(any("[章节规则" in e for e in vc.errors))
        self.assertTrue(any("[章节规则-综合占比]" in w for w in vc.warnings))


# ---------------- R6 题源格式 ----------------

class TestR6SourceFormat(ChapterRuleTestBase):
    """R6：资料名（教材/习题册）必须伴随印刷页号；PDF 号必须伴随
    印刷页号；页码范围后必须跟数字。防止"PDF 号当印刷页号"再犯。"""

    def test_valid_formats(self):
        """各章实际使用的合法格式均不应误报。"""
        valid = [
            "教材 P129",
            "教材 P128—130；习题 4 第 3 题",
            "习题册 P42（PDF 57）",
            "习题册 PDF73（印刷P58）答案",
            "教材 P215—P217；7.3.3 扩展E-R模型",
            "教材习题 3 第 7 题（P123）+ 教材 P121—P122",
            "习题 5 第 8 题；教材 P152",
            "习题册第6章补充第5题；题目 PDF77（印刷P62）/答案 PDF80（印刷P65）",
        ]
        for s in valid:
            self.assertEqual(vc.check_source_format(s), [], msg=s)

    def test_bare_material_without_page(self):
        """ch01/ch02 场景：只写"习题册"/"教材"三个字必须报警。"""
        for s in ("习题册", "教材"):
            msgs = vc.check_source_format(s)
            self.assertEqual(len(msgs), 1, msg=s)
            self.assertIn("未标印刷页号", msgs[0])

    def test_pdf_number_without_printed_page(self):
        """只写 PDF 页号（无印刷页号）必须报警，且 PDF73 不被误认成 P73。"""
        for s in ("习题册 PDF57", "习题册 PDF73（答案）"):
            msgs = vc.check_source_format(s)
            self.assertTrue(any("只写了 PDF 页号" in m for m in msgs), msg=s)
            self.assertFalse(any(m.startswith("页码范围") for m in msgs))

    def test_page_range_formats(self):
        """范围写法：P129—130 / P129—P130 合法；P129— 残缺报警。"""
        self.assertEqual(vc.check_source_format("教材 P129—130"), [])
        self.assertEqual(vc.check_source_format("教材 P129—P130"), [])
        msgs = vc.check_source_format("教材 P129—；例 3.64")
        self.assertEqual(len(msgs), 1)
        self.assertIn("页码范围格式不规范", msgs[0])

    def test_empty_source(self):
        """source 为空必须报警（连资料名都没有）。"""
        msgs = vc.check_source_format("")
        self.assertEqual(len(msgs), 1)
        self.assertIn("为空", msgs[0])

    def test_wiring_strict_errors_and_draft_warns(self):
        """接线：题源裸标在严格模式报 ERROR、草稿模式降级 WARNING。"""
        kp, q = self.write_chapter(["concept"], [
            dict(qtype="填空", difficulty="1"),
            dict(qtype="填空", difficulty="2")])
        # 把第 2 题的 source 改成裸"习题册"（write_chapter 默认写"教材 P100"）
        import csv as _csv
        with open(q, encoding="utf-8-sig", newline="") as f:
            rows = list(_csv.DictReader(f))
        rows[1]["source"] = "习题册"
        with open(q, "w", encoding="utf-8-sig", newline="") as f:
            w = _csv.DictWriter(f, fieldnames=vc.Q_HEADER)
            w.writeheader(); w.writerows(rows)
        rc = self.run_main(kp, q, strict=True)
        self.assertEqual(rc, 1)
        self.assertTrue(any("题源格式不规范" in e and "Q_8002" in e
                            for e in vc.errors))
        self.run_main(kp, q, strict=False)
        self.assertTrue(any("题源格式不规范" in w and "Q_8002" in w
                            for w in vc.warnings))


if __name__ == "__main__":
    unittest.main()
