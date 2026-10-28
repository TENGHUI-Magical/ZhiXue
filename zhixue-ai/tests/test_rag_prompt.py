"""Prompt 组装：检索切片必须进入 L1/L2 Prompt，而不是只当引用列表。"""
from app.schemas import RagExplainRequest
from app.services.rag_service import _prompt
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
