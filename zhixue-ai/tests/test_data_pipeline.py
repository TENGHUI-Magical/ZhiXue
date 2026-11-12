"""data_pipeline 与 ocr_quality 的数学正确性测试（无网络、无 OCR 引擎依赖）。"""
from __future__ import annotations

import asyncio
import json
import random

import numpy as np
import pytest

from app.services.ocr_quality import aggregate_confidence, wilson_lower_bound
from data_pipeline import calibration as cal
from data_pipeline import dataset_builder as ds
from data_pipeline import image_math as im
from data_pipeline.web_crawler import WebCrawler, extract_text


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


def test_dedup_handles_many_empty_and_identical_texts():
    """空文本 SimHash 全 0、同文距离 0：不得对 k 条产生 C(k,2) 候选对（O(n²) 内存）。"""
    records = [{"id": f"empty{i}", "text": ""} for i in range(200)]
    records += [{"id": f"same{i}", "text": "事务的ACID特性包括原子性、一致性" * 3}
                for i in range(200)]
    records.append({"id": "other", "text": "B+树索引的所有关键字都出现在叶子节点" * 3})
    kept, removed = ds.dedup(records)
    assert {r["id"] for r in kept} == {"empty0", "same0", "other"}
    assert len(removed) == 398  # 401 条 → 3 组，每组保留 1 条


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


def test_stable_bucket_is_pinned_across_processes():
    """分桶不得依赖内置 hash()（PYTHONHASHSEED 随机化会让分层抽样跨进程漂移）。"""
    assert ds._stable_bucket("A") == 291
    assert ds._stable_bucket("B") == 438
    assert ds._stable_bucket("wiki") == 111


def test_near_duplicate_pairs_matches_bruteforce():
    """分块候选对枚举与全量海明距离暴力解一致（含块边界）。"""
    base = np.uint64(0xDEADBEEF12345678)
    signatures = [base ^ np.uint64((1 << k) - 1) for k in range(12)]
    rng = np.random.default_rng(11)
    signatures.extend(rng.integers(0, 2 ** 63, size=9, dtype=np.uint64).tolist())
    signatures = np.asarray(signatures, dtype=np.uint64)
    fast = set(ds._near_duplicate_pairs(signatures, max_hamming=8, block=5))
    brute = {(i, j) for i in range(len(signatures)) for j in range(i + 1, len(signatures))
             if bin(int(signatures[i]) ^ int(signatures[j])).count("1") <= 8}
    assert fast == brute
    assert brute  # 构造数据必须真的产生近重复对，否则测试无意义


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


# ---------- calibration：训练对 → 混淆矩阵 → 增广（闭环） ----------

def test_align_errors_extracts_substitutions_and_indels():
    subs, dropped, added = cal.align_errors("数据库糸统", "数据库系统")
    assert subs == [("糸", "系")] and dropped == 0 and added == 0
    subs, dropped, added = cal.align_errors("数据库", "数据库系统")  # OCR 漏识 2 字
    assert subs == [] and dropped == 2 and added == 0
    subs, dropped, added = cal.align_errors("数据库系统概论", "数据库系统")  # OCR 多识 2 字
    assert subs == [] and dropped == 0 and added == 2


def test_fit_confusion_is_smoothed_and_normalized():
    model = cal.fit([("糸统", "系统")] * 8 + [("关糸", "关系")] * 2)
    assert model["substitutionCount"] == 10
    # Beta 先验收缩：(10 + 0.04×100) / (20 + 100)
    assert model["errorRates"]["p_substitute"] == pytest.approx(14 / 120, abs=1e-6)
    row = model["confusion"]["系"]
    assert set(row) == {"糸"}
    assert row["糸"] == pytest.approx(11 / 12)  # (10+α)/(10+α(|V|+1))


def test_fit_shrinks_zero_error_sample_toward_prior():
    """少量完全正确的 OCR 样本不得把扰动率估成 0（否则增广退化）。"""
    model = cal.fit([("关系数据库系统", "关系数据库系统")] * 5)
    rates = model["errorRates"]
    assert 0 < rates["p_substitute"] < cal.DEFAULT_RATES["p_substitute"]
    # 足够多干净样本时，先验仍保留但被明显压低
    big = cal.fit([("关系数据库系统", "关系数据库系统")] * 500)
    assert big["errorRates"]["p_substitute"] < rates["p_substitute"]


def test_calibrated_augmentation_is_deterministic_and_uses_model():
    records = [{"text": "关系数据库系统", "source": "s"}]
    model = cal.fit([("关系数据库糸统", "关系数据库系统")] * 20)
    first = ds.monte_carlo_augment(records, 30, seed=5, calibration=model)
    second = ds.monte_carlo_augment(records, 30, seed=5, calibration=model)
    assert first == second
    assert all(r["calibrated"] for r in first)
    assert any("糸" in r["input"] for r in first)  # 拟合出的混淆字符真的进入增广


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


def test_extract_text_regex_fallback_still_follows_links(monkeypatch):
    """无 bs4 时（生产 requirements 不含 bs4）仍需抽出同站链接，否则前沿爬取失效。"""
    from data_pipeline import web_crawler as wc

    monkeypatch.setattr(wc, "BeautifulSoup", None)
    html = ('<html><head><title>T</title></head><body><p>正文</p>'
            '<a class="nav" href="/a?x=1&amp;y=2">A</a>'
            "<a href='b.html'>B</a><a>无 href</a></body></html>")
    title, text, links = extract_text(html)
    assert title == "T" and "正文" in text
    assert links == ["/a?x=1&y=2", "b.html"]


class _FakeResponse:
    status_code = 200
    headers = {"content-type": "text/html"}

    def __init__(self, text: str) -> None:
        self.text = text


class _FakeCrawler(WebCrawler):
    """不发网络请求的爬虫：记录并发峰值，验证 worker 池确实并行。"""

    def __init__(self, delay: float = 0.03) -> None:
        super().__init__(respect_robots=False, min_interval=0.0)
        self.delay = delay
        self.active = 0
        self.peak = 0

    async def fetch(self, client, url):
        self.active += 1
        self.peak = max(self.peak, self.active)
        await asyncio.sleep(self.delay)
        self.active -= 1
        return _FakeResponse(f"<html><head><title>{url}</title></head>"
                             f"<body><p>{url} 正文</p></body></html>")


def test_crawler_is_bounded_concurrent_and_respects_max_pages():
    crawler = _FakeCrawler()
    seeds = [f"https://example.com/p{i}" for i in range(8)]
    records = asyncio.run(crawler.crawl(seeds, max_pages=8, concurrency=4))
    assert len(records) == 8
    assert 2 <= crawler.peak <= 4  # 真并发，且不超过 worker 数

    capped = _FakeCrawler()
    capped_records = asyncio.run(capped.crawl(
        [f"https://example.com/q{i}" for i in range(8)], max_pages=3, concurrency=4))
    assert len(capped_records) == 3  # 出队预占配额，并发下也严格不超发


class _WaveCrawler(WebCrawler):
    """单种子页 → 抓完才抽出子链接：记录"链接波次"的并发峰值。"""

    def __init__(self, delay: float = 0.05) -> None:
        super().__init__(respect_robots=False, min_interval=0.0)
        self.delay = delay
        self.active = 0
        self.wave_peak = 0

    async def fetch(self, client, url):
        is_root = url.endswith("/root")
        self.active += 1
        if not is_root:
            self.wave_peak = max(self.wave_peak, self.active)
        await asyncio.sleep(self.delay)
        self.active -= 1
        return _FakeResponse(f"<html><body><p>{url} 正文</p></body></html>")


def test_crawler_workers_survive_empty_queue_for_link_wave(monkeypatch):
    """种子页抓完才发现链接时，其余 worker 必须等待而非退出，第二波仍并发。"""
    from data_pipeline import web_crawler as wc

    def fake_extract(html: str) -> tuple[str, str, list[str]]:
        if "/root" in html:
            return "root", "根页正文", [f"/c{i}" for i in range(6)]
        return "child", "子页正文", []

    monkeypatch.setattr(wc, "extract_text", fake_extract)
    crawler = _WaveCrawler()
    records = asyncio.run(crawler.crawl(
        ["https://example.com/root"], max_pages=7, concurrency=4))
    assert len(records) == 7  # 根页 + 6 个子页
    assert crawler.wave_peak >= 2  # 旧实现只有 1 个 worker 存活，此处置 1
    assert crawler.wave_peak <= 4  # 仍受 worker 数约束



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
