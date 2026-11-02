# last_mapping_eval.json 说明（过期事故线索，禁止引用）

本目录的 `last_mapping_eval.json` 记录 `top1_accuracy = 0.0`、`n_cases = 32`、`n_ok = 32`。
**它不是算法真实准确率，也不是标注集标签缺失**，而是**语料未加载时的零候选产物**。

## 为什么过期

- 生成时间：**2026-10-25 02:11:52**。
- `zhixue-server/sql/seed-kp-ch02-ch03.sql` 落盘时间 **2026-10-25 03:55**；
  `seed-question-ch02-ch03.sql` 落盘时间 **2026-10-26 23:04**。
- 即评测发生时知识点/概念切片尚未入库，`/api/diagnosis/map` 返回**空候选**，32 题全部无法命中。
- 当时在线脚本的 probe 用 ASCII `"probe"`，无法识别“零候选”的假阴性。

## 复核证据（2026-11-03）

- 用 main `6391c6b` 起真实后端重跑同一脚本 `tools/eval_mapping.py`：
  **n=32，top1 15/32=46.9%，top3 23/32=71.9%，AUTO 10/32=31.2%**，候选非空，0.0 不再复现。
- main 上 U60（60 题 × 60 点语料）只读重跑：
  **top1 33/60=55.0%，top3 44/60=73.3%，AUTO 24（精确率 87.5%），人工率 60.0%，零候选 0**。
  报告：`spark-output/qa/2026-11-03-mapping/main-u60-eval.json`。

## 引用规则

- **禁止**在答辩、软著、验收材料中引用 `0.0/32`；它只作为“零候选事故”的线索保留。
- 线上语义指标用 **U60（N=60）**；历史小语料指标用 **U20（N=20）**，必须标注语料规模。
- 权威口径见 `docs/05-算法与规则设计.md` §6 与 `tools/testdata/MAPPING_EVAL_STATUS.md`
  （后者在未合入分支 `ai/mapping-eval-20261103`）。

## 复现（只读）

```powershell
cd zhixue\zhixue-server
$env:JAVA_HOME='D:\jdk-21.0.13.0.3'
mvn -B -ntp -DskipTests package
$env:DB_PASSWORD='<本地口令>'
java -jar zhixue-bootstrap\target\zhixue-bootstrap-0.1.0-SNAPSHOT.jar

# 另开终端：登录拿 satoken 后
cd zhixue
D:\Python3.12.10\python.exe tools\eval_mapping.py --token <satoken>
```
