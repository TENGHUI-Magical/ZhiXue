from app.services.compliance import BLOCK_TIP, compliance_check


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
