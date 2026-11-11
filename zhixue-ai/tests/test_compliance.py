from app.services.compliance import (
    BLOCK_TIP,
    compliance_check,
    compliance_probability,
    containment_score,
)


def test_blocks_homework_request() -> None:
    blocked, tip = compliance_check("老师，帮我写作业，直接给答案")
    assert blocked is True
    assert tip == BLOCK_TIP


def test_allows_normal_question() -> None:
    blocked, tip = compliance_check("关系模型的三要素是什么？")
    assert blocked is False
    assert tip is None


def test_empty_text_is_allowed() -> None:
    assert compliance_check("") == (False, None)


# ---- 模糊拦截路径（升级新增）：空格/标点/语序扰动不得绕过 ----

def test_blocks_spaced_keyword_variant() -> None:
    """旧版精确子串被空格拆词绕过；新版归一化后仍拦截。"""
    assert compliance_check("帮我 写 作业")[0] is True
    assert compliance_check("直接、给、答、案")[0] is True


def test_blocks_word_order_paraphrase() -> None:
    assert compliance_check("作业帮我写一下呗")[0] is True
    assert compliance_check("这道题能不能直接给我答案")[0] is True


def test_blocks_mild_help_request() -> None:
    assert compliance_check("请帮我做一下这道数据库作业")[0] is True


def test_allows_questions_mentioning_homework() -> None:
    """正常提问中包含"作业/答案"字样不得误伤。"""
    assert compliance_check("数据库作业什么时候交")[0] is False
    assert compliance_check("帮我讲解一下第三范式")[0] is False
    assert compliance_check("B+树索引的叶子节点存什么")[0] is False


def test_score_separates_positive_and_negative_samples() -> None:
    """判决边界可分：正样本包含度显著高于负样本（τ=0.75 居中）。"""
    positive = ["作业帮我写一下呗", "直接给我答案吧", "求完整答案不要过程"]
    negative = ["数据库作业什么时候交", "什么是ACID特性", "帮我讲解一下视图的原理"]
    assert all(containment_score(t) >= 0.75 for t in positive)
    assert all(containment_score(t) < 0.50 for t in negative)


def test_probability_is_monotonic_and_bounded() -> None:
    """P(block) 随包含度单调递增，边界平滑且 ∈ [0,1]。"""
    low = compliance_probability("什么是视图")
    high = compliance_probability("直接给我答案")
    assert 0.0 <= low < 0.5 <= high <= 1.0
    mid = compliance_probability("帮我做一下这道题")
    assert low <= mid <= high


def test_short_intent_keyword_still_exact_match() -> None:
    """「代写」<4 字只走精确匹配：命中拦截，但模糊同义词不误伤。"""
    assert compliance_check("这门课的报告能帮我代写吗")[0] is True
    # 不含"代写"字面且无 ≥4 字意图证据 → 不拦截
    assert compliance_check("代表的写作思路是什么")[0] is False
