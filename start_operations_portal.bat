@echo off
title BLACKOUT Operations Command Center
cd /d "%~dp0web"

echo ============================================================
echo   BLACKOUT - GOVERNMENT EMERGENCY OPERATIONS PORTAL (v6.0)
echo   National Disaster and Civil Defense Command Center
echo ============================================================
echo.

if not exist "node_modules\" (
    echo [INFO] First time setup detected. Installing web dependencies...
    call npm install
    if errorlevel 1 (
        echo [ERROR] Failed to install npm dependencies.
        pause
        exit /b 1
    )
)

echo [OK] Launching BLACKOUT Operations Command Center...
echo [INFO] URL: http://localhost:5173
echo.
echo ============================================================
echo   CREDENTIALS:
echo   - Commander: admin@blackout.ops   / Blackout2026!
echo   - Operator:  operator@blackout.ops / Operator2026!
echo ============================================================
echo.

start "" "http://localhost:5173"
call npm run dev

pause
