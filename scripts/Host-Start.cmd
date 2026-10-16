@echo off
call "%~dp0Run-Database.cmd" host-all
exit /b %errorlevel%
