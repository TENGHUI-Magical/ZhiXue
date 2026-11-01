# 内容生产工具链（智学领航）

> 配套文档：`spark-output/content/内容生产流水线规划_V1.0_2026-10-31.md`
>
> 红线：**不编题、不编答案、不编解析**。AI 只出初稿，必须人工复核签核后才可入库。

## 1. 为什么需要 OCR

实测（2026-10-31）：

- 教材 PDF `pdftotext` 前 40 页仅 12.6KB，且**中文大面积缺失**（CJK 字体无 ToUnicode 映射）。
- 习题册 PDF 前 40 页仅 40 字符，是扫描件。

因此**不能直接取中文文本**。可靠方案是先渲染 PNG 再 OCR：

```powershell
# 教材某章（示例：第 1 章，页码范围需按实际调整）
pdftoppm -f 15 -l 40 -r 200 -png "数据库系统概论 第6版 王珊 杜小勇 陈红 .pdf" chapter01
# 习题册同样处理
pdftoppm -f 1 -l 30 -r 200 -png "数据库系统概论(第6版)习题解析与实验指导.pdf" exercise01
```

再用本机 OCR（RapidOCR / PaddleOCR）逐页识别，输出到 `技术学习/content/chXX/ocr/`。

## 2. CSV 规范

### 2.1 知识点 `kp.csv`

列：`kpId,chapterNo,name,conceptText,misconception,textbookPage,dimensionKey,sortNo`

- `kpId`：`KP` + 两位章号 + 两位序号（如 `KP0401`）。
- `dimensionKey`：`concept / sql / schema / transaction / boundary`。
- `misconception`：多条用 `|` 分隔（生成 SQL 时转成换行）。
- `textbookPage`：教材印刷页，`P40` 形式。

### 2.2 题目 `questions.csv`

列：`qId,primaryKpId,questionType,questionText,optionsJson,answerText,analysisText,difficulty,source,variantHint`

- `qId`：`Q_` + 两位章号 + 两位序号。
- `questionType`：`单选/多选/填空/简答/综合`。
- `optionsJson`：JSON 数组 `[{"key":"A","text":"..."}]`；非选择题留空。
- `answerText`：选择题填选项 key；其它填答案文本。
- `source`：`教材 / 习题册 / 自编`；自编必须可被教材内容验证。
- CSV 中 JSON 的引号按标准 CSV 双写（见 `templates/questions-template.csv`）。

## 3. 使用步骤

```powershell
cd zhixue\zhixue-server

# 1) 草稿自检（不阻断缺页/短解析）
D:\Python3.12.10\python.exe tools\content\validate_content.py --kp ch01-kp.csv --questions ch01-questions.csv

# 2) 人工复核签核后，严格校验（必须 0 ERROR）
D:\Python3.12.10\python.exe tools\content\validate_content.py --kp ch01-kp.csv --questions ch01-questions.csv --strict

# 3) 生成 upsert SQL
D:\Python3.12.10\python.exe tools\content\csv_to_sql.py --kp ch01-kp.csv --questions ch01-questions.csv `
  --chapter-no 1 --chapter-title 绪论 --sort-no 1 --outdir sql\generated

# 4) 导入（必须显式 utf8mb4，否则 Windows 下中文乱码）
mysql -u root -p --default-character-set=utf8mb4 zhixue < sql\generated\seed-kp-ch01.sql
mysql -u root -p --default-character-set=utf8mb4 zhixue < sql\generated\seed-question-ch01.sql

# 5) 冒烟
# GET /api/courses/1/chapters、/knowledge-graph、/questions/{qId}、/questions/{qId}/answer
```

## 4. 校验规则（严格模式）

- 编号格式/唯一、`primaryKpId` 必须存在、`dimensionKey` 合法。
- 选择题 ≥4 选项、`answerText` 必须在选项 key 内。
- 每题解析 ≥50 字；题干去重（忽略空白）。
- 每个知识点 ≥2 题；`difficulty ∈ {1,2,3}`。
- 知识点 `conceptText ≥150` 字、误区 ≥2 条、页码 `Pxx`。

## 5. 复核纪律

1. 出题人 ≠ 签核人；每题至少两人经手。
2. 答案与解析必须能回到教材/习题册原文；无法回溯的题**剔除**，不凑数。
3. 签核记录放 `技术学习/content/chXX/review-signoff.md`（题号/结论/复核人/日期）。
4. 未签核内容一律不入库；导入后在台账登记章/点/题数与冒烟结果。

## 6. 当前状态

- 第 2、3 章：已入库 20 知识点 / 60 题。
- 第 1 章：已产出 **10 知识点 / 25 题初稿**（`spark-output/content/ch01/`，含 OCR 证据、coverage、签核单与 SQL），`--strict` 结构校验通过；**待人工签核，未入库**。
- 其余 5 章：待按本流程产出。
