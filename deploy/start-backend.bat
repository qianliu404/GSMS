@echo off
rem =============================================================
rem  GSMS backend launcher (Windows)
rem  Usage: double-click, or run from cmd
rem =============================================================
chcp 65001 >nul
setlocal

set GSMS_HOME=E:\GSMS
set DB_USER=case_app
set DB_PASSWORD=%DB_PASSWORD%
set JAVA_OPTS=-Xms512m -Xmx2g -Dfile.encoding=UTF-8

cd /d %GSMS_HOME%\backend
echo [GSMS] Starting backend on http://127.0.0.1:8089 ...
java %JAVA_OPTS% -jar target\gsms-backend.jar --spring.profiles.active=local --server.port=8089

endlocal
