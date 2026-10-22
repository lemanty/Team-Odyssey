@echo off
setlocal DisableDelayedExpansion
chcp 65001 >nul
pushd "%~dp0.." || exit /b 1
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0Windows-Setup.ps1"
set "result=%errorlevel%"
if not "%result%"=="0" echo Setup/check failed. Read the message above. See docs\windows-setup.md.
echo.
pause
popd
exit /b %result%
