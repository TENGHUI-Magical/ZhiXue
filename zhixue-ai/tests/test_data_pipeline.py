"""data_pipeline 与 ocr_quality 的数学正确性测试（无网络、无 OCR 引擎依赖）。"""
from __future__ import annotations

import json
import random

import numpy as np
import pytest

from app.services.ocr_quality import aggregate_confidence, wilson_lower_bound
from data_pipeline import dataset_builder as ds
from data_pipeline import image_math as im
from data_pipeline.web_crawler import extract_text


# ---------- image_math ----------

def test_grayscale_weighted_sum():
    rgb = np.full((4, 4, 3), 0, dtype=np.uint8)
    rgb[:, :, 0] = 100  # R=100 → 0.299*100 ≈ 30
    gray = im.to_grayscale(rgb)
    assert gray.shape == (4, 4)
    assert abs(int(gray[0, 0]) - 30) <= 1


def test_gaussian_kernel_normalized_and_symmetric():
    kernel = im.gaussian_kernel_1d(sigma=1.5)
    assert abs(kernel.sum() - 1.0) < 1e-12
    assert np.allclose(kernel, kernel[::-1])
    assert kernel.size % 2 == 1


def test_gaussian_blur_constant_image_unchanged():
    constant = np.full((50, 60), 128, dtype=np.uint8)
    assert np.all(im.gaussian_blur(constant, sigma=1.0) == 128)


def test_gaussian_blur_reduces_noise_variance():
    rng = np.random.default_rng(7)
    clean = np.zeros((80, 80), dtype=np.uint8)
    clean[40:, :] = 200  # 阶跃边缘
    noisy = np.clip(clean.astype(int) + rng.normal(0, 30, clean.shape), 0, 255)
    denoised = im.gaussian_blur(noisy.astype(np.uint8), sigma=2.0).astype(float)
    # 平坦区（远离边缘）噪声方差应显著下降
    flat_noisy = noisy[5:20, 5:20].astype(float).std()
    flat_denoised = denoised[5:20, 5:20].std()
    assert flat_denoised < flat_noisy * 0.6


def test_otsu_threshold_separates_bimodal():
    rng = np.random.default_rng(3)
    dark = rng.normal(60, 8, 3000).clip(0, 255)
    bright = rng.normal(200, 8, 3000).clip(0, 255)
    image = np.concatenate([dark, bright]).astype(np.uint8)
    threshold = im.otsu_threshold(image)
    assert 90 < threshold < 170  # 落在两峰之间的谷


def test_binarize_dark_text_on_light_background():
    gray = np.full((40, 40), 240, dtype=np.uint8)
    gray[10:20, 10:30] = 40  # 深色文字块
    binary, threshold = im.binarize(gray)
    assert binary[15, 20] == 255  # 前景（文字）=255
    assert binary[0, 0] == 0      # 背景=0
    # 两峰间无像素时 Otsu 取首个最大类间方差点（可落在低峰上），但必须可分
    assert 40 <= threshold < 240


def _synthetic_text_rows(h: int = 400, w: int = 600) -> np.ndarray:
    """白底黑字行：模拟扫描件文本行投影。"""
    gray = np.full((h, w), 255, dtype=np.uint8)
    for top in range(60, h - 40, 80):
        gray[top:top + 18, 40:w - 40] = 30
    return gray


def test_deskew_recovers_known_tilt():
    upright = _synthetic_text_rows()
    skewed = im._rotate_nearest(upright, 3.0)      # 人为倾斜 +3°
    binary, _ = im.binarize(skewed)
    estimated = im.deskew_angle(binary)
    assert abs(estimated - (-3.0)) <= 1.0          # 校正角应为 -3°（容差 1°）


def test_deskew_roundtrip_near_original():
    upright = _synthetic_text_rows()
    corrected, angle = im.deskew(im._rotate_nearest(upright, 2.0))
    assert abs(angle - (-2.0)) <= 1.0
    # 行投影方差：校正后应接近原图（歪斜被消除）
    var_upright = upright.sum(axis=1).astype(float).var()
    var_corrected = corrected.sum(axis=1).astype(float).var()
    var_skewed = im._rotate_nearest(upright, 2.0).sum(axis=1).astype(float).var()
    assert var_corrected > var_skewed
    assert var_corrected > var_upright * 0.5


# ---------- ocr_quality ----------

def test_wilson_lower_bound_conservative_for_small_n():
    # 同样 p̂=0.9：n=5 的下界应远低于 n=500
    assert wilson_lower_bound(0.9, 5) < wilson_lower_bound(0.9, 500)
    # 极端：无证据 → 0
    assert wilson_lower_bound(0.9, 0) == 0.0


def test_aggregate_single_high_conf_line_is_conservative():
    """1 行 0.98：旧口径 0.98 看似优秀；Wilson 下界必须显著低于整页 100 行 0.90。"""
    single = aggregate_confidence([("唯一一行很长的文本", 0.98)])
    full = aggregate_confidence([("第" + "x" * 18 + "行", 0.90)] * 100)
    assert single.mean == pytest.approx(0.98)
    assert single.wilson_lower < 0.5           # 小样本被大幅压低（n_eff≈1）
    assert full.wilson_lower > 0.80            # 大样本仅小幅收缩
    assert single.wilson_lower < full.wilson_lower


def test_aggregate_length_weighting():
    """长行低置信应把加权均值拉到低于算术平均。"""
    stats = aggregate_confidence([("短", 0.99), ("这" * 99, 0.10)])
    assert stats.weighted < stats.mean
    assert 0 < stats.n_eff < 2.0              # 单行垄断 → 有效样本量趋近 1


def test_aggregate_empty_is_zero():
    stats = aggregate_confidence([])
    assert (stats.mean, stats.weighted, stats.wilson_lower) == (0.0, 0.0, 0.0)


# ---------- dataset_builder ----------

def test_simhash_identical_and_near_duplicate():
    base = "关系数据库的标准语言SQL支持数据定义、数据查询、数据操纵和数据控制，" * 4
    same = base
    near = base.replace("控制", "控侧")  # 单字符扰动（OCR 形近噪声）
    far = "事务的ACID特性包括原子性、一致性、隔离性和持久性，" * 4
    h_base, h_same = ds.simhash(base), ds.simhash(same)
    h_near, h_far = ds.simhash(near), ds.simhash(far)
    assert bin(h_base ^ h_same).count("1") == 0
    assert bin(h_base ^ h_near).count("1") <= 6    # 近重复：海明距离小（实测 4）
    assert bin(h_base ^ h_far).count("1") > 10     # 无关文本：距离大（实测 33）


def test_dedup_removes_exact_and_near_duplicates():
    records = [
        {"id": "a", "text": "关系数据库的标准语言SQL支持数据定义、数据查询、数据操纵和数据控制，" * 4,
         "source": "wiki"},
        {"id": "b", "text": "关系数据库的标准语言SQL支持数据定义、数据查询、数据操纵和数据控制，" * 4,
         "source": "wiki"},  # 精确重复
        {"id": "c", "text": "关系数据库的标准语言SQL支持数据定义、数据查询、数据操纵和数据控侧，" * 4,
         "source": "wiki"},  # 近重复（1 字符）
        {"id": "d", "text": "事务的ACID特性包括原子性、一致性、隔离性和持久性，" * 4, "source": "book"},
        {"id": "e", "text": "B+树索引的所有关键字都出现在叶子节点，非叶节点仅作索引导向，" * 4,
         "source": "book"},
    ]
    kept, removed = ds.dedup(records)
    kept_ids = {r["id"] for r in kept}
    removed_ids = {r["id"] for r in removed}
    assert kept_ids == {"a", "d", "e"}       # 重复组保留最长/首个
    assert removed_ids == {"b", "c"}


def test_iqr_filter_removes_length_outlier():
    records = [{"id": i, "text": "x" * n, "textLen": n}
               for i, n in enumerate([100] * 20 + [10000])]
    kept, outliers = ds.iqr_filter(records)
    assert len(kept) == 20
    assert [r["id"] for r in outliers] == [20]


def test_stratified_split_preserves_label_proportions():
    records = [{"id": i, "label": "A" if i < 60 else "B", "text": f"t{i}"}
               for i in range(100)]
    train, val, test = ds.stratified_split(records, label_key="label", seed=7)
    assert len(train) + len(val) + len(test) == 100
    # 标签比例保持：A:B = 60:40 → train 中 A 占比 ≈ 0.6
    a_in_train = sum(1 for r in train if r["label"] == "A")
    assert abs(a_in_train / len(train) - 0.6) < 0.05
    # 确定性：同种子两次切分完全一致
    again = ds.stratified_split(records, label_key="label", seed=7)
    assert [r["id"] for r in train] == [r["id"] for r in again[0]]


def test_monte_carlo_augment_deterministic_and_corrupts():
    records = [{"text": "关系模型由关系数据结构、关系操作集合和关系完整性约束三部分组成", "source": "s"}]
    first = ds.monte_carlo_augment(records, 50, seed=42)
    second = ds.monte_carlo_augment(records, 50, seed=42)
    assert first == second                     # 同种子可复现
    assert len(first) == 50
    assert all(r["synthetic"] and r["target"] == records[0]["text"] for r in first)
    corrupted = sum(1 for r in first if r["input"] != r["target"])
    # 形近替换只作用于可混淆字符，删/插作用于任意字符：期望编辑率 4%+/字符
    assert corrupted >= 25
    # 编辑距离应远小于文本长度（轻噪声，不是毁灭性破坏）
    max_edits = max(abs(len(r["input"]) - len(r["target"])) for r in first)
    assert max_edits <= len(records[0]["text"]) * 0.15


def test_corrupt_text_expected_edit_rate():
    rng = random.Random(1)
    text = "数据库系统概论第六版第三章关系数据库标准语言SQL" * 2  # 26 字
    distances = []
    for _ in range(200):
        distances.append(len(ds.corrupt_text(text, rng)))
    # 期望编辑率 8%（p_d+p_i+p_s），长度变化应在 ±15% 内
    assert abs(np.mean(distances) - len(text)) < len(text) * 0.15


# ---------- web_crawler.extract_text（离线） ----------

def test_extract_text_strips_script_and_style():
    html = ("<html><head><title>关系模型 - 维基百科</title>"
            "<style>body{color:red}</style></head>"
            "<body><nav>导航</nav><p>关系模型三要素</p>"
            "<script>alert(1)</script></body></html>")
    title, text, _links = extract_text(html)
    assert title == "关系模型 - 维基百科"
    assert "关系模型三要素" in text
    assert "alert" not in text and "color:red" not in text


def test_dataset_build_end_to_end(tmp_path):
    """端到端冒烟：JSONL 输入 → 去重 → 清洗 → 分层 → 增广 → 三格式输出。"""
    rows = []
    for i in range(30):
        rows.append({"text": f"知识点{i}：事务的ACID特性包括原子性和一致性第{i}号" * 2,
                     "source": "synthetic", "textLen": 30})
    # 混入重复与异常
    rows.append(dict(rows[0]))
    rows.append({"text": "异常长样本" * 500, "source": "synthetic", "textLen": 2000})
    input_path = tmp_path / "in.jsonl"
    input_path.write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in rows),
                          encoding="utf-8")
    stats = ds.build([input_path], tmp_path / "out", label_key="source", augment=100)
    assert stats["input"] == 32
    assert stats["duplicates_removed"] == 1
    assert stats["iqr_outliers_removed"] == 1
    assert stats["synthetic_generated"] == 100
    assert stats["train"] + stats["val"] + stats["test"] == 30 + 100
    train = [json.loads(line) for line in
             (tmp_path / "out" / "train.jsonl").read_text(encoding="utf-8").splitlines()]
    assert len(train) == stats["train"]
    assert all(set(r) >= {"input", "target", "synthetic"} for r in train if r.get("synthetic"))
