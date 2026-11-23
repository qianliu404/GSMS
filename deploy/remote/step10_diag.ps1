$ErrorActionPreference = 'Continue'
$nssm = 'C:\partsportal\deploy\tools\nssm.exe'
$svc  = 'GSMS-Backend'
$log  = 'C:\GSMS\logs\backend.log'

sc.exe stop $svc | Out-Null
Start-Sleep -Seconds 2
foreach ($p in @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' })) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2

# 日志改为追加模式，避免重启被截断
& $nssm set $svc AppStdoutCreationDisposition 1 | Out-Null
& $nssm set $svc AppStderrCreationDisposition 1 | Out-Null
Rename-Item $log ("backend.before-nssm-" + (Get-Date -Format 'HHmmss') + ".log") -Force -ErrorAction SilentlyContinue

sc.exe start $svc | Out-Null

for ($i = 1; $i -le 40; $i++) {
    Start-Sleep -Seconds 2
    $st = (Get-Service $svc).Status
    $j  = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' }).Count
    $ln = 0
    if (Test-Path $log) { $ln = (Get-Content $log -ErrorAction SilentlyContinue | Measure-Object -Line).Lines }
    $port = [bool](netstat -ano | Select-String ':8089\s+.*LISTENING')
    "t=$($i*2)s status=$st java=$j log_lines=$ln port8089=$port"
    if ($port) { break }
}

'--- log tail ---'
if (Test-Path $log) { Get-Content $log -Tail 30 } else { 'no log file' }
