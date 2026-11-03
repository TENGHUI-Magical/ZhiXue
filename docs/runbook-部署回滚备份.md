# Runbook · 部署 / 回滚 / 备份

> 适用：单机演示/院系内网。生产 TLS、网关、监控为 P1。

## 1. 环境变量

| 变量 | 用途 | 是否必填 |
| --- | --- | --- |
| `DB_PASSWORD` | MySQL 密码 | 是 |
| `DB_URL` | 覆盖 JDBC URL | 否 |
| `REDIS_HOST/PORT/PASSWORD` | Redis | 否 |
| `DEEPSEEK_API_KEY` | L1 讲解 | 否（无则 L3） |
| `GLM_API_KEY` | L2 讲解 | 否 |
| `AI_BASE_URL` | AI 服务地址（application.yml 实际读取的变量名） | 否，默认 `http://127.0.0.1:8000` |

密钥只放 `.env`/系统环境变量；禁止提交。

## 2. 构建

```powershell
cd zhixue\zhixue-server
..\scripts\mvn21.ps1 -B -ntp clean package
cd ..\zhixue-web
npm ci
npm run build
```

产物：`zhixue-bootstrap/target/zhixue-bootstrap-0.1.0-SNAPSHOT.jar`、`zhixue-web/dist/`。

> **铁则：演示与试用环境必须完整反应堆构建**（上面的 `clean package`，或
> `mvn -B -ntp install -DskipTests`）；**禁止 `-pl <module>` 单模块起服务**。
> 原因：`-pl` 起动用的是本地仓库的旧依赖 jar，跨模块新增的内部类（如
> `ReviewItemView$SourceRef`）在旧 jar 里不存在，运行时抛 `NoClassDefFoundError`
> 却经全局异常处理器**伪装成业务错误码 50000**，从接口侧几乎无法排查
> （2026-11-03 走查环境实证；完整构建后同一代码立刻 `code=0`）。
> `scripts/precheck-demo.ps1` 的"教师评阅"项专门探测这类失败并给出该提示。

## 3. 部署（单机）

1. **备份**：见 §5，先备份 MySQL 与 `zhixue-ai/data/vector_index`。
2. 停旧进程（记录 PID）：Java 8080、AI 8000、前端静态服务 5173/80。
3. 放新 jar / dist；保留上一版 jar 改名 `*.bak`。
4. 起 Java：`java -jar ...`；起 AI：`uvicorn app.main:app --host 127.0.0.1 --port 8000`；Nginx/静态服务器指向 `dist/`。
5. 健康检查：`GET /api/system/health` 返回 200；db/redis UP；ai 允许 DEGRADED（未配 key）。
6. 自检：跑 `scripts/pre-demo-check.ps1`（见 §3.1），0 FAIL 才算演示环境就绪。

### 3.1 演示前自检（pre-demo-check.ps1）

> 已落地：`zhixue/scripts/pre-demo-check.ps1`。默认**只读**，不写业务数据；`-FullSmoke` / `-UiSmoke` 才会写演示库。

```powershell
cd zhixue
$env:E2E_USER='student01'; $env:E2E_PASS='<演示口令>'
$env:E2E_TEACHER_USER='teacher01'; $env:E2E_TEACHER_PASS='<演示口令>'
$env:DB_PASSWORD='<本地DB密码>'          # 仅用于内容计数，缺失则 SKIP 该项
.\scripts\pre-demo-check.ps1            # 只读预检：19 项
.\scripts\pre-demo-check.ps1 -FullSmoke # 追加 35 项真实 API 冒烟（写 1 条错题/1 周计划/1 次打卡）
.\scripts\pre-demo-check.ps1 -UiSmoke   # 追加真实 UI 冒烟（需前端 5173）
```

- 检查项：Java/AI 可达与 `db/redis/ai` 组件状态、未登录 40100、学生/教师登录与角色、课程列表、图谱节点 ≥20、知识点详情与题目、推荐、学生/班级画像、教师评阅队列与学生列表、数据库内容计数、RAG 索引文件。
- 判定：任一 `FAIL` → 退出码 1，演示前必须修复或改演示路径；`WARN`（如 AI 降级、Redis 不可用、某知识点 0 题、索引偏小）需在讲稿中规避或说明。
- 报告：`spark-output/qa/<日期>-predemo/predemo-report.json`（`-FullSmoke` 的 API 冒烟报告同目录，强制覆盖，避免复用旧报告造成假绿）。
- **实跑证据（2026-11-02）**：main `002deac` 构建 jar + 本机 MySQL/Redis/AI 8000，19/19 PASS、0 WARN、0 FAIL；报告 `spark-output/qa/2026-11-02-predemo/predemo-report.json`。同日另一会话正在导入 ch04/ch05 内容，脚本只读计数（kp/qb/ch 随之变化），不设内容阈值门禁。

### 3.2 演示前回归清单（映射参数）

- 验收口径固定为**离线 U60 固定集（N=60）**：top1 60.0%、top3 80.0%、AUTO 精确率 91.3%、人工率 61.7%；
  **当前 top1 与人工率均未达标**，材料不得写成达标。
- 每次演示前做一次**在线运行观测**（60 题 × 当前在线语料，必须标 N）：人工率 = `decision != AUTO` 占比。
- **规则写死**：在线人工率 **>65%** 时，把 `application.yml` 的 `mapping.confirm-floor` 改为 **0.40** 并重启后端；
  **只改配置，不回滚提交**；在台账记录观测值、N 与改动。
- 任何对外数字必须带 **“实测日期 + N + 语料口径”** 三要素，缺一即口径违规。

## 4. 回滚

```powershell
# 代码回滚
git -C zhixue log --oneline -5
git -C zhixue checkout <上一个稳定 commit>
# 重新 package 后按 §3 起服务
```

- 回滚必须同时回滚 `zhixue-web/dist` 与 AI 代码；只回滚一端会出现契约不一致。
- 数据库迁移若已执行，回滚前先确认是否兼容旧代码；不兼容则用 §5 恢复备份。

## 5. MySQL 备份/恢复

```powershell
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$mysqldump = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
$env:MYSQL_PWD = '<本地DB密码>'

# 备份（含建表；先建目录）
New-Item -ItemType Directory -Force backup | Out-Null
& $mysqldump -h 127.0.0.1 -u root --single-transaction --routines --triggers zhixue > backup\zhixue_20261028.sql

# 恢复（先停 Java，避免写入）
& $mysql -h 127.0.0.1 -u root -e "DROP DATABASE IF EXISTS zhixue; CREATE DATABASE zhixue DEFAULT CHARSET utf8mb4;"
Get-Content backup\zhixue_20261028.sql -Raw | & $mysql -h 127.0.0.1 -u root --default-character-set=utf8mb4 zhixue
```

> PowerShell 不支持 `mysql ... < file`（`<` 是保留运算符）；恢复用 `Get-Content -Raw | & $mysql`。cmd.exe 可直接用 `<`。

- 结项前每次批量导入内容后备份一次；保留最近 7 天。
- RAG 索引备份：复制 `zhixue-ai/data/vector_index/`（`vectors.npy` + `chunks.json` + `meta.json`）。
- **2026-10-29 实跑**：dump 126,325 字节，SHA256 `FA7A928BC6195F5215563930BF8C83D6566E5BA8CB7012F443DF8F6E29863BB8`；导入临时库后 10 张关键表计数全部一致（diff=0）。
- **中文 dump 必带 `--default-character-set=utf8mb4`**：Windows 客户端默认按本地编码解析，漏掉会报 `ERROR at line 161: Unknown command '\n'`（中文字节里的反斜杠被当成命令）。备份与恢复两侧都建议显式指定。
- 口令可用 `MYSQL_PWD` 环境变量传入，避免 `-p` 交互与明文出现在命令历史。

## 6. 教师端聚合：`idx_user_agg` 条件与预聚合

### 6.1 `idx_user_agg`：演示库不建，生产前按条件建

20 万行合成数据终局实测：**仅 Q1 `aggregateByUser` 受益**，P95 731.124→355.608ms；Q2/Q3 未选用该索引。写入 P50 +26.13%、P95 +23.60%、均值 +22.14%，额外磁盘 17.27MiB/20 万行；189 行演示规模无一致可测收益。证据：`spark-output/review/index-user-agg-verify-2026-11-03.md`。

**不提供按行数预估的门槛；若需预估，误差不可控，不作为判据。** 是否执行只看真实运行时的持续延迟、收益占比与资源余量。

**主触发条件**：Q1 `aggregateByUser` 的 **P95 每 10 分钟采样一次，连续 3 次 >400ms**（即连续 30 分钟窗口）即满足迁移触发条件。400ms 的依据是 20 万行 C 组实测 Q1 P95=355.608ms，在其上保留 44.392ms 余量，同时远低于 §6.2 的 2s 预聚合触发线；它不是行数换算值。

**辅助条件**：计算 `Q1 P95/(Q1 P95+Q2 P95+Q3 P95)`，以 **>40%** 判断 Q1 是否为主要耗时来源；20 万行实测占比为 40.72%。该比例只用于收益判断，不单独触发迁移，也不是行数门槛；占比偏低时优先排查 Q2/Q3，而不是指望 `idx_user_agg`。

**两个否决项**：① 写入 SLO 无法容纳 P50 约 +26%、P95 约 +24% 的余量；② 按 `行数×90.52B` 估算的额外磁盘与缓冲池余量不足。任一否决项成立都不执行。

**运维三步：**

1. **看 Q1 P95**：后端值班人员在 APM/慢查询日志中按 `aggregateByUser` SQL 指纹筛选（`GROUP BY user_id`，不要混入 Q2 的 `GROUP BY user_id,kp_id`），每 10 分钟记录一次 P95。未接 APM 时，用同一持久连接每次执行 30 次真实 Q1 SQL 并取 P95，把时间、样本数和结果登记台账；不使用演示种子库数字。
2. **做判定**：连续 3 次 >400ms 后，计算 Q1/Q2/Q3 的耗时占比作辅助确认，再检查上述写入 SLO 与磁盘否决项。
3. **执行与复核**：触发且无否决项时，先按 §5 备份，再只执行 `zhixue-server/sql/migration/2026-11-03-add-performance-indexes.sql` 的 `idx_user_agg` 段；执行后确认 Q1 `EXPLAIN key=idx_user_agg`、聚合结果逐值一致，并复测写入 P50/P95。若实际接口已到 P95>2s 或单次>5s，直接按 §6.2 启动预聚合，不用本索引替代快照。

### 6.2 何时必须上预聚合

以下触发线按**先到先动**执行；任一触发即启动 `learning_portrait_snapshot` 预聚合切换，不得只靠继续加索引拖延。

| 触发线 | 看到什么就动 | 谁看、看哪里 | 必须动作 |
| --- | --- | --- | --- |
| 教师端聚合接口 P95 | 最近 7 天、同一课程、单接口样本 ≥30 次时，`GET /api/portraits/class/{courseId}` 或 `GET /api/teacher/students` 任一接口 **P95 > 2s**；样本不足 30 次时不计算 P95，但单次真实请求 **> 5s** 直接触发 | **后端负责人**每个工作日看真实环境的 APM/网关耗时统计，两个接口分开看；当前未接 APM 时，用教师端浏览器 Network 对同一课程逐接口采集 30 次并计算 P95，结果登记台账，不得拿种子库耗时代替 | 启动预聚合；触发后教师端聚合改读 `learning_portrait_snapshot`，不再直接全量扫描 `practice_record` |
| `practice_record` 行数 | **≥80 万行**即启动预聚合；**达到 100 万行前必须完成切换**，不得等到接口已经超时再动 | **后端负责人**每周一及每次批量导入后，在主库执行 `SELECT COUNT(*) FROM practice_record;`，把行数与日期登记台账 | 80 万行开始快照写入与回源校验；100 万行前完成教师端读路径切换与回滚演练 |

**实测悬崖（Windows 11 单机、临时库 MySQL 8.0.44、InnoDB 缓冲池 128MiB，证据见 `spark-output/review/db-audit-2026-11-03.md` §11.4）**：无 `idx_user_agg` 时，教师端 `aggregateByUser` 从 37.2 万行的 **1.31s** 跳到 55.8 万行的 **47.16s**；即 **40—50 万行区间发生断崖式劣化，不是线性变慢**，机制是工作集超过缓冲池后回表随机 IO 雪崩。该悬崖是提前做预聚合的风险信号，**不得用来绕过 §6.1 的 Q2/Q3 未选用证据与写入成本检查**；`idx_user_agg` 不再是普遍的“必需品”，只按 §6.1 条件执行。

## 7. 日志与排障

> 当前没有文件日志配置，Java 日志只到 stdout。启动时必须重定向，否则关掉终端即丢。`logs/` 默认不存在，必须先建目录：
> `New-Item -ItemType Directory -Force logs | Out-Null; java -jar ... > logs\zhixue-YYYYMMDD.log 2>&1`。`logs/` 不进 git。

| 现象 | 先看 |
| --- | --- |
| Java 起不来 | 控制台 `DB_PASSWORD` / 端口占用 / JDK21 |
| 前端 40100 | `satoken` 过期，重新登录 |
| 讲解一直 L3 | `DEEPSEEK_API_KEY`、AI 8000、`/api/rag/explain` |
| 检索空 | 是否先调 `/api/rag/embed`；阈值 0.35；索引目录 |
| 40900 | 重复提交/打卡/评阅，刷新列表 |
| 教师页 40300 | 当前账号不是 TEACHER |

- **2026-10-29 断网 L3 实跑**：AI 8000 关闭时 health `ai=DEGRADED`，`POST /api/diagnosis/explain` 依次返回 `meta→source→degraded(L3, "AI 服务不可用")→delta×4→done`，181ms，日志无 ERROR；日志 9,305 字节 / 9,146 字符，SHA256 `355EF4902A5DA40C1588CFA4541E3C025142C5F9A029E0062F40C3EF09363422`。

## 8. 生产待补（P1）

TLS/HTTPS、Nginx 限流、健康探针内网限制、MySQL 定时备份、日志轮转、监控告警、孤儿图片清理。
