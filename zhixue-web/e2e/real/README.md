# 真实联调 E2E（不 mock）

三套脚本：前两套为本地联调；`e2e:real:ci` 是 CI 门禁（workflow `e2e-real`，真 MySQL/Redis + 真 Spring Boot + 真 Vite + 真浏览器，与 mock 的 `e2e` job 完全独立）。

| 脚本 | 覆盖 | 前置 |
|---|---|---|
| `npm run e2e:real` | 登录、课程/图谱/知识点、映射、错题创建+确认、SSE 讲解、推荐、计划生成、打卡、画像、教师学生/班级/评阅、health | 后端 8080 + MySQL/Redis + 种子数据 |
| `npm run e2e:real:ui` | 真实浏览器：登录、今日、错题本、画像、计划、图谱（含邻接高亮）、诊断台、个人中心、教师三页、评阅提交/只读 | 上面全部 + Vite 5174 + Edge |
| `npm run e2e:real:ci` | 5 场景 / 30 子断言：学生作答真写库、画像掌握度/薄弱点、教师评阅 PASS 学生可见、teacher02 越权 40300、评阅留痕 CSV（表头/行数/不含正文） | 能 `mvn -DskipTests package` 的后端 jar + MySQL/Redis + Vite 5173 + Edge/Chrome |

## CI 门禁怎么跑

workflow `.github/workflows/ci.yml` 的 `e2e-real` job：`services` 拉 `mysql:8.0` + `redis:7-alpine`，`mvn -DskipTests package` 打包，`npm ci`，然后 `npm run e2e:real:ci`。数据准备每次 `DROP DATABASE IF EXISTS <E2E_DB_NAME>` 后重放仓库 SQL，可幂等重跑；`E2E_DB_NAME` 必须以 `zhixue_e2e` 开头，防止误删开发库。失败/异常一律非 0 退出，日志与截图随 artifact 上传。

## 准备工作

```powershell
# 1. 后端（示例；口令按本地实际）
cd zhixue-server
$env:JAVA_HOME='D:\jdk-21.0.13.0.3'
mvn -B -ntp -DskipTests package
java -jar zhixue-bootstrap/target/zhixue-bootstrap-0.1.0-SNAPSHOT.jar `
  --spring.datasource.username=root --spring.datasource.password=<示例密码，勿用真实值>

# 2. 前端
cd zhixue-web
npm run dev -- --port 5174
```

种子账号口令见 `zhixue-server/sql/seed-dev-users.local.sql` 的生成说明；以下示例中的口令均为占位符 `<示例密码，勿用真实值>`，运行前替换为本地实际口令（**仅本地**）。

## 运行

```powershell
cd zhixue-web
$env:E2E_USER='student01'; $env:E2E_PASS='<示例密码，勿用真实值>'
$env:E2E_TEACHER_USER='teacher01'; $env:E2E_TEACHER_PASS='<示例密码，勿用真实值>'
npm run e2e:real
npm run e2e:real:ui

# 可选：真实 OCR（指向一张本地错题图片）
$env:E2E_OCR_IMAGE='D:\path\to\question.png'
npm run e2e:real
```

报告默认写到 `spark-output/qa/2026-11-01-real/`；可用 `E2E_REAL_OUT` 覆盖。  
AI 服务可选：启动 `zhixue-ai`（uvicorn 8000）后可验证真实 OCR/LLM；不启动时 SSE 应出现 `degraded` 事件，但必须仍有 `meta/delta/done`；脚本按这个口径断言。

## 已知契约缺口（不是前端 bug）

`docs/openapi.yaml` 声明了 `GET /api/teacher/questions` 与 `GET /api/teacher/knowledge-points`，当前后端无对应 Controller，实测返回 `40400 资源不存在`。前端未使用这两个端点；结项前要么实现，要么从契约中移除（T2 变更，需 PM 确认）。

## 数据写入声明

`api-smoke` 会写入 1 条错题、1 周计划、1 次打卡；`ui-real` 会提交 1 条教师评阅（若队列有未评阅且有证据的记录）。只允许对本地/测试库运行，运行前建议 `mysqldump` 备份。