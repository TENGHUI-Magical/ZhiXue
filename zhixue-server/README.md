# zhixue-server 工程骨架说明

智学领航业务服务 · Spring Boot 4.0.6 + Java 21（2026-10-24 验证通过，见 `docs/adr/ADR-004-SpringBoot版本选型.md`）。模块化单体（规划书 2.1 节 阶段 A，3 个部署单元中的 Java 侧）。

## 模块结构

```
zhixue-server/
├── pom.xml                 父 POM：统一 Spring Boot / MyBatis-Plus / Sa-Token 版本
├── sql/init-v2.sql         唯一执行 DDL（16 表）+ 数据库原理种子数据
├── sql/init.sql            V1 历史快照（9 表，仅期中材料引用）
├── zhixue-common           统一响应 ApiResponse / 错误码 ErrorCode / 全局异常处理（契约 1 节）
├── zhixue-user             用户与权限：AuthController 登录桩（契约 2.1 节）
├── zhixue-course           课程模块 ★完整分层切片示例：知识点查询（契约附录）
├── zhixue-diagnosis        诊断核心：OCR 调度/错题/映射/讲解/画像（答辩核心，不拆分）
├── zhixue-plan             练习推荐/周计划/打卡
├── zhixue-notification     站内信/督学提醒（永远单体内模块）
└── zhixue-bootstrap        唯一启动入口 + application.yml（聚合 5 个业务模块）
```

## 分层规范（新模块照此扩展）

```
controller  → 只做参数接收与 ApiResponse 包装
service     → 业务规则、BizException 抛错（错误码见契约 1.2 节）
mapper      → MyBatis-Plus BaseMapper，实体与 DDL 字段一一对应
entity      → @TableName 与 init-v2.sql 对齐
```

## 运行

```bash
# 前置：JDK 21；MySQL/Redis 已启动（docker compose up -d 或本机服务）
# 首次启动按 01/02/03 顺序执行：建表(16 表) → 第2-3章 20 知识点 → 60 道题
mvn -q -DskipTests package
DB_PASSWORD=123456 java -jar zhixue-bootstrap/target/zhixue-bootstrap-0.1.0-SNAPSHOT.jar
# 验证分层示例：GET http://localhost:8080/api/course/kp/KP0201
```

种子文件：
- `sql/seed-kp-ch02-ch03.sql`：20 个知识点（KP0201—KP0310）、先修边、关键词，均可重复执行
- `sql/seed-question-ch02-ch03.sql`：60 道题 + 81 条题目-知识点关联；Q_0220、Q_0224、Q_0326 待教师复核

已有 `data/mysql` 时 init 脚本不会重跑；要重新灌种子，先 `docker compose down` 并删除 `data/mysql`（或手动 `mysql < sql/seed-*.sql`）。

## 对接 zhixue-ai

diagnosis 模块通过 `zhixue.ai.base-url`（application.yml）调用 FastAPI 三路由，超时 10s 触发 60001 降级（契约 2.4 节）。Java 侧不引入任何 Python 依赖。

## TODO 清单（下一阶段任务）

- AuthController 登录校验与 Sa-Token 签发（P0）
- diagnosis OCR 任务表轮询 + RAG SSE 讲解（P0，契约 2.2/2.4 节）
- plan 三层推荐逻辑（P0，契约 2.6 节）