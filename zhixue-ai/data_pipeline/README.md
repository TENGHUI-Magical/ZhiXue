# data_pipeline · 数据采集与训练集构建管线

> 状态：已实现并实测（见 `../../algorithm_and_data_opt/02_Algorithm_Refactoring_Report.md` §2）
> 定位：内容线工程化辅助——产出一律为「待人工签核」中间产物，不直接入库（AGENTS.md 红线 1）。

## 模块与数学内核

| 模块 | 数学内核 | 实测验证 |
| --- | --- | --- |
| `image_math.py` | BT.601 灰度化；可分离高斯卷积（O(HWk)）；Otsu 最大类间方差二值化；Postl 行投影方差倾斜估计 + 仿射校正 | 6° 倾斜合成扫描件 → 估计 -6.0°（精确），单测覆盖 |
| `web_crawler.py` | 有界并发 worker 池（默认 4）+ 每域限速锁；robots.txt；指数退避 base·2^k+U(0,base)；断点续传 | 并发峰值/配额上限离线单测；公开页面真实抓取 |
| `ocr_pipeline.py` | RapidOCR→PaddleOCR→EasyOCR→Tesseract 降级链；行级置信度过滤 + 页级 Wilson 95% 下界门禁（Kish 有效样本量） | 倾斜扫描件 5/5 行文本 100% 识别 |
| `dataset_builder.py` | SimHash(64bit) 分块候选 + 精确 Jaccard 复核去重（峰值 O(block·n)）；IQR 清洗；分层抽样（最大余数法 + 跨进程稳定分桶）；蒙特卡洛字符级扰动增广 | 3 页 → 10003 条 → 8003/1000/1000 分层输出 |
| `calibration.py` | 真实 OCR 训练对 → 编辑对齐 → 拉普拉斯平滑混淆矩阵 + 三类编辑率 → 反哺增广采样（拟合-校准-推理闭环） | 对齐/平滑/确定性单测 |

## 快速上手（在 `zhixue-ai` 目录执行）

```bash
# 1. 爬取（种子见 data/pipeline_smoke/seeds.json 示例）
python -m data_pipeline.web_crawler --seeds seeds.json --out data/crawled.jsonl \
    --max-pages 20 --min-interval 1.5 --concurrency 4 --checkpoint data/crawl_state.json

# 2. OCR（本地图片目录 → 结构化 JSONL，含置信度过滤）
python -m data_pipeline.ocr_pipeline --images data/upload --out data/ocr_out.jsonl

# 3. 从真实训练对拟合 OCR 混淆参数（input=OCR 文本，target=人工校对）
python -m data_pipeline.calibration --input data/ocr_pairs.jsonl \
    --out data/calibration/confusion.json

# 4. 训练集构建（去重 → 清洗 → 分层 → 增广；可接校准参数）
python -m data_pipeline.dataset_builder --input data/crawled.jsonl data/ocr_out.jsonl \
    --out-dir data/datasets --label-key source --augment 5000 \
    --calibration data/calibration/confusion.json
```

## 依赖边界

生产依赖（requirements.txt）不变。可选依赖按需安装：
`pip install beautifulsoup4`（爬虫解析）、`pip install rapidocr-onnxruntime`
（OCR，见 requirements-ocr.txt）、`pip install pandas pyarrow`（parquet 输出）。
全部缺失时自动降级，不阻塞管线其余环节。

## 合规边界

- 只抓公开页面；不登录、不绕过反爬、不伪造凭据；
- 产出标记 `synthetic` / `contentHash`，人工签核后方可进入 CSV/SQL 内容线；
- 增广数据只用于模型训练/评测，不作为项目成效证据（不编数据，红线 1）。
