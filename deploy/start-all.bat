@echo off
rem =============================================================
rem  GSMS one-click start: Nginx(8080) + backend(8089)
rem  Usage: start-all.bat [rebuild]
rem  Env  : NGINX_HOME  (default D:\nginx)
rem  Note : file saved as GBK + CRLF (cmd compatibility)
rem =============================================================
setlocal

set GSMS_HOME=E:\GSMS
if not defined NGINX_HOME set NGINX_HOME=D:\nginx
set APP_PORT=8089
set WEB_PORT=8080
set JAR=%GSMS_HOME%\backend\target\gsms-backend.jar
set DIST_INDEX=%GSMS_HOME%\web\dist\index.html

if exist "%NGINX_HOME%\nginx.exe" goto :nginx_ok
echo [ERROR] Nginx not found: %NGINX_HOME%\nginx.exe
echo         Set env NGINX_HOME to your Nginx folder and retry.
goto :fail
:nginx_ok

echo [1/5] 检查构建产物 ...
if /i "%~1"=="rebuild" goto :build
if not exist "%JAR%" goto :build
if not exist "%DIST_INDEX%" goto :build
echo       产物已存在，跳过打包（需要重新打包请执行 start-all.bat rebuild）
goto :deploy_conf

:build
echo       执行完整打包：后端 jar + 前端 dist ...
call "%GSMS_HOME%\deploy\build-all.bat"
if errorlevel 1 goto :build_fail
goto :deploy_conf

:build_fail
echo [ERROR] 打包失败，请查看上方 Maven / npm 输出
goto :fail

:deploy_conf
echo [2/5] 停止已在运行的实例 ...
call "%GSMS_HOME%\deploy\stop-all.bat" quiet

echo [3/5] 部署 Nginx 配置 ...
if exist "%NGINX_HOME%\conf\nginx.conf.bak" goto :backup_skip
copy /Y "%NGINX_HOME%\conf\nginx.conf" "%NGINX_HOME%\conf\nginx.conf.bak" >nul
echo       已备份原配置为 nginx.conf.bak
:backup_skip
copy /Y "%GSMS_HOME%\deploy\nginx.conf" "%NGINX_HOME%\conf\nginx.conf" >nul
if errorlevel 1 goto :conf_fail
goto :start_backend

:conf_fail
echo [ERROR] 复制 Nginx 配置失败，请检查目录权限
goto :fail

:start_backend
echo [4/5] 启动后端，端口 %APP_PORT% ...
start "GSMS-Backend" /min java -Xms512m -Xmx2g -Dfile.encoding=UTF-8 -jar "%JAR%" --spring.profiles.active=local --server.port=%APP_PORT%
call :wait_http "http://127.0.0.1:%APP_PORT%/actuator/health" 90
if errorlevel 1 goto :backend_fail
echo       后端已就绪

echo [5/5] 启动 Nginx，端口 %WEB_PORT% ...
start "" /min "%NGINX_HOME%\nginx.exe" -p "%NGINX_HOME%"
call :wait_http "http://127.0.0.1:%WEB_PORT%/" 20
if errorlevel 1 goto :nginx_fail

echo.
echo =============================================================
echo   启动完成: http://127.0.0.1:%WEB_PORT%
echo   接口前缀: http://127.0.0.1:%WEB_PORT%/api/v1
echo   健康检查: http://127.0.0.1:%WEB_PORT%/actuator/health
echo   后端日志: %GSMS_HOME%\data\logs\gsms-backend.log
echo   访问日志: %GSMS_HOME%\data\logs\nginx-access.log
echo   停止服务: deploy\stop-all.bat
echo   局域网访问: 运行 deploy\lan-address.bat 查看本机 IP

echo =============================================================
start "" "http://127.0.0.1:%WEB_PORT%"
goto :ok

:backend_fail
echo [ERROR] 后端未就绪，日志: %GSMS_HOME%\data\logs\gsms-backend.log
goto :fail

:nginx_fail
echo [ERROR] Nginx 未就绪，日志: %GSMS_HOME%\data\logs\nginx-error.log
goto :fail

rem ---- helpers ----
:wait_http
set /a _n=0
:wait_loop
curl -s --noproxy "*" --max-time 2 -o nul %~1 && exit /b 0
set /a _n+=1
if %_n% geq %~2 exit /b 1
ping -n 2 127.0.0.1 >nul
goto :wait_loop

:ok
endlocal & exit /b 0

:fail
echo 启动失败，请查看上述日志。
endlocal & exit /b 1
