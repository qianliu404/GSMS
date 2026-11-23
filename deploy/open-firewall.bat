@echo off
rem =============================================================
rem  GSMS open inbound TCP 8080 for other PCs (LAN access)
rem  Usage: double click; a UAC prompt will appear
rem  Note : file saved as GBK + CRLF (cmd compatibility)
rem =============================================================
setlocal

net session >nul 2>&1
if %errorlevel% equ 0 goto :elevated

echo 需要管理员权限，正在申请提权，请在弹窗中点“是” ...
powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
exit /b 0

:elevated
echo 正在放行 TCP 8080 入站 ...
netsh advfirewall firewall delete rule name="GSMS-Web-8080" >nul 2>&1
netsh advfirewall firewall add rule name="GSMS-Web-8080" dir=in action=allow protocol=TCP localport=8080 profile=domain,private >nul
if errorlevel 1 goto :fail

echo.
echo =============================================================
echo   已放行 TCP 8080，局域网内其他电脑可访问:
echo     http://本机IP:8080
echo   本机 IP 查看方式: 命令行执行 ipconfig
echo   服务停止后端口关闭，规则保留，下次启动无需再放行
echo =============================================================
goto :done

:fail
echo [ERROR] 放行失败，请右键“以管理员身份运行”本脚本

:done
echo.
pause
