# 智学领航 · 合并演示库种子（供 docker-entrypoint-initdb.d 单文件执行）
# 顺序固定：knowledge_point → question_bank → knowledge_prereq → 演示账号/数据
# 幂等：所有 INSERT 均为 upsert；无 upsert 的由前置 DELETE 或 WHERE NOT EXISTS 保证
# 用法：
#   pwsh -ExecutionPolicy Bypass -File .\scripts\build-seed-all.ps1
# 输出：zhixue-server/sql/seed-demo-all.sql（覆盖写，勿手改）

[CmdletBinding()]
param(
    [string]$SqlDir = (Join-Path $PSScriptRoot '..\zhixue-server\sql'),
    [string]$OutFile = ''
)

$ErrorActionPreference = 'Stop'
if (-not $OutFile) {
    $OutFile = Join-Path $SqlDir 'seed-demo-all.sql'
}

$kpFiles = @(
    'seed-kp-ch01.sql',
    'seed-kp-ch02-ch03.sql',
    'seed-kp-ch04.sql',
    'seed-kp-ch05.sql',
    'seed-kp-ch06.sql',
    'seed-kp-ch07.sql',
    'seed-kp-ch08.sql'
)
$questionFiles = @(
    'seed-question-ch01.sql',
    'seed-question-ch02-ch03.sql',
    'seed-question-ch04.sql',
    'seed-question-ch05.sql',
    'seed-question-ch06.sql',
    'seed-question-ch07.sql',
    'seed-question-ch08.sql'
)
$prereqFiles = @(
    'seed-prereq-ch01.sql',
    'seed-prereq-ch04.sql',
    'seed-prereq-ch05.sql',
    'seed-prereq-ch06.sql',
    'seed-prereq-ch07.sql',
    'seed-prereq-ch08.sql'
)
$demoFiles = @(
    'seed-demo-users.sql',
    'seed-demo-data.sql'
)

$allFiles = @($kpFiles + $questionFiles + $prereqFiles + $demoFiles)
$missing = @($allFiles | Where-Object { -not (Test-Path (Join-Path $SqlDir $_)) })
if ($missing.Count -gt 0) {
    throw "缺少种子文件：$($missing -join ', ')"
}

function Read-Sql([string]$name) {
    $text = Get-Content -Raw -Encoding utf8 (Join-Path $SqlDir $name)
    # 合并文件只保留一个 USE/SET NAMES 头，避免 23 份重复
    $text = $text -replace '(?m)^USE zhixue;\s*$', ''
    $text = $text -replace '(?m)^SET NAMES utf8mb4;\s*$', ''
    return $text.Trim()
}

# ch02-03 的 KP 文件里混有 knowledge_prereq 段；拆出来放到最后的 prereq 阶段
$ch23Path = Join-Path $SqlDir 'seed-kp-ch02-ch03.sql'
$ch23 = Get-Content -Raw -Encoding utf8 $ch23Path
$marker = 'INSERT INTO knowledge_prereq (kp_id, prereq_kp_id) VALUES'
$markerIndex = $ch23.IndexOf($marker)
if ($markerIndex -lt 0) {
    throw 'seed-kp-ch02-ch03.sql 未找到 knowledge_prereq 段，无法保证 prereq 全部后置'
}
$endIndex = $ch23.IndexOf(';', $markerIndex)
if ($endIndex -lt 0) {
    throw 'seed-kp-ch02-ch03.sql 的 knowledge_prereq 段缺少分号'
}
$ch23Prereq = $ch23.Substring($markerIndex, $endIndex - $markerIndex + 1).Trim()
$ch23Kp = $ch23.Remove($markerIndex, $endIndex - $markerIndex + 1)
$ch23Kp = ($ch23Kp -replace '(?m)^USE zhixue;\s*$', '') -replace '(?m)^SET NAMES utf8mb4;\s*$', ''

$out = [System.Text.StringBuilder]::new()
[void]$out.AppendLine('-- 智学领航 · 演示库合并种子（自动生成，勿手改）')
[void]$out.AppendLine('-- 生成脚本：scripts/build-seed-all.ps1')
[void]$out.AppendLine('-- 顺序：knowledge_point -> question_bank -> knowledge_prereq -> 演示账号/数据')
[void]$out.AppendLine('-- 幂等：INSERT 均为 upsert；无 upsert 的由前置 DELETE 或 WHERE NOT EXISTS 保证')
[void]$out.AppendLine('USE zhixue;')
[void]$out.AppendLine('SET NAMES utf8mb4;')

function Add-Section([string]$phase, [string]$name, [string]$text) {
    [void]$out.AppendLine('')
    [void]$out.AppendLine('-- ============================================================')
    [void]$out.AppendLine("-- [$phase] $name")
    [void]$out.AppendLine('-- ============================================================')
    [void]$out.AppendLine($text)
}

foreach ($name in $kpFiles) {
    $text = if ($name -eq 'seed-kp-ch02-ch03.sql') { $ch23Kp.Trim() } else { Read-Sql $name }
    Add-Section 'kp' $name $text
}
foreach ($name in $questionFiles) {
    Add-Section 'question' $name (Read-Sql $name)
}
Add-Section 'prereq' 'seed-kp-ch02-ch03.sql (knowledge_prereq)' $ch23Prereq
foreach ($name in $prereqFiles) {
    Add-Section 'prereq' $name (Read-Sql $name)
}
foreach ($name in $demoFiles) {
    Add-Section 'demo' $name (Read-Sql $name)
}

$text = $out.ToString()
$tmp = "$OutFile.tmp"
[System.IO.File]::WriteAllText($tmp, $text, [System.Text.UTF8Encoding]::new($false))
Move-Item -Force $tmp $OutFile

# ---------- 静态校验 ----------
$content = Get-Content -Raw -Encoding utf8 $OutFile
$lastKp = $content.LastIndexOf('INSERT INTO knowledge_point')
$firstQuestion = $content.IndexOf('INSERT INTO question_bank')
$lastQuestion = $content.LastIndexOf('INSERT INTO question_bank')
$firstPrereq = $content.IndexOf('INSERT INTO knowledge_prereq')
if ($lastKp -lt 0 -or $firstQuestion -lt 0 -or $lastQuestion -lt 0 -or $firstPrereq -lt 0) {
    throw '合并文件缺少 kp/question/prereq 段'
}
if (-not ($lastKp -lt $firstQuestion -and $lastQuestion -lt $firstPrereq)) {
    throw "顺序校验失败：lastKp=$lastKp firstQuestion=$firstQuestion lastQuestion=$lastQuestion firstPrereq=$firstPrereq"
}

$insertMatches = [regex]::Matches($content, '(?m)^INSERT (?:IGNORE )?INTO ([a-z_]+)')
$insertIssues = @()
$mechByTable = @{}
for ($i = 0; $i -lt $insertMatches.Count; $i++) {
    $match = $insertMatches[$i]
    $start = $match.Index
    $end = if ($i + 1 -lt $insertMatches.Count) { $insertMatches[$i + 1].Index } else { $content.Length }
    $segment = $content.Substring($start, $end - $start)
    $table = $match.Groups[1].Value
    $mechanism = if ($segment -match 'ON DUPLICATE KEY UPDATE') {
        'ON DUPLICATE'
    } elseif ($segment -match 'WHERE NOT EXISTS') {
        'WHERE NOT EXISTS'
    } elseif ($table -eq 'question_kp' -and $content.Substring([Math]::Max(0, $start - 2000), $start - [Math]::Max(0, $start - 2000)) -match 'DELETE FROM question_kp') {
        'PRE DELETE'
    } else {
        'NONE'
    }
    $mechByTable[$table] = $mechanism
    if ($mechanism -eq 'NONE') {
        $insertIssues += "$table@$start"
    }
}
if ($insertIssues.Count -gt 0) {
    throw "幂等校验失败：$($insertIssues -join ', ')"
}

$kpSet = [System.Collections.Generic.HashSet[string]]::new()
$kpByChapter = @{}
foreach ($m in [regex]::Matches($content, "SELECT '(KP(\d{2})\d{2})'")) {
    $id = $m.Groups[1].Value
    if ($kpSet.Add($id)) {
        $ch = [int]$m.Groups[2].Value
        $kpByChapter[$ch] = 1 + ($kpByChapter[$ch] -as [int])
    }
}
$qSet = [System.Collections.Generic.HashSet[string]]::new()
$qByChapter = @{}
foreach ($m in [regex]::Matches($content, "VALUES \('(Q_(\d{2})\d{2})'")) {
    $id = $m.Groups[1].Value
    if ($qSet.Add($id)) {
        $ch = [int]$m.Groups[2].Value
        $qByChapter[$ch] = 1 + ($qByChapter[$ch] -as [int])
    }
}
$edgeSet = [System.Collections.Generic.HashSet[string]]::new()
foreach ($m in [regex]::Matches($content, "(?m)^\('KP\d+', 'KP\d+'\)")) {
    [void]$edgeSet.Add($m.Value)
}
$edges = $edgeSet.Count
$totalKp = $kpSet.Count
$totalQ = $qSet.Count

Write-Host "已生成 $OutFile"
Write-Host ("顺序检查 PASS：lastKp={0} < firstQuestion={1}；lastQuestion={2} < firstPrereq={3}" -f $lastKp, $firstQuestion, $lastQuestion, $firstPrereq)
Write-Host ("幂等检查 PASS：{0} 条 INSERT 均有 ON DUPLICATE / WHERE NOT EXISTS / 前置 DELETE" -f $insertMatches.Count)
Write-Host '  按表机制：'
foreach ($entry in ($mechByTable.GetEnumerator() | Sort-Object Key)) {
    Write-Host ("  {0,-22} {1}" -f $entry.Key, $entry.Value)
}
Write-Host ''
Write-Host '逐章推导（来自合并文件）：'
Write-Host 'chapter | kp | question'
foreach ($ch in 1..8) {
    $kp = if ($kpByChapter.ContainsKey($ch)) { $kpByChapter[$ch] } else { 0 }
    $q = if ($qByChapter.ContainsKey($ch)) { $qByChapter[$ch] } else { 0 }
    Write-Host ("{0,7} | {1,2} | {2,3}" -f $ch, $kp, $q)
}
Write-Host ("TOTAL   | {0,2} | {1,3} | edges={2}" -f $totalKp, $totalQ, $edges)
