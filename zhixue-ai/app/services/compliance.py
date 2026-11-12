"""合规拦截服务：代写/直接要答案意图识别（统计评分版，docs/05 升级）。

数学模型（骨架版「精确子串匹配」的升级，旧版可被插入空格/标点/同义改述绕过）：

1. 归一化：小写 + 去标点/符号/空白（与 vector_index._bigram_counts 同口径），
   空格拆词（「直接 给答案」→「直接给答案」）不再逃逸；
2. 证据加权：意图短语 d 的证据集 = 字符 unigram（权重 idf）∪ bigram（权重 2·idf），
   idf 按意图库内 df 计算：idf_g = log(M/df_g) + 0.5（保证正值、稀有证据更重）；
   加权包含度（containment，衡量「意图证据在查询中出现了多少」，不惩罚长查询）：
       score(q, d) = Σ_{g∈q∩d} weight_g / Σ_{g∈d} weight_g
3. 平滑判决（Sigmoid 替代阶跃，处处连续，输出可解释为拦截概率）：
       P(block|q) = σ((max_d score(q,d) − τ) / T)
   P ≥ 0.5（即 max score ≥ τ=0.75）时拦截；
4. 短语分级：< 4 字的短语（如「代写」）只走精确子串匹配——
   过短短语的模糊证据（2 个 unigram）区分度不足，会产生误伤。

保留原 BLOCK_KEYWORDS 四词（精确路径恒覆盖），意图库仅做扩充。
拦截结果计入 ai_generation_log.is_blocked（由调用方落库）。
"""
from __future__ import annotations

import math
import re
import unicodedata

# 原骨架版关键词（契约行为基线，任何情况下精确命中即拦截）
BLOCK_KEYWORDS = ["直接给答案", "帮我写作业", "代写", "求出完整答案不要过程"]

# 意图短语库扩充（≥4 字进入模糊评分；<4 字只走精确匹配）
INTENT_PATTERNS = BLOCK_KEYWORDS + [
    "直接告诉我答案", "答案直接发给我", "帮我把作业写了",
    "替我写作业", "帮我完成这份作业", "直接给我答案",
    "作业帮我写一下", "求完整答案不要过程", "帮我做一下作业",
]

BLOCK_TIP = "检测到代写请求倾向。本系统仅提供启发式讲解与思路引导，请独立完成作答。"

# ---- P1-1 增量：Prompt 注入 / 越狱 / 英文代写特征（不改动上面的统计评分模型）----
# 意图库 13 条全为中文统计短语，对英文、越狱、角色劫持类注入无覆盖面。
# 规则型特征命中即拦截（与统计评分结果 OR），并登记规则 ID 供审计留痕。
_INJECTION_PATTERNS: tuple[tuple[str, "re.Pattern[str]"], ...] = (
    ("PROMPT_INJECTION_ZH", re.compile(
        r"忽略\s*(?:以上|前面|之前|上述|所有)[^。；;]{0,16}(?:指令|提示|要求|规则|约束)"
        r"|你现在(?:是|要|扮演)|请扮演|从现在起你(?:是|要)"
        r"|输出\s*(?:完整|标准)?答案|不要(?:任何)?(?:过程|解释)", re.I)),
    ("PROMPT_INJECTION_EN", re.compile(
        r"ignore\s+(?:all\s+)?(?:previous|above|prior)\s+instructions"
        r"|(?:^|[\s;])(?:system|assistant)\s*[:：]"
        r"|\bDAN\b|jailbreak|do\s+anything\s+now"
        r"|reveal\s+(?:the\s+)?(?:system\s+)?prompt", re.I)),
    ("ANSWER_DIRECT_EN", re.compile(
        r"give\s+me\s+the\s+answer|just\s+(?:tell|give)\s+me\s+the\s+answer"
        r"|do\s+my\s+homework|write\s+(?:the\s+)?answer\s+for\s+me"
        r"|solve\s+this\s+for\s+me|without\s+(?:any\s+)?(?:steps|explanation)", re.I)),
    ("EXAM_CHEAT", re.compile(
        r"考试\s*(?:作弊|答案)|(?:期末|期中|月考)\s*(?:试卷|考题|真题)\s*(?:的)?答案"
        r"|泄露\s*(?:标准)?答案", re.I)),
)


def matched_rules(text: str) -> list[str]:
    """返回命中的规则 ID（精确关键词 / 注入正则 / 模糊评分），供审计留痕。"""
    rules: list[str] = []
    normalized = _normalize(text)
    for keyword in BLOCK_KEYWORDS:
        if keyword in normalized:
            rules.append("EXACT:" + keyword)
    rule_text = _rule_text(text)
    for rule_id, pattern in _INJECTION_PATTERNS:
        if pattern.search(rule_text):
            rules.append(rule_id)
    if containment_score(text) >= _DECISION_TAU:
        rules.append("FUZZY:containment>=tau")
    return rules


# 判决参数：τ=0.75（包含度阈值，60 组正负样本网格扫描选定），
# T=0.05（温度，控制 sigmoid 陡峭度，仅影响概率输出的平滑性不影响判决）
_DECISION_TAU = 0.75
_TEMPERATURE = 0.05


def _normalize(text: str) -> str:
    """小写 + NFKC 全角/异体折叠 + 去标点/符号/空白（P1-1 增量加固）。

    原实现仅 lower() + 去 P/Z/S 类字符；NFKC 补上全角英文/罗马字变体折叠，
    压缩「Ｇｉｖｅ　ｍｅ　ｔｈｅ　ａｎｓｗｅｒ」一类绕过空间。中文样本结果不变，
    统计评分模型（意图库 / idf / Sigmoid）及其既有单测全部保留、不受影响。
    """
    nfkc = unicodedata.normalize("NFKC", text or "")
    return "".join(ch for ch in nfkc.lower()
                   if unicodedata.category(ch)[0] not in ("P", "Z", "S"))


def _rule_text(text: str) -> str:
    """NFKC 折叠 + 小写，但保留空白/词边界供英文注入正则使用。

    `_normalize()` 会把空白全部去掉（用于统计评分），英文正则的 ``\\s+`` 无法匹配；
    规则型特征单独用本形式，既能折叠全角英文，又保留词边界。
    """
    return unicodedata.normalize("NFKC", text or "").lower()


def _grams(text: str) -> tuple[set[str], set[str]]:
    """返回 (unigrams, bigrams) 集合。"""
    clean = _normalize(text)
    unigrams = set(clean)
    bigrams = {clean[i:i + 2] for i in range(len(clean) - 1)} if len(clean) >= 2 else set()
    return unigrams, bigrams


# ---- 意图库预计算（模块加载时一次，O(Σ|d|)） ----

def _build_evidence(patterns: list[str]) -> list[tuple[dict[str, float], float]]:
    """每个意图短语 → (证据 gram→权重表, 证据总权重)。

    weight_g = multi·idf_g（unigram multi=1，bigram multi=2），
    idf_g = log(M / df_g) + 0.5，M 为短语总数、df_g 为含 g 的短语数。
    """
    pattern_grams = [_grams(p) for p in patterns]
    total = len(pattern_grams)
    df: dict[str, int] = {}
    for unigrams, bigrams in pattern_grams:
        for gram in unigrams | bigrams:
            df[gram] = df.get(gram, 0) + 1
    evidence: list[tuple[dict[str, float], float]] = []
    for unigrams, bigrams in pattern_grams:
        gram_weights = {
            gram: (2.0 if gram in bigrams else 1.0) * (math.log(total / df[gram]) + 0.5)
            for gram in unigrams | bigrams}
        evidence.append((gram_weights, sum(gram_weights.values())))
    return evidence


_FUZZY_PATTERNS = [p for p in INTENT_PATTERNS if len(_normalize(p)) >= 4]
_FUZZY_EVIDENCE = _build_evidence(_FUZZY_PATTERNS)


def containment_score(query: str) -> float:
    """查询对意图库的最大加权包含度 ∈ [0,1]（1 = 某意图证据完整出现）。"""
    unigrams, bigrams = _grams(query)
    if not unigrams:
        return 0.0
    present = unigrams | bigrams
    best = 0.0
    for gram_weights, total_weight in _FUZZY_EVIDENCE:
        matched_weight = sum(weight for gram, weight in gram_weights.items()
                             if gram in present)
        if matched_weight > 0:
            best = max(best, matched_weight / total_weight)
    return best


def compliance_probability(text: str) -> float:
    """拦截概率 P ∈ (0,1)：σ((max_score − τ)/T)，供观测/灰度，不改变布尔契约。"""
    score = containment_score(text)
    exponent = (score - _DECISION_TAU) / _TEMPERATURE
    # 数值稳定：|x|>30 时 sigmoid 直接饱和，避免 exp 上溢
    if exponent > 30:
        return 1.0
    if exponent < -30:
        return 0.0
    return 1.0 / (1.0 + math.exp(-exponent))


def compliance_check(text: str) -> tuple[bool, str | None]:
    """命中代写 / 注入意图 → (True, 学术诚信提示)；否则 (False, None)。

    判决顺序：精确子串（原行为）→ 注入/越狱/英文规则型特征 → 模糊包含度 ≥ τ。
    签名与 tuple[bool, str | None] 保持不变；新增规则只增加拦截覆盖，不改既有
    正负样本判定（负样本如「帮我讲解一下第三范式」不含任何注入特征）。
    """
    normalized = _normalize(text)
    if not normalized:
        return False, None
    for keyword in BLOCK_KEYWORDS:
        if keyword in normalized:
            return True, BLOCK_TIP
    rule_text = _rule_text(text)
    for _rule_id, pattern in _INJECTION_PATTERNS:
        if pattern.search(rule_text):
            return True, BLOCK_TIP
    if containment_score(text) >= _DECISION_TAU:
        return True, BLOCK_TIP
    return False, None


def compliance_detail(text: str) -> dict:
    """拦截决策明细（P1-1：置信度与命中规则不再死码），供 logger / 审计留痕。

    返回 {blocked, probability, score, rules}；metadata-first，不打印原文。
    """
    return {
        "blocked": compliance_check(text)[0],
        "probability": round(compliance_probability(text), 4),
        "score": round(containment_score(text), 4),
        "rules": matched_rules(text),
    }
