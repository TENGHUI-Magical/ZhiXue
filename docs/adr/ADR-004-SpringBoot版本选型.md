# ADR-004 Spring Boot 版本选型

| 项目 | 内容 |
| --- | --- |
| 文档编号 | ZX-ADR-004 |
| 版本 | V1.0 |
| 日期 | 2026-10-24 |
| 状态 | 已采纳 |
| 决策 | **Spring Boot 4.0.6 + Java 21**（不再使用 3.2.5；除非核心依赖回归失败，否则不退回 3.5.16） |
| 依据 | 《04-技术栈评估与优化》《10-计划收口与申报书差异说明》P0 门禁 |

---

## 1. 背景

项目骨架最初使用 Spring Boot 3.2.5 + Java 17。3.2 已过 OSS 支持期，且团队尚未写业务代码，升级成本最低。需要验证三件事：

1. Spring Boot 4 + MyBatis-Plus 3.5.17 能否启动并完成一次数据库查询；
2. Spring Boot 4 + Sa-Token 1.46.0 能否签发 token；
3. Spring Boot 4 + springdoc-openapi 3.1.1 能否打开 Swagger。

---

## 2. 验证环境

| 项 | 版本 |
| --- | --- |
| JDK | 21.0.7（`D:\jdk21\jdk-21.0.7`） |
| Maven | 3.9.9 |
| Spring Boot | 4.0.6 |
| MyBatis-Plus | 3.5.17（`mybatis-plus-spring-boot4-starter`） |
| Sa-Token | 1.46.0（`sa-token-spring-boot4-starter`） |
| springdoc-openapi | 3.1.1 |
| 数据库 | H2 内存库（本机无 Docker/MySQL，用 H2 验证 ORM 链路） |
| 应用端口 | 18080 |

验证工程位于临时目录，不属于项目仓库；仅用于版本兼容性验证。

---

## 3. 验证方法

最小工程包含：

- `Boot4CheckApplication`：`@SpringBootApplication` + `@MapperScan`
- `PingMapper`：`@Select("SELECT 1") int ping()`
- `CheckController`：`/health`、`/db`、`/token`
- H2 内存数据源；Sa-Token 默认内存会话

启动后逐项访问：

| 检查项 | 预期 | 实测 | 结果 |
| --- | --- | --- | --- |
| `GET /health` | `ok` | `ok` | ✅ |
| `GET /db`（MyBatis-Plus） | `db=1` | `db=1` | ✅ |
| `GET /token`（Sa-Token） | 返回 token | `token=e00e944a-...` | ✅ |
| `GET /v3/api-docs`（springdoc） | HTTP 200 | 200，647 bytes | ✅ |
| `GET /swagger-ui.html` | HTTP 200 | 200，734 bytes | ✅ |

启动日志关键行：

```text
Spring Boot :: (v4.0.6)
Starting Boot4CheckApplication using Java 21.0.7
Tomcat initialized with port 18080 (http)
Starting Servlet engine: [Apache Tomcat/11.0.21]
Root WebApplicationContext: initialization completed in 1643 ms
```

---

## 4. 决策

**采纳 Spring Boot 4.0.6 + Java 21**，并同步升级项目：

| 项 | 旧 | 新 |
| --- | --- | --- |
| Spring Boot | 3.2.5 | **4.0.6** |
| Java | 17 | **21** |
| MyBatis-Plus | 3.5.7（boot3） | **3.5.17（boot4）** |
| Sa-Token | 1.39.0（boot3） | **1.46.0（boot4）** |
| 接口文档 | 无 | **springdoc-openapi 3.1.1** |

升级范围：父 POM、`zhixue-common`、`zhixue-user`、`zhixue-course`、`zhixue-diagnosis`、`zhixue-plan`、`zhixue-bootstrap`。

---

## 5. 风险与回退

| 风险 | 说明 | 应对 |
| --- | --- | --- |
| Boot 4 生态仍在演进 | 个别第三方库可能未适配 | 只保留已验证的 MP/Sa-Token/springdoc；新增依赖前先做兼容性验证 |
| 首次冷启动偏慢 | 临时工程首次启动超过 40 秒轮询窗口，第二次启动正常；推断为首次类加载/依赖扫描 | 团队机器复测冷启动时间；若稳定 >60 秒再排查依赖，不影响兼容性结论 |
| 未验证真实 MySQL | 本次用 H2 验证 ORM，MySQL 驱动/连接池未跑 | 首次 `docker compose up` 后执行 `GET /api/course/kp/KP0201` 验证 |
| Spring Cloud Alibaba 不兼容 | 微服务已取消，不引入 Nacos/Gateway | 不涉及 |
| 回退条件 | 任一核心模块在 Boot 4 下编译/启动失败 | 当天退回 Spring Boot 3.5.16 + Java 21，并在本 ADR 追加回退记录 |

**结论**：本次验证三项全过，不触发回退。

---

## 6. 复现命令

```powershell
# 1. 设置 JDK 21（PowerShell）
$env:JAVA_HOME="D:\jdk21\jdk-21.0.7"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

# 2. 在项目根目录编译
cd zhixue-server
mvn -q -DskipTests clean compile

# 3. 首次启动（需 MySQL/Redis）
docker compose up -d
mvn -q -DskipTests package
java -jar zhixue-bootstrap/target/zhixue-bootstrap-0.1.0-SNAPSHOT.jar

# 4. 验证
curl http://127.0.0.1:8080/api/course/kp/KP0201
curl http://127.0.0.1:8080/v3/api-docs
```

---

## 7. 版本记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| V1.0 | 2026-10-24 | 首次验证并采纳 Boot 4.0.6 + Java 21；三项全过，无回退 |
