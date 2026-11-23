$ErrorActionPreference = 'Stop'
$base = 'C:\GSMS'
$src = 'http://192.168.2.209:8080/__deploy/login-fix-web.zip'

# 下载
Invoke-WebRequest -UseBasicParsing $src -OutFile "$base\sql\login-fix-web.zip"
"downloaded=$((Get-Item "$base\sql\login-fix-web.zip").Length)"

# 解压到新目录并原子切换（保留旧目录便于回滚）
if (Test-Path "$base\web\dist-new") { Remove-Item "$base\web\dist-new" -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$base\web\dist-new" | Out-Null
Expand-Archive "$base\sql\login-fix-web.zip" -DestinationPath "$base\web\dist-new" -Force
"index=$(Test-Path "$base\web\dist-new\index.html") assets=$(@(Get-ChildItem "$base\web\dist-new\assets" -File).Count)"

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
if (Test-Path "$base\web\dist") { Rename-Item "$base\web\dist" "dist-backup-$stamp" }
Rename-Item "$base\web\dist-new" "dist"
"switched=$(Test-Path "$base\web\dist\index.html")"

# 静态文件热生效，验证入口
"http8081=" + (Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 'http://127.0.0.1:8081/login').StatusCode
