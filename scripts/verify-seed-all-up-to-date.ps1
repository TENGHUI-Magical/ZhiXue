# 智学领航 · 合并种子新鲜度自检
# 重新生成到临时文件，与仓库里的 seed-demo-all.sql 比较【内容】：
#   - 比较前两侧都规范化行尾（CRLF / CR / LF 一律折成 LF）再求 SHA256；
#     仓库文件是 LF（.gitattributes: * text=auto eol=lf），而 build-seed-all.ps1 在
#     Windows 上用 StringBuilder.AppendLine 写出的行是 CRLF，直接比原始字节会
#     永远误报 STALE（2026-11-06 实测：去 CR 后两侧 SHA256 完全相同）。
#   - 内容一致：exit 0（并额外说明原始字节是否也相同）
#   - 内容不一致：exit 1，并列出比生成文件新的源 SQL
# 注意：规范化只消除行尾差异，不弱化比较——任何字符的增删改都会改变 SHA256。
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

# 规范化行尾后的内容 SHA256。
# 读文本用 UTF8（自动识别并跳过 BOM），把 \r\n 与孤立 \r 一律折成 \n，再对 UTF-8 字节求
# SHA256。除行尾外不做任何裁剪/合并/归一化，保证真实漂移仍然一定被检出。
function Get-NormalizedContentHash([string]$Path) {
    $encoding = [System.Text.UTF8Encoding]::new($false)
    $text = [System.IO.File]::ReadAllText($Path, $encoding)
    $normalized = ($text -replace '\r\n', "`n") -replace '\r', "`n"
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = $encoding.GetBytes($normalized)
        return ([System.BitConverter]::ToString($sha.ComputeHash($bytes)) -replace '-', '')
    }
    finally {
        $sha.Dispose()
    }
}

try {
    if (-not (Test-Path $RepoFile)) {
        throw "缺少 $RepoFile；先执行 scripts/build-seed-all.ps1 并提交生成文件"
    }
    if (-not (Test-Path $buildScript)) {
        throw "缺少生成脚本 $buildScript"
    }

    # -SqlDir 必须透传给生成脚本，否则「重新生成」永远读默认目录，
    # -SqlDir 就只改比较目标、不改被比较的生成内容（2026-11-06 反向自检发现）。
    & $buildScript -SqlDir $SqlDir -OutFile $tmp | Out-Null
    if (-not (Test-Path $tmp)) {
        throw '重新生成失败：临时文件未产出'
    }

    $repoRawHash = (Get-FileHash $RepoFile -Algorithm SHA256).Hash
    $regenRawHash = (Get-FileHash $tmp -Algorithm SHA256).Hash
    $repoHash = Get-NormalizedContentHash $RepoFile
    $regenHash = Get-NormalizedContentHash $tmp

    if ($repoHash -eq $regenHash) {
        Write-Host 'UP-TO-DATE：seed-demo-all.sql 与当前源 SQL 内容一致'
        Write-Host "SHA256(规范化行尾)=$repoHash"
        if ($repoRawHash -eq $regenRawHash) {
            Write-Host '内容一致，原始字节亦相同（无行尾差异）'
        }
        else {
            Write-Host "内容一致，仅行尾差异（原始 SHA256 不同：repo=$repoRawHash regen=$regenRawHash）"
        }
        Write-Host '若刚重生成，请重跑 scripts/report-numbers.ps1 更新 docs/numbers-current.md'
        exit 0
    }

    Write-Host 'STALE：内容不一致，需要重跑 scripts/build-seed-all.ps1 并提交新的 seed-demo-all.sql' -ForegroundColor Yellow
    Write-Host '  内容不一致'
    Write-Host "  SHA256(规范化行尾)：仓库文件=$repoHash"
    Write-Host "  SHA256(规范化行尾)：重新生成=$regenHash"
    Write-Host "  SHA256(原始字节)：仓库文件=$repoRawHash"
    Write-Host "  SHA256(原始字节)：重新生成=$regenRawHash"

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
