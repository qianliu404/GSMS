$ErrorActionPreference = 'Stop'
$base = 'C:\GSMS'
$src  = 'http://192.168.2.209:8080/__deploy'
$mysql = 'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe'
$dump  = 'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysqldump.exe'
$rootPwd = $env:MYSQL_ROOT_PASSWORD

# 1) 下载数据脚本与附件包
Invoke-WebRequest "$src/data-sync.sql" -OutFile "$base\sql\data-sync.sql" -UseBasicParsing
Invoke-WebRequest "$src/files.zip"    -OutFile "$base\sql\files.zip"    -UseBasicParsing
"download: sql=$((Get-Item "$base\sql\data-sync.sql").Length) zip=$((Get-Item "$base\sql\files.zip").Length)"

# 2) 停后端
schtasks /end /tn "GSMS-Backend" | Out-Null
$procs = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' })
foreach ($p in $procs) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 3
$left = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*gsms-backend.jar*' })
"backend_stopped_left=$($left.Count)"

# 3) 备份当前库
$bk = "$base\backup\case_system-before-sync-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".sql"
cmd /c "`"$dump`" -uroot -p$rootPwd --default-character-set=utf8mb4 --single-transaction --skip-lock-tables case_system > `"$bk`""
"backup=$bk bytes=$((Get-Item $bk).Length)"

# 4) 覆盖导入
cmd /c "`"$mysql`" -uroot -p$rootPwd --default-character-set=utf8mb4 < `"$base\sql\data-sync.sql`" > `"$base\logs\data-sync.log`" 2>&1"
"import_exit=$LASTEXITCODE"
Get-Content "$base\logs\data-sync.log" -Tail 25
