$ErrorActionPreference = 'Stop'
$base = 'C:\GSMS'

# 1) 附件解压（覆盖同名，不删除目标已有文件）
if (Test-Path "$base\sql\files.zip") {
    Expand-Archive "$base\sql\files.zip" -DestinationPath "$base\data\files" -Force
}
"files_count=$((Get-ChildItem "$base\data\files" -Recurse -File | Measure-Object).Count)"
"files_size=$((Get-ChildItem "$base\data\files" -Recurse -File | Measure-Object -Property Length -Sum).Sum)"

# 2) 启动脚本：补 STORAGE_ROOT / STORAGE_TEMP（服务器无 E 盘，默认值会写入失败）
$backendBat = @'
@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_231"
set "STORAGE_ROOT=C:/GSMS/data/files"
set "STORAGE_TEMP=C:/GSMS/data/tmp"
cd /d C:\GSMS\backend
"%JAVA_HOME%\bin\java.exe" -Xms512m -Xmx1024m -Dfile.encoding=UTF-8 -jar gsms-backend.jar --spring.profiles.active=local --server.port=8089 >> C:\GSMS\logs\backend.log 2>&1
'@
Set-Content -Path "$base\start-backend.bat" -Value $backendBat -Encoding ASCII
"bat_updated=" + (Select-String -Path "$base\start-backend.bat" -Pattern 'STORAGE_ROOT' -Quiet)

# 3) 启动并等待健康
schtasks /run /tn "GSMS-Backend" | Out-Null
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $h = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 'http://127.0.0.1:8089/actuator/health'
        if ($h.StatusCode -eq 200) { $ok = $true; "health=" + ($h.Content); break }
    } catch { }
}
"health_ok=$ok wait_seconds=$($i * 2)"

# 4) 通过 nginx 端口验证
try { "web8081=" + (Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 'http://127.0.0.1:8081/').StatusCode } catch { "web8081_err=" + $_.Exception.Message }
try {
    $body = '{"userId":"admin","password":"' + $env:GSMS_PASSWORD + '"}'
    $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri 'http://127.0.0.1:8081/api/v1/auth/login' -Method POST -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($body))
    "login=" + $r.StatusCode
} catch { "login_err=" + $_.Exception.Message }
