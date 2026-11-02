# 映射评测证据状态（更新于 2026-11-03，映射子系统优化轮次）

本文件锁定「映射子系统」全部评测证据的**口径与可引用性**。改动依据见
`docs/design/mapping-optimization-2026-11-03.md`（架构师审核 + 最优方案）。

> **引用规则（先读这一条）**：本目录任何一个 top-1 / top-3 数字都必须同时写明
> **语料规模 N**。`73.3%` 是 **N=20** 的数字，**不是线上数字**；线上语义是 **N=60**，
> 对应 V0 `55.0%`、V2 `60.0%`。两者不可混引。

---

## 1. 两个语料口径（universe）

| 口径 | 脚本参数 | N | 语料来源 | 语义 |
| --- | --- | --- | --- | --- |
| **U20** | `--universe ch23` | **20** | `zhixue-server/sql/seed-kp-ch02-ch03.sql` | 历史权威口径（只覆盖第 2/3 章），**不是线上语义** |
| **U60** | `--universe all`（**默认**） | **60** | `zhixue-server/sql/seed-kp-ch01..ch06.sql`（10/10/10/11/9/10） | **线上语义**：生产 `listMatchProfiles` 返回课程全部启用知识点 |

U60 才是线上真值的原因：`KnowledgePointService.listMatchProfiles(courseId)` 取
`course_id=1 AND is_active=1` 的**全部**行，`docker-compose.yml` 挂载的种子共 60 个。
语料从 20 涨到 60，IDF 与"竞争者数量"同时变化，top-1 从 73.3% 掉到 55.0%。

---

## 2. 权威证据（可引用）

### 2.1 文件清单

| 文件 | 生成命令 | N |
| --- | --- | --- |
| `mapping_eval_offline_authoritative.json` | `python tools/eval_mapping_offline.py --universe ch23 --json tools/testdata/mapping_eval_offline_authoritative.json` | 20 |
| `mapping_eval_offline_all_universe.json` | `python tools/eval_mapping_offline.py --universe all --json tools/testdata/mapping_eval_offline_all_universe.json` | 60 |

两份 JSON 均含：`universe` / `universe_size`(=N) / `chapters` /
`config{score_scale,auto,floor,margin,idf_power,name_repeat,recommended_use_misconception}` /
`recommended{variant,use_misconception,floor}` / `results[]`（全变体，每个变体自带
`use_misconception` / `chapter_mode` / `autoHits`）/ `gate`（门禁逐条核对，含精确分数）/
`per_case[]`（**V0 口径**的逐题 `qId/gold/ranked_top3/score/cosine/decision/hit1/hit3`）/
`per_case_variant` + `per_case_use_misconception` + `per_case_note`（显式标明 per_case 属于哪个变体；
推荐方案 V2 的逐题明细在 `results[2].per_case`）。
`config` 只放**所有变体共用**的阈值旋钮，与变体相关的开关不混入，避免
"config 说开了误区、per_case 却是 V0" 的歧义。
运行环境：Python 3.13.14；退出码 0；**无服务器 / 数据库依赖**。

### 2.2 主结果（`confirm-floor = 0.35`，与 `application.yml` 同值）

**U20（N=20）**

| 方案 | top1 | top3 | AUTO | AUTO 精确率 | CONFIRM | FAILED | 覆盖 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| V0 baseline（误区不入语料） | 73.3% | 81.7% | 24 | 95.8% | 30 | 6 | 90.0% |
| **V2 误区入语料（推荐 / 待合并）** | 73.3% | **85.0%** | 29 | 93.1% | 26 | **5** | **91.7%** |

**U60（N=60，线上语义）**

| 方案 | top1 | top3 | AUTO | AUTO 精确率 | CONFIRM | FAILED | 覆盖 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| V0 baseline（误区不入语料） | 55.0% | 73.3% | 24 | 87.5% | 29 | 7 | 88.3% |
| **V2 误区入语料（推荐 / 待合并）** | **60.0%** | **80.0%** | 23 | **91.3%** | 29 | 8 | **86.7%** |

**准确性指标无退化，覆盖率有 1 题级退化**（如实记录，不粉饰）：

- top1 / top3 / AUTO 精确率：两个口径均不退化，U60 top-3 **零丢失**（McNemar 4 升 0 降）。
- U60 覆盖率 `88.3% → 86.7%`（52/60，即 FAILED `7 → 8`，**各退化 1 题**）。
  这是把 `confirm-floor` 从 0.40 下调到 0.35 并叠加误区入语料后的净结果；
  **相对改动前的生产配置仍有明显改善**：V0 @floor 0.40 的覆盖率是 **83.3%**、FAILED **10**。
  即 `83.3%（改动前）→ 86.7%（改动后）`，退出 FAILED 的题减少 2 题。
- 该 1 题退化的机制是：误区入语料后该题 top1 的余弦降到 0.35 以下（由 CONFIRM 转 FAILED），
  属可解释的代价；可否接受属产品决策，已列入 §8 待办（扩样后复核）。

### 2.3 合并门禁核对（V2，floor 0.35）

| 门禁 | U20 要求 | U20 实测（精确分数） | U60 要求 | U60 实测（精确分数） |
| --- | --- | --- | --- | --- |
| top1 | ≥ 73.3% | 44/60=73.33% → 73.3% PASS | ≥ 60.0% | 36/60=60.00% PASS |
| top3 | ≥ 85.0% | 51/60=85.00% PASS | ≥ 80.0% | 48/60=80.00% PASS |
| AUTO 精确率 | ≥ 90.0% | 27/29=93.10% PASS | ≥ 90.0% | 21/23=91.30% PASS |
| 覆盖率（非 FAILED） | ≥ 86.7% | 55/60=91.67% → 91.7% PASS | ≥ 86.7% | **52/60=86.67% → 86.7% PASS** |
| FAILED 题数 | ≤ 8 | 5 PASS | ≤ 8 | 8 PASS |

**门禁比较口径**：实测值与阈值都按**报告精度**（百分比 1 位小数）舍入后再比，阈值一位未下调
（脚本 `_same_precision`）。所以 `52/60 = 86.6667%` 报为 86.7%，与阈值 86.7% 同精度达标；
而 86.64% 报为 86.6%，仍然 FAIL。精确分数始终随门禁表打印，便于人工复核。

> ⚠ **本门禁是"零余量锁死型"**：绝大多数阈值**等于实测值**，不是在实测之上留了余量——
> - **U60：5 条里 4 条恰好压线**（top1 60.0=60.0、top3 80.0=80.0、覆盖率 86.67≈86.7、FAILED 8=8），
>   只有 AUTO 精确率有 1.3pp 余量（91.3 vs 90.0）。
> - **U20：5 条里 2 条压线**（top1 73.33≈73.3、top3 85.0=85.0），其余 3 条有余量。
> 这意味着**任何一题抖动都会直接 FAIL**。它不是"有余量的质量线"，而是
> "当前算法 + 当前种子下恰好成立的下界"。据此：任何算法 / 种子 / 阈值改动都必须重跑本表，
> 不得假设门禁会继续通过；也不得以"门禁通过"反推算法有余量。

门禁结论在脚本输出末尾与 JSON 的 `gate` 字段里逐条打印（含 `detail` 精确分数），可复核。

### 2.4 校准与决策分布（U60 / V2）

分数区间 → 实际 top-1 正确率（U60 V0）：`0.0–0.4` 30%、`0.4–0.6` 36%、
`0.6–0.8` 73%、`0.8–1.0` 93%。
**中段（0.4–0.6）在大语料下几乎不再区分对错**，这是 `AUTO 精确率` 而不是 `top1`
被设为硬约束的直接原因；也是 0.4–0.6 段必须留在 CONFIRM（人工确认）的证据。

---

## 3. 章节硬过滤（V6）：已实现但**零调用方**，不得宣称"线上可用"

> **更正（本文件旧版错误陈述）**：旧版第 15–16 行称
> 「V6……贴近线上真实可用口径，因为诊断流已知题目所属章节」。**该陈述错误**，已删除。

事实（可复核）：

1. 前端 `zhixue-web/src/api/diagnosis.ts:96-97` 只发 `{text, courseId}`，**不发 `chapterId`**。
2. 工作台 `DiagnosisWorkbench.vue` **没有章节选择器**（`grep 章节|chapter` 为空）。
3. `DiagnosisController` 直接 `kpMappingService.match(request)`，**不注入/不派生** chapter。
4. 因此 `chapterId` 分支是**已实现、零调用方**的死路径；V6/V7/V10/V11 的分数
   **不是线上可达数字**。

另外，V6/V7 的离线分数本身还带一个强假设：**调用方传对了章**（脚本用 gold 所属章过滤），
这属于上限参考。真正接入章选择后，用户可能选错章，而"错章 + 高分"比 FAILED 更糟。

离线留档（N=20 / N=60，floor 0.35）：V6 `76.7%/88.3%`（U20）、`73.3%/86.7%`（U60），
FAILED 分别是 9 / 15（覆盖率 85.0% / 75.0%，**违反覆盖率门禁**）。这也是不采纳章硬过滤的量化理由。

V11（误区 + 章硬 + 低置信回退）为本次首次评测：U20 `73.3%/85.0%` 与 V2 **逐项相等**，
U60 `65.0%/81.7%`，相对 V2 无"过滤 + 回退"的净增量（回退把过滤收益退回），故不采纳。

---

## 4. 统计显著性：**60 题样本不显著，禁止宣称"显著提升"**

- 样本结构：60 题 = 20 个知识点 × 3 题。**每点只有 3 题**，单点估计标准误极大
  （3 题错 1 题即 33pp 波动），逐点指标不可用。
- 95% Wilson 区间（由本轮 JSON 的 `top1_hits/n` 实算）：
  U20 V0 73.3% → `[61.0%, 82.9%]`；U60 V0 55.0% → `[42.5%, 66.9%]`；
  U60 V2 60.0% → `[47.4%, 71.4%]`。**区间大幅重叠。**
- 配对显著性（McNemar 精确检验，由两份 JSON 的 `per_case` 实算）：
  - U60 V0→V2 top1：4 升 1 降 → `p = 0.375`；top3：4 升 0 降 → `p = 0.125`。
  - U20 V0→V2 top1：2 升 2 降 → `p = 1.000`；top3：2 升 0 降 → `p = 0.500`。
- **结论：没有任何单一指标达到统计显著（α=0.05）。** 本轮的正当性来自
  "机制可解释 + 两口径方向一致 + 不引入新旋钮"，**不能**表述为"显著提升"。
- 扩样方案（未执行）：≥150 题、每点 ≥5 题、按章分层、20% 双标 + Kappa ≥0.8。

---

## 5. 补充观察集（32 题）：**不作门禁**

`mapping_cases.csv` 是习题册选择题原文，**含 A/B/C/D 选项文本**，选项文字对概念切片
相似度是纯噪声。本轮已把它真正纳入脚本评测（历史版本只在 `main()` 里打印数量、
从未参与任何变体，属缺陷 F1）：

| 口径 | V0 top1 / top3 | V2 top1 / top3 |
| --- | --- | --- |
| U20（N=20） | 56.2% / 84.4% | 62.5% / 84.4% |
| U60（N=60） | 46.9% / 71.9% | 46.9% / 78.1% |

只能作为"干扰鲁棒性"的补充观察，**不得作为门禁集、不得单独引用为算法水平**。

---

## 6. 已废弃证据（不可引用）

- **`last_mapping_eval.json`** —— 在线脚本 `eval_mapping.py` 对 `/api/diagnosis/map`
  的实测，记录 `top1_accuracy = 0.0`、`n_cases = 32`、`n_ok = 32`。
  **该结果不反映算法真实准确率**；同一 32 题同算法离线复刻为 top1 56.2%~62.5%，
  "候选非空而 32 题全错"的概率约 `0.4^32 ≈ 10⁻¹³`，因此 0.0 = **候选全空 / 响应体无 kpId**
  （部署态或数据态问题），不是算法问题。**严禁在答辩 / 软著 / 交付中引用 0.0/32。**
  文件保留作为问题线索，不删除。

本轮已把该事故做成**可自动识别**的（缺陷 F2/F3 修复）：

- 在线报告新增逐题 `results`（`case_id/true_kp/predicted/decision/top1_hit/top3_hit`）
  与 `n_empty_candidates`，事后可归因；
- probe 改用**真实中文题干**并校验候选非空：为空直接打印
  「⚠ 端点返回零候选：疑似 DB 未种子化/编码不符」并 `return 1`；
- `verdict` 新增 `PIPELINE_BROKEN`（全部用例零候选时），并提示执行
  `SELECT COUNT(*) FROM knowledge_point WHERE course_id=<id> AND is_active=1;`。

---

## 7. 复现命令（只读、无 DB 依赖）

```bash
cd "D:/Desktop/智学领航：高校课程学情诊断系统/_wt/mapping-eval-20261103"

python tools/eval_mapping_offline.py                                  # 默认 U60（N=60）
python tools/eval_mapping_offline.py --universe ch23                  # U20（N=20）
python tools/eval_mapping_offline.py --universe ch23 \
  --json tools/testdata/mapping_eval_offline_authoritative.json
python tools/eval_mapping_offline.py --universe all \
  --json tools/testdata/mapping_eval_offline_all_universe.json
```

脚本已锁的忠实性规则（改脚本前必读）：

1. **排序键是原始 cosine**，不是校准后的 `score`；否则 `cos ≥ 0.3` 的候选饱和到 1.0，
   排序退化为按 kpId 字母序。
2. **df / N 在全部画像上统计**，章过滤只缩小参与排序的候选集合。
3. `soft` / `oracle` 章模式使用 gold 所属章，输出会强制标注「禁止用于线上效果声明」。
4. `_KP_PATTERN` 兼容 `sort_no` 带引号（ch01/ch04+）与不带引号（ch02-ch03）两种写法；
   旧版 `\d+` 会静默漏掉 ch01/ch04+ 的 40 个知识点。
5. **门禁按"同精度"比较**（`_same_precision`）：实测值与阈值都舍入到百分比 1 位小数后再比，
   阈值一位未下调。**禁止**改成"给实测值加 0.0005 容差"这类写法——容差会把
   报告值 86.6% 也算达标，等于变相降低门禁（AGENTS §3.2）。精确分数（`52/60=86.67%`）
   必须随门禁表一起打印。
6. `--floor` 默认 **0.35**，与 `application.yml` 的 `mapping.confirm-floor` 同值；
   `--universe` 默认 **all**（线上语义）。改任一默认值等于改口径，必须同步本文件与设计文档。

---

## 8. 待办

- 第 7/8 章种子入库后：**必须**用 `--universe all` 复跑并更新第 2 节两张表，
  同时在门禁里更新 N（语料继续增长会继续压低 top-1，见设计文档 §9-R1）。
- 扩样到 ≥150 题再谈统计结论（见第 4 节）。
- 在线 0.0 的具体成因（DB 未种子化 vs 编码不符）需在线环境执行第 6 节的 3 条判定命令。
- U60 覆盖率 88.3%→86.7%（1 题）的退化是否可接受：扩样后复核；若不可接受，
  备选是把 floor 回调 0.36~0.40 并接受覆盖率下降，或对"余弦在 0.33—0.36 之间"的题
  保留 CONFIRM 而非 FAILED（需产品决策，不在本轮）。
