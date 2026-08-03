@echo off
start "run-prod" cmd /k "cd /d ""%~dp0prod"" && call run.bat"
pause