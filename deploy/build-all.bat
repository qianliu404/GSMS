@echo off
rem =============================================================
rem  GSMS one-click build: backend jar + frontend dist
rem  Output: E:\GSMS\web\dist (served by Nginx)
rem =============================================================
chcp 65001 >nul
setlocal

set GSMS_HOME=E:\GSMS

echo [1/3] Building backend ...
cd /d %GSMS_HOME%\backend
call mvn -B -DskipTests package
if errorlevel 1 (
  echo [ERROR] backend build failed
  exit /b 1
)

echo [2/3] Building frontend ...
cd /d %GSMS_HOME%\frontend
call npm run build
if errorlevel 1 (
  echo [ERROR] frontend build failed
  exit /b 1
)

echo [3/3] Copying dist to %GSMS_HOME%\web\dist ...
if not exist "%GSMS_HOME%\web\dist" mkdir "%GSMS_HOME%\web\dist"
xcopy /E /Y /I "%GSMS_HOME%\frontend\dist\*" "%GSMS_HOME%\web\dist\" >nul

echo [DONE] Artifacts ready.
echo   backend : %GSMS_HOME%\backend\target\gsms-backend.jar
echo   frontend: %GSMS_HOME%\web\dist

endlocal
