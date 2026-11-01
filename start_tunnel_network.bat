@echo off
setlocal enabledelayedexpansion
title BLACKOUT Cross-Platform Tunnel & Network Gateway

echo ======================================================================
echo   BLACKOUT - CROSS-PLATFORM TUNNEL & LOCAL GATEWAY MANAGER
echo   Secure Bridge: Mobile App (Android) ^<---^> Operations Portal (Web)
echo ======================================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "PORTAL_PORT=3000"
set "BACKEND_PORT=54321"

:: 1. Identify Host LAN IPv4 Address
echo [1/4] Detecting Local Network Interface...
for /f "tokens=4" %%a in ('route print ^| findstr 0.0.0.0.*0.0.0.0') do (
    if not defined LAN_IP set "LAN_IP=%%a"
)

if not defined LAN_IP (
    set "LAN_IP=192.168.29.178"
)
echo   -- Detected Host LAN IP: %LAN_IP%
echo   -- Web Operations Portal: http://%LAN_IP%:%PORTAL_PORT%
echo   -- Android Emulator Target: http://10.0.2.2:%PORTAL_PORT%
echo.

:: 2. ADB Reverse Port Forwarding for USB / Emulator
echo [2/4] Configuring Android Debug Bridge (ADB) Reverse Port Forwarding...
where adb >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo   -- Forwarding port %PORTAL_PORT% to connected Android devices...
    adb reverse tcp:%PORTAL_PORT% tcp:%PORTAL_PORT% >nul 2>nul
    adb reverse tcp:%BACKEND_PORT% tcp:%BACKEND_PORT% >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        echo   [OK] ADB reverse port forwarding active!
        echo   Connected Android devices can reach portal via: http://localhost:%PORTAL_PORT%
    ) else (
        echo   [INFO] No active ADB device/emulator attached. Skipping reverse forwarding.
    )
) else (
    echo   [INFO] ADB not found in system PATH. Physical USB device forwarding skipped.
)
echo.

:: 3. Start Local Web Portal if not already running
echo [3/4] Checking BLACKOUT Web Operations Portal...
netstat -ano | findstr :%PORTAL_PORT% | findstr LISTENING >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo   [OK] Web Portal is already running on port %PORTAL_PORT%.
) else (
    echo   [INFO] Starting Web Portal on 0.0.0.0:%PORTAL_PORT%...
    start "BLACKOUT Portal Server" cmd /k "cd /d %SCRIPT_DIR%web && npm run dev"
    timeout /t 3 /nobreak >nul
)
echo.

:: 4. Launch Localtunnel for Remote / Cellular Testing
echo [4/4] Establishing Secure Public HTTPS Tunnel for Mobile Network Access...
echo   To allow Android devices on 4G/5G cellular data or external Wi-Fi to
echo   connect to this workstation, launching localtunnel on port %PORTAL_PORT%...
echo.
echo ======================================================================
echo   NETWORK ACCESS MATRIX:
echo   - Local Browser:        http://localhost:%PORTAL_PORT%
echo   - Android Emulator:     http://10.0.2.2:%PORTAL_PORT%
echo   - Android (Same Wi-Fi): http://%LAN_IP%:%PORTAL_PORT%
echo   - Android (USB Cable):  http://localhost:%PORTAL_PORT% (via ADB reverse)
echo ======================================================================
echo.
echo Launching tunnel in separate window...
start "BLACKOUT Public HTTPS Tunnel" cmd /k "npx localtunnel --port %PORTAL_PORT% --subdomain blackout-ops"

echo [OK] Tunnel network initialization complete.
pause

