@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo ========================================
echo Starting Velocity Proxy Server...
echo ========================================
echo.

java -Xmx2G -jar velocity.jar

pause
