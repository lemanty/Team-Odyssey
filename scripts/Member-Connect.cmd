@echo off
call "%~dp0Run-Database.cmd" tunnel
exit /b %errorlevel%
