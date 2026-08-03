@echo off
title Master Launcher - Running 3 BAT files

set "ROOT_DIR=%~dp0"

echo [1/3] Starting first BAT...
start "Picolimbo" cmd /k "cd /d "%ROOT_DIR%" && call picolimbo/run.bat"

echo [2/3] Starting second BAT...
start "Velocity" cmd /k "cd /d "%ROOT_DIR%" && call velocity-plugin/velocity/run.bat"

echo [3/3] Starting third BAT...
start "Spring Boot Server" cmd /k "cd /d "%ROOT_DIR%" && call minecraft-server-operator/target/run.bat"

echo ========================================
echo All BAT files have been launched!
echo Check the individual windows.
echo ========================================
echo.
pause