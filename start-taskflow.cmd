@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-taskflow.ps1" %*
set "taskflowExit=%errorlevel%"
if not "%taskflowExit%"=="0" pause
exit /b %taskflowExit%
