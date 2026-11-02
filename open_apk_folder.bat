@echo off
title BLACKOUT APK Location
set "APK_PATH=%~dp0BLACKOUT.apk"
if not exist "%APK_PATH%" (
    set "APK_PATH=%~dp0app\build\outputs\apk\debug\app-debug.apk"
)

echo Opening File Explorer to BLACKOUT APK...
explorer.exe /select,"%APK_PATH%"
