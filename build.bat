@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Building Minecraft Server Operator...
echo ========================================
cd /d "%~dp0minecraft-server-operator"
call mvnw.cmd clean package -DskipTests -q

if !errorlevel! neq 0 (
    echo ERROR: Failed to build minecraft-server-operator
    exit /b 1
)

:: Генерируем run.bat для Operator в папке target
echo.
echo Generating run.bat for Operator...
(
echo @echo off
echo setlocal enabledelayedexpansion
echo.
echo cd /d "%%~dp0"
echo.
echo echo ========================================
echo echo Starting Minecraft Server Operator...
echo echo ========================================
echo echo.
echo.
echo java -Xmx2G -Xms1G -jar minecraft-server-operator-1.0.0.jar
echo.
echo pause
) > "%~dp0minecraft-server-operator\target\run.bat"

if !errorlevel! neq 0 (
    echo WARNING: Failed to generate operator run.bat
) else (
    echo operator run.bat created successfully!
)

echo.
echo ========================================
echo Building Velocity Plugin...
echo ========================================
cd /d "%~dp0velocity-plugin"
call mvnw.cmd clean package -DskipTests -q

if !errorlevel! neq 0 (
    echo ERROR: Failed to build velocity-plugin
    exit /b 1
)

:: Генерируем run.bat для Velocity в папке velocity
echo.
echo Generating run.bat for Velocity...
(
echo @echo off
echo setlocal enabledelayedexpansion
echo.
echo cd /d "%%~dp0"
echo.
echo echo ========================================
echo echo Starting Velocity Proxy Server...
echo echo ========================================
echo echo.
echo.
echo java -Xmx2G -jar velocity.jar
echo.
echo pause
) > "%~dp0velocity-plugin\velocity\run.bat"

if !errorlevel! neq 0 (
    echo WARNING: Failed to generate velocity run.bat
) else (
    echo velocity run.bat created successfully!
)

echo.
echo ========================================
echo Copying plugin to Velocity plugins folder...
echo ========================================

set PLUGIN_SOURCE=%~dp0velocity-plugin\target\AuthPlugin-1.0.0.jar
set PLUGIN_DEST=%~dp0velocity-plugin\velocity\plugins\AuthPlugin.jar

if not exist "%~dp0velocity-plugin\velocity\plugins\" (
    mkdir "%~dp0velocity-plugin\velocity\plugins\"
)

REM Remove old plugin if it exists
if exist "%PLUGIN_DEST%" (
    del /F "%PLUGIN_DEST%"
    echo Removed old plugin
)

copy /Y "%PLUGIN_SOURCE%" "%PLUGIN_DEST%"

if !errorlevel! neq 0 (
    echo ERROR: Failed to copy plugin
    exit /b 1
)

:: Генерируем run.bat для PicoLimbo в папке picolimbo
echo.
echo Generating run.bat for PicoLimbo...
(
echo @echo off
echo setlocal enabledelayedexpansion
echo.
echo cd /d "%%~dp0"
echo.
echo echo ========================================
echo echo Starting PicoLimbo Auth Lobby...
echo echo ========================================
echo echo.
echo.
echo java -Xmx512M -jar PicoLimbo.jar
echo.
echo pause
) > "%~dp0picolimbo\run.bat"

if !errorlevel! neq 0 (
    echo WARNING: Failed to generate picolimbo run.bat
) else (
    echo picolimbo run.bat created successfully!
)

echo.
echo ========================================
echo BUILD COMPLETED SUCCESSFULLY!
echo ========================================
echo Server JAR: %~dp0minecraft-server-operator\target\minecraft-server-operator-1.0.0.jar
echo Operator run script: %~dp0minecraft-server-operator\target\run.bat
echo Velocity run script: %~dp0velocity-plugin\velocity\run.bat
echo PicoLimbo run script: %~dp0picolimbo\run.bat
echo Plugin JAR: %PLUGIN_DEST%
echo ========================================

pause