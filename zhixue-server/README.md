# zhixue-server 工程骨架说明

智学领航业务服务 · 当前 Spring Boot 3.2.5（迁移起点）；目标 4.0.x，验证不过退回 3.5.16。模块化单体（规划书 2.1 节 阶段 A，3 个部署单元中的 Java 侧）。

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
# 前置：docker compose up -d 起好 MySQL/Redis（见部署方案 2 节），表结构由 init-v2.sql 自动建（16 表）
mvn -pl zhixue-bootstrap -am spring-boot:run
# 验证分层示例：GET http://localhost:8080/api/course/kp/KP0101
```

## 对接 zhixue-ai

diagnosis 模块通过 `zhixue.ai.base-url`（application.yml）调用 FastAPI 三路由，超时 10s 触发 60001 降级（契约 2.4 节）。Java 侧不引入任何 Python 依赖。

## TODO 清单（下一阶段任务）

- AuthController 登录校验与 Sa-Token 签发（P0）
- diagnosis OCR 任务表轮询 + RAG SSE 讲解（P0，契约 2.2/2.4 节）
- plan 三层推荐逻辑（P0，契约 2.6 节）