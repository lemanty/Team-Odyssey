@echo off
call "%~dp0Run-Database.cmd" web-member
exit /b %errorlevel%
