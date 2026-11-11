r"""OCR 置信度统计聚合：长度加权均值 + Wilson 95% 下界（docs/05 口径升级）。

数学定义
--------
设一页 OCR 输出 $L$ 行，第 $i$ 行文本长度 $l_i$、引擎置信度 $c_i\in[0,1]$：

- 旧口径（保留为 mean 字段，向后兼容）：$\\bar c = \\frac{1}{L}\\sum_i c_i$
  —— 行数当样本量，1 行 0.98 的空页与 30 行 0.90 的满页不可区分。
- 长度加权均值：$\\hat c = \\frac{\\sum_i l_i c_i}{\\sum_i l_i}$
  —— 每个字符是一次识别试验，长行证据权重更高。
- Kish 有效样本量：$n_{\\mathrm{eff}} = \\frac{(\\sum_i l_i)^2}{\\sum_i l_i^2}$
  —— 加权比例的方差修正，介于 1（单行垄断）与 L（等长）之间。
- Wilson 95% 下界（z=1.96）：
  $c_{\\mathrm{low}} = \\frac{\\hat c + \\tfrac{z^2}{2n} - z\\sqrt{\\tfrac{\\hat c(1-\\hat c)}{n} + \\tfrac{z^2}{4n^2}}}{1 + \\tfrac{z^2}{n}}$
  —— 小样本自动保守：证据越少下界越低，单行高置信不再能伪装整页合格。

纯函数、无第三方依赖；生产路由 /tools/ocr_batch.py 与 data_pipeline 共用。
"""
from __future__ import annotations

import math
from dataclasses import dataclass

Z_95 = 1.959963984540054  # Φ⁻¹(0.975)


@dataclass(frozen=True)
class OcrConfidence:
    """一页 OCR 的置信度族：mean 兼容旧契约，wilson_lower 供质量门禁。"""
    mean: float            # 行数算术平均（旧口径，兼容展示）
    weighted: float        # 字符长度加权均值
    wilson_lower: float    # Wilson 95% 下界（脏样本判定用）
    n_eff: float           # 有效样本量（Kish）
    line_count: int
    char_count: int


def wilson_lower_bound(p_hat: float, n: float, z: float = Z_95) -> float:
    """二项比例的 Wilson 置信下界；n<=0 或 p∉[0,1] 时返回 0（无证据即不自信）。"""
    if n <= 0 or p_hat < 0.0 or p_hat > 1.0:
        return 0.0
    denom = 1.0 + z * z / n
    center = p_hat + z * z / (2.0 * n)
    margin = z * math.sqrt(p_hat * (1.0 - p_hat) / n + z * z / (4.0 * n * n))
    return max(0.0, (center - margin) / denom)


def aggregate_confidence(lines: list[tuple[str, float]]) -> OcrConfidence:
    """从 (text, score) 行列表计算置信度族。空列表全零（无证据 → 不自信）。"""
    if not lines:
        return OcrConfidence(0.0, 0.0, 0.0, 0.0, 0, 0)
    lengths = [max(0, len(text)) for text, _ in lines]
    scores = [min(1.0, max(0.0, float(score))) for _, score in lines]
    line_count = len(lines)
    char_count = sum(lengths)
    mean = sum(scores) / line_count
    if char_count <= 0:  # 全是空串行：仅剩行数证据
        return OcrConfidence(round(mean, 4), round(mean, 4),
                             round(wilson_lower_bound(mean, line_count), 4),
                             float(line_count), line_count, 0)
    weighted = sum(l * c for l, c in zip(lengths, scores)) / char_count
    n_eff = char_count * char_count / sum(l * l for l in lengths if l > 0) \
        if any(l > 0 for l in lengths) else float(line_count)
    return OcrConfidence(round(mean, 4), round(weighted, 4),
                         round(wilson_lower_bound(weighted, n_eff), 4),
                         round(n_eff, 2), line_count, char_count)
