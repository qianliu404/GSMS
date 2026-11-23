@echo off
rem =============================================================
rem  GSMS daily backup: MySQL dump + document folder archive
rem  Schedule via Windows Task Scheduler at 02:00 daily
rem  Output: E:\GSMS\data\backup
rem =============================================================
chcp 65001 >nul
setlocal

set GSMS_HOME=E:\GSMS
set MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin
set DB_NAME=case_system
set DB_USER=case_app
set DB_PASSWORD=%DB_PASSWORD%

for /f "tokens=2 delims==" %%a in ('wmic os get localdatetime /value') do set DT=%%a
set STAMP=%DT:~0,8%

if not exist "%GSMS_HOME%\data\backup\mysql" mkdir "%GSMS_HOME%\data\backup\mysql"
if not exist "%GSMS_HOME%\data\backup\files" mkdir "%GSMS_HOME%\data\backup\files"

echo [1/2] Dumping database ...
"%MYSQL_BIN%\mysqldump.exe" -u %DB_USER% -p%DB_PASSWORD% --single-transaction --routines --triggers --default-character-set=utf8mb4 %DB_NAME% > "%GSMS_HOME%\data\backup\mysql\%DB_NAME%_%STAMP%.sql"
if errorlevel 1 (
  echo [ERROR] mysqldump failed
  exit /b 1
)

echo [2/2] Archiving documents ...
powershell -NoProfile -Command "Compress-Archive -Path '%GSMS_HOME%\data\files\*' -DestinationPath '%GSMS_HOME%\data\backup\files\files_%STAMP%.zip' -Force"

echo [DONE] Backup finished: %GSMS_HOME%\data\backup

endlocal
