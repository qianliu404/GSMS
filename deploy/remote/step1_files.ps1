$ErrorActionPreference = 'Stop'
$base = 'C:\GSMS'
$src = 'http://192.168.2.209:8010'

# 1. 目录
foreach ($d in @("$base\backend", "$base\web", "$base\nginx", "$base\data\logs", "$base\data\files", "$base\data\tmp", "$base\sql", "$base\logs")) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

# 2. 下载
Invoke-WebRequest "$src/gsms-backend.jar" -OutFile "$base\backend\gsms-backend.jar" -UseBasicParsing
Invoke-WebRequest "$src/web.zip" -OutFile "$base\web\web.zip" -UseBasicParsing
Invoke-WebRequest "$src/init-mysql57.sql" -OutFile "$base\sql\init-mysql57.sql" -UseBasicParsing
Invoke-WebRequest "$src/nginx.conf" -OutFile "$base\nginx.conf.new" -UseBasicParsing

# 3. 前端解压
if (Test-Path "$base\web\dist") { Remove-Item "$base\web\dist" -Recurse -Force }
Expand-Archive "$base\web\web.zip" -DestinationPath "$base\web\dist" -Force

# 4. nginx 复用服务器现成安装
if (-not (Test-Path "$base\nginx\nginx.exe")) {
    Copy-Item 'C:\Cedex\nginx-1.22.0\*' "$base\nginx\" -Recurse -Force
}
Copy-Item "$base\nginx.conf.new" "$base\nginx\conf\nginx.conf" -Force
Remove-Item "$base\nginx.conf.new" -Force

# 5. 结果
"jar_bytes=$((Get-Item "$base\backend\gsms-backend.jar").Length)"
"web_index=$(Test-Path "$base\web\dist\index.html")"
"nginx_exe=$(Test-Path "$base\nginx\nginx.exe")"
"nginx_conf=$(Test-Path "$base\nginx\conf\nginx.conf")"
"sql=$(Test-Path "$base\sql\init-mysql57.sql")"
Get-Content "$base\nginx\conf\nginx.conf" | Select-String 'listen|root ' | Select-Object -First 3
