# 固定 JDK 21 后运行 Maven，规避机器级 JAVA_HOME=JDK17 导致的 class file version 65/61 假失败。
# 用法（在 zhixue-server 目录下）：..\scripts\mvn21.ps1 -B -ntp verify
# 可选：设置 ZHIXUE_JAVA_HOME 指向任意 JDK 21，脚本优先使用它。
$ErrorActionPreference = 'Stop'

function Get-JdkMajor([string] $Path) {
    if (-not $Path -or -not (Test-Path (Join-Path $Path 'bin\java.exe'))) {
        return $null
    }
    $release = Join-Path $Path 'release'
    if (Test-Path $release) {
        $line = Get-Content $release | Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
        if ($line -match 'JAVA_VERSION="(\d+)') {
            return [int] $Matches[1]
        }
    }
    try {
        $output = & (Join-Path $Path 'bin\java.exe') -version 2>&1
        if ($output -match 'version "(\d+)') {
            return [int] $Matches[1]
        }
    } catch {
        return $null
    }
    return $null
}

$candidates = @()
if ($env:ZHIXUE_JAVA_HOME) { $candidates += $env:ZHIXUE_JAVA_HOME }
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
$candidates += @(
    'D:\jdk-21.0.13.0.3',
    'D:\jdk21',
    "$env:ProgramFiles\Java\jdk-21",
    "$env:ProgramFiles\Eclipse Adoptium\jdk-21"
)
$candidates += Get-ChildItem -Path 'D:\' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
    ForEach-Object { $_.FullName }
$candidates += Get-ChildItem -Path "$env:ProgramFiles\Java" -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
    ForEach-Object { $_.FullName }

$jdk = $null
foreach ($candidate in ($candidates | Select-Object -Unique)) {
    if ((Get-JdkMajor $candidate) -eq 21) {
        $jdk = $candidate
        break
    }
}

if (-not $jdk) {
    Write-Host '未找到 JDK 21。' -ForegroundColor Red
    Write-Host '请设置 ZHIXUE_JAVA_HOME 指向 JDK 21，或安装到 D:\jdk-21.0.13.0.3。' -ForegroundColor Red
    Write-Host '本项目要求 JDK 21；机器级 JAVA_HOME 可能是 JDK 17，直接 mvn 会报 class file version 65/61。' -ForegroundColor Red
    exit 2
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host '未找到 mvn，请确认 Maven 已加入 PATH。' -ForegroundColor Red
    exit 2
}

$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path
Write-Host "使用 JDK 21: $jdk" -ForegroundColor Green

& mvn @args
exit $LASTEXITCODE
