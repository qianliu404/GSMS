$ErrorActionPreference = 'Continue'
$base  = 'C:\GSMS'
$nssm  = 'C:\partsportal\deploy\tools\nssm.exe'
$java  = 'C:\Program Files\Java\jdk1.8.0_231\bin\java.exe'
$svc   = 'GSMS-Backend'

if (-not (Test-Path $nssm)) { throw "nssm not found: $nssm" }
if (-not (Test-Path $java)) { throw "java not found: $java" }

# 1) 停用并删除原计划任务
schtasks /end /tn "GSMS-Backend" | Out-Null
$procs = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' })
foreach ($p in $procs) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 3
"task_stopped_left=$(@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' }).Count)"

schtasks /delete /tn "GSMS-Backend" /f 2>&1 | Out-Null
"task_deleted=$(-not [bool](schtasks /query /tn 'GSMS-Backend' 2>$null))"

# 2) 清理同名旧服务（若存在）
if (Get-Service $svc -ErrorAction SilentlyContinue) {
    & $nssm stop $svc | Out-Null
    Start-Sleep -Seconds 2
    & $nssm remove $svc confirm | Out-Null
    Start-Sleep -Seconds 2
}

# 3) 注册服务
& $nssm install $svc $java | Out-Null
& $nssm set $svc AppParameters '-Xms512m -Xmx1024m -Dfile.encoding=UTF-8 -jar C:\GSMS\backend\gsms-backend.jar --spring.profiles.active=local --server.port=8089' | Out-Null
& $nssm set $svc AppDirectory "$base\backend" | Out-Null
& $nssm set $svc DisplayName 'GSMS Backend' | Out-Null
& $nssm set $svc Description 'GSMS case management backend (Spring Boot, port 8089)' | Out-Null
& $nssm set $svc Start SERVICE_AUTO_START | Out-Null
& $nssm set $svc ObjectName LocalSystem | Out-Null

# 环境变量：附件根目录（服务器无 E 盘，默认 E:/GSMS 会写失败）
& $nssm set $svc AppEnvironmentExtra "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_231" "STORAGE_ROOT=C:/GSMS/data/files" "STORAGE_TEMP=C:/GSMS/data/tmp" | Out-Null

# 日志：追加写 + 10MB 自动轮转
& $nssm set $svc AppStdout "$base\logs\backend.log" | Out-Null
& $nssm set $svc AppStderr "$base\logs\backend.log" | Out-Null
& $nssm set $svc AppStdoutCreationDisposition 2 | Out-Null
& $nssm set $svc AppStderrCreationDisposition 2 | Out-Null
& $nssm set $svc AppRotateFiles 1 | Out-Null
& $nssm set $svc AppRotateOnline 1 | Out-Null
& $nssm set $svc AppRotateBytes 10485760 | Out-Null

# 退出即重启，5 秒后重试；停止时先 CtrlC 再强杀
& $nssm set $svc AppExit Default Restart | Out-Null
& $nssm set $svc AppRestartDelay 5000 | Out-Null
& $nssm set $svc AppStopMethodConsole 15000 | Out-Null
& $nssm set $svc AppThrottle 5000 | Out-Null

# 4) 启动
Start-Service $svc
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $h = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 'http://127.0.0.1:8089/actuator/health'
        if ($h.StatusCode -eq 200) { $ok = $true; break }
    } catch { }
}
"health_ok=$ok wait=$($i*2)s"
Get-Service $svc | Select-Object Name,Status,StartType | Format-Table -AutoSize | Out-String

# 5) 启停包装脚本改用服务方式
$startAll = @'
@echo off
sc start GSMS-Backend >nul 2>&1
sc start GSMS-Nginx >nul 2>&1
echo GSMS services started
'@
$stopAll = @'
@echo off
sc stop GSMS-Nginx >nul 2>&1
sc stop GSMS-Backend >nul 2>&1
echo GSMS services stopped
'@
Set-Content -Path "$base\start-all.bat" -Value $startAll -Encoding ASCII
Set-Content -Path "$base\stop-all.bat"  -Value $stopAll  -Encoding ASCII
Remove-Item "$base\start-backend.bat", "$base\start-nginx.bat" -Force -ErrorAction SilentlyContinue
"bat_updated=" + ((Select-String -Path "$base\start-all.bat" -Pattern 'sc start').Count -gt 0)
