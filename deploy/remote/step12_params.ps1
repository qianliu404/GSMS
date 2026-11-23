$ErrorActionPreference = 'Continue'
$svc = 'GSMS-Backend'
$key = "HKLM:\SYSTEM\CurrentControlSet\Services\$svc\Parameters"
$log = 'C:\GSMS\logs\backend.log'

$params = '-Xms512m -Xmx1024m -Dfile.encoding=UTF-8 ' +
          '-Dgsms.storage.root=C:/GSMS/data/files -Dgsms.storage.temp=C:/GSMS/data/tmp ' +
          '-jar C:\GSMS\backend\gsms-backend.jar --spring.profiles.active=local --server.port=8089'

sc.exe stop $svc | Out-Null
Start-Sleep -Seconds 3
foreach ($p in @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' })) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2

Set-ItemProperty -Path $key -Name 'AppParameters' -Value $params -Type String
Set-ItemProperty -Path $key -Name 'AppEnvironmentExtra' -Value @(
    'JAVA_HOME=C:\Program Files\Java\jdk1.8.0_231',
    'STORAGE_ROOT=C:/GSMS/data/files',
    'STORAGE_TEMP=C:/GSMS/data/tmp'
) -Type MultiString
"params=" + (Get-ItemProperty $key).AppParameters
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
"health_ok=$ok wait=$($i*2)s status=$((Get-Service $svc).Status) java=$(@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' }).Count)"
if (-not $ok) { '--- log tail ---'; Get-Content $log -Tail 15 -ErrorAction SilentlyContinue }
