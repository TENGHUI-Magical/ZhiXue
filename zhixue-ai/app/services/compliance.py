"""合规拦截服务：代写/直接要答案意图识别（骨架版）。

正式版扩展至 Prompt 注入特征与代写意图识别，命中结果计入 ai_generation_log.is_blocked。
"""

from __future__ import annotations

# 合规拦截词表（骨架版）：正式版扩展至 Prompt 注入特征与代写意图识别
BLOCK_KEYWORDS = ["直接给答案", "帮我写作业", "代写", "求出完整答案不要过程"]

BLOCK_TIP = "检测到代写请求倾向。本系统仅提供启发式讲解与思路引导，请独立完成作答。"


def compliance_check(text: str) -> tuple[bool, str | None]:
    """命中代写意图 → 返回学术诚信提示，讲解定位为「思路点拨」而非「答案交付」。"""
    for keyword in BLOCK_KEYWORDS:
        if keyword in text:
            return True, BLOCK_TIP
    return False, None
