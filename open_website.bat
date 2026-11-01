@echo off
setlocal enabledelayedexpansion
title Launch BLACKOUT Portal

set "SCRIPT_DIR=%~dp0"
if exist "%SCRIPT_DIR%start_operations_portal.bat" (
    call "%SCRIPT_DIR%start_operations_portal.bat"
) else (
    echo [ERROR] start_operations_portal.bat not found in: %SCRIPT_DIR%
    pause
)
