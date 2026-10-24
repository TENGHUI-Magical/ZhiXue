# 智学领航：高校课程学情诊断系统

工作仓库。三个工程 + 基础设施编排，文档在上级目录 `技术学习/`。当前执行口径见 `../技术学习/10-计划收口与申报书差异说明.md`。

## 工程

| 目录 | 技术栈 | 端口 | 职责 |
| --- | --- | --- | --- |
| `zhixue-server` | Spring Boot 3 模块化单体（Maven 多模块） | 8080 | 业务服务，唯一对外入口 |
| `zhixue-ai` | Python + FastAPI | 8000 | OCR / RAG / LLM 三路由，仅内网调用 |
| `zhixue-web` | Vue3 + TypeScript + Element Plus | 5173 | 学生端与教师端 |

规则：Java 不直连大模型，所有 AI 能力经 `zhixue-ai` 暴露；`zhixue-ai` 不对浏览器开放。

## 启动

```bash
# 1. 基础设施（MySQL 8 + Redis 7）
docker compose up -d
docker compose ps

# 2. 后端（JDK 17+，Maven）
cd zhixue-server
mvn -pl zhixue-bootstrap -am spring-boot:run

# 3. AI 服务（Python 3.11+，建议先建 venv）
cd zhixue-ai
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload

# 4. 前端（Node 18+）
cd zhixue-web
npm install
npm run dev
```

启动顺序不能乱：后端要连库，前端要连后端。等日志出现 `Started ServerApplication` 再发请求。

访问 `http://localhost:5173`，接口文档 `http://localhost:8080/swagger-ui/index.html`。

## 验证

```
GET http://127.0.0.1:8080/api/course/kp/KP0101
```

返回 `code: 0` 和知识点数据，说明库、后端、种子数据三者都通了。

用 `127.0.0.1` 不用 `localhost`：Windows 上 `localhost` 优先解析成 IPv6，后端可能只监听 IPv4，会报连接被拒绝。

## 目录

```
05-代码/
├── zhixue-server/          Spring Boot 多模块
│   └── sql/
│       ├── init.sql        V1 历史快照（9 表，仅期中材料）
│       └── init-v2.sql     唯一执行 DDL（16 表，compose 已挂载）
├── zhixue-ai/              FastAPI 三路由
├── zhixue-web/             Vue3 前端
├── docs/                   项目文档（接口契约、算法设计等，待补）
├── tools/                  脚本（数据导入、源码抽取、冒烟测试）
└── docker-compose.yml      MySQL + Redis
```

## 文档

在上级目录 `技术学习/`：

| 文件 | 用途 |
| --- | --- |
| `01-开发规范-对齐阿里手册.md` | 命名、分层、异常、日志、数据库、前端、Git |
| `03-数据库设计-V2.md` / `.sql` | 表结构与改动说明 |
| `05-开发路线图.md` | 9 阶段 55 步 |
| `06-关键决策记录.md` | 课程、SSE、技术栈的定案与理由 |
| `08-技术避坑清单.md` | 27 条真踩过的坑 |
| `10-计划收口与申报书差异说明.md` | 当前唯一执行口径与申报书差异 |

## 提交前

- [ ] `.env` 和密钥没有进仓库
- [ ] 没有 `target/`、`node_modules/`、`.idea/`
- [ ] 接口有变动时同步更新契约文档

## 首次建仓

```bash
git init
git add .
git commit -m "chore: 初始化工作仓库，导入三工程骨架与基础设施配置"
git remote add origin <Gitee 仓库地址>
git push -u origin main
```
