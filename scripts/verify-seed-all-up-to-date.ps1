# 智学领航 · 合并种子新鲜度自检
# 重新生成到临时文件，与仓库里的 seed-demo-all.sql 比 SHA256：
#   - 一致：exit 0
#   - 不一致：exit 1，并列出比生成文件新的源 SQL
# 用法：
#   pwsh -ExecutionPolicy Bypass -File .\scripts\verify-seed-all-up-to-date.ps1
# 以后接 CI 可直接跑这一条；本脚本不修改仓库文件。

[CmdletBinding()]
param(
    [string]$SqlDir = (Join-Path $PSScriptRoot '..\zhixue-server\sql'),
    [string]$RepoFile = ''
)

$ErrorActionPreference = 'Stop'
if (-not $RepoFile) {
    $RepoFile = Join-Path $SqlDir 'seed-demo-all.sql'
}
$buildScript = Join-Path $PSScriptRoot 'build-seed-all.ps1'
$tmp = Join-Path $env:TEMP ("seed-demo-all.check.{0}.sql" -f ([guid]::NewGuid().ToString('N')))

try {
    if (-not (Test-Path $RepoFile)) {
        throw "缺少 $RepoFile；先执行 scripts/build-seed-all.ps1 并提交生成文件"
    }
    if (-not (Test-Path $buildScript)) {
        throw "缺少生成脚本 $buildScript"
    }

    & $buildScript -OutFile $tmp | Out-Null
    if (-not (Test-Path $tmp)) {
        throw '重新生成失败：临时文件未产出'
    }

    $repoHash = (Get-FileHash $RepoFile -Algorithm SHA256).Hash
    $regenHash = (Get-FileHash $tmp -Algorithm SHA256).Hash

    if ($repoHash -eq $regenHash) {
        Write-Host 'UP-TO-DATE：seed-demo-all.sql 与当前源 SQL 一致'
        Write-Host "SHA256=$repoHash"
        Write-Host '若刚重生成，请重跑 scripts/report-numbers.ps1 更新 docs/numbers-current.md'
        exit 0
    }

    Write-Host 'STALE：内容改过，需要重跑 scripts/build-seed-all.ps1 并提交新的 seed-demo-all.sql' -ForegroundColor Yellow
    Write-Host "  仓库文件 SHA256=$repoHash"
    Write-Host "  重新生成 SHA256=$regenHash"

    $sourceFiles = @(
        'seed-kp-ch01.sql',
        'seed-kp-ch02-ch03.sql',
        'seed-kp-ch04.sql',
        'seed-kp-ch05.sql',
        'seed-kp-ch06.sql',
        'seed-kp-ch07.sql',
        'seed-kp-ch08.sql',
        'seed-question-ch01.sql',
        'seed-question-ch02-ch03.sql',
        'seed-question-ch04.sql',
        'seed-question-ch05.sql',
        'seed-question-ch06.sql',
        'seed-question-ch07.sql',
        'seed-question-ch08.sql',
        'seed-prereq-ch01.sql',
        'seed-prereq-ch04.sql',
        'seed-prereq-ch05.sql',
        'seed-prereq-ch06.sql',
        'seed-prereq-ch07.sql',
        'seed-prereq-ch08.sql',
        'seed-demo-users.sql',
        'seed-demo-data.sql'
    )

    $generatedTime = (Get-Item $RepoFile).LastWriteTimeUtc
    $newer = foreach ($name in $sourceFiles) {
        $path = Join-Path $SqlDir $name
        if (Test-Path $path) {
            $time = (Get-Item $path).LastWriteTimeUtc
            if ($time -gt $generatedTime) {
                [pscustomobject]@{ File = $name; LastWriteTime = $time }
            }
        }
    }

    if ($newer.Count -gt 0) {
        Write-Host '以下源 SQL 比 seed-demo-all.sql 新：'
        foreach ($item in ($newer | Sort-Object LastWriteTime)) {
            Write-Host ("  {0,-30} {1:yyyy-MM-dd HH:mm:ss} UTC" -f $item.File, $item.LastWriteTime)
        }
    } else {
        Write-Host '没有源 SQL 比生成文件新；可能是 seed-demo-all.sql 被手改，或生成后尚未提交。'
    }

    Write-Host '修复后请重跑 build-seed-all.ps1，再跑 report-numbers.ps1 更新 docs/numbers-current.md'
    exit 1
}
catch {
    Write-Host "ERROR：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
finally {
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    Remove-Item "$tmp.tmp" -Force -ErrorAction SilentlyContinue
}
