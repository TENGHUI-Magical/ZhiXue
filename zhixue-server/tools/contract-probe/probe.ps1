# 契约边界黑盒探测脚本（一次性工具，非生产代码）。
# 用法：先起后端，再设置环境变量，最后 pwsh -File probe.ps1
#   PROBE_BASE      后端地址，默认 http://127.0.0.1:8080
#   PROBE_PASSWORD  演示账号密码（必填，脚本不内置口令）
#   PROBE_OUT_DIR   输出目录，默认系统临时目录下的 zhixue-contract-probe
# 输出：probe-matrix.csv / probe-matrix.md / raw/<ID>.txt，均不写进仓库。
param(
    [string]$Base = $(if ($env:PROBE_BASE) { $env:PROBE_BASE } else { 'http://127.0.0.1:8080' }),
    [string]$OutDir = $(if ($env:PROBE_OUT_DIR) { $env:PROBE_OUT_DIR } else { Join-Path ([System.IO.Path]::GetTempPath()) 'zhixue-contract-probe' })
)

$ErrorActionPreference = 'Continue'
$Password = $env:PROBE_PASSWORD
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw '请先设置环境变量 PROBE_PASSWORD（演示账号密码），脚本不内置口令。'
}
$outDir = Join-Path $OutDir 'raw'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$rows = [System.Collections.Generic.List[object]]::new()
# 泄露判定：只看“技术栈痕迹”，不匹配业务文本。
# 早期把 'SELECT ' 当 SQL 泄露，误报了 2 条（K21/K26 返回的是数据库题目原文），已剔除；
# 真正的 SQL 泄露应带表名/关键字组合，因此改用更具体的模式。
$leakPatterns = @('Exception', 'java.lang', 'java.sql', 'com.zhixue', 'org.springframework',
    'org.apache', 'SQLException', 'at com.', "`tat ", 'HikariPool', 'MyBatis',
    'localhost:8080', 'FROM t_', 'SELECT * FROM')

function J($h) { return ($h | ConvertTo-Json -Compress -Depth 6) }

function Login([string]$u, [string]$p) {
    $body = J @{ username = $u; password = $p }
    try {
        $r = Invoke-WebRequest -Uri "$Base/api/auth/login" -Method Post -ContentType 'application/json' -Body $body -SkipHttpErrorCheck -TimeoutSec 15
        $j = $r.Content | ConvertFrom-Json
        if ($j.code -eq 0) { return $j.data.token }
    } catch { }
    return $null
}

function SaveRaw([string]$id, [string]$content) {
    if ($null -eq $content) { $content = '' }
    Set-Content -Path (Join-Path $outDir "$id.txt") -Value $content -Encoding utf8
}

function Exec([string]$id, [string]$group, [string]$method, [string]$path, [string]$token,
              [object]$body, [string]$ctype, [string]$inputDesc, [string]$expect) {
    $url = "$Base$path"
    $headers = @{}
    if ($token) { $headers['satoken'] = $token }
    $started = Get-Date
    $http = 0; $content = ''; $errMsg = ''
    try {
        $args = @{ Uri = $url; Method = $method; Headers = $headers; SkipHttpErrorCheck = $true; TimeoutSec = 45 }
        if ($null -ne $body) { $args['ContentType'] = $ctype; $args['Body'] = $body }
        $r = Invoke-WebRequest @args
        $http = [int]$r.StatusCode
        $content = [string]$r.Content
    } catch {
        $errMsg = $_.Exception.Message
        $content = "TRANSPORT: $errMsg"
    }
    $elapsed = [int]((Get-Date) - $started).TotalMilliseconds
    SaveRaw $id $content

    $code = $null
    if ($content -match '"code"\s*:\s*(-?\d+)') { $code = [int]$matches[1] }
    $leak = @()
    foreach ($p in $leakPatterns) { if ($content -and $content.Contains($p)) { $leak += $p } }

    $verdict = 'PASS'
    if ($http -eq 0) { $verdict = 'TRANSPORT_ERROR' }
    elseif ($http -ge 500) { $verdict = 'HTTP_5XX' }
    elseif ($http -ne 200) { $verdict = 'NON_200' }
    elseif ($code -eq 50000) { $verdict = 'CODE_50000' }
    elseif ($expect -eq 'ANY') { $verdict = 'PASS' }
    elseif ($expect -eq 'ENVELOPE') { if ($code -eq 50000 -or $null -eq $code) { $verdict = 'CONTRACT_BREACH' } }
    elseif ((($expect -split '\|') | ForEach-Object { [int]$_ }) -notcontains $code) { $verdict = 'CODE_MISMATCH' }
    if ($leak.Count -gt 0) { $verdict = 'LEAK' }

    $shortInput = $inputDesc
    if ($shortInput.Length -gt 90) { $shortInput = $shortInput.Substring(0, 90) + '...' }
    $shortResp = ($content -replace "`r|`n", ' ')
    if ($shortResp.Length -gt 220) { $shortResp = $shortResp.Substring(0, 220) + '...' }

    $rows.Add([pscustomobject]@{
        ID = $id; Group = $group; Method = $method; Endpoint = $path; Input = $shortInput
        Expect = $expect; HTTP = $http; Code = $(if ($null -eq $code) { '' } else { $code })
        Verdict = $verdict; Leak = ($leak -join ','); Ms = $elapsed; Resp = $shortResp
    })
    Write-Host ("{0,-4} {1,-4} HTTP={2,3} CODE={3,-5} {4,-16} {5}" -f $id, $method, $http, $code, $verdict, $path)
}

function Upload([string]$id, [string]$token, [string]$filePath, [string]$clientToken,
                [string]$inputDesc, [string]$expect, [string]$fieldName = 'file') {
    $headers = @{}
    if ($token) { $headers['satoken'] = $token }
    $started = Get-Date
    $http = 0; $content = ''
    try {
        # PowerShell -Form 会把文件按 application/octet-stream 发送，服务端按 image/jpeg 白名单直接拒绝，
        # 导致“合法 JPEG”也被 40000 误判。curl.exe 可以显式指定 part 的 Content-Type。
        $rawFile = Join-Path $outDir "$id.http"
        $curlArgs = @('-s', '-o', $rawFile, '-w', '%{http_code}',
            '-X', 'POST', "$Base/api/error/upload")
        if ($token) { $curlArgs += @('-H', "satoken: $token") }
        if ($filePath) { $curlArgs += @('-F', ("{0}=@{1};type=image/jpeg" -f $fieldName, $filePath)) }
        if ($null -ne $clientToken -and $clientToken -ne '') {
            $curlArgs += @('-F', "clientToken=$clientToken")
        }
        $statusText = (& curl.exe @curlArgs | Out-String).Trim()
        if ($statusText -match '^\d+$') { $http = [int]$statusText }
        if (Test-Path $rawFile) {
            $content = [string](Get-Content -Raw -Path $rawFile -Encoding utf8)
            Remove-Item $rawFile -Force -ErrorAction SilentlyContinue
        }
    } catch { $content = "TRANSPORT: $($_.Exception.Message)" }
    $elapsed = [int]((Get-Date) - $started).TotalMilliseconds
    SaveRaw $id $content
    $code = $null
    if ($content -match '"code"\s*:\s*(-?\d+)') { $code = [int]$matches[1] }
    $leak = @()
    foreach ($p in $leakPatterns) { if ($content -and $content.Contains($p)) { $leak += $p } }
    $verdict = 'PASS'
    if ($http -eq 0) { $verdict = 'TRANSPORT_ERROR' }
    elseif ($http -ge 500) { $verdict = 'HTTP_5XX' }
    elseif ($http -ne 200) { $verdict = 'NON_200' }
    elseif ($code -eq 50000) { $verdict = 'CODE_50000' }
    elseif ($expect -eq 'ANY') { $verdict = 'PASS' }
    elseif ((($expect -split '\|') | ForEach-Object { [int]$_ }) -notcontains $code) { $verdict = 'CODE_MISMATCH' }
    if ($leak.Count -gt 0) { $verdict = 'LEAK' }
    $shortResp = ($content -replace "`r|`n", ' ')
    if ($shortResp.Length -gt 220) { $shortResp = $shortResp.Substring(0, 220) + '...' }
    $rows.Add([pscustomobject]@{
        ID = $id; Group = 'Upload'; Method = 'POST'; Endpoint = '/api/error/upload'; Input = $inputDesc
        Expect = $expect; HTTP = $http; Code = $(if ($null -eq $code) { '' } else { $code })
        Verdict = $verdict; Leak = ($leak -join ','); Ms = $elapsed; Resp = $shortResp
    })
    Write-Host ("{0,-4} POST HTTP={1,3} CODE={2,-5} {3,-16} upload {4}" -f $id, $http, $code, $verdict, $inputDesc)
}

# ---- tokens ----
$stu = Login 'demo_stu01' $Password
$stu2 = Login 'demo_stu02' $Password
$tea = Login 'teacher01' $Password
$tmpToken = Login 'demo_stu03' $Password
Write-Host "tokens: stu=$stu stu2=$stu2 tea=$tea"

$kp = 'KP0201'
$q = 'Q_0101'
$ownLog = 'EL_DEMO_9001_1'
$otherLog = 'EL_DEMO_9002_1'
$ownExplain = 'DEMO_EX_EL_DEMO_9001_3'
$otherExplain = 'DEMO_EX_EL_DEMO_9002_4'
$ownPlan = 4
$otherPlan = 2
$runId = Get-Date -Format 'HHmmss'
$long1000 = 'A' * 1000
$long100000 = 'A' * 100000
$xss = '<script>alert(1)</script>'
$sqli = "' OR 1=1 --"

# ---- A auth ----
Exec 'A01' 'Auth' 'POST' '/api/auth/login' '' (J @{username='student01';password=$Password}) 'application/json' 'valid credentials' '0'
Exec 'A02' 'Auth' 'POST' '/api/auth/login' '' (J @{username='student01';password='wrongpass'}) 'application/json' 'wrong password' '40100'
Exec 'A03' 'Auth' 'POST' '/api/auth/login' '' (J @{username='student01'}) 'application/json' 'missing password' '40000'
Exec 'A04' 'Auth' 'POST' '/api/auth/login' '' (J @{username=123;password='Valid@2026'}) 'application/json' 'username as number (Jackson coercion)' '40100'
Exec 'A04b' 'Auth' 'POST' '/api/auth/login' '' (J @{username=@('a','b');password='Valid@2026'}) 'application/json' 'username as array' '40000'
Exec 'A04c' 'Auth' 'POST' '/api/auth/login' '' (J @{username=('U'*1000);password='Valid@2026'}) 'application/json' 'username 1000 chars' '40000'
Exec 'A04d' 'Auth' 'POST' '/api/auth/login' '' (J @{username=$sqli;password='Valid@2026'}) 'application/json' 'SQLi username' '40100'
Exec 'A05' 'Auth' 'POST' '/api/auth/login' '' '[{"username":"demo_stu01"}]' 'application/json' 'body is array' '40000'
Exec 'A06' 'Auth' 'GET' '/api/auth/me' '' $null '' 'no token' '40100'
Exec 'A07' 'Auth' 'GET' '/api/auth/me' 'bogus-token-123' $null '' 'bogus token' '40100'
Exec 'A08' 'Auth' 'GET' '/api/auth/me' $stu $null '' 'valid student token' '0'
Exec 'A09' 'Auth' 'POST' '/api/auth/logout' '' $null '' 'logout no token' '40100'
Exec 'A10' 'Auth' 'POST' '/api/auth/logout' $tmpToken $null '' 'logout valid token' '0'
Exec 'A11' 'Auth' 'GET' '/api/auth/login' '' $null '' 'GET on POST-only endpoint' 'ENVELOPE'
Exec 'A12' 'Auth' 'POST' '/api/auth/me' $stu $null '' 'POST on GET-only endpoint' 'ENVELOPE'

# ---- B course ----
Exec 'B01' 'Course' 'GET' '/api/courses' $stu $null '' 'valid' '0'
Exec 'B02' 'Course' 'GET' '/api/courses' '' $null '' 'no token' '40100'
Exec 'B03' 'Course' 'GET' '/api/courses/1/chapters' $stu $null '' 'valid courseId=1' '0'
Exec 'B04' 'Course' 'GET' '/api/courses/0/chapters' $stu $null '' 'courseId=0' '40400'
Exec 'B05' 'Course' 'GET' '/api/courses/-1/chapters' $stu $null '' 'courseId=-1' '40400'
Exec 'B06' 'Course' 'GET' '/api/courses/2147483648/chapters' $stu $null '' 'courseId=2147483648 (fits int64)' '40400'
Exec 'B07' 'Course' 'GET' '/api/courses/99999999999999999999/chapters' $stu $null '' 'courseId overflows Long' '40000'
Exec 'B08' 'Course' 'GET' '/api/courses/abc/chapters' $stu $null '' 'courseId=abc' '40000'
Exec 'B09' 'Course' 'GET' '/api/courses/1/knowledge-graph' $stu $null '' 'valid' '0'
Exec 'B10' 'Course' 'GET' '/api/courses/0/knowledge-graph' $stu $null '' 'courseId=0' '40400'
Exec 'B11' 'Course' 'GET' "/api/course/kp/$kp" $stu $null '' 'valid kpId' '0'
Exec 'B12' 'Course' 'GET' '/api/course/kp/NO_SUCH_KP' $stu $null '' 'nonexistent kpId' '40400'
Exec 'B13' 'Course' 'GET' '/api/course/kp/%00' $stu $null '' 'NUL byte in path' 'ENVELOPE'
Exec 'B14' 'Course' 'GET' ("/api/course/kp/" + [uri]::EscapeDataString($sqli)) $stu $null '' 'SQL injection kpId' '40400|40000'
Exec 'B15' 'Course' 'GET' ("/api/course/kp/$long1000") $stu $null '' '1000-char kpId' '40400|40000'
Exec 'B16' 'Course' 'GET' ("/api/course/kp/" + [uri]::EscapeDataString('数据库系统🚀')) $stu $null '' 'Chinese+emoji kpId' '40400'

# ---- C questions ----
Exec 'C01' 'Question' 'GET' "/api/questions?kpId=$kp" $stu $null '' 'valid' '0'
Exec 'C02' 'Question' 'GET' '/api/questions' $stu $null '' 'missing kpId' '40000'
Exec 'C03' 'Question' 'GET' '/api/questions?kpId=' $stu $null '' 'empty kpId' '40000'
Exec 'C04' 'Question' 'GET' '/api/questions?kpId=%20%20%20' $stu $null '' 'blank kpId' '40000'
Exec 'C05' 'Question' 'GET' "/api/questions?kpId=$kp&limit=0" $stu $null '' 'limit=0 (contract min 1)' '0'
Exec 'C06' 'Question' 'GET' "/api/questions?kpId=$kp&limit=-1" $stu $null '' 'limit=-1' '0'
Exec 'C07' 'Question' 'GET' "/api/questions?kpId=$kp&limit=2147483647" $stu $null '' 'limit=Integer.MAX' '0'
Exec 'C08' 'Question' 'GET' "/api/questions?kpId=$kp&limit=abc" $stu $null '' 'limit non-numeric' '40000'
Exec 'C09' 'Question' 'GET' "/api/questions?kpId=$kp&limit=1e10" $stu $null '' 'limit=1e10' '40000'
Exec 'C10' 'Question' 'GET' '/api/questions?kpId=NO_SUCH_KP' $stu $null '' 'nonexistent kpId' '40400'
Exec 'C11' 'Question' 'GET' ("/api/questions?kpId=" + [uri]::EscapeDataString($sqli)) $stu $null '' 'SQL injection kpId' '40400|40000'
Exec 'C12' 'Question' 'GET' ("/api/questions?kpId=" + [uri]::EscapeDataString($xss)) $stu $null '' 'XSS kpId' '40400|40000'
Exec 'C13' 'Question' 'GET' "/api/questions/$q" $stu $null '' 'valid qId' '0'
Exec 'C14' 'Question' 'GET' '/api/questions/NO_SUCH_Q' $stu $null '' 'nonexistent qId' '40400'
Exec 'C15' 'Question' 'GET' '/api/questions/0' $stu $null '' 'qId=0' '40400'
Exec 'C16' 'Question' 'GET' "/api/questions/$q/answer" $stu $null '' 'valid answer' '0'
Exec 'C17' 'Question' 'GET' '/api/questions/NO_SUCH_Q/answer' $stu $null '' 'nonexistent answer' '40400'
Exec 'C18' 'Question' 'GET' ("/api/questions/" + $long100000) $stu $null '' '100000-char qId' 'ENVELOPE'

# ---- D upload ----
$tmp = Join-Path $env:TEMP 'zhixue-probe-files'
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
Set-Content -Path (Join-Path $tmp 'empty.jpg') -Value '' -NoNewline -Encoding ascii
Set-Content -Path (Join-Path $tmp 'note.txt') -Value 'hello' -NoNewline -Encoding ascii
$b64png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='
[IO.File]::WriteAllBytes((Join-Path $tmp 'tiny.png'), [Convert]::FromBase64String($b64png))
Copy-Item (Join-Path $tmp 'tiny.png') (Join-Path $tmp 'fake.jpg') -Force
Copy-Item (Join-Path $tmp 'tiny.png') (Join-Path $tmp 'traversal.jpg') -Force
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 8,8
$bmp.Save((Join-Path $tmp 'real.jpg'), [System.Drawing.Imaging.ImageFormat]::Jpeg)
$bmp.Dispose()
Copy-Item (Join-Path $tmp 'real.jpg') (Join-Path $tmp 'traversal-real.jpg') -Force
Upload 'D01' $stu $null 'tok-no-file-1' 'no file part' '40000'
Upload 'D02' $stu (Join-Path $tmp 'note.txt') 'tok-txt-1' '.txt extension' '40000'
Upload 'D03' $stu (Join-Path $tmp 'empty.jpg') 'tok-empty-1' 'empty .jpg' '40000'
Upload 'D04' $stu (Join-Path $tmp 'real.jpg') "probe-d04-$runId" 'valid JPEG' '0'
Upload 'D04b' $stu (Join-Path $tmp 'fake.jpg') 'probe-d04b-20261104' 'PNG bytes named .jpg (magic mismatch)' '40000'
Upload 'D05' $stu (Join-Path $tmp 'traversal-real.jpg') 'probe-d05-20261104' 'filename traversal attempt' '0|40000'
Upload 'D06' $stu (Join-Path $tmp 'real.jpg') ' ' 'blank clientToken' '40000'
Upload 'D07' $stu (Join-Path $tmp 'real.jpg') ('T' * 65) 'clientToken 65 chars' '40000'
Upload 'D08' $stu (Join-Path $tmp 'real.jpg') $null 'missing clientToken' '40000'
$d04 = Get-Content (Join-Path $outDir 'D04.txt') -Raw | ConvertFrom-Json
$taskId = $d04.data.taskId
Exec 'D09' 'Upload' 'GET' "/api/error/tasks/$taskId" $stu $null '' 'own OCR task' '0'
Exec 'D10' 'Upload' 'GET' "/api/error/tasks/$taskId" $stu2 $null '' 'other user OCR task' '40300|40400'
Exec 'D11' 'Upload' 'GET' '/api/error/tasks/NO_SUCH_TASK' $stu $null '' 'nonexistent OCR task' '40400'

# ---- E error records ----
$ct = 'clientToken'
Exec 'E01' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken="probe-e01-$runId";kpId=$kp;qId='Q_0201';ocrText='probe';ocrConfidence=0.9;errorType='CONCEPT_CONFUSION';studentNote='probe'}) 'application/json' 'valid baseline' '0'
Exec 'E02' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{clientToken='probe-e02-20261104'}) 'application/json' 'missing courseId' '40000'
Exec 'E03' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1}) 'application/json' 'missing clientToken' '40000'
Exec 'E04' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken=('T'*65)}) 'application/json' 'clientToken 65 chars' '40000'
Exec 'E05' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=0;clientToken='probe-e05-20261104'}) 'application/json' 'courseId=0' '40400'
Exec 'E06' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=9223372036854775807;clientToken='probe-e06-20261104'}) 'application/json' 'courseId=Long.MAX' '40400'
Exec 'E07' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken='probe-e07-20261104';qId='NO_SUCH_Q'}) 'application/json' 'nonexistent qId' '40400'
Exec 'E08' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken='probe-e08-20261104';ocrConfidence=2}) 'application/json' 'ocrConfidence=2 (contract max=1)' '40000'
Exec 'E09' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken='probe-e09-20261104';ocrConfidence=-1}) 'application/json' 'ocrConfidence=-1' '40000'
Exec 'E10' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken='probe-e10-20261104';errorType='HACK'}) 'application/json' 'errorType=HACK (enum violation)' '40000'
Exec 'E11' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken=$sqli}) 'application/json' 'SQLi in clientToken' '0'
Exec 'E12' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken=$xss}) 'application/json' 'XSS in clientToken' '0'
Exec 'E13' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken='probe-e13-20261104';unknownField='x'}) 'application/json' 'undocumented field' '0'
Exec 'E14' 'ErrorRecord' 'POST' '/api/error/records' $stu '[1,2,3]' 'application/json' 'body is array' '40000'
Exec 'E15' 'ErrorRecord' 'POST' '/api/error/records' $stu 'null' 'application/json' 'body is null' '40000'
Exec 'E16' 'ErrorRecord' 'POST' '/api/error/records' $stu '' 'application/json' 'empty body' '40000'
Exec 'E17' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken='probe-e17-20261104'}) 'text/plain' 'unsupported media type' 'ENVELOPE'
Exec 'E18' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken='probe-e18-20261104';ocrText=('X'*8001)}) 'application/json' 'ocrText 8001 chars' '40000'
Exec 'E19' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken='probe-e19-20261104';studentNote=('X'*501)}) 'application/json' 'studentNote 501 chars' '40000'
Exec 'E20' 'ErrorRecord' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken='probe-e20-20261104';ocrText="a`u{0000}b"}) 'application/json' 'NUL char in ocrText' '0|40000'
Exec 'E21' 'ErrorRecord' 'GET' '/api/error/records' $stu $null '' 'valid page' '0'
Exec 'E22' 'ErrorRecord' 'GET' '/api/error/records?page=-1' $stu $null '' 'page=-1' '40000'
Exec 'E23' 'ErrorRecord' 'GET' '/api/error/records?page=0' $stu $null '' 'page=0' '40000'
Exec 'E24' 'ErrorRecord' 'GET' '/api/error/records?page=2147483647&size=50' $stu $null '' 'page=Integer.MAX' '0'
Exec 'E25' 'ErrorRecord' 'GET' '/api/error/records?size=0' $stu $null '' 'size=0' '40000'
Exec 'E26' 'ErrorRecord' 'GET' '/api/error/records?size=100000' $stu $null '' 'size=100000' '40000'
Exec 'E27' 'ErrorRecord' 'GET' '/api/error/records?size=-1' $stu $null '' 'size=-1' '40000'
Exec 'E28' 'ErrorRecord' 'GET' '/api/error/records?size=abc' $stu $null '' 'size=abc' '40000'
Exec 'E29' 'ErrorRecord' 'GET' '/api/error/records?status=HACK' $stu $null '' 'status=HACK' '40000'
Exec 'E30' 'ErrorRecord' 'GET' '/api/error/records?status=DRAFT' $stu $null '' 'status=DRAFT' '0'
Exec 'E31' 'ErrorRecord' 'GET' '/api/error/records?courseId=999' $stu $null '' 'courseId=999 (no data)' '0'
Exec 'E32' 'ErrorRecord' 'GET' "/api/error/records/$ownLog" $stu $null '' 'own logId' '0'
Exec 'E33' 'ErrorRecord' 'GET' "/api/error/records/$otherLog" $stu $null '' 'other student logId' '40300|40400'
Exec 'E34' 'ErrorRecord' 'GET' '/api/error/records/NO_SUCH_LOG' $stu $null '' 'nonexistent logId' '40400'
Exec 'E35' 'ErrorRecord' 'GET' '/api/error/records/0' $stu $null '' 'logId=0' '40400'
$draftLog = (Get-Content (Join-Path $outDir 'E01.txt') -Raw | ConvertFrom-Json).data.logId
Exec 'E36' 'ErrorRecord' 'PUT' "/api/error/records/$draftLog/confirm" $stu (J @{kpId=$kp;errorType='HACK'}) 'application/json' 'draft confirm invalid errorType' '40000'
Exec 'E37' 'ErrorRecord' 'PUT' "/api/error/records/$draftLog/confirm" $stu (J @{errorType='CONCEPT_CONFUSION'}) 'application/json' 'draft confirm missing kpId' '40000'
Exec 'E38' 'ErrorRecord' 'PUT' "/api/error/records/$draftLog/confirm" $stu (J @{kpId=$kp;errorType='CONCEPT_CONFUSION';correctedText=('X'*3001)}) 'application/json' 'correctedText 3001 chars' '40000'
Exec 'E39' 'ErrorRecord' 'PUT' "/api/error/records/$draftLog/confirm" $stu (J @{kpId=$kp;errorType='CONCEPT_CONFUSION';correctedText='probe'}) 'application/json' 'draft confirm valid' '0'
Exec 'E40' 'ErrorRecord' 'PUT' "/api/error/records/$otherLog/confirm" $stu (J @{kpId='KP0202';errorType='CONCEPT_CONFUSION'}) 'application/json' 'other student logId' '40300|40400'
Exec 'E41' 'ErrorRecord' 'PUT' '/api/error/records/NO_SUCH_LOG/confirm' $stu (J @{kpId=$kp;errorType='CONCEPT_CONFUSION'}) 'application/json' 'nonexistent logId' '40400'
Exec 'E42' 'ErrorRecord' 'PUT' "/api/error/records/$draftLog/confirm" $stu (J @{kpId=$kp;errorType='CONCEPT_CONFUSION'}) 'application/json' 'repeat confirm after CONFIRMED' '40900'
Exec 'E43' 'ErrorRecord' 'PUT' "/api/error/records/$ownLog/confirm" $stu2 (J @{kpId=$kp;errorType='CONCEPT_CONFUSION'}) 'application/json' 'other user confirm (CONFIRMED row)' '40300|40400'

# ---- F diagnosis ----
Exec 'F01' 'Diagnosis' 'POST' '/api/diagnosis/map' $stu (J @{courseId=1;text='关系模式分解应满足什么条件'}) 'application/json' 'valid map' '0'
Exec 'F02' 'Diagnosis' 'POST' '/api/diagnosis/map' $stu (J @{text='abc'}) 'application/json' 'missing courseId' '40000'
Exec 'F03' 'Diagnosis' 'POST' '/api/diagnosis/map' $stu (J @{courseId=1;text=''}) 'application/json' 'empty text' '40000'
Exec 'F04' 'Diagnosis' 'POST' '/api/diagnosis/map' $stu (J @{courseId=1;text=('X'*3001)}) 'application/json' 'text 3001 chars' '40000'
Exec 'F05' 'Diagnosis' 'POST' '/api/diagnosis/map' $stu (J @{courseId=1;text=$long100000}) 'application/json' 'text 100000 chars' '40000'
Exec 'F06' 'Diagnosis' 'POST' '/api/diagnosis/map' $stu (J @{courseId=1;chapterId=0;text='abc'}) 'application/json' 'chapterId=0' '40000|40400'
Exec 'F07' 'Diagnosis' 'POST' '/api/diagnosis/map' $stu (J @{courseId=1;chapterId=-1;text='abc'}) 'application/json' 'chapterId=-1' '40000|40400'
Exec 'F08' 'Diagnosis' 'POST' '/api/diagnosis/map' $stu '[1,2]' 'application/json' 'body is array' '40000'
Exec 'F09' 'Diagnosis' 'POST' '/api/diagnosis/explain' $stu (J @{logId=$ownLog;kpId=$kp;errorType='CONCEPT_CONFUSION'}) 'application/json' 'valid explain (may fallback L3)' '0'
Exec 'F10' 'Diagnosis' 'POST' '/api/diagnosis/explain' $stu (J @{logId=$ownLog;kpId=$kp;errorType='HACK'}) 'application/json' 'invalid errorType' '40000'
Exec 'F11' 'Diagnosis' 'POST' '/api/diagnosis/explain' $stu (J @{kpId=$kp;errorType='CONCEPT_CONFUSION'}) 'application/json' 'missing logId' '40000'
Exec 'F12' 'Diagnosis' 'POST' '/api/diagnosis/explain' $stu (J @{logId='NO_SUCH_LOG';kpId=$kp;errorType='CONCEPT_CONFUSION'}) 'application/json' 'nonexistent logId' '40400'
Exec 'F13' 'Diagnosis' 'POST' '/api/diagnosis/explain' $stu (J @{logId=$otherLog;kpId=$kp;errorType='CONCEPT_CONFUSION'}) 'application/json' 'other student logId' '40300|40400'
Exec 'F17' 'Diagnosis' 'POST' '/api/diagnosis/explain' $stu (J @{logId=$ownLog;kpId=$kp;errorType='CONCEPT_CONFUSION';questionText=('Q'*2001)}) 'application/json' 'questionText 2001 chars' '40000'
Exec 'F18' 'Diagnosis' 'POST' '/api/diagnosis/explain' $stu (J @{logId=$ownLog;kpId='KP0202';errorType='CONCEPT_CONFUSION'}) 'application/json' 'kpId inconsistent with record' '40000'
Exec 'F14' 'Diagnosis' 'GET' "/api/diagnosis/explain/$ownExplain" $stu $null '' 'own explainId' '0'
Exec 'F15' 'Diagnosis' 'GET' "/api/diagnosis/explain/$otherExplain" $stu $null '' 'other student explainId' '40300|40400'
Exec 'F16' 'Diagnosis' 'GET' '/api/diagnosis/explain/NO_SUCH_EXPLAIN' $stu $null '' 'nonexistent explainId' '40400'

# ---- G recommendations ----
Exec 'G01' 'Recommendation' 'GET' "/api/recommendations?kpId=$kp" $stu $null '' 'valid kpId' '0'
Exec 'G02' 'Recommendation' 'GET' "/api/recommendations?logId=$ownLog" $stu $null '' 'valid own logId' '0'
Exec 'G03' 'Recommendation' 'GET' '/api/recommendations' $stu $null '' 'neither logId nor kpId' '40000'
Exec 'G04' 'Recommendation' 'GET' "/api/recommendations?logId=$otherLog" $stu $null '' 'other student logId' '40300|40400'
Exec 'G05' 'Recommendation' 'GET' '/api/recommendations?logId=NO_SUCH_LOG' $stu $null '' 'nonexistent logId' '40400'
Exec 'G06' 'Recommendation' 'GET' '/api/recommendations?kpId=KP0201&limit=abc' $stu $null '' 'undocumented limit param (ignored)' 'ANY'
Exec 'G07' 'Recommendation' 'GET' "/api/recommendations?logId=$ownLog&courseId=999" $stu $null '' 'courseId inconsistent with log' '40000'
Exec 'G08' 'Recommendation' 'GET' "/api/recommendations?logId=$ownLog&kpId=$kp" $stu $null '' 'both logId and kpId' '0|40000'

# ---- H practice ----
Exec 'H01' 'Practice' 'POST' '/api/practices/start' $stu (J @{qId=$q;clientToken='probe-h01-20261104'}) 'application/json' 'valid start' '0'
Exec 'H02' 'Practice' 'POST' '/api/practices/start' $stu (J @{clientToken='probe-h02-20261104'}) 'application/json' 'missing qId' '40000'
Exec 'H03' 'Practice' 'POST' '/api/practices/start' $stu (J @{qId='NO_SUCH_Q';clientToken='probe-h03-20261104'}) 'application/json' 'nonexistent qId' '40400'
Exec 'H04' 'Practice' 'POST' '/api/practices/start' $stu (J @{qId=$q;clientToken=' '}) 'application/json' 'blank clientToken' '40000'
Exec 'H05' 'Practice' 'POST' '/api/practices' $stu (J @{qId=$q;kpId='KP0101';myAnswer='A';isCorrect=$true;clientToken='probe-h05-20261104';source='SELF'}) 'application/json' 'valid submit' '0'
Exec 'H06' 'Practice' 'POST' '/api/practices' $stu (J @{kpId='KP0101';clientToken='probe-h06-20261104'}) 'application/json' 'missing qId' '40000'
Exec 'H07' 'Practice' 'POST' '/api/practices' $stu (J @{qId='NO_SUCH_Q';kpId='KP0101';clientToken='probe-h07-20261104'}) 'application/json' 'nonexistent qId' '40400'
Exec 'H08' 'Practice' 'POST' '/api/practices' $stu (J @{qId=$q;kpId=$kp;myAnswer='A';isCorrect=$true;clientToken='probe-h08-20261104'}) 'application/json' 'kpId mismatch course' '40000|40400'
Exec 'H09' 'Practice' 'POST' '/api/practices' $stu (J @{qId=$q;kpId='KP0101';myAnswer='A';isCorrect=$true;costSeconds=-1;clientToken='probe-h09-20261104'}) 'application/json' 'costSeconds=-1' '40000'
Exec 'H10' 'Practice' 'POST' '/api/practices' $stu (J @{qId=$q;kpId='KP0101';myAnswer='A';isCorrect=$true;costSeconds=86401;clientToken='probe-h10-20261104'}) 'application/json' 'costSeconds=86401' '40000'
Exec 'H11' 'Practice' 'POST' '/api/practices' $stu (J @{qId=$q;kpId='KP0101';myAnswer='A';isCorrect=$true;source='HACK';clientToken='probe-h11-20261104'}) 'application/json' 'source=HACK' '40000'
Exec 'H12' 'Practice' 'POST' '/api/practices' $stu (J @{qId=$q;kpId='KP0101';myAnswer='A';isCorrect=$true;errorType='HACK';clientToken='probe-h12-20261104'}) 'application/json' 'errorType=HACK' '40000'
Exec 'H13' 'Practice' 'POST' '/api/practices' $stu (J @{qId=$q;kpId='KP0101';myAnswer=('X'*20001);isCorrect=$true;clientToken='probe-h13-20261104'}) 'application/json' 'myAnswer 20001 chars' '40000'
Exec 'H14' 'Practice' 'POST' '/api/practices' $stu (J @{qId='Q_0103';kpId='KP0101';myAnswer='abc';clientToken='probe-h14-20261104'}) 'application/json' 'subjective without isCorrect' '40000'
Exec 'H15' 'Practice' 'POST' '/api/practices' $stu (J @{qId='Q_0102';kpId='KP0101';myAnswer='A';isCorrect=$true;clientToken='probe-h15-20261104'}) 'application/json' 'objective wrong answer' '0'
Exec 'H16' 'Practice' 'POST' '/api/practices' $stu (J @{qId=$q;kpId='KP0101';myAnswer='A';isCorrect=$true;clientToken=$long100000}) 'application/json' 'clientToken 100000 chars' '40000'

# ---- I portraits ----
Exec 'I01' 'Portrait' 'GET' '/api/portraits/me' $stu $null '' 'student self' '0'
Exec 'I02' 'Portrait' 'GET' '/api/portraits/me' $tea $null '' 'teacher self' '0'
Exec 'I03' 'Portrait' 'GET' '/api/portraits/class/1' $tea $null '' 'teacher class' '0'
Exec 'I04' 'Portrait' 'GET' '/api/portraits/class/1' $stu $null '' 'student class (role)' '40300'
Exec 'I05' 'Portrait' 'GET' '/api/portraits/class/0' $tea $null '' 'courseId=0' '40400'
Exec 'I06' 'Portrait' 'GET' '/api/portraits/class/abc' $tea $null '' 'courseId=abc' '40000'
Exec 'I07' 'Portrait' 'GET' '/api/portraits/class/999' $tea $null '' 'nonexistent course' '40400'

# ---- J plans ----
Exec 'J01' 'Plan' 'GET' '/api/plans' $stu $null '' 'valid' '0'
Exec 'J02' 'Plan' 'GET' '/api/plans?from=2026-13-45' $stu $null '' 'invalid month' '40000'
Exec 'J03' 'Plan' 'GET' '/api/plans?from=2026-11-30&to=2026-11-01' $stu $null '' 'from > to' '40000'
Exec 'J04' 'Plan' 'GET' '/api/plans?courseId=0' $stu $null '' 'courseId=0' '40000|40400'
Exec 'J05' 'Plan' 'GET' '/api/plans/due-reviews?courseId=1' $stu $null '' 'valid' '0'
Exec 'J05b' 'Plan' 'GET' '/api/plans/due-reviews' $stu $null '' 'missing courseId' '40000'
Exec 'J06' 'Plan' 'POST' '/api/plans/generate' $stu (J @{courseId=1;weekStart='2026-11-16';dailyQuestionCount=3}) 'application/json' 'valid generate' '0|40900'
Exec 'J07' 'Plan' 'POST' '/api/plans/generate' $stu (J @{courseId=1;dailyQuestionCount=3}) 'application/json' 'missing weekStart' '40000'
Exec 'J08' 'Plan' 'POST' '/api/plans/generate' $stu (J @{courseId=1;weekStart='2026-02-30'}) 'application/json' 'invalid date' '40000'
Exec 'J09' 'Plan' 'POST' '/api/plans/generate' $stu (J @{courseId=1;weekStart='2026-11-09';dailyQuestionCount=2}) 'application/json' 'dailyQuestionCount=2' '40000'
Exec 'J10' 'Plan' 'POST' '/api/plans/generate' $stu (J @{courseId=1;weekStart='2026-11-09';dailyQuestionCount=6}) 'application/json' 'dailyQuestionCount=6' '40000'
Exec 'J11' 'Plan' 'POST' '/api/plans/generate' $stu (J @{courseId=999;weekStart='2026-11-09'}) 'application/json' 'nonexistent course' '40400'
Exec 'J12' 'Plan' 'POST' '/api/plans/4/checkin' $stu (J @{qId='Q_0201';clientToken='probe-j12-20261104';answered=$false}) 'application/json' 'own plan already done' '0|40900'
Exec 'J13' 'Plan' 'POST' '/api/plans/2/checkin' $stu (J @{qId='Q_0201';clientToken='probe-j13-20261104';answered=$false}) 'application/json' 'other student plan' '40300|40400'
Exec 'J14' 'Plan' 'POST' '/api/plans/999999/checkin' $stu (J @{qId='Q_0201';clientToken='probe-j14-20261104';answered=$false}) 'application/json' 'nonexistent plan' '40400'
Exec 'J15' 'Plan' 'POST' '/api/plans/4/checkin' $stu (J @{qId='Q_0201';clientToken='probe-j15-20261104';answered=$false;myAnswer='abc'}) 'application/json' 'answered=false with myAnswer' '40000'
Exec 'J16' 'Plan' 'POST' '/api/plans/4/checkin' $stu (J @{qId='Q_0201';clientToken='probe-j16-20261104'}) 'application/json' 'missing answered' '40000'
Exec 'J17' 'Plan' 'POST' '/api/plans/4/checkin' $stu (J @{qId='Q_9999';clientToken='probe-j17-20261104';answered=$false}) 'application/json' 'qId not in plan' '40000|40400'
Exec 'J18' 'Plan' 'POST' '/api/plans/4/checkin' $stu (J @{qId='Q_0201';clientToken='probe-j18-20261104';answered=$true;costSeconds=-1}) 'application/json' 'costSeconds=-1' '40000'
Exec 'J19' 'Plan' 'POST' '/api/plans/4/checkin' $stu (J @{qId='Q_0201';clientToken='probe-j19-20261104';answered=$true;errorType='HACK'}) 'application/json' 'errorType=HACK' '40000'
Exec 'J20' 'Plan' 'POST' '/api/plans/4/checkin' $stu (J @{qId='Q_0201';clientToken=$long100000;answered=$false}) 'application/json' 'clientToken 100000 chars' '40000'

# ---- K teacher ----
Exec 'K01' 'Teacher' 'GET' '/api/teacher/reviews?page=1&size=10' $tea $null '' 'valid queue' '0'
# 评阅接口的路径参数是 ai_generation_log 的数值主键 logPk（不是错题 logId）。
# 早先把 EL_DEMO_9002_1 直接塞进去，服务端按 long 转换失败返回 40000，用例被误记为缺陷。
$k01 = Get-Content (Join-Path $outDir 'K01.txt') -Raw | ConvertFrom-Json
$logPk = $k01.data.records[0].logPk
if ($null -eq $logPk) { Write-Host 'WARN: K01 未取到 logPk，评阅用例退化为 40400' ; $logPk = 999999999 }
Exec 'K02' 'Teacher' 'GET' '/api/teacher/reviews' $stu $null '' 'student token' '40300'
Exec 'K03' 'Teacher' 'GET' '/api/teacher/reviews' '' $null '' 'no token' '40100'
Exec 'K04' 'Teacher' 'GET' '/api/teacher/reviews?page=-1' $tea $null '' 'page=-1' '40000'
Exec 'K05' 'Teacher' 'GET' '/api/teacher/reviews?size=100000' $tea $null '' 'size=100000' '40000'
Exec 'K06' 'Teacher' 'GET' '/api/teacher/reviews?status=HACK' $tea $null '' 'status=HACK' '40000'
Exec 'K07' 'Teacher' 'GET' '/api/teacher/reviews?from=2026-13-45' $tea $null '' 'invalid date' '40000'
Exec 'K08' 'Teacher' 'GET' '/api/teacher/reviews?from=2026-11-30&to=2026-11-01' $tea $null '' 'from > to' '0|40000'
Exec 'K09' 'Teacher' 'GET' '/api/teacher/reviews/export' $tea $null '' 'valid export (CSV)' 'ANY'
Exec 'K10' 'Teacher' 'GET' '/api/teacher/reviews/export' $stu $null '' 'student export' '40300'
# 非法请求体先跑，避免 K11 成功评阅后状态变为已评阅、后续被 40900 掩盖
Exec 'K12' 'Teacher' 'POST' "/api/teacher/reviews/$logPk" $tea (J @{score=0;comment='probe'}) 'application/json' 'score=0' '40000'
Exec 'K12b' 'Teacher' 'POST' "/api/teacher/reviews/$logPk" $tea (J @{score=4;comment=('X'*501)}) 'application/json' 'comment 501 chars' '40000'
Exec 'K12c' 'Teacher' 'POST' "/api/teacher/reviews/$logPk" $tea (J @{score=4;comment='';result='RETURN'}) 'application/json' 'RETURN with empty comment' '40000'
Exec 'K13' 'Teacher' 'POST' "/api/teacher/reviews/$logPk" $stu (J @{score=4;comment='probe'}) 'application/json' 'student submit review (valid body)' '40300'
Exec 'K13b' 'Teacher' 'POST' '/api/teacher/reviews/NO_SUCH_LOG' $tea (J @{score=4;comment='probe'}) 'application/json' 'nonexistent logPk' '40400'
Exec 'K11' 'Teacher' 'POST' "/api/teacher/reviews/$logPk" $tea (J @{score=4;comment='probe';result='PASS'}) 'application/json' 'teacher submit review' '0|40300|40900'
Exec 'K14' 'Teacher' 'GET' '/api/teacher/students?courseId=1' $tea $null '' 'valid students' '0'
Exec 'K15' 'Teacher' 'GET' '/api/teacher/students?courseId=1' $stu $null '' 'student token' '40300'
Exec 'K16' 'Teacher' 'GET' '/api/teacher/students?courseId=0' $tea $null '' 'courseId=0' '40000'
Exec 'K17' 'Teacher' 'GET' '/api/teacher/students?courseId=1&sort=HACK' $tea $null '' 'sort=HACK' '40000'
Exec 'K18' 'Teacher' 'GET' '/api/teacher/students?courseId=1&page=0' $tea $null '' 'page=0' '40000'
Exec 'K19' 'Teacher' 'GET' '/api/teacher/students?courseId=1&page=2147483647&size=50' $tea $null '' 'page=Integer.MAX' '0'
Exec 'K20' 'Teacher' 'GET' '/api/teacher/students/export?courseId=1' $tea $null '' 'valid export (CSV)' 'ANY'
Exec 'K21' 'Teacher' 'GET' '/api/teacher/students/9002/learning-records?courseId=1' $tea $null '' 'valid student detail' '0'
Exec 'K22' 'Teacher' 'GET' '/api/teacher/students/0/learning-records?courseId=1' $tea $null '' 'userId=0' '40400'
Exec 'K23' 'Teacher' 'GET' '/api/teacher/students/999999/learning-records?courseId=1' $tea $null '' 'nonexistent userId' '40400'
Exec 'K24' 'Teacher' 'GET' '/api/teacher/students/9002/learning-records?courseId=1' $stu $null '' 'student token' '40300'
Exec 'K25' 'Teacher' 'GET' '/api/teacher/students/abc/learning-records?courseId=1' $tea $null '' 'non-numeric userId' '40000'
Exec 'K26' 'Teacher' 'GET' '/api/teacher/reviews?status=all' $tea $null '' 'lowercase status=all' '0'

# ---- L system / M container ----
Exec 'L01' 'System' 'GET' '/api/system/health' '' $null '' 'no token' '0'
Exec 'L02' 'System' 'GET' '/api/system/health' $stu $null '' 'with token' '0'
Exec 'L03' 'System' 'DELETE' '/api/system/health' '' $null '' 'DELETE on GET-only endpoint' 'ENVELOPE'
Exec 'M01' 'Container' 'GET' '/api/no-such-path' $stu $null '' 'unknown path' '40400'
Exec 'M02' 'Container' 'GET' '/api/questions?kpId=%00' $stu $null '' 'NUL byte in query' 'ENVELOPE'
Exec 'M03' 'Container' 'GET' ("/api/questions?kpId=" + $long100000) $stu $null '' '100000-char query value' 'ENVELOPE'
Exec 'M04' 'Container' 'GET' '/api/questions?kpId=KP0201' ('X' * 100000) $null '' 'satoken header 100000 chars' 'ENVELOPE'
Exec 'M05' 'Container' 'GET' '/api/plans?from=2026-11-04T23:59:59Z' $stu $null '' 'datetime literal for LocalDate' '40000'
Exec 'M06' 'Container' 'GET' '/api/plans?from=2026-02-30' $stu $null '' '2026-02-30' '40000'

# ---- S 鉴权全覆盖：35 个 operation 无 token 调用 ----
Exec 'S01' 'AuthSweep' 'POST' '/api/auth/login' '' (J @{username='student01';password=$Password}) 'application/json' 'public login' '0'
Exec 'S02' 'AuthSweep' 'POST' '/api/auth/logout' '' $null '' 'logout without token' '40100'
Exec 'S03' 'AuthSweep' 'GET' '/api/auth/me' '' $null '' 'me without token' '40100'
Exec 'S04' 'AuthSweep' 'GET' '/api/courses' '' $null '' 'courses without token' '40100'
Exec 'S05' 'AuthSweep' 'GET' '/api/courses/1/chapters' '' $null '' 'chapters without token' '40100'
Exec 'S06' 'AuthSweep' 'GET' '/api/courses/1/knowledge-graph' '' $null '' 'graph without token' '40100'
Exec 'S07' 'AuthSweep' 'GET' '/api/course/kp/KP0201' '' $null '' 'kp without token' '40100'
Exec 'S08' 'AuthSweep' 'GET' '/api/questions?kpId=KP0201' '' $null '' 'questions without token' '40100'
Exec 'S09' 'AuthSweep' 'GET' '/api/questions/Q_0101' '' $null '' 'question detail without token' '40100'
Exec 'S10' 'AuthSweep' 'GET' '/api/questions/Q_0101/answer' '' $null '' 'answer without token' '40100'
Exec 'S11' 'AuthSweep' 'POST' '/api/error/upload' '' $null '' 'upload without token' '40100'
Exec 'S12' 'AuthSweep' 'GET' '/api/error/tasks/NO_SUCH_TASK' '' $null '' 'task without token' '40100'
Exec 'S13' 'AuthSweep' 'POST' '/api/error/records' '' (J @{courseId=1;clientToken='sweep-s13'}) 'application/json' 'create record without token' '40100'
Exec 'S14' 'AuthSweep' 'GET' '/api/error/records' '' $null '' 'records page without token' '40100'
Exec 'S15' 'AuthSweep' 'GET' '/api/error/records/EL_DEMO_9001_1' '' $null '' 'record detail without token' '40100'
Exec 'S16' 'AuthSweep' 'PUT' '/api/error/records/EL_DEMO_9001_1/confirm' '' (J @{kpId='KP0201';errorType='CONCEPT_CONFUSION'}) 'application/json' 'confirm without token' '40100'
Exec 'S17' 'AuthSweep' 'POST' '/api/diagnosis/map' '' (J @{courseId=1;text='abc'}) 'application/json' 'map without token' '40100'
Exec 'S18' 'AuthSweep' 'POST' '/api/diagnosis/explain' '' (J @{logId='EL_DEMO_9001_1';kpId='KP0201';errorType='CONCEPT_CONFUSION'}) 'application/json' 'explain without token' '40100'
Exec 'S19' 'AuthSweep' 'GET' '/api/diagnosis/explain/DEMO_EX_EL_DEMO_9001_3' '' $null '' 'explain detail without token' '40100'
Exec 'S20' 'AuthSweep' 'GET' '/api/recommendations?kpId=KP0201' '' $null '' 'recommendations without token' '40100'
Exec 'S21' 'AuthSweep' 'POST' '/api/practices/start' '' (J @{qId='Q_0101';clientToken='sweep-s21'}) 'application/json' 'practice start without token' '40100'
Exec 'S22' 'AuthSweep' 'POST' '/api/practices' '' (J @{qId='Q_0101';kpId='KP0101';myAnswer='A';isCorrect=$true;clientToken='sweep-s22'}) 'application/json' 'practice submit without token' '40100'
Exec 'S23' 'AuthSweep' 'GET' '/api/portraits/me' '' $null '' 'portrait me without token' '40100'
Exec 'S24' 'AuthSweep' 'GET' '/api/portraits/class/1' '' $null '' 'class portrait without token' '40100'
Exec 'S25' 'AuthSweep' 'GET' '/api/plans' '' $null '' 'plans without token' '40100'
Exec 'S26' 'AuthSweep' 'GET' '/api/plans/due-reviews?courseId=1' '' $null '' 'due reviews without token' '40100'
Exec 'S27' 'AuthSweep' 'POST' '/api/plans/generate' '' (J @{courseId=1;weekStart='2026-11-16'}) 'application/json' 'plan generate without token' '40100'
Exec 'S28' 'AuthSweep' 'POST' '/api/plans/4/checkin' '' (J @{qId='Q_0201';clientToken='sweep-s28';answered=$false}) 'application/json' 'checkin without token' '40100'
Exec 'S29' 'AuthSweep' 'GET' '/api/teacher/reviews' '' $null '' 'teacher reviews without token' '40100'
Exec 'S30' 'AuthSweep' 'GET' '/api/teacher/reviews/export' '' $null '' 'teacher export without token' '40100'
Exec 'S31' 'AuthSweep' 'POST' '/api/teacher/reviews/EL_DEMO_9002_1' '' (J @{score=4;comment='probe'}) 'application/json' 'teacher submit without token' '40100'
Exec 'S32' 'AuthSweep' 'GET' '/api/teacher/students?courseId=1' '' $null '' 'teacher students without token' '40100'
Exec 'S33' 'AuthSweep' 'GET' '/api/teacher/students/export?courseId=1' '' $null '' 'teacher students export without token' '40100'
Exec 'S34' 'AuthSweep' 'GET' '/api/teacher/students/9002/learning-records?courseId=1' '' $null '' 'learning records without token' '40100'
Exec 'S35' 'AuthSweep' 'GET' '/api/system/health' '' $null '' 'public health' '0'

# ---- T 补充边界 ----
Exec 'T01' 'Extra' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken=$long100000}) 'application/json' 'clientToken 100000 chars' '40000'
Exec 'T02' 'Extra' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken="probe-t02-$runId";kpId='NO_SUCH_KP'}) 'application/json' 'nonexistent kpId' '40400'
Exec 'T03' 'Extra' 'POST' '/api/plans/generate' $stu (J @{courseId=1;weekStart=@('2026-11-16')}) 'application/json' 'weekStart as array' '40000'
Exec 'T04' 'Extra' 'GET' "/api/questions?kpId=$kp&limit=1.5" $stu $null '' 'limit=1.5' '40000'
Exec 'T05' 'Extra' 'GET' '/api/error/records?page=1.5' $stu $null '' 'page=1.5' '40000'
Exec 'T06' 'Extra' 'POST' '/api/auth/login' '' (J @{username='demo_stu01';password=$long100000}) 'application/json' 'password 100000 chars' '40000'
Exec 'T07' 'Extra' 'GET' '/api/teacher/reviews/abc' $tea $null '' 'non-numeric logPk' '40000'
Exec 'T08' 'Extra' 'GET' '/api/error/records?status=confirmed' $stu $null '' 'lowercase status=confirmed' '40000'
Exec 'T09' 'Extra' 'GET' "/api/recommendations?logId=$ownLog&courseId=1" $stu $null '' 'consistent courseId' '0'
Exec 'T10' 'Extra' 'GET' '/api/teacher/reviews?page=2147483647&size=50' $tea $null '' 'page=Integer.MAX' '0'
Exec 'T11' 'Extra' 'POST' '/api/plans/generate' $stu (J @{courseId=-1;weekStart='2026-11-16'}) 'application/json' 'courseId=-1' '40400'
Exec 'T12' 'Extra' 'POST' '/api/practices/start' $stu (J @{qId='';clientToken="probe-t12-$runId"}) 'application/json' 'empty qId' '40000'
Exec 'T13' 'Extra' 'GET' '/api/error/records?page=1&size=50&errorType=HACK' $stu $null '' 'errorType=HACK filter' '40000'
Exec 'T14' 'Extra' 'POST' '/api/error/records' $stu (J @{courseId=1;clientToken="probe-t14-$runId";clientToken2='x'}) 'application/json' 'duplicate/extra field' '0'

$csv = Join-Path $OutDir 'probe-matrix.csv'
$rows | Export-Csv -Path $csv -NoTypeInformation -Encoding utf8
$md = Join-Path $OutDir 'probe-matrix.md'
$lines = @('| ID | 分组 | 方法 | 端点 | 输入 | 期望 | HTTP | code | 判定 | 泄露 | 耗时ms | 响应摘要 |', '| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |')
foreach ($r in $rows) {
    $resp = ($r.Resp -replace '\|', '/')
    $lines += "| $($r.ID) | $($r.Group) | $($r.Method) | $($r.Endpoint) | $($r.Input -replace '\|','/') | $($r.Expect) | $($r.HTTP) | $($r.Code) | $($r.Verdict) | $($r.Leak) | $($r.Ms) | $resp |"
}
Set-Content -Path $md -Value ($lines -join "`n") -Encoding utf8

$total = $rows.Count
$byVerdict = $rows | Group-Object Verdict | ForEach-Object { "$($_.Name)=$($_.Count)" }
Write-Host "TOTAL=$total"
Write-Host ($byVerdict -join '; ')
Write-Host "report=$md"
