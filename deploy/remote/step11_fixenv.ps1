$ErrorActionPreference = 'Continue'
$svc = 'GSMS-Backend'
$nssm = 'C:\partsportal\deploy\tools\nssm.exe'
$key = "HKLM:\SYSTEM\CurrentControlSet\Services\$svc\Parameters"

sc.exe stop $svc | Out-Null
Start-Sleep -Seconds 3
foreach ($p in @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' })) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2

# 1) 环境变量直接写入服务注册表（REG_MULTI_SZ），nssm 启动子进程时注入
Set-ItemProperty -Path $key -Name 'AppEnvironmentExtra' -Value @(
    'JAVA_HOME=C:\Program Files\Java\jdk1.8.0_231',
    'STORAGE_ROOT=C:/GSMS/data/files',
    'STORAGE_TEMP=C:/GSMS/data/tmp'
) -Type MultiString
$env_now = (Get-ItemProperty $key).AppEnvironmentExtra -join ' | '
"env=$env_now"

# 2) 同时用 -D 系统属性兜底（优先级高于 application.yml）
$params = (Get-ItemProperty $key).AppParameters
if ($params -notlike '*gsms.storage.root*') {
    $newParams = $params + ' -Dgsms.storage.root=C:/GSMS/data/files -Dgsms.storage.temp=C:/GSMS/data/tmp'
    & $nssm set $svc AppParameters $newParams | Out-Null
}
"params=" + (Get-ItemProperty $key).AppParameters

# 3) 启动并等待
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
