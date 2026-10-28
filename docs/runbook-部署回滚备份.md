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
$env:JAVA_HOME='D:\jdk-21.0.13.0.3'
mvn -B -ntp clean package
cd ..\zhixue-web
npm ci
npm run build
```

产物：`zhixue-bootstrap/target/zhixue-bootstrap-0.1.0-SNAPSHOT.jar`、`zhixue-web/dist/`。

## 3. 部署（单机）

1. **备份**：见 §5，先备份 MySQL 与 `zhixue-ai/data/vector_index`。
2. 停旧进程（记录 PID）：Java 8080、AI 8000、前端静态服务 5173/80。
3. 放新 jar / dist；保留上一版 jar 改名 `*.bak`。
4. 起 Java：`java -jar ...`；起 AI：`uvicorn app.main:app --host 127.0.0.1 --port 8000`；Nginx/静态服务器指向 `dist/`。
5. 健康检查：`GET /api/system/health` 返回 200；db/redis UP；ai 允许 DEGRADED（未配 key）。
6. 冒烟：登录 → 课程列表 → 错题列表 → 生成讲解 → 教师评阅队列。

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
# 备份（含建表；先建目录）
New-Item -ItemType Directory -Force backup | Out-Null
mysqldump -h 127.0.0.1 -u root -p --single-transaction --routines --triggers zhixue > backup\zhixue_20261028.sql

# 恢复（先停 Java，避免写入）
mysql -h 127.0.0.1 -u root -p -e "DROP DATABASE IF EXISTS zhixue; CREATE DATABASE zhixue DEFAULT CHARSET utf8mb4;"
mysql -h 127.0.0.1 -u root -p --default-character-set=utf8mb4 zhixue < backup\zhixue_20261028.sql
```

- 结项前每次批量导入内容后备份一次；保留最近 7 天。
- RAG 索引备份：复制 `zhixue-ai/data/vector_index/`（`vectors.npy` + `chunks.json` + `meta.json`）。
- **2026-10-29 实跑**：dump 126,325 字节，SHA256 `FA7A928BC6195F5215563930BF8C83D6566E5BA8CB7012F443DF8F6E29863BB8`；导入临时库后 10 张关键表计数全部一致（diff=0）。
- **中文 dump 必带 `--default-character-set=utf8mb4`**：Windows 客户端默认按本地编码解析，漏掉会报 `ERROR at line 161: Unknown command '\n'`（中文字节里的反斜杠被当成命令）。备份与恢复两侧都建议显式指定。
- 口令可用 `MYSQL_PWD` 环境变量传入，避免 `-p` 交互与明文出现在命令历史。

## 6. 日志与排障

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

## 7. 生产待补（P1）

TLS/HTTPS、Nginx 限流、健康探针内网限制、MySQL 定时备份、日志轮转、监控告警、孤儿图片清理。
