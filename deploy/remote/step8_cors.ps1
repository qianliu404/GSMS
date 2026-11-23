$ErrorActionPreference = 'Stop'
$base = 'C:\GSMS'
$src  = 'http://192.168.2.209:8080/__deploy'

# 1) 下载新包
Invoke-WebRequest "$src/gsms-backend.jar" -OutFile "$base\backend\gsms-backend.jar.new" -UseBasicParsing
"downloaded=$((Get-Item "$base\backend\gsms-backend.jar.new").Length)"

# 2) 停后端
schtasks /end /tn "GSMS-Backend" | Out-Null
$procs = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' })
foreach ($p in $procs) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 3
"stopped_left=$(@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' }).Count)"

# 3) 备份并替换
$bk = "$base\backup\gsms-backend-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".jar"
Copy-Item "$base\backend\gsms-backend.jar" $bk -Force
Remove-Item "$base\backend\gsms-backend.jar" -Force
Move-Item "$base\backend\gsms-backend.jar.new" "$base\backend\gsms-backend.jar" -Force
"jar_now=$((Get-Item "$base\backend\gsms-backend.jar").Length)"

# 4) 启动
schtasks /run /tn "GSMS-Backend" | Out-Null
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $h = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 'http://127.0.0.1:8089/actuator/health'
        if ($h.StatusCode -eq 200) { $ok = $true; break }
    } catch { }
}
"health_ok=$ok wait=$($i*2)s"

# 5) CORS 回归：四种 Origin 登录
$body = '{"userId":"admin","password":"' + $env:GSMS_PASSWORD + '"}'
foreach ($o in @('http://127.0.0.1:8081','http://localhost:8081','http://192.168.11.179:8081','http://evil.example.com')) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8081/api/v1/auth/login' -Method POST -ContentType 'application/json' -Headers @{ Origin = $o } -Body $body -TimeoutSec 15
        "origin=$o => $($r.StatusCode)"
    } catch {
        "origin=$o => ERR $([int]$_.Exception.Response.StatusCode)"
    }
}
