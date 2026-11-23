$ErrorActionPreference = 'Stop'
$base = 'C:\GSMS'

# 1. 启动脚本（纯 ASCII，避免编码问题）
$backendBat = @'
@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_231"
cd /d C:\GSMS\backend
"%JAVA_HOME%\bin\java.exe" -Xms512m -Xmx1024m -Dfile.encoding=UTF-8 -jar gsms-backend.jar --spring.profiles.active=local --server.port=8089 >> C:\GSMS\logs\backend.log 2>&1
'@
Set-Content -Path "$base\start-backend.bat" -Value $backendBat -Encoding ASCII

$nginxBat = @'
@echo off
cd /d C:\GSMS\nginx
tasklist /FI "IMAGENAME eq nginx.exe" | find /I "nginx.exe" >nul && exit /b 0
start "" nginx.exe
'@
Set-Content -Path "$base\start-nginx.bat" -Value $nginxBat -Encoding ASCII

$stopBat = @'
@echo off
cd /d C:\GSMS\nginx
nginx.exe -s quit >nul 2>&1
for /f "tokens=2" %%i in ('tasklist /FI "IMAGENAME eq java.exe" /FO LIST ^| find "PID:"') do (
  wmic process where "ProcessId=%%i and CommandLine like '%%gsms-backend.jar%%'" delete >nul 2>&1
)
'@
Set-Content -Path "$base\stop-all.bat" -Value $stopBat -Encoding ASCII

# 2. 计划任务：开机自启 + 立即运行
schtasks /create /tn "GSMS-Backend" /tr "cmd /c C:\GSMS\start-backend.bat" /sc onstart /ru SYSTEM /rl HIGHEST /f | Out-Null
schtasks /create /tn "GSMS-Nginx" /tr "cmd /c C:\GSMS\start-nginx.bat" /sc onstart /ru SYSTEM /rl HIGHEST /f | Out-Null
schtasks /run /tn "GSMS-Backend" | Out-Null
schtasks /run /tn "GSMS-Nginx" | Out-Null

Start-Sleep -Seconds 3

# 3. 防火墙放行 8081
$rule = Get-NetFirewallRule -DisplayName 'GSMS-8081' -ErrorAction SilentlyContinue
if (-not $rule) {
    New-NetFirewallRule -DisplayName 'GSMS-8081' -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow -Profile Any | Out-Null
    'firewall=added'
} else { 'firewall=exists' }

"tasks:"
schtasks /query /tn "GSMS-Backend" /fo LIST | Select-String 'TaskName|Status'
schtasks /query /tn "GSMS-Nginx" /fo LIST | Select-String 'TaskName|Status'
