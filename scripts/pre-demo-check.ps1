# 演示前环境自检：一次跑完「服务可达 + 账号 + 主链路读接口 + 内容/索引/数据库」。
#
# 用法（在 zhixue 目录下，或任意目录用绝对路径调用）：
#   $env:E2E_USER='student01'; $env:E2E_PASS='<演示口令>'
#   $env:E2E_TEACHER_USER='teacher01'; $env:E2E_TEACHER_PASS='<演示口令>'
#   ..\scripts\pre-demo-check.ps1                 # 只读预检，不写业务数据
#   ..\scripts\pre-demo-check.ps1 -FullSmoke      # 追加 35 项真实 API 冒烟（会写 1 条错题/1 周计划/1 次打卡）
#   ..\scripts\pre-demo-check.ps1 -UiSmoke        # 追加真实 UI 冒烟（需 Edge/Chromium 与前端 5173）
#
# 退出码：0 全部通过；1 存在 FAIL；2 缺少账号口令或前置工具。
# 报告：项目根 spark-output/qa/<日期>-predemo/predemo-report.json（可用 -OutDir 覆盖）。
#
# 说明：本脚本默认只读；-FullSmoke / -UiSmoke 才会写演示库，且在报告里单独标记。

[CmdletBinding()]
param(
    [string]$ApiBase = 'http://127.0.0.1:8080',
    [string]$AiBase = 'http://127.0.0.1:8000',
    [switch]$FullSmoke,
    [switch]$UiSmoke,
    [string]$OutDir = '',
    [string]$DbPassword = $env:DB_PASSWORD
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Split-Path -Parent $repoRoot
if (-not $OutDir) {
    $OutDir = Join-Path $projectRoot ("spark-output\qa\" + (Get-Date -Format 'yyyy-MM-dd') + '-predemo')
}
New-Item -ItemType Directory -Force $OutDir | Out-Null

$checks = [System.Collections.Generic.List[object]]::new()
function Add-Check([string]$Name, [string]$Status, [string]$Detail = '') {
    $checks.Add([pscustomobject]@{ name = $Name; status = $Status; detail = $Detail })
    $color = switch ($Status) { 'PASS' { 'Green' } 'WARN' { 'Yellow' } 'SKIP' { 'DarkGray' } default { 'Red' } }
    Write-Host ("{0,-4} {1}" -f $Status, $Name) -ForegroundColor $color
    if ($Detail) { Write-Host ("     " + $Detail) -ForegroundColor DarkGray }
}

$studentUser = $env:E2E_USER
$studentPass = $env:E2E_PASS
$teacherUser = if ($env:E2E_TEACHER_USER) { $env:E2E_TEACHER_USER } else { $studentUser }
$teacherPass = if ($env:E2E_TEACHER_PASS) { $env:E2E_TEACHER_PASS } else { $studentPass }
if (-not $studentUser -or -not $studentPass) {
    Write-Host '缺少 E2E_USER / E2E_PASS：请先导出演示账号口令后再运行。' -ForegroundColor Red
    exit 2
}

function Invoke-Api {
    param(
        [string]$Method, [string]$Path, [string]$Token, $Body, [int]$TimeoutSec = 15
    )
    $params = @{
        Uri                = $ApiBase + $Path
        Method             = $Method
        TimeoutSec         = $TimeoutSec
        SkipHttpErrorCheck = $true
        Headers            = @{ Accept = 'application/json' }
    }
    if ($Token) { $params.Headers.satoken = $Token }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = ($Body | ConvertTo-Json -Compress -Depth 6)
    }
    $res = Invoke-WebRequest @params
    $json = $null
    try { $json = $res.Content | ConvertFrom-Json } catch { }
    [pscustomobject]@{ status = [int]$res.StatusCode; json = $json }
}

# ---------- 1. Java 服务与组件健康 ----------
try {
    $health = Invoke-Api -Method GET -Path '/api/system/health'
    $h = $health.json.data
    if ($h.db -eq 'UP') { Add-Check 'Java 健康检查 db=UP' 'PASS' ("status=" + $h.status) }
    else { Add-Check 'Java 健康检查 db=UP' 'FAIL' ("status=" + $h.status + " db=" + $h.db) }
    if ($h.redis -eq 'UP') { Add-Check 'Java 健康检查 redis=UP' 'PASS' }
    else { Add-Check 'Java 健康检查 redis=UP' 'WARN' 'Redis 不可用：讲解缓存与限流降级，演示可继续但性能受影响' }
    if ($h.ai -eq 'UP') { Add-Check 'Java 健康检查 ai=UP' 'PASS' }
    else { Add-Check 'Java 健康检查 ai=UP' 'WARN' 'AI 不可用：讲解将走 L3 模板降级，演示前必须确认讲稿是否允许' }
}
catch {
    Add-Check 'Java 服务可达（8080）' 'FAIL' ($ApiBase + ' 无法访问：' + $_.Exception.Message)
}

# ---------- 2. AI 服务直连 ----------
try {
    $aiHealth = Invoke-WebRequest -Uri ($AiBase + '/health') -TimeoutSec 5 -SkipHttpErrorCheck
    $aiJson = $aiHealth.Content | ConvertFrom-Json
    if ($aiJson.status -eq 'UP') { Add-Check 'AI 服务可达（8000）' 'PASS' ("service=" + $aiJson.service) }
    else { Add-Check 'AI 服务可达（8000）' 'FAIL' ($aiHealth.Content) }
}
catch {
    Add-Check 'AI 服务可达（8000）' 'FAIL' ($AiBase + ' 无法访问：' + $_.Exception.Message)
}

# ---------- 3. 鉴权与账号 ----------
try {
    $anon = Invoke-Api -Method GET -Path '/api/auth/me'
    if ($anon.json.code -eq 40100) { Add-Check '未登录访问 /auth/me 返回 40100' 'PASS' }
    else { Add-Check '未登录访问 /auth/me 返回 40100' 'FAIL' ("http=" + $anon.status + " code=" + $anon.json.code) }
}
catch { Add-Check '未登录访问 /auth/me 返回 40100' 'FAIL' $_.Exception.Message }

$studentToken = $null
try {
    $login = Invoke-Api -Method POST -Path '/api/auth/login' -Body @{ username = $studentUser; password = $studentPass }
    $studentToken = $login.json.data.token
    if ($login.json.code -eq 0 -and $studentToken) { Add-Check '学生登录' 'PASS' ("user=" + $studentUser) }
    else { Add-Check '学生登录' 'FAIL' ("code=" + $login.json.code + " msg=" + $login.json.message) }
}
catch { Add-Check '学生登录' 'FAIL' $_.Exception.Message }

$teacherToken = $null
if ($teacherUser) {
    try {
        $tLogin = Invoke-Api -Method POST -Path '/api/auth/login' -Body @{ username = $teacherUser; password = $teacherPass }
        $teacherToken = $tLogin.json.data.token
        if ($tLogin.json.code -eq 0 -and $teacherToken) { Add-Check '教师登录' 'PASS' ("user=" + $teacherUser) }
        else { Add-Check '教师登录' 'FAIL' ("code=" + $tLogin.json.code + " msg=" + $tLogin.json.message) }
    }
    catch { Add-Check '教师登录' 'FAIL' $_.Exception.Message }
}

# ---------- 4. 主链路读接口（不写数据） ----------
$courseId = $null
if ($studentToken) {
    try {
        $me = Invoke-Api -Method GET -Path '/api/auth/me' -Token $studentToken
        if ($me.json.data.role -eq 'STUDENT') { Add-Check '学生角色 STUDENT' 'PASS' ("realName=" + $me.json.data.realName) }
        else { Add-Check '学生角色 STUDENT' 'FAIL' ("role=" + $me.json.data.role) }

        $courses = Invoke-Api -Method GET -Path '/api/courses' -Token $studentToken
        if ($courses.json.code -eq 0 -and @($courses.json.data).Count -gt 0) {
            $courseId = @($courses.json.data)[0].courseId
            Add-Check '课程列表非空' 'PASS' ("courseId=" + $courseId)
        }
        else { Add-Check '课程列表非空' 'FAIL' ("code=" + $courses.json.code) }
    }
    catch { Add-Check '学生基础查询' 'FAIL' $_.Exception.Message }

    if ($courseId) {
        try {
            $graph = Invoke-Api -Method GET -Path ("/api/courses/" + $courseId + '/knowledge-graph') -Token $studentToken
            $nodes = @($graph.json.data.nodes)
            if ($nodes.Count -ge 20) { Add-Check '知识图谱节点 >=20' 'PASS' ("nodes=" + $nodes.Count + " edges=" + @($graph.json.data.edges).Count) }
            else { Add-Check '知识图谱节点 >=20' 'FAIL' ("nodes=" + $nodes.Count) }
            $kpId = $null
            if ($nodes.Count -gt 0) { $kpId = $nodes[0].id }

            if ($kpId) {
                $kp = Invoke-Api -Method GET -Path ('/api/course/kp/' + $kpId) -Token $studentToken
                if ($kp.json.code -eq 0) { Add-Check '知识点详情可读' 'PASS' ($kpId) }
                else { Add-Check '知识点详情可读' 'FAIL' ($kpId + " code=" + $kp.json.code) }

                $qs = Invoke-Api -Method GET -Path ('/api/questions?kpId=' + $kpId + '&limit=3') -Token $studentToken
                if ($qs.json.code -eq 0) {
                    $qCount = @($qs.json.data).Count
                    if ($qCount -gt 0) { Add-Check '知识点题目可读' 'PASS' ($kpId + " count=" + $qCount) }
                    else { Add-Check '知识点题目可读' 'WARN' ($kpId + ' 无题：演示该知识点时推荐区会空，需避开或先补题') }
                }
                else { Add-Check '知识点题目可读' 'FAIL' ($kpId + " code=" + $qs.json.code) }
            }
        }
        catch { Add-Check '图谱/知识点查询' 'FAIL' $_.Exception.Message }

        try {
            # 契约要求 logId 与 kpId 至少一个，只传 courseId 会返回 40000
            $recPath = if ($kpId) { '/api/recommendations?kpId=' + $kpId } else { '/api/recommendations?courseId=' + $courseId }
            $recs = Invoke-Api -Method GET -Path $recPath -Token $studentToken
            if ($recs.json.code -eq 0) {
                $rCount = @($recs.json.data).Count
                if ($rCount -gt 0) { Add-Check '推荐接口可用' 'PASS' ("count=" + $rCount) }
                else { Add-Check '推荐接口可用' 'WARN' '返回空推荐：演示前确认目标知识点有题' }
            }
            else { Add-Check '推荐接口可用' 'FAIL' ("code=" + $recs.json.code) }
        }
        catch { Add-Check '推荐接口可用' 'FAIL' $_.Exception.Message }

        try {
            $p = Invoke-Api -Method GET -Path '/api/portraits/me' -Token $studentToken
            if ($p.json.code -eq 0) { Add-Check '学生画像可读' 'PASS' ("totalPractice=" + $p.json.data.totalPractice) }
            else { Add-Check '学生画像可读' 'FAIL' ("code=" + $p.json.code) }
        }
        catch { Add-Check '学生画像可读' 'FAIL' $_.Exception.Message }
    }

    if ($teacherToken -and $courseId) {
        try {
            $cp = Invoke-Api -Method GET -Path ('/api/portraits/class/' + $courseId) -Token $teacherToken
            if ($cp.json.code -eq 0) { Add-Check '班级画像可读（教师）' 'PASS' ("students=" + $cp.json.data.studentCount) }
            else { Add-Check '班级画像可读（教师）' 'FAIL' ("code=" + $cp.json.code) }

            $rv = Invoke-Api -Method GET -Path '/api/teacher/reviews?status=PENDING&page=1&size=10' -Token $teacherToken
            if ($rv.json.code -eq 0) { Add-Check '教师评阅队列可读' 'PASS' ("total=" + $rv.json.data.total) }
            else { Add-Check '教师评阅队列可读' 'FAIL' ("code=" + $rv.json.code) }

            $st = Invoke-Api -Method GET -Path ('/api/teacher/students?courseId=' + $courseId + '&page=1&size=20') -Token $teacherToken
            if ($st.json.code -eq 0) { Add-Check '教师学生列表可读' 'PASS' ("total=" + $st.json.data.total) }
            else { Add-Check '教师学生列表可读' 'FAIL' ("code=" + $st.json.code) }
        }
        catch { Add-Check '教师端查询' 'FAIL' $_.Exception.Message }
    }
}

# ---------- 5. 数据库计数（只读） ----------
$dbCounts = $null
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
if ((Test-Path $mysql) -and $DbPassword) {
    try {
        $sql = 'SELECT (SELECT COUNT(*) FROM knowledge_point) AS kp, (SELECT COUNT(*) FROM question_bank) AS qb, (SELECT COUNT(*) FROM course_chapter) AS ch, (SELECT COUNT(*) FROM question_kp) AS kpEdge;'
        # 注意：不能写 -p$DbPassword，PowerShell 会把 "-p" 与变量拆成两个参数，导致空口令被拒
        $raw = & $mysql -h 127.0.0.1 -u root ('-p' + $DbPassword) --default-character-set=utf8mb4 -N -B -e "use zhixue; $sql" 2>$null
        $vals = @()
        if ($raw) { $vals = @($raw -split "`t") }
        if ($vals.Count -lt 4) { throw ('mysql 未返回预期行列：' + ($raw -join ' ')) }
        $dbCounts = [pscustomobject]@{
            knowledgePoint = [int]$vals[0]; questionBank = [int]$vals[1]
            courseChapter  = [int]$vals[2]; questionKpEdge = [int]$vals[3]
        }
        Add-Check '数据库内容计数' 'PASS' ("kp=" + $dbCounts.knowledgePoint + " qb=" + $dbCounts.questionBank + " ch=" + $dbCounts.courseChapter + " 题-点边=" + $dbCounts.questionKpEdge)
    }
    catch { Add-Check '数据库内容计数' 'WARN' ('查询失败：' + $_.Exception.Message) }
}
else {
    Add-Check '数据库内容计数' 'SKIP' '未找到 mysql.exe 或未设置 DB_PASSWORD'
}

# ---------- 6. RAG 索引 ----------
$indexDir = Join-Path $repoRoot 'zhixue-ai\data\vector_index'
$need = @('vectors.npy', 'chunks.json', 'meta.json')
$missing = @($need | Where-Object { -not (Test-Path (Join-Path $indexDir $_)) })
if ($missing.Count -eq 0) {
    $size = (Get-ChildItem $indexDir | Measure-Object -Property Length -Sum).Sum
    if ($size -lt 10KB) { Add-Check 'RAG 索引存在' 'WARN' ("索引仅 " + $size + " 字节：检索命中率可能不足，演示前确认 /api/rag/retrieve") }
    else { Add-Check 'RAG 索引存在' 'PASS' ("目录 " + $size + " 字节") }
}
else { Add-Check 'RAG 索引存在' 'FAIL' ('缺少：' + ($missing -join ', ')) }

# ---------- 7. 可选：真实 API 冒烟 ----------
$smokeSummary = $null
if ($FullSmoke) {
    Write-Host "`n运行真实 API 冒烟（会写演示库）……" -ForegroundColor Cyan
    # 强制本次报告落到本次目录，避免复用上一次的旧报告造成假绿
    $reportPath = Join-Path $OutDir 'api-smoke-report.json'
    if (Test-Path $reportPath) { Remove-Item $reportPath -Force }
    $prevOut = $env:E2E_REAL_OUT
    $env:E2E_REAL_OUT = $reportPath
    Push-Location (Join-Path $repoRoot 'zhixue-web')
    try {
        & npm run e2e:real 2>&1 | ForEach-Object { Write-Host $_ }
        $smokeExit = $LASTEXITCODE
    }
    finally {
        Pop-Location
        $env:E2E_REAL_OUT = $prevOut
    }
    if (Test-Path $reportPath) {
        $smoke = Get-Content $reportPath -Raw | ConvertFrom-Json
        $smokeSummary = [pscustomobject]@{
            total = $smoke.results.Count
            failed = @($smoke.results | Where-Object { -not $_.pass }).Count
            report = $reportPath
        }
        if ($smokeExit -eq 0 -and $smokeSummary.failed -eq 0) { Add-Check '真实 API 冒烟' 'PASS' ($smokeSummary.total.ToString() + '/' + $smokeSummary.total.ToString()) }
        else { Add-Check '真实 API 冒烟' 'FAIL' ("exit=" + $smokeExit + " failed=" + $smokeSummary.failed) }
    }
    else { Add-Check '真实 API 冒烟' 'FAIL' ('未生成报告：' + $reportPath) }
}

# ---------- 8. 可选：真实 UI 冒烟 ----------
if ($UiSmoke) {
    Write-Host "`n运行真实 UI 冒烟（需前端 5173）……" -ForegroundColor Cyan
    Push-Location (Join-Path $repoRoot 'zhixue-web')
    try {
        & npm run e2e:real:ui 2>&1 | ForEach-Object { Write-Host $_ }
        $uiExit = $LASTEXITCODE
    }
    finally { Pop-Location }
    if ($uiExit -eq 0) { Add-Check '真实 UI 冒烟' 'PASS' } else { Add-Check '真实 UI 冒烟' 'FAIL' ("exit=" + $uiExit) }
}

# ---------- 汇总 ----------
$failed = @($checks | Where-Object { $_.status -eq 'FAIL' })
$warned = @($checks | Where-Object { $_.status -eq 'WARN' })
$report = [pscustomobject]@{
    generatedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    apiBase     = $ApiBase
    aiBase      = $AiBase
    writeMode   = ($FullSmoke -or $UiSmoke)
    dbCounts    = $dbCounts
    smoke       = $smokeSummary
    summary     = [pscustomobject]@{ total = $checks.Count; passed = @($checks | Where-Object { $_.status -eq 'PASS' }).Count; failed = $failed.Count; warned = $warned.Count }
    checks      = $checks
}
$reportPath = Join-Path $OutDir 'predemo-report.json'
$report | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 $reportPath

Write-Host ("`n演示自检：" + $checks.Count + " 项，PASS " + @($checks | Where-Object { $_.status -eq 'PASS' }).Count + "，WARN " + $warned.Count + "，FAIL " + $failed.Count) -ForegroundColor $(if ($failed.Count) { 'Red' } else { 'Green' })
Write-Host ('报告：' + $reportPath)
if ($failed.Count) {
    Write-Host '存在 FAIL 项，演示前必须修复或调整演示路径。' -ForegroundColor Red
    exit 1
}
exit 0
