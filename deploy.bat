@echo off
setlocal enabledelayedexpansion

:: ========================================
:: DEPLOYING TO PRODUCTION FOLDER
:: ========================================
echo ========================================
echo DEPLOYING TO PRODUCTION FOLDER
echo ========================================
echo.

:: Определяем пути
set "PROJECT_ROOT=%~dp0"
set "PROD_ROOT=%PROJECT_ROOT%prod"

:: Создаем корневую папку prod если её нет
echo [1/4] Creating production folder structure...
if not exist "%PROD_ROOT%" (
    mkdir "%PROD_ROOT%"
    echo   Created: %PROD_ROOT%
) else (
    echo   Already exists: %PROD_ROOT%
)
echo.

:: ========================================
:: 1. Копируем ВСЮ папку velocity из velocity-plugin
:: ========================================
echo [2/4] Copying Velocity folder...
set "SOURCE_VELOCITY=%PROJECT_ROOT%velocity-plugin\velocity"
set "DEST_VELOCITY=%PROD_ROOT%\velocity"

if exist "%SOURCE_VELOCITY%" (
    if exist "%DEST_VELOCITY%" (
        echo   Removing old velocity folder...
        rmdir /S /Q "%DEST_VELOCITY%"
    )
    xcopy /E /I /Y "%SOURCE_VELOCITY%" "%DEST_VELOCITY%" >nul
    echo   Velocity folder copied successfully!
    
    if exist "%SOURCE_VELOCITY%\run.bat" (
        copy /Y "%SOURCE_VELOCITY%\run.bat" "%DEST_VELOCITY%\run.bat" >nul
    )
) else (
    echo   ERROR: Velocity folder not found!
    echo   Expected: %SOURCE_VELOCITY%
    pause
    exit /b 1
)
echo.

:: ========================================
:: 2. Копируем ВСЮ папку picolimbo (если есть)
:: ========================================
echo [3/4] Copying PicoLimbo folder...
set "SOURCE_PICOLIMBO=%PROJECT_ROOT%picolimbo"
set "DEST_PICOLIMBO=%PROD_ROOT%\picolimbo"

if exist "%SOURCE_PICOLIMBO%" (
    if exist "%DEST_PICOLIMBO%" (
        echo   Removing old picolimbo folder...
        rmdir /S /Q "%DEST_PICOLIMBO%"
    )
    xcopy /E /I /Y "%SOURCE_PICOLIMBO%" "%DEST_PICOLIMBO%" >nul
    echo   PicoLimbo folder copied successfully!
    
    if exist "%SOURCE_PICOLIMBO%\run.bat" (
        copy /Y "%SOURCE_PICOLIMBO%\run.bat" "%DEST_PICOLIMBO%\run.bat" >nul
    )
) else (
    echo   WARNING: PicoLimbo folder not found, skipping...
)
echo.

:: ========================================
:: 3. Копируем minecraft-server-operator-1.0.0.jar
:: ========================================
echo [4/4] Copying Minecraft Server Operator JAR...
set "SOURCE_JAR=%PROJECT_ROOT%minecraft-server-operator\target\minecraft-server-operator-1.0.0.jar"
set "DEST_JAR_DIR=%PROD_ROOT%\minecraft-server-operator"
set "DEST_JAR=%DEST_JAR_DIR%\minecraft-server-operator-1.0.0.jar"

if exist "%SOURCE_JAR%" (
    if not exist "%DEST_JAR_DIR%" (
        mkdir "%DEST_JAR_DIR%"
    )
    copy /Y "%SOURCE_JAR%" "%DEST_JAR%" >nul
    echo   minecraft-server-operator-1.0.0.jar copied successfully!
    
    if exist "%PROJECT_ROOT%minecraft-server-operator\target\run.bat" (
        copy /Y "%PROJECT_ROOT%minecraft-server-operator\target\run.bat" "%DEST_JAR_DIR%\run.bat" >nul
        echo   run.bat copied successfully!
    )
) else (
    echo   ERROR: minecraft-server-operator-1.0.0.jar not found!
    echo   Expected: %SOURCE_JAR%
    echo   Please run build.bat first to build the application
    pause
    exit /b 1
)
echo.

:: ========================================
:: 4. Создаем главный run.bat в prod
:: ========================================
echo [Bonus] Creating master run.bat for production...
(
echo @echo off
echo title Production Launcher
echo.
echo set "ROOT_DIR=%%~dp0"
echo.
echo echo ========================================
echo echo Starting all services in production...
echo echo ========================================
echo echo.
echo.
echo :: PicoLimbo
echo if exist "%%ROOT_DIR%%picolimbo\PicoLimbo.jar" (
echo     echo [1/3] Starting PicoLimbo Auth Lobby...
echo     start "Picolimbo" cmd /k "cd /d "%%ROOT_DIR%%picolimbo" && call run.bat"
echo ^) else (
echo     echo [1/3] PicoLimbo not found, skipping...
echo ^)
echo.
echo :: Minecraft Server Operator
echo if exist "%%ROOT_DIR%%minecraft-server-operator\minecraft-server-operator-1.0.0.jar" (
echo     echo [2/3] Starting Minecraft Server Operator...
echo     start "Spring Boot Server" cmd /k "cd /d "%%ROOT_DIR%%minecraft-server-operator" && call run.bat"
echo ^) else (
echo     echo [2/3] Operator not found, skipping...
echo ^)
echo.
echo :: Velocity
echo if exist "%%ROOT_DIR%%velocity\velocity.jar" (
echo     echo [3/3] Starting Velocity Proxy Server...
echo     start "Velocity" cmd /k "cd /d "%%ROOT_DIR%%velocity" && call run.bat"
echo ^) else (
echo     echo [3/3] Velocity not found, skipping...
echo ^)
echo.
echo echo.
echo echo ========================================
echo echo All services launched!
echo echo ========================================
echo echo.
echo exit
) > "%PROD_ROOT%\run.bat"

echo   Master run.bat created successfully!
echo.

:: ========================================
:: FINAL SUMMARY
:: ========================================
echo ========================================
echo DEPLOYMENT COMPLETED SUCCESSFULLY!
echo ========================================
echo.
echo Production folder: %PROD_ROOT%
echo.
echo Structure:
echo   %PROD_ROOT%\
echo   ├── velocity\
echo   │   ├── velocity.jar
echo   │   ├── run.bat
echo   │   └── plugins\
echo   │       └── AuthPlugin.jar
if exist "%DEST_PICOLIMBO%" (
echo   ├── picolimbo\
echo   │   ├── PicoLimbo.jar
echo   │   └── run.bat
)
echo   └── minecraft-server-operator\
echo       ├── minecraft-server-operator-1.0.0.jar
echo       └── run.bat
echo.
echo To run all services:
echo   cd /d %PROD_ROOT%
echo   run.bat
echo.
echo ========================================

pause