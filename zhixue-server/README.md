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
# 演示库一条命令（第1—4章 + 演示账号 + 演示数据）；细节见 docs/13-演示库恢复.md
pwsh -ExecutionPolicy Bypass -File ..\scripts\seed-demo.ps1
mvn -q -DskipTests package
DB_PASSWORD=123456 java -jar zhixue-bootstrap/target/zhixue-bootstrap-0.1.0-SNAPSHOT.jar
# 验证分层示例：GET http://localhost:8080/api/course/kp/KP0201
```

种子文件：
- `sql/seed-kp-ch01.sql` / `seed-question-ch01.sql`：第1章 10 点 / 25 题（由文档仓 ch01 复制）
- `sql/seed-kp-ch02-ch03.sql` / `seed-question-ch02-ch03.sql`：第2—3章 20 点 / 60 题 + 81 条关联
- `sql/seed-kp-ch04.sql` / `seed-question-ch04.sql`：第4章 11 点 / 26 题（由文档仓 ch04 复制）
- `sql/seed-prereq-ch01.sql` / `seed-prereq-ch04.sql`：第1/4章先修边
- `sql/seed-demo-users.sql` / `seed-demo-data.sql`：演示账号（固定演示口令）与 12 名学生/180 练习
- 所有种子均可重复执行；演示账号 SQL 不重置已有账号口令

Docker 首次启动会按 `docker-compose.yml` 的 01—11 顺序自动导入；已有 `data/mysql` 时 init 脚本不会重跑，要重新灌种子先备份并删除 `data/mysql`（或本机执行 `scripts/seed-demo.ps1`）。

## 对接 zhixue-ai

diagnosis 模块通过 `zhixue.ai.base-url`（application.yml）调用 FastAPI 三路由，超时 10s 触发 60001 降级（契约 2.4 节）。Java 侧不引入任何 Python 依赖。

## TODO 清单（下一阶段任务）

- AuthController 登录校验与 Sa-Token 签发（P0）
- diagnosis OCR 任务表轮询 + RAG SSE 讲解（P0，契约 2.2/2.4 节）
- plan 三层推荐逻辑（P0，契约 2.6 节）