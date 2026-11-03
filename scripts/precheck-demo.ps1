# 智学领航 · 答辩现场自检（一条命令确认全部就绪）
# 覆盖：MySQL、主库计数、演示账号登录、后端健康（db/redis/ai）、教师评阅接口（防旧 jar 伪装 50000）、
#       前端 5173、RAG 索引、到期队列、今日计划、AI 真实链路。
# 输出：每项 ✅/⚠️/❌ + 一句修复提示；有 ❌ 时退出码 1。
# 用法：
#   $env:DB_PASSWORD = '<本机 root 口令>'
#   pwsh -ExecutionPolicy Bypass -File .\scripts\precheck-demo.ps1
#   pwsh ... -SkipAiLive      # 跳过真实模型调用（省时间/省 token）
#   pwsh ... -Database zhixue_demo -BackendBase http://127.0.0.1:8082

[CmdletBinding()]
param(
    [string]$MySqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$Password = $env:DB_PASSWORD,
    [string]$Database = 'zhixue',
    [string]$BackendBase = 'http://127.0.0.1:8080',
    [string]$AiBase = 'http://127.0.0.1:8000',
    [string]$FrontendUrl = 'http://localhost:5173',
    [string]$IndexDir = (Join-Path $PSScriptRoot '..\zhixue-ai\data\vector_index'),
    [string]$ContentRoot = (Join-Path $PSScriptRoot '..\..\spark-output\content'),
    [string]$DemoUsername = 'demo_stu01',
    [string]$DemoPassword = 'Demo@2026',
    [string]$DemoTeacherUsername = 'demo_teacher01',
    [switch]$SkipAiLive
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.Encoding]::UTF8
$results = New-Object System.Collections.Generic.List[object]

function Add-Result([string]$status, [string]$name, [string]$detail, [string]$fix = '') {
    $results.Add([pscustomobject]@{ Status = $status; Name = $name; Detail = $detail; Fix = $fix })
}

function Format-ApiError($err) {
    return ($err.Exception.Message -replace "`r|`n", ' ')
}

# ---------- 1. MySQL 可连 + 主库计数 + 账号 ----------
$dbReady = $false
$env:MYSQL_PWD = $Password
$baseArgs = @('-h', $DbHost, '-P', "$Port", '-u', $User, '--default-character-set=utf8mb4')
try {
    if (-not (Test-Path $MySqlExe)) { throw "找不到 mysql 客户端：$MySqlExe" }
    $probe = & $MySqlExe @baseArgs -N -B -e "SELECT 1" 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($probe -join ' ') }
    $dbReady = $true
    Add-Result 'OK' 'MySQL' "连接正常（$DbHost`:$Port / $Database）"
}
catch {
    Add-Result 'FAIL' 'MySQL' (Format-ApiError $_) '检查 MySQL80 服务与 -Password；管理员 PowerShell 跑 Start-Service MySQL80'
}

if ($dbReady) {
    try {
        $row = & $MySqlExe @baseArgs -N -B $Database -e @"
SELECT
 (SELECT COUNT(*) FROM course_chapter),
 (SELECT COUNT(*) FROM knowledge_point),
 (SELECT COUNT(*) FROM question_bank),
 (SELECT COUNT(*) FROM knowledge_prereq),
 (SELECT COUNT(*) FROM sys_user),
 (SELECT COUNT(*) FROM ai_generation_log WHERE review_result='RETURN' AND NULLIF(TRIM(teacher_comment),'') IS NOT NULL);
"@ 2>&1
        if ($LASTEXITCODE -ne 0) { throw ($row -join ' ') }
        $c = @($row -split "`t")
        $chapters = [int]$c[0]; $kps = [int]$c[1]; $questions = [int]$c[2]
        $edges = [int]$c[3]; $users = [int]$c[4]
        $returnWithComment = [int]$c[5]
        $detail = "$chapters 章 / $kps 点 / $questions 题 / $edges 边"
        if ($chapters -ge 8 -and $kps -ge 80 -and $questions -ge 200 -and $edges -ge 80) {
            Add-Result 'OK' '主库计数' $detail
        } else {
            Add-Result 'FAIL' '主库计数' "$detail（低于演示下限 8/80/200/80）" '重跑 scripts/seed-demo.ps1 -Database zhixue'
        }
        $csvFiles = @(Get-ChildItem -Path $ContentRoot -Directory -Filter 'ch*' -ErrorAction SilentlyContinue |
            ForEach-Object { Get-ChildItem $_.FullName -Filter '*questions.csv' -File -ErrorAction SilentlyContinue })
        if ($csvFiles.Count -eq 0) {
            Add-Result 'FAIL' '题数一致' "未在 $ContentRoot 找到 *questions.csv" '用 -ContentRoot 指定 CSV 内容目录'
        } else {
            $csvQuestionCount = 0
            foreach ($file in $csvFiles) {
                $csvQuestionCount += @(Import-Csv $file.FullName).Count
            }
            if ($questions -eq $csvQuestionCount) {
                Add-Result 'OK' '题数一致' "库内 = CSV = $questions"
            } else {
                Add-Result 'FAIL' '题数一致' "库内 $questions ≠ CSV $csvQuestionCount" '重跑 build-seed-all.ps1 后用单一路径 seed-demo.ps1 重建'
            }
        }
        if ($returnWithComment -ge 1) {
            Add-Result 'OK' 'RETURN 评阅' "$returnWithComment 条带评语 RETURN"
        } else {
            Add-Result 'FAIL' 'RETURN 评阅' '0 条带评语 RETURN（期望 ≥1）' '检查 seed-demo-all.sql 的 review_result/teacher_comment，重跑 seed-demo.ps1'
        }
        if ($users -ge 13) {
            Add-Result 'OK' '演示账号' "$users 个账号"
        } else {
            Add-Result 'FAIL' '演示账号' "$users 个账号（少于 13）" '重跑 seed-demo-users.sql 或 seed-demo.ps1'
        }
    }
    catch {
        Add-Result 'FAIL' '主库计数' (Format-ApiError $_) '检查 zhixue 库是否完整导入'
    }
}

# ---------- 2. 后端健康 ----------
$token = $null
$health = $null
try {
    $health = Invoke-RestMethod -Uri "$BackendBase/api/system/health" -TimeoutSec 5
    $d = $health.data
    $detail = "db=$($d.db) redis=$($d.redis) ai=$($d.ai)"
    if ($d.db -eq 'UP' -and $d.redis -eq 'UP' -and $d.ai -eq 'UP') {
        Add-Result 'OK' '后端健康' $detail
    } else {
        $fixParts = @()
        if ($d.db -ne 'UP') { $fixParts += '检查 MySQL' }
        if ($d.redis -ne 'UP') { $fixParts += '启动 Redis 服务' }
        if ($d.ai -ne 'UP') { $fixParts += '启动 zhixue-ai：cd zhixue-ai; D:\Python3.12.10\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000' }
        Add-Result 'FAIL' '后端健康' $detail ($fixParts -join '；')
    }
}
catch {
    Add-Result 'FAIL' '后端健康' (Format-ApiError $_) '启动后端：cd zhixue-server; $env:DB_PASSWORD=...; mvn -B -ntp -pl zhixue-bootstrap spring-boot:run'
}

# ---------- 3. 演示账号登录 ----------
if ($health) {
    try {
        $login = Invoke-RestMethod -Uri "$BackendBase/api/auth/login" -Method Post -ContentType 'application/json' `
            -Body (@{ username = $DemoUsername; password = $DemoPassword } | ConvertTo-Json -Compress) -TimeoutSec 10
        if ($login.code -eq 0) {
            $token = $login.data.token
            Add-Result 'OK' '演示登录' "$DemoUsername 登录成功（userId=$($login.data.userId)）"
        } else {
            Add-Result 'FAIL' '演示登录' "code=$($login.code) $($login.message)" '检查演示账号与口令（docs/13）'
        }
    }
    catch {
        Add-Result 'FAIL' '演示登录' (Format-ApiError $_) '后端未就绪或账号缺失；先修后端与 seed-demo-users'
    }
} else {
    Add-Result 'SKIP' '演示登录' '后端未就绪，跳过' '先修“后端健康”'
}

# ---------- 4. 到期队列 + 今日计划 ----------
if ($token) {
    $headers = @{ satoken = $token }
    try {
        $due = Invoke-RestMethod -Uri "$BackendBase/api/plans/due-reviews?courseId=1" -Headers $headers -TimeoutSec 10
        $dueCount = @($due.data).Count
        if ($dueCount -ge 2) {
            Add-Result 'OK' '到期队列' "$DemoUsername 有 $dueCount 条"
        } else {
            Add-Result 'FAIL' '到期队列' "$dueCount 条（期望 ≥2）" '跑 scripts/refresh-demo-dates.ps1；仍为空则检查到期种子'
        }
    }
    catch {
        Add-Result 'FAIL' '到期队列' (Format-ApiError $_) '检查 /api/plans/due-reviews 与到期种子'
    }
    try {
        $plans = Invoke-RestMethod -Uri "$BackendBase/api/plans" -Headers $headers -TimeoutSec 10
        $today = (Get-Date).ToString('yyyy-MM-dd')
        $todayPlan = @($plans.data) | Where-Object { $_.planDate -eq $today } | Select-Object -First 1
        if ($todayPlan -and @($todayPlan.items).Count -ge 3) {
            Add-Result 'OK' '今日计划' "$DemoUsername 今日 $($todayPlan.items.Count) 题（$($todayPlan.doneCount)/$($todayPlan.planCount)）"
        } else {
            Add-Result 'FAIL' '今日计划' '今天没有 3 题的计划' '跑 scripts/refresh-demo-dates.ps1；仍为空则重跑 seed-demo.ps1'
        }
    }
    catch {
        Add-Result 'FAIL' '今日计划' (Format-ApiError $_) '检查 /api/plans 与计划种子'
    }
} else {
    Add-Result 'SKIP' '到期队列' '未登录，跳过' '先修“演示登录”'
    Add-Result 'SKIP' '今日计划' '未登录，跳过' '先修“演示登录”'
}

# ---------- 4b. 教师评阅接口（防"旧 jar 伪装成 50000"） ----------
# 走查环境实证：运行时 jar 未做完整反应堆构建时，ReviewItemView$SourceRef 报
# NoClassDefFoundError，但经全局异常处理器后以业务错误码 50000 出现，极难排查。
# 本项区分三种失败：后端没起（catch）/ 类加载失败（message 含类加载异常）/ 其他业务错误。
if ($health) {
    try {
        $tLogin = Invoke-RestMethod -Uri "$BackendBase/api/auth/login" -Method Post -ContentType 'application/json' `
            -Body (@{ username = $DemoTeacherUsername; password = $DemoPassword } | ConvertTo-Json -Compress) -TimeoutSec 10
        if ($tLogin.code -ne 0) { throw "教师登录 code=$($tLogin.code) $($tLogin.message)" }
        $tHeaders = @{ satoken = $tLogin.data.token }
        $reviews = Invoke-RestMethod -Uri "$BackendBase/api/teacher/reviews?page=1&size=20&status=ALL" -Headers $tHeaders -TimeoutSec 10
        $msg = "$($reviews.code) $($reviews.message)"
        if ($reviews.code -eq 0 -and $reviews.data.total -gt 0) {
            Add-Result 'OK' '教师评阅' "code=0，total=$($reviews.data.total)"
        } elseif ($msg -match 'NoClassDefFound|ClassNotFound|LinkageError') {
            Add-Result 'FAIL' '教师评阅' "类加载失败：$msg" '后端运行时 jar 是旧的：先停服务，执行 mvn -B -ntp install -DskipTests（完整反应堆构建）再启动；禁止 -pl 单模块起服务'
        } elseif ($reviews.code -eq 0) {
            Add-Result 'FAIL' '教师评阅' 'code=0 但 total=0（演示库无评阅数据）' '按 docs/13 恢复演示库（重导 seed-demo-all.sql 或跑 seed-demo.ps1）'
        } else {
            Add-Result 'FAIL' '教师评阅' $msg '很可能是未做完整反应堆构建（旧 jar 缺类伪装成 50000）：mvn -B -ntp install -DskipTests 后重启；后端日志搜 NoClassDefFoundError 可确认'
        }
    }
    catch {
        Add-Result 'FAIL' '教师评阅' (Format-ApiError $_) '先确认后端已起（后端健康项）；若已起仍失败，后端日志搜 NoClassDefFoundError → mvn -B -ntp install -DskipTests 完整构建后重启，勿用 -pl 单模块启动'
    }
} else {
    Add-Result 'SKIP' '教师评阅' '后端未就绪，跳过' '先修“后端健康”'
}

# ---------- 5. 前端 5173 ----------
try {
    $resp = Invoke-WebRequest -Uri $FrontendUrl -UseBasicParsing -TimeoutSec 5
    if ($resp.StatusCode -eq 200) {
        Add-Result 'OK' '前端 5173' "HTTP 200（$FrontendUrl）"
    } else {
        Add-Result 'FAIL' '前端 5173' "HTTP $($resp.StatusCode)" 'cd zhixue-web; npm run dev'
    }
}
catch {
    Add-Result 'FAIL' '前端 5173' (Format-ApiError $_) 'cd zhixue-web; npm run dev'
}

# ---------- 6. RAG 索引 ----------
try {
    $chunkFile = Join-Path $IndexDir 'chunks.json'
    $metaFile = Join-Path $IndexDir 'meta.json'
    if (-not (Test-Path $chunkFile)) { throw "找不到 $chunkFile" }
    $chunks = Get-Content $chunkFile -Raw -Encoding utf8 | ConvertFrom-Json
    $chunkCount = @($chunks).Count
    $mode = 'unknown'
    if (Test-Path $metaFile) {
        $meta = Get-Content $metaFile -Raw -Encoding utf8 | ConvertFrom-Json
        if ($meta.mode) { $mode = $meta.mode }
    }
    $kpCount = @($chunks | ForEach-Object { $_.kp_id } | Select-Object -Unique).Count
    if ($chunkCount -gt 0) {
        Add-Result 'OK' 'RAG 索引' "$chunkCount 切片 / $kpCount 知识点（$mode）"
    } else {
        Add-Result 'FAIL' 'RAG 索引' '切片数为 0' 'cd zhixue-ai; python tools/build_demo_index.py --base-url http://127.0.0.1:8000'
    }
}
catch {
    Add-Result 'FAIL' 'RAG 索引' (Format-ApiError $_) 'cd zhixue-ai; python tools/build_demo_index.py --base-url http://127.0.0.1:8000'
}

# ---------- 7. AI 真实链路（L1/L2/L3） ----------
if ($SkipAiLive) {
    Add-Result 'SKIP' 'AI 真实链路' '已用 -SkipAiLive 跳过' '需要确认时去掉该参数'
} else {
    try {
        $aiBody = @{
            kpId         = 'KP0201'
            kpName       = '关系模型的基本概念与三要素'
            conceptText  = '关系模型由关系数据结构、关系操作集合、关系完整性约束三部分组成。'
            misconception = '把关系等同于二维表'
            errorType    = 'CONCEPT_CONFUSION'
            questionText = '关于关系模型，下列叙述不正确的是（ ）。'
            textbookPage = 'P27'
        } | ConvertTo-Json -Compress
        $sse = Invoke-WebRequest -Uri "$AiBase/api/rag/explain" -Method Post -ContentType 'application/json' `
            -Body $aiBody -UseBasicParsing -TimeoutSec 60
        $dataLines = @($sse.Content -split "`n" | Where-Object { $_ -like 'data:*' })
        $done = $null
        foreach ($line in $dataLines) {
            $obj = $line.Substring(5).Trim() | ConvertFrom-Json
            if ($obj.PSObject.Properties.Name -contains 'modelUsed') { $done = $obj }
        }
        if (-not $done) { throw 'AI 未返回 done 事件' }
        if ($done.modelUsed -eq 'deepseek-chat' -and -not $done.degraded) {
            Add-Result 'OK' 'AI 真实链路' "L1 deepseek-chat（degraded=false, tokens=$($done.tokens)）"
        } elseif ($done.modelUsed -eq 'glm-4-flash') {
            Add-Result 'WARN' 'AI 真实链路' "L2 glm-4-flash（L1 失败，已降级）" '检查 DEEPSEEK_API_KEY 与网络'
        } elseif ($done.modelUsed -eq 'local-template') {
            Add-Result 'FAIL' 'AI 真实链路' 'L3 local-template（L1/L2 均不可用）' '检查 .env 的 DEEPSEEK_API_KEY、GLM_API_KEY 与网络'
        } else {
            Add-Result 'WARN' 'AI 真实链路' "未知档位：$($done.modelUsed)" '查看 zhixue-ai 日志'
        }
    }
    catch {
        Add-Result 'FAIL' 'AI 真实链路' (Format-ApiError $_) '启动 zhixue-ai：cd zhixue-ai; D:\Python3.12.10\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000'
    }
}

# ---------- 汇总 ----------
Write-Host ''
foreach ($r in $results) {
    switch ($r.Status) {
        'OK'   { $icon = '✅'; $color = 'Green' }
        'WARN' { $icon = '⚠️'; $color = 'Yellow' }
        'FAIL' { $icon = '❌'; $color = 'Red' }
        'SKIP' { $icon = '⚪'; $color = 'DarkGray' }
    }
    Write-Host ("{0} {1,-12} {2}" -f $icon, $r.Name, $r.Detail) -ForegroundColor $color
    if ($r.Fix -and $r.Status -ne 'OK') {
        Write-Host ("   修复：{0}" -f $r.Fix) -ForegroundColor DarkGray
    }
}

$failed = @($results | Where-Object { $_.Status -eq 'FAIL' })
$warned = @($results | Where-Object { $_.Status -eq 'WARN' })
Write-Host ''
if ($failed.Count -gt 0) {
    Write-Host ("❌ 未就绪 {0} 项：{1}" -f $failed.Count, (($failed | ForEach-Object { $_.Name }) -join '、')) -ForegroundColor Red
    exit 1
}
if ($warned.Count -gt 0) {
    Write-Host ("⚠️ 就绪但有降级 {0} 项：{1}" -f $warned.Count, (($warned | ForEach-Object { $_.Name }) -join '、')) -ForegroundColor Yellow
    exit 0
}
Write-Host '✅ 全部就绪，可以开始演示。' -ForegroundColor Green
exit 0
