# 映射评测证据状态（2026-11-03，P0-02）

本目录映射评测证据的口径与可引用性，由本轮 P0-02 任务锁定。

## 权威证据（可引用）

- **`mapping_eval_offline_authoritative.json`** —— 由 `eval_mapping_offline.py` 生成，
  离线复刻生产 `KpMappingService` 的字符二元组 TF-IDF 余弦算法（公式与 Java 一致：
  `tf = 1 + ln(count)`，`idf = (ln((N+1)/(df+1)) + 1)^1.5`，L2 归一后余弦，
  `score = min(1, cos / 0.3)`）。
  - 样本：第 2/3 章知识点 20 个 + 题库 60 题（来自
    `zhixue-server/sql/seed-kp-ch02-ch03.sql`、`seed-question-ch02-ch03.sql`）。
  - 基线 **V0：top1 = 73.3%（44/60）、top3 = 81.7%（49/60）**，满足验收口径
    top1 ≥ 70%、top3 ≥ 80%。
  - 传 `chapterId` 的硬过滤变体 **V6：top1 = 76.7%、top3 = 88.3%**（贴近线上真实可用
    口径，因为诊断流已知题目所属章节）。
  - 命令：`python tools/eval_mapping_offline.py --json tools/testdata/mapping_eval_offline_authoritative.json`
  - 运行环境：Python 3.12.10；退出码 0；**无服务器 / 数据库依赖**。
  - 覆盖与校准：V0 有 8 题（13.3%）落在确认下限 0.40 以下被判 FAILED；
    分数段 0.8–1.0 实际正确率 93.3%，0.4–0.8 段约 60–64%（20 选 1 随机为 5%，
    仍显著优于随机，但中段区分度有限）。详见 JSON 内 `calibration`、`misses`。

## 废弃证据（不可引用）

- **`last_mapping_eval.json`** —— 由在线脚本 `eval_mapping.py` 对 `/api/diagnosis/map`
  端点实测得到，记录 `top1_accuracy = 0.0`、`n_cases = 32`。
  **该结果不反映算法真实准确率**，系评测侧问题（端点返回格式 / 解析或部署态不符），
  并非映射算法 0% 正确。**严禁在答辩 / 软著 / 交付中引用 0.0/32。**
  保留文件作为问题线索，不删除。

## 与“top1 73.3% 不可直接引用”的关系

历史口径称“映射 top1 73.3% 不可直接引用”，根因是唯一的持久化证据文件是上述已废弃的
0.0/32 在线评测。本任务已建立离线权威证据（73.3% / 81.7%），替代之。
在线评测为何得到 0.0 属独立排查项（根因待查，**不在本轮**）。

## 待办（依赖 P0-03）

第 4/5 章签核入库后，将种子 SQL 扩到 ≥100 题并复跑，更新本权威 JSON（验收标准 ⑤）。
