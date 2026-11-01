@echo off
setlocal enabledelayedexpansion
title BLACKOUT Operations Command Center

:: Determine project and web directory path safely with spaces
set "SCRIPT_DIR=%~dp0"
set "WEB_DIR=%SCRIPT_DIR%web"

if not exist "%WEB_DIR%" (
    echo [ERROR] Web directory not found at: "%WEB_DIR%"
    echo Please make sure this script is located in the BLACKOUT root folder.
    pause
    exit /b 1
)

cd /d "%WEB_DIR%"

echo ============================================================
echo   BLACKOUT - GOVERNMENT EMERGENCY OPERATIONS PORTAL (v6.0)
echo   National Disaster and Civil Defense Command Center
echo ============================================================
echo.

:: Check for Node.js
where node >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Node.js is not installed or not found in system PATH.
    echo Please download and install Node.js from https://nodejs.org/
    pause
    exit /b 1
)

:: Check for node_modules and install if missing
if not exist "node_modules\" (
    echo [INFO] First-time setup detected. Installing web dependencies...
    call npm install
    if %ERRORLEVEL% neq 0 (
        echo [ERROR] Failed to install npm dependencies.
        pause
        exit /b 1
    )
)

echo [OK] Launching BLACKOUT Operations Command Center...
echo [INFO] Local Portal URL: http://localhost:3000
echo.
echo ============================================================
echo   LOGIN CREDENTIALS:
echo   - Commander: admin@blackout.ops    / Blackout2026!
echo   - Operator:  operator@blackout.ops / Operator2026!
echo ============================================================
echo.

:: Automatically open default web browser to the operations portal
start "" "http://localhost:3000"

:: Start the Vite development server on port 3000
call npm run dev

pause
