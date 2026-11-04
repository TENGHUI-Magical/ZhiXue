"""Prompt 组装：检索切片必须进入 L1/L2 Prompt，而不是只当引用列表。"""
from app.schemas import PrereqWeakness, RagExplainRequest
from app.services.rag_service import (
    PROMPT_HARD_LIMIT_TOKENS, PROMPT_VERSION, _prompt, _request_payload, _template_chunks)

from app.services.vector_index import Chunk, Hit


def _hit(kp_id: str, content: str) -> Hit:
    return Hit(Chunk(f"{kp_id}#1", kp_id, kp_id, 1, None, "P1", content), 0.5, 0.5, 0.5)


def test_prompt_includes_retrieved_chunks():
    req = RagExplainRequest(kpId="KP0201", kpName="关系模型", conceptText="概念原文",
                            questionText="三要素是什么")
    messages = _prompt(req, [_hit("KP0202", "候选码的定义"), _hit("KP0203", "主码的选择")])
    user = messages[1]["content"]
    assert "检索切片" in user
    assert "[KP0202] 候选码的定义" in user
    assert "[KP0203] 主码的选择" in user
    assert "概念原文" in user


def test_prompt_without_hits_keeps_original_shape():
    req = RagExplainRequest(kpId="KP0201", kpName="关系模型", conceptText="概念原文")
    user = _prompt(req, [])[1]["content"]
    assert "检索切片" not in user
    assert "概念原文" in user


def test_prompt_never_includes_answer_text():
    req = RagExplainRequest(kpId="KP0201", kpName="关系模型", conceptText="概念原文",
                            answerText="SECRET_ANSWER")
    user = _prompt(req, [])[1]["content"]
    assert "SECRET_ANSWER" not in user


def test_prompt_v2_assembles_student_evidence_in_fixed_order():
    req = RagExplainRequest(
        kpId="KP0201", kpName="关系模型", conceptText="概念原文",
        misconception="误区一\n误区二\n误区三", textbookPage="P34",
        questionText="题干原文", myAnswer="学生作答原文",
        attemptCount=4, wrongCount=3, errorType="CONCEPT_CONFUSION",
        prereqWeakness=PrereqWeakness(
            kpId="KP0101", kpName="集合", mastery=0.25,
            sampleCount=3, conceptText="先修概念提示",
        ),
    )

    messages = _prompt(req, [])

    assert PROMPT_VERSION == "v2"
    assert messages[1]["content"] == (
        "[当前问题]\n"
        "知识点：关系模型（KP0201）\n"
        "错因标签：CONCEPT_CONFUSION\n"
        "作答次数：第 4 次作答，其中第 3 次答错\n"
        "[学生证据]\n"
        "题干：题干原文\n"
        "最近作答：学生作答原文\n"
        "[教材证据]\n"
        "教材页码：P34\n"
        "概念切片：概念原文\n"
        "常见误区：\n- 误区一\n- 误区二\n"
        "[先修弱项]\n"
        "先修知识点：集合（KP0101）\n"
        "掌握度：0.250；样本数：3\n"
        "概念提示：先修概念提示\n"
        "[输出格式]\n"
        "【错因定位】不超过 3 句，只分析这一步为什么错。\n"
        "【完整讲解】不超过 3 段，每段不超过 120 字。\n"
        "【自查】只提出 1 个能动手验证的问题。"
    )


def test_prompt_without_answer_explicitly_forbids_fabrication():
    req = RagExplainRequest(kpId="KP0201", kpName="关系模型", conceptText="概念原文")

    messages = _prompt(req, [])

    assert "最近作答：未提供具体作答" in messages[1]["content"]
    assert "作答次数：第 0 次作答，其中第 0 次答错" in messages[1]["content"]
    assert "未提供具体作答时不得虚构学生步骤" in messages[0]["content"]


def test_prompt_budget_truncates_retrieval_before_prereq_and_preserves_answer():
    req = RagExplainRequest(
        kpId="KP0201", kpName="关系模型", conceptText="概" * 900,
        misconception="误" * 400, textbookPage="P34", questionText="题" * 1000,
        myAnswer="答" * 500, attemptCount=4, wrongCount=3,
        prereqWeakness=PrereqWeakness(
            kpId="KP0101", kpName="集合", mastery=0.25,
            sampleCount=3, conceptText="先" * 300,
        ),
    )

    messages = _prompt(req, [_hit("KP0201", "检" * 800), _hit("KP0201", "索" * 800)])
    user = messages[1]["content"]

    assert sum(len(message["content"]) for message in messages) <= 3000
    assert f"[KP0201] {'检' * 400}" in user
    assert f"[KP0201] {'索' * 400}" not in user
    assert f"概念提示：{'先' * 200}" in user
    assert f"最近作答：{'答' * 300}" in user
    assert "作答次数：第 4 次作答，其中第 3 次答错" in user
    assert "教材页码：P34" in user


def test_prompt_caps_unbounded_metadata_fields():
    """kpName/kpId/errorType/textbookPage 来自请求体，超长时必须被截断，不能突破硬上限。"""
    huge = "超" * 20000
    req = RagExplainRequest(kpId=huge, kpName=huge, errorType=huge, textbookPage=huge)

    user = _prompt(req, [])[1]["content"]

    assert len(user) <= PROMPT_HARD_LIMIT_TOKENS


def test_prompt_page_cap_matches_column_width():
    """textbook_page 列宽 50：50 字原样保留，51 字才截断，避免截断列内合法值。"""
    page50 = "P" + "1" * 49
    page51 = page50 + "2"

    user50 = _prompt(RagExplainRequest(kpId="KP0201", textbookPage=page50), [])[1]["content"]
    user51 = _prompt(RagExplainRequest(kpId="KP0201", textbookPage=page51), [])[1]["content"]
    card = "".join(_template_chunks(RagExplainRequest(kpId="KP0201", textbookPage=page51)))

    assert page50 in user50
    assert page51 not in user51
    assert page50 in user51
    assert page51 not in card
    assert page50 in card


def test_request_payload_limits_output_to_700_tokens():
    messages = [{"role": "user", "content": "题干"}]

    assert _request_payload("deepseek-chat", messages, True) == {
        "model": "deepseek-chat",
        "messages": messages,
        "stream": True,
        "temperature": 0.3,
        "max_tokens": 700,
        "stream_options": {"include_usage": True},
    }
