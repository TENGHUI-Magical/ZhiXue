# 07 · Prompt 库

> 当前版本：`v2`（`rag_service.PROMPT_VERSION`）。每次改 Prompt 必须递增版本并写入 `ai_generation_log.prompt_version`；Java `RagExplainService.PROMPT_VERSION` 必须同步，`PromptVersionConsistencyTest` 会在两侧不一致时失败。

## 1. 讲解 Prompt（L1/L2 共用，v2）

> 单一事实源：逐字文本在 `zhixue-ai/app/services/rag_service.py` 的 `_prompt()`；本节只记录结构与约束，避免复制后漂移。

- **System**：数据库原理助教；只做思路点拨、不给最终答案或完整解题过程；必须依据给定教材证据、不得编造页码；有具体作答才分析作答。
- **User 分区**：`[当前问题]`（知识点、错因、作答/答错次数）→ `[学生证据]`（题干、最近作答）→ `[教材证据]`（页码、概念切片）→ 常见误区 → `[先修弱项]`（仅样本数 ≥3）→ `[检索切片]`（最多 2 条、合计 800 字）→ `[输出格式]`（错因定位 ≤3 句；完整讲解 ≤3 段、每段 ≤120 字；自查 1 问）。
- **预算**：目标 3000 token、硬上限 4000 token（按字符保守估算）；裁撤顺序：检索切片 → 先修概念 → 概念尾部 → 误区 → 题干尾部，仅触硬上限时才裁学生作答。
- **生成参数**：`temperature=0.3`、`max_tokens=700`、流式。

## 2. 约束

- 只用 `conceptText/misconception/textbookPage` 中的事实；没有就承认“资料不足”。
- 不输出完整作业答案；不编造引用；不生成“实验表明”等结论。
- 引用格式：`《数据库系统概论（第6版）》Pxx`；`sources` 由 Java/Python 结构化下发，不靠模型自由生成。
- 低温度 `0.3`；缓存键 `zhixue:explain:cache:{PROMPT_VERSION}:{sha256(canonicalContext)}`，TTL 7 天。`canonicalContext` 的字段与顺序以 `RagExplainService#canonicalContext` 为准（含 userId/courseId/kpId/kpName/qId/errorType/题干/概念/误区/页码/标准答案/最近作答/作答次数/先修弱项）；任一字段变化旧缓存自动失效。

## 3. 版本与评估

| 版本 | 变更 | 评估 |
| --- | --- | --- |
| v1 | 首版三段式讲解 | 教师 5 条试评（17/18 号材料） |
| v2 | 结构化分区 Prompt（学生证据/教材证据/先修弱项/检索切片 + 输出格式；3000/4000 token 预算） | 尚未单独做 5 条样例对比（待补） |

评估维度：准确性、贴合错因、可执行性、来源可追溯、是否泄露完整答案。任何改动先跑 5 条样例对比，人工复核后再上线。

## 4. 合规

`app/services/compliance.py` 在 Prompt 前做学术诚信拦截；命中返回 60004，不生成正文。词表当前 4 条，P1 扩到 30 条样例并做准确率评估。

## 5. 修改流程

1. 改 `_prompt()` 或模板 → 递增 `PROMPT_VERSION`。
2. 用固定 5 条样例（含 L3/L4）跑对比，记录差异。
3. 更新本文与 `ai_generation_log.prompt_version` 口径。
4. 提交信息写 `feat(prompt): v2 ...`，不与业务改动混提。
