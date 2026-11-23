$ErrorActionPreference = 'Continue'  # mysql 的密码告警走 stderr，严格模式会误中断
$base = 'C:\GSMS'
$src = 'http://192.168.2.209:8080/__deploy'
$mysql = 'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe'
$rootPwd = 'fxxkcrmjob'

# 1) 下载三个产物
Invoke-WebRequest -UseBasicParsing "$src/gsms-backend.jar" -OutFile "$base\backend\gsms-backend.jar.new"
Invoke-WebRequest -UseBasicParsing "$src/migrate-v1.17.sql" -OutFile "$base\sql\migrate-v1.17.sql"
Invoke-WebRequest -UseBasicParsing "$src/web.zip" -OutFile "$base\sql\web.zip"
"downloaded jar=$((Get-Item "$base\backend\gsms-backend.jar.new").Length) sql=$((Get-Item "$base\sql\migrate-v1.17.sql").Length) web=$((Get-Item "$base\sql\web.zip").Length)"

# 2) 数据库迁移（幂等）
$col = & $mysql -uroot "-p$rootPwd" -N -e "select count(*) from information_schema.columns where table_schema='case_system' and table_name='deadline_reminder' and column_name='handle_date';" 2>$null
"handle_date_before=$col"
if ($col -eq '0') {
    cmd /c "`"$mysql`" -uroot -p$rootPwd --default-character-set=utf8mb4 case_system < `"$base\sql\migrate-v1.17.sql`""
    "migrate_exit=$LASTEXITCODE"
}
$col2 = & $mysql -uroot "-p$rootPwd" -N -e "select count(*) from information_schema.columns where table_schema='case_system' and table_name='deadline_reminder' and column_name='handle_date';" 2>$null
"handle_date_after=$col2"

# 3) 停服务、换 jar、启动
sc.exe stop GSMS-Backend | Out-Null
Start-Sleep -Seconds 3
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
Copy-Item "$base\backend\gsms-backend.jar" "$base\backup\gsms-backend-$stamp.jar" -Force
Remove-Item "$base\backend\gsms-backend.jar" -Force
Move-Item "$base\backend\gsms-backend.jar.new" "$base\backend\gsms-backend.jar" -Force
sc.exe start GSMS-Backend | Out-Null
$ok = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $h = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 'http://127.0.0.1:8089/actuator/health'
        if ($h.StatusCode -eq 200) { $ok = $true; break }
    } catch { }
}
"health_ok=$ok wait=$($i*2)s"

# 4) 前端替换
if (Test-Path "$base\web\dist-new") { Remove-Item "$base\web\dist-new" -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$base\web\dist-new" | Out-Null
Expand-Archive "$base\sql\web.zip" -DestinationPath "$base\web\dist-new" -Force
if (Test-Path "$base\web\dist") { Rename-Item "$base\web\dist" "dist-backup-$stamp" }
Rename-Item "$base\web\dist-new" "dist"
"web_index=$(Test-Path "$base\web\dist\index.html") assets=$(@(Get-ChildItem "$base\web\dist\assets" -File).Count)"
"http8081=" + (Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 'http://127.0.0.1:8081/').StatusCode
