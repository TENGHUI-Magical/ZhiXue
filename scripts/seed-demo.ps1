# 智学领航 · 演示库统一入口（本地 MySQL 8）
# 用途：按章节顺序导入第1—5章种子 + 演示账号 + 演示练习/错题/评阅数据。
# 幂等：所有种子均为 upsert；默认不删库，只有显式 -Recreate -Force 才重建。
# 用法：
#   $env:DB_PASSWORD = '<本机 root 口令>'
#   pwsh -ExecutionPolicy Bypass -File .\scripts\seed-demo.ps1
#   pwsh -ExecutionPolicy Bypass -File .\scripts\seed-demo.ps1 -Recreate -Force
#   # 不碰现有 zhixue 库的演练（应用侧用 SPRING_DATASOURCE_URL 指向该库）：
#   pwsh -ExecutionPolicy Bypass -File .\scripts\seed-demo.ps1 -Database zhixue_demo -Recreate -Force
# 注意：Windows PowerShell 5.1 读取无 BOM 的 UTF-8 脚本可能把中文路径读成乱码，推荐 pwsh（PS7）。
# 说明：Docker 环境用 `docker compose up -d`，initdb 会自动按顺序执行同一批 SQL。

[CmdletBinding()]
param(
    [string]$MySqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$Password = $env:DB_PASSWORD,
    [string]$Database = 'zhixue',
    [switch]$Recreate,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $MySqlExe)) {
    throw "找不到 mysql 客户端：$MySqlExe；请用 -MySqlExe 指定路径"
}
if (-not $Password) {
    $Password = 'change-me'
    Write-Host '未提供 -Password 或 DB_PASSWORD，按 docker-compose 默认口令 change-me 尝试连接。' -ForegroundColor Yellow
}
if ($Recreate -and -not $Force) {
    throw '重建会删除 zhixue 库；确认后追加 -Force'
}

$sqlDir = Join-Path $PSScriptRoot '..\zhixue-server\sql'
$files = @(
    'init-v2.sql',
    'seed-kp-ch01.sql',
    'seed-question-ch01.sql',
    'seed-kp-ch02-ch03.sql',
    'seed-question-ch02-ch03.sql',
    'seed-kp-ch04.sql',
    'seed-question-ch04.sql',
    'seed-kp-ch05.sql',
    'seed-question-ch05.sql',
    'seed-prereq-ch01.sql',
    'seed-prereq-ch04.sql',
    'seed-prereq-ch05.sql',
    'seed-demo-users.sql',
    'seed-demo-data.sql'
)

$previousPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $Password
$baseArgs = @('-h', $DbHost, '-P', "$Port", '-u', $User, '--default-character-set=utf8mb4')

try {
    & $MySqlExe @baseArgs -e 'SELECT 1;' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'MySQL 连接失败，请检查 -Host/-Port/-User/-Password' }

    if ($Recreate) {
        Write-Host "删除并重建 $Database 库…" -ForegroundColor Yellow
        & $MySqlExe @baseArgs -e "DROP DATABASE IF EXISTS $Database;"
        if ($LASTEXITCODE -ne 0) { throw 'DROP DATABASE 失败' }
    }

    foreach ($name in $files) {
        $path = Join-Path $sqlDir $name
        if (-not (Test-Path $path)) { throw "缺少种子文件：$path" }
        Write-Host "导入 $name …"
        $content = Get-Content -Raw -Encoding utf8 $path
        if ($name -eq 'init-v2.sql') {
            $content = $content -replace 'CREATE DATABASE IF NOT EXISTS zhixue', "CREATE DATABASE IF NOT EXISTS $Database"
        }
        $content = $content -replace 'USE zhixue;', "USE $Database;"
        if ($name -eq 'init-v2.sql') {
            $content | & $MySqlExe @baseArgs
        } else {
            $content | & $MySqlExe @baseArgs $Database
        }
        if ($LASTEXITCODE -ne 0) { throw "导入失败：$name" }
    }

    Write-Host ''
    Write-Host '导入完成，当前演示库数据量：'
    $countSql = @'
SELECT 'chapter' AS item, COUNT(*) AS cnt FROM course_chapter
UNION ALL SELECT 'knowledge_point', COUNT(*) FROM knowledge_point
UNION ALL SELECT 'question_bank', COUNT(*) FROM question_bank
UNION ALL SELECT 'sys_user', COUNT(*) FROM sys_user
UNION ALL SELECT 'practice_record', COUNT(*) FROM practice_record
UNION ALL SELECT 'student_error_record', COUNT(*) FROM student_error_record
UNION ALL SELECT 'ai_generation_log', COUNT(*) FROM ai_generation_log
UNION ALL SELECT 'pending_review', COUNT(*) FROM ai_generation_log WHERE scene='RAG' AND reviewed_at IS NULL;
'@
    $countSql | & $MySqlExe @baseArgs -t $Database
    if ($LASTEXITCODE -ne 0) { throw '统计查询失败' }

    Write-Host ''
    Write-Host '演示账号：teacher01 / student01 / demo_stu01…demo_stu11（全新库口令 Demo@2026）'
    Write-Host 'RAG 索引：先启动 zhixue-ai，再执行 python tools/build_demo_index.py --base-url http://127.0.0.1:8000'
}
finally {
    $env:MYSQL_PWD = $previousPwd
}
