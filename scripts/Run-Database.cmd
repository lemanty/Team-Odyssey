@echo off
setlocal DisableDelayedExpansion
chcp 65001 >nul
set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"
pushd "%~dp0.." || exit /b 1
if "%~1"=="host-all" goto action_ok
if "%~1"=="tunnel" goto action_ok
if "%~1"=="web-member" goto action_ok
echo Use Host-Start.cmd, Member-Connect.cmd or Member-Website.cmd.
set "result=1"
goto finish
:action_ok
py -3 -c "import sys; sys.exit(0 if sys.version_info >= (3,9) else 1)" >nul 2>&1
if not errorlevel 1 goto use_py
python -c "import sys; sys.exit(0 if sys.version_info >= (3,9) else 1)" >nul 2>&1
if not errorlevel 1 goto use_python
python3 -c "import sys; sys.exit(0 if sys.version_info >= (3,9) else 1)" >nul 2>&1
if not errorlevel 1 goto use_python3
echo Python 3.9 or newer was not found. Install Python and enable Add Python to PATH.
set "result=1"
goto finish
:use_py
py -3 scripts\database.py %1
set "result=%errorlevel%"
goto finish
:use_python
python scripts\database.py %1
set "result=%errorlevel%"
goto finish
:use_python3
python3 scripts\database.py %1
set "result=%errorlevel%"
:finish
if not "%result%"=="0" echo Startup failed. Read the error above before closing this window.
echo.
pause
popd
exit /b %result%
