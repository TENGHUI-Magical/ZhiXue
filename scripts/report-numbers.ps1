# 智学领航 · 当前数字单一事实来源生成器
# 连当前 MySQL 库实查，生成 docs/numbers-current.md；不要手写该文件。
# 用法：
#   $env:DB_PASSWORD = '<本机 root 口令>'
#   pwsh -ExecutionPolicy Bypass -File .\scripts\report-numbers.ps1
# 说明：内容层（seed-demo-all.sql）与当前库可能不一致；脚本会同时给出两侧数字，
#       不一致时明确提示“以重建后的库为准”。

[CmdletBinding()]
param(
    [string]$MySqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$Password = $env:DB_PASSWORD,
    [string]$Database = 'zhixue',
    [string]$OutFile = ''
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.Encoding]::UTF8
if (-not $OutFile) {
    $OutFile = Join-Path $PSScriptRoot '..\docs\numbers-current.md'
}
if (-not (Test-Path $MySqlExe)) {
    throw "找不到 mysql 客户端：$MySqlExe"
}
if (-not $Password) {
    throw '未提供 -Password 或 DB_PASSWORD'
}

$previousPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $Password
$baseArgs = @('-h', $DbHost, '-P', "$Port", '-u', $User, '--default-character-set=utf8mb4')
$seedAll = Join-Path $PSScriptRoot '..\zhixue-server\sql\seed-demo-all.sql'

function Invoke-Sql([string]$sql) {
    $result = $sql | & $MySqlExe @baseArgs -N -B $Database
    if ($LASTEXITCODE -ne 0) {
        throw "SQL 查询失败：$sql"
    }
    return @($result)
}

try {
    & $MySqlExe @baseArgs -e 'SELECT 1;' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'MySQL 连接失败，请检查连接参数' }

    $summaryRows = Invoke-Sql @'
SELECT 'chapters', COUNT(*) FROM course_chapter
UNION ALL SELECT 'kps', COUNT(*) FROM knowledge_point
UNION ALL SELECT 'questions', COUNT(*) FROM question_bank
UNION ALL SELECT 'edges', COUNT(*) FROM knowledge_prereq
UNION ALL SELECT 'practices', COUNT(*) FROM practice_record
UNION ALL SELECT 'errors', COUNT(*) FROM student_error_record
UNION ALL SELECT 'ai_logs', COUNT(*) FROM ai_generation_log
UNION ALL SELECT 'pending', COUNT(*) FROM ai_generation_log WHERE scene='RAG' AND reviewed_at IS NULL
UNION ALL SELECT 'demo_accounts', COUNT(*) FROM sys_user WHERE username IN ('teacher01','student01') OR username LIKE 'demo_stu%';
'@

    $summary = @{}
    foreach ($line in $summaryRows) {
        $parts = $line -split "`t"
        if ($parts.Count -ge 2) { $summary[$parts[0]] = [int]$parts[1] }
    }

    $chapterRows = Invoke-Sql @'
SELECT c.chapter_no, c.title, COUNT(DISTINCT k.kp_id), COUNT(DISTINCT q.q_id)
FROM course_chapter c
LEFT JOIN knowledge_point k ON k.chapter_id = c.chapter_id
LEFT JOIN question_bank q ON q.primary_kp_id = k.kp_id
GROUP BY c.chapter_no, c.title
ORDER BY c.chapter_no;
'@

    $chapters = foreach ($line in $chapterRows) {
        $parts = $line -split "`t"
        if ($parts.Count -ge 4) {
            [pscustomobject]@{
                Chapter  = [int]$parts[0]
                Title    = $parts[1]
                Kps      = [int]$parts[2]
                Questions = [int]$parts[3]
            }
        }
    }

    $contentKp = $null
    $contentQ = $null
    $contentEdges = $null
    $contentNote = '未找到 seed-demo-all.sql，未做内容层对比。'
    if (Test-Path $seedAll) {
        $content = Get-Content -Raw -Encoding utf8 $seedAll
        $kpSet = [System.Collections.Generic.HashSet[string]]::new()
        foreach ($m in [regex]::Matches($content, "SELECT '(KP\d{4})'")) {
            [void]$kpSet.Add($m.Groups[1].Value)
        }
        $qSet = [System.Collections.Generic.HashSet[string]]::new()
        foreach ($m in [regex]::Matches($content, "VALUES \('(Q_\d{4})'")) {
            [void]$qSet.Add($m.Groups[1].Value)
        }
        $edgeSet = [System.Collections.Generic.HashSet[string]]::new()
        foreach ($m in [regex]::Matches($content, "(?m)^\('KP\d{4}', 'KP\d{4}'\)")) {
            [void]$edgeSet.Add($m.Value)
        }
        $contentKp = $kpSet.Count
        $contentQ = $qSet.Count
        $contentEdges = $edgeSet.Count
        if ($contentKp -ne $summary['kps'] -or $contentQ -ne $summary['questions'] -or $contentEdges -ne $summary['edges']) {
            $contentNote = "⚠️ 内容层与当前库不一致：合并种子 $contentKp 点 / $contentQ 题 / $contentEdges 边；当前库 $($summary['kps']) 点 / $($summary['questions']) 题 / $($summary['edges']) 边。重建未完成前报数必须带此说明；以重建后的库为准。"
        } else {
            $contentNote = "内容层与当前库一致：$contentKp 点 / $contentQ 题 / $contentEdges 边。"
        }
    }

    $totalKp = ($chapters | Measure-Object Kps -Sum).Sum
    $totalQ = ($chapters | Measure-Object Questions -Sum).Sum
    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('# 当前数字（单一事实来源）')
    $lines.Add('')
    $lines.Add('> 本文件由 `scripts/report-numbers.ps1` 生成，不要手改；报数请引用本文。')
    $lines.Add("> 生成时间：$timestamp")
    $lines.Add("> 所连库：$Database @ ${DbHost}:${Port}")
    $lines.Add('')
    $lines.Add('## 汇总')
    $lines.Add('')
    $lines.Add('| 指标 | 数值 |')
    $lines.Add('| --- | --- |')
    $lines.Add("| 章节 | $($summary['chapters']) |")
    $lines.Add("| 知识点 | $($summary['kps']) |")
    $lines.Add("| 题目 | $($summary['questions']) |")
    $lines.Add("| 先修边 | $($summary['edges']) |")
    $lines.Add("| 练习记录 | $($summary['practices']) |")
    $lines.Add("| 错题记录 | $($summary['errors']) |")
    $lines.Add("| 讲解留痕 | $($summary['ai_logs']) |")
    $lines.Add("| 待评阅 | $($summary['pending']) |")
    $lines.Add("| 演示账号 | $($summary['demo_accounts']) |")
    $lines.Add('')
    $lines.Add('## 按章')
    $lines.Add('')
    $lines.Add('| 章 | 标题 | 知识点 | 题 |')
    $lines.Add('| --- | --- | --- | --- |')
    foreach ($row in $chapters) {
        $lines.Add("| $($row.Chapter) | $($row.Title) | $($row.Kps) | $($row.Questions) |")
    }
    $lines.Add("| **合计** | | **$totalKp** | **$totalQ** |")
    $lines.Add('')
    $lines.Add('## 内容层与当前库')
    $lines.Add('')
    $lines.Add("> $contentNote")
    $lines.Add('')
    $lines.Add('> 通用口径：内容层（seed-demo-all.sql）与库不一致时，以重建后的库为准；重建未完成前报数必须带此说明。')

    $tmp = "$OutFile.tmp"
    [System.IO.File]::WriteAllText($tmp, ($lines -join "`n") + "`n", [System.Text.UTF8Encoding]::new($false))
    Move-Item -Force $tmp $OutFile

    Write-Host "已生成 $OutFile"
    Write-Host ("当前库：{0} 章 / {1} 点 / {2} 题 / {3} 边 / {4} 账号 / {5} 练习 / {6} 错题 / {7} 留痕 / {8} 待评阅" -f `
        $summary['chapters'], $summary['kps'], $summary['questions'], $summary['edges'], `
        $summary['demo_accounts'], $summary['practices'], $summary['errors'], $summary['ai_logs'], $summary['pending'])
    if ($null -ne $contentKp) {
        Write-Host ("内容层：{0} 点 / {1} 题 / {2} 边；{3}" -f $contentKp, $contentQ, $contentEdges, $contentNote)
    } else {
        Write-Host $contentNote
    }
}
finally {
    $env:MYSQL_PWD = $previousPwd
}
