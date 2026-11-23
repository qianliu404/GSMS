$ErrorActionPreference = 'Continue'
$svc = 'GSMS-Backend'
$key = "HKLM:\SYSTEM\CurrentControlSet\Services\$svc\Parameters"
$log = 'C:\GSMS\logs\backend.log'

sc.exe stop $svc | Out-Null
Start-Sleep -Seconds 3
foreach ($p in @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' })) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2

# 补齐 stdout/stderr 重定向（registry 直接写，避免 nssm set 长参数被吞）
Set-ItemProperty -Path $key -Name 'AppStdout' -Value $log -Type String
Set-ItemProperty -Path $key -Name 'AppStderr' -Value $log -Type String
Set-ItemProperty -Path $key -Name 'AppStdoutCreationDisposition' -Value 1 -Type DWord
Set-ItemProperty -Path $key -Name 'AppStderrCreationDisposition' -Value 1 -Type DWord

# 脱敏后的 application-local.yml 要求 DB_PASSWORD 环境变量
Set-ItemProperty -Path $key -Name 'AppEnvironmentExtra' -Value @(
    'JAVA_HOME=C:\Program Files\Java\jdk1.8.0_231',
    'STORAGE_ROOT=C:/GSMS/data/files',
    'STORAGE_TEMP=C:/GSMS/data/tmp',
    'DB_USER=case_app',
    'DB_PASSWORD=GsmsApp@2026'
) -Type MultiString

"stdout=" + (Get-ItemProperty $key).AppStdout
"env=" + ((Get-ItemProperty $key).AppEnvironmentExtra -join ' | ')

sc.exe start $svc | Out-Null
$ok = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $h = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 'http://127.0.0.1:8089/actuator/health'
        if ($h.StatusCode -eq 200) { $ok = $true; break }
    } catch { }
}
"health_ok=$ok wait=$($i*2)s status=$((Get-Service $svc).Status)"
if (-not $ok) { 'log_tail:'; Get-Content $log -Tail 15 }
