# 智学领航：高校课程学情诊断系统

工作仓库。三个工程 + 基础设施编排。执行口径见 `../技术学习/10-计划收口与申报书差异说明.md`；接口、算法与测试以 `docs/` 下的 V2 文档为准。

## 工程

| 目录 | 技术栈 | 端口 | 职责 |
| --- | --- | --- | --- |
| `zhixue-server` | Spring Boot 4.0.6 模块化单体（Maven 多模块，Java 21） | 8080 | 业务服务，唯一对外入口 |
| `zhixue-ai` | Python 3.12 + FastAPI | 8000 | OCR / RAG / LLM 三路由，仅内网调用 |
| `zhixue-web` | Vue3 + TypeScript + Element Plus | 5173 | 学生端与教师端 |

规则：Java 不直连大模型，所有 AI 能力经 `zhixue-ai` 暴露；`zhixue-ai` 不对浏览器开放。

## 启动

```bash
# 0. 本地口令（可选；不建 .env 时使用开发默认值 change-me）
cp .env.example .env
#    编辑 .env，把 MYSQL_ROOT_PASSWORD / DB_PASSWORD 改成同一个强口令
#    注意：Spring 不自动读 .env，启动后端时需显式传入 DB_PASSWORD

# 1. 基础设施（MySQL 8 + Redis 7，只绑定 127.0.0.1）
#    Docker 可选；没有 Docker 时用本机 MySQL 8 + Redis 7（3306/6379），SQL 导入见 docs/08
#    Docker 首次启动只执行 2 个文件：01-init-v2.sql + 02-seed-demo-all.sql
#    合并种子由 scripts/build-seed-all.ps1 生成；任何一章 seed-*.sql 改动后必须重跑再提交
#    若 seed-demo-all.sql 不存在：先 pwsh -ExecutionPolicy Bypass -File scripts/build-seed-all.ps1
#    自检生成文件是否过期：pwsh -ExecutionPolicy Bypass -File scripts/verify-seed-all-up-to-date.ps1
#    预期：8 章 / 222 题（当前种子快照；以实际输出为准，不要把 222 当门禁）
docker compose up -d
docker compose ps

#    核对（口令用 .env 的 MYSQL_ROOT_PASSWORD）：
#    docker exec -it zhixue-mysql mysql -uroot -p -e "USE zhixue; SELECT COUNT(*) AS chapters FROM course_chapter; SELECT COUNT(*) AS questions FROM question_bank;"

# 1.5 本地开发账号（仅本地；口令不进入仓库）
#     生成随机口令并写入已 gitignore 的本地 SQL，终端会打印一次口令
pip install bcrypt
python tools/gen_dev_users.py
mysql -h 127.0.0.1 -u root -p --default-character-set=utf8mb4 zhixue < zhixue-server/sql/seed-dev-users.local.sql

# 1.6 答辩/换机演示库（第1—8章 + 12 名演示学生 + 教师评阅队列）
#     Docker：首次 docker compose up -d 自动导入合并种子
#     本机 MySQL：下面一条命令；不碰现有库用 -Database zhixue_demo -Recreate -Force
pwsh -ExecutionPolicy Bypass -File scripts/seed-demo.ps1
#     内容线更新种子后，先重新生成合并文件：
pwsh -ExecutionPolicy Bypass -File scripts/build-seed-all.ps1
#     账号/口令/RAG 重建/验收数字见 docs/13-演示库恢复.md
#     对外报数先跑：pwsh -ExecutionPolicy Bypass -File scripts/report-numbers.ps1
#     再引用 docs/numbers-current.md（由脚本生成，不要手改）
#     答辩当天先对齐日期再自检（一条命令看全部就绪）：
pwsh -ExecutionPolicy Bypass -File scripts/refresh-demo-dates.ps1
pwsh -ExecutionPolicy Bypass -File scripts/precheck-demo.ps1

# 2. 后端（JDK 21，Maven；默认 dev profile）
cd zhixue-server
#    Windows 推荐（自动固定 JDK 21）：
..\scripts\mvn21.ps1 -B -ntp -pl zhixue-bootstrap spring-boot:run
#    bash：JAVA_HOME=/path/to/jdk-21 mvn -B -ntp -pl zhixue-bootstrap spring-boot:run
#    口令：$env:DB_PASSWORD="你的口令"（bash：export DB_PASSWORD='你的口令'）
#    说明：target 里的 jar 是 thin jar（无主清单），java -jar 会报“没有主清单属性”；
#          统一用上面的 spring-boot:run（已在 8080 实测登录、画像、评阅可用）。

# 3. AI 服务（Python 3.12，建议先建 venv）
cd zhixue-ai
pip install -r requirements.txt
#    说明：requirements.txt 含 sentence-transformers（会带 torch，约 2GB）；无 bge 模型时检索自动走 hash-bigram
#    需要 OCR 时再装（与上表配套，勿单独升级）：
#    pip install -r requirements-ocr.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload

# 4. 前端（Node 20+）
cd zhixue-web
npm ci
npm run dev
```

> Windows PowerShell 注意：不支持 `mysql ... < file`（`<` 是保留运算符）；改用 `Get-Content ... -Raw | & mysql ...`，并先设 `$env:MYSQL_PWD`。本机 `mysql.exe` 不在 PATH，完整路径与命令见 `docs/08-新人上手.md`。
>
> Maven 自检：`mvn -v` 应显示 `Java version: 21`；显示 17 就用 `..\scripts\mvn21.ps1`。若 `settings.xml` 的 `localRepository` 指向 `D:\MAVEN\...` 且该路径不可写，改用默认 `~/.m2` 或 `mvn -s`。

启动顺序不能乱：后端要连库，前端要连后端。等日志出现 `Started ServerApplication` 再发请求。

访问 `http://127.0.0.1:5173`，接口文档 `http://127.0.0.1:8080/swagger-ui/index.html`。

### 配置分层

| 文件 | 用途 |
| --- | --- |
| `application.yml` | 公共配置，默认激活 `dev` |
| `application-dev.yml` | 本地 MySQL/Redis、SQL 日志；允许 `useSSL=false` |
| `application-prod.yml` | 生产：`DB_URL/DB_USERNAME/DB_PASSWORD` 必须环境变量注入，强制 `useSSL=true`，关闭 SQL 打印 |

生产启动示例：`SPRING_PROFILES_ACTIVE=prod DB_URL=... DB_USERNAME=... DB_PASSWORD=... java -jar ...`。

## 验证

```
GET http://127.0.0.1:8080/api/course/kp/KP0201
```

返回 `code: 0` 和知识点数据，说明库、后端、种子数据三者都通了。种子账号：`teacher01` / `student01`，口令由 `tools/gen_dev_users.py` 生成并打印一次（本地 SQL 已 gitignore；生产环境不得使用固定口令）。

`GET /api/system/health` 无鉴权，仅返回 db/redis/ai 组件状态；生产部署需在网关限制为内网探针，或收敛为只返回 `status` 字段。

用 `127.0.0.1` 不用 `localhost`：Windows 上 `localhost` 优先解析成 IPv6，后端可能只监听 IPv4，会报连接被拒绝。

## 目录

```
zhixue/
├── zhixue-server/          Spring Boot 多模块
│   └── sql/
│       ├── init.sql        V1 历史快照（9 表，仅期中材料）
│       ├── init-v2.sql     唯一执行 DDL（16 表，已含 V2.1：动态维度/幂等键）
│       └── init-v2.1-migration.sql  仅旧库升级用；新库不要执行
├── zhixue-ai/              FastAPI 三路由
├── zhixue-web/             Vue3 前端
├── docs/                   openapi.yaml + 01 契约 / 05 算法 / 06 测试 + adr/
├── tools/                  脚本（数据导入、源码抽取、冒烟测试）
└── docker-compose.yml      MySQL + Redis
```

## 文档

| 文件 | 用途 |
| --- | --- |
| `docs/openapi.yaml` | 接口唯一机器可读源（2.2.3，30 条路径） |
| `docs/01-接口契约V2.md` | 状态机、错误码、SSE、降级、幂等 |
| `docs/05-算法与规则设计.md` | 映射 / 掌握度 / 推荐 / 检索四套公式与参数 |
| `docs/06-测试验收计划.md` | 测试分层与申报书指标对照 |
| `docs/adr/ADR-004-SpringBoot版本选型.md` | Boot 4 + Java 21 验证记录 |
| `../技术学习/01-10` | 规范、路线图、决策记录、避坑清单、收口口径 |

## 提交前

- [ ] `.env` 和密钥没有进仓库
- [ ] 没有 `target/`、`node_modules/`、`.idea/`
- [ ] 接口有变动时同步更新 `docs/openapi.yaml` 与契约文档
