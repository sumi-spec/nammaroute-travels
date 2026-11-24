@echo off
title NammaRoute Travels - Local Web Server
echo =========================================================================
echo Starting NammaRoute Travels Local Web Server on Port 3000...
echo =========================================================================
echo.
echo Local URL: http://localhost:3000
echo.
echo Press Ctrl + C to stop the server anytime.
echo.

where python >nul 2>nul
if %ERRORLEVEL% equ 0 (
    python -m http.server 3000 --bind 127.0.0.1
) else (
    if exist "C:\Users\Admin\.jdks\openjdk-24.0.2+12-54\bin\jwebserver.exe" (
        "C:\Users\Admin\.jdks\openjdk-24.0.2+12-54\bin\jwebserver.exe" -p 3000 -d "."
    ) else (
        echo [ERROR] Neither Python nor JDK jwebserver was found.
        pause
    )
)
