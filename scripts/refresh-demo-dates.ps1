# 智学领航 · 演示库日期平移（答辩当天先跑这一条）
# 作用：把演示计划、练习、错题、讲解留痕、画像快照整体平移到“今天/昨天”，保持相对关系不变。
# 锚点：演示计划的最大日期（种子导入日）；delta = 今天 - 锚点；重复跑 delta=0，不改任何数据（幂等）。
# 只动演示标记数据：demo 学生（2,9001—9011）、demo-prac-* 练习、EL_DEMO_* 错题、DEMO_EX_* 讲解。
# 用法：
#   $env:DB_PASSWORD = '<本机 root 口令>'
#   pwsh -ExecutionPolicy Bypass -File .\scripts\refresh-demo-dates.ps1
#   pwsh -ExecutionPolicy Bypass -File .\scripts\refresh-demo-dates.ps1 -Database zhixue_demo -Password ***

[CmdletBinding()]
param(
    [string]$MySqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$Password = $env:DB_PASSWORD,
    [string]$Database = 'zhixue'
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.Encoding]::UTF8
if (-not $Password) {
    throw '未提供 -Password 或 DB_PASSWORD'
}
if (-not (Test-Path $MySqlExe)) {
    throw "找不到 mysql 客户端：$MySqlExe"
}

$previousPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $Password
$baseArgs = @('-h', $DbHost, '-P', "$Port", '-u', $User, '--default-character-set=utf8mb4')
$demoUsers = '2,9001,9002,9003,9004,9005,9006,9007,9008,9009,9010,9011'

function Invoke-Sql([string]$sql) {
    $result = $sql | & $MySqlExe @baseArgs -N -B $Database
    if ($LASTEXITCODE -ne 0) {
        throw "SQL 执行失败：$sql"
    }
    return $result
}

try {
    $anchorRow = Invoke-Sql @"
SELECT DATEDIFF(CURDATE(), MAX(plan_date)), MAX(plan_date), CURDATE()
FROM study_plan WHERE user_id IN ($demoUsers);
"@
    $cols = @($anchorRow -split "`t")
    if ($cols.Count -lt 3 -or -not $cols[1]) {
        throw "库里没有演示计划（user_id IN $demoUsers），请先跑 seed-demo.ps1"
    }
    $delta = [int]$cols[0]
    $anchor = $cols[1]
    $today = $cols[2]

    if ($delta -ne 0) {
        Write-Host "检测到日期漂移：计划锚点 $anchor，今天 $today，平移 $delta 天…"
        $updateSql = @"
START TRANSACTION;
UPDATE study_plan SET plan_date = DATE_ADD(plan_date, INTERVAL $delta DAY)
  WHERE user_id IN ($demoUsers);
UPDATE practice_record SET created_at = DATE_ADD(created_at, INTERVAL $delta DAY)
  WHERE client_token LIKE 'demo-prac-%';
UPDATE student_error_record
  SET created_at = DATE_ADD(created_at, INTERVAL $delta DAY),
      confirmed_at = DATE_ADD(confirmed_at, INTERVAL $delta DAY)
  WHERE log_id LIKE 'EL_DEMO_%';
UPDATE ai_generation_log
  SET created_at = DATE_ADD(created_at, INTERVAL $delta DAY),
      reviewed_at = DATE_ADD(reviewed_at, INTERVAL $delta DAY)
  WHERE explain_id LIKE 'DEMO_EX_%';
UPDATE learning_portrait_snapshot SET stat_date = DATE_ADD(stat_date, INTERVAL $delta DAY)
  WHERE user_id IN ($demoUsers);
COMMIT;
"@
        $updateSql | & $MySqlExe @baseArgs $Database
        if ($LASTEXITCODE -ne 0) {
            throw '日期平移失败（事务已回滚），请检查数据库'
        }
    } else {
        Write-Host "日期已对齐：计划锚点 $anchor = 今天，无需平移。"
    }

    $todayPlan = @((Invoke-Sql @"
SELECT COUNT(*), COALESCE(SUM(plan_count),0), COALESCE(SUM(done_count),0)
FROM study_plan WHERE plan_date = CURDATE() AND user_id IN ($demoUsers);
"@) -split "`t")
    $stuToday = @((Invoke-Sql @"
SELECT COUNT(*), COALESCE(SUM(plan_count),0), COALESCE(SUM(done_count),0)
FROM study_plan WHERE plan_date = CURDATE() AND user_id = 2;
"@) -split "`t")
    $stuItems = [int](Invoke-Sql @"
SELECT COUNT(*) FROM study_plan_item i JOIN study_plan p ON p.plan_id = i.plan_id
WHERE p.plan_date = CURDATE() AND p.user_id = 2;
"@)
    $week = @((Invoke-Sql @"
SELECT COALESCE(SUM(done_count),0), COALESCE(SUM(plan_count),0)
FROM study_plan WHERE user_id = 2
  AND plan_date BETWEEN DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY)
                     AND DATE_ADD(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 6 DAY);
"@) -split "`t")
    $snap = @((Invoke-Sql @"
SELECT COUNT(*), MIN(stat_date), MAX(stat_date), DATEDIFF(MAX(stat_date), MIN(stat_date))
FROM learning_portrait_snapshot WHERE user_id = 2;
"@) -split "`t")

    Write-Host ''
    Write-Host ("① 今日计划：{0} 名学生 / {1} 题（已完成 {2}）；student01 {3} 题 {4}/{5}，明细 {6} 条" -f `
        $todayPlan[0], $todayPlan[1], $todayPlan[2], $stuToday[1], $stuToday[2], $stuToday[1], $stuItems)
    Write-Host ("② 本周：student01 {0}/{1}" -f $week[0], $week[1])
    Write-Host ("③ 快照：student01 {0} 条，{1} → {2}，跨度 {3} 天" -f `
        $snap[0], $snap[1], $snap[2], $snap[3])
    Write-Host ''
    Write-Host "完成。演示库日期已对齐今天（$today）。"
}
finally {
    $env:MYSQL_PWD = $previousPwd
}
