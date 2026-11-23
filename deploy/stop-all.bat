@echo off
rem =============================================================
rem  GSMS stop: backend(8089) + Nginx(8080)
rem  Usage: stop-all.bat [quiet]
rem  Note : file saved as GBK + CRLF (cmd compatibility)
rem =============================================================
setlocal

if not defined NGINX_HOME set NGINX_HOME=D:\nginx
set APP_PORT=8089

if not exist "%NGINX_HOME%\nginx.exe" goto :kill_backend
"%NGINX_HOME%\nginx.exe" -p "%NGINX_HOME%" -s stop >nul 2>&1
ping -n 2 127.0.0.1 >nul
tasklist /FI "IMAGENAME eq nginx.exe" 2>nul | findstr /I "nginx.exe" >nul && taskkill /F /IM nginx.exe >nul 2>&1

:kill_backend
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%APP_PORT%" ^| findstr "LISTENING"') do taskkill /F /PID %%p >nul 2>&1

if /i "%~1"=="quiet" goto :done
echo “—Õ£÷π£∫Nginx(8080) ”Î∫Û∂À(%APP_PORT%)
:done
endlocal & exit /b 0
