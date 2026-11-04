# 07 · Prompt 库

> 当前版本：`v2`（`rag_service.PROMPT_VERSION`）。每次改 Prompt 必须递增版本并写入 `ai_generation_log.prompt_version`；Java `RagExplainService.PROMPT_VERSION` 必须同步，`PromptVersionConsistencyTest` 会在两侧不一致时失败。

## 1. 讲解 Prompt（L1/L2 共用）

**System**

```text
你是数据库原理课程的助教。只做思路点拨，不直接替学生写作业答案。
必须依据给定概念切片讲解，不得编造教材页码或引用。
用中文分 3—4 段，每段不超过 120 字。
```

**User**

```text
知识点：{kpName}（{kpId}）
教材页码：{textbookPage}
概念切片：{conceptText}
常见误区：{misconception}
错因标签：{errorType}
学生题干：{questionText}
请指出这道题考什么、学生最可能错在哪、下一步怎么自查。
```

## 2. 约束

- 只用 `conceptText/misconception/textbookPage` 中的事实；没有就承认“资料不足”。
- 不输出完整作业答案；不编造引用；不生成“实验表明”等结论。
- 引用格式：`《数据库系统概论（第6版）》Pxx`；`sources` 由 Java/Python 结构化下发，不靠模型自由生成。
- 低温度 `0.3`，同一输入尽量稳定；缓存键 `sha256(kpId+errorType+correctedText+"|concept:"+sha256(conceptText))`，TTL 7 天；概念内容变化后旧缓存自动失效。

## 3. 版本与评估

| 版本 | 变更 | 评估 |
| --- | --- | --- |
| v1 | 首版三段式讲解 | 教师 5 条试评（17/18 号材料） |

评估维度：准确性、贴合错因、可执行性、来源可追溯、是否泄露完整答案。任何改动先跑 5 条样例对比，人工复核后再上线。

## 4. 合规

`app/services/compliance.py` 在 Prompt 前做学术诚信拦截；命中返回 60004，不生成正文。词表当前 4 条，P1 扩到 30 条样例并做准确率评估。

## 5. 修改流程

1. 改 `_prompt()` 或模板 → 递增 `PROMPT_VERSION`。
2. 用固定 5 条样例（含 L3/L4）跑对比，记录差异。
3. 更新本文与 `ai_generation_log.prompt_version` 口径。
4. 提交信息写 `feat(prompt): v2 ...`，不与业务改动混提。
