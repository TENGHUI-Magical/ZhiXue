# 真实联调 E2E（不 mock）

两套脚本，只在本地/测试环境跑，**不接入 CI**（CI 没有 MySQL/Redis/后端）。

| 脚本 | 覆盖 | 前置 |
|---|---|---|
| `npm run e2e:real` | 登录、课程/图谱/知识点、映射、错题创建+确认、SSE 讲解、推荐、计划生成、打卡、画像、教师学生/班级/评阅、health | 后端 8080 + MySQL/Redis + 种子数据 |
| `npm run e2e:real:ui` | 真实浏览器：登录、今日、错题本、画像、计划、图谱（含邻接高亮）、诊断台、个人中心、教师三页、评阅提交/只读 | 上面全部 + Vite 5174 + Edge |

## 准备工作

```powershell
# 1. 后端（示例；口令按本地实际）
cd zhixue-server
$env:JAVA_HOME='D:\jdk-21.0.13.0.3'
mvn -B -ntp -DskipTests package
java -jar zhixue-bootstrap/target/zhixue-bootstrap-0.1.0-SNAPSHOT.jar `
  --spring.datasource.username=root --spring.datasource.password=123456

# 2. 前端
cd zhixue-web
npm run dev -- --port 5174
```

种子账号口令见 `zhixue-server/sql/seed-dev-users.local.sql` 的生成说明；本地联调若重置过口令，以下示例使用 `123456`（**仅本地**）。

## 运行

```powershell
cd zhixue-web
$env:E2E_USER='student01'; $env:E2E_PASS='123456'
$env:E2E_TEACHER_USER='teacher01'; $env:E2E_TEACHER_PASS='123456'
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