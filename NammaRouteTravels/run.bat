@echo off
setlocal enabledelayedexpansion
title NammaRoute Travels - Bus Booking System

REM Look for java / javac
where javac >nul 2>nul
if %ERRORLEVEL% equ 0 (
    set JAVAC_CMD=javac
    set JAVA_CMD=java
) else (
    if exist "C:\Users\Admin\.jdks\openjdk-24.0.2+12-54\bin\javac.exe" (
        set JAVAC_CMD="C:\Users\Admin\.jdks\openjdk-24.0.2+12-54\bin\javac.exe"
        set JAVA_CMD="C:\Users\Admin\.jdks\openjdk-24.0.2+12-54\bin\java.exe"
    ) else if exist "%JAVA_HOME%\bin\javac.exe" (
        set JAVAC_CMD="%JAVA_HOME%\bin\javac.exe"
        set JAVA_CMD="%JAVA_HOME%\bin\java.exe"
    ) else (
        echo [ERROR] JDK compiler (javac) not found. Please ensure Java 17+ JDK is installed and added to PATH.
        pause
        exit /b 1
    )
)

echo =========================================================================
echo Compiling NammaRoute Travels (Java 17 target)...
echo =========================================================================
if not exist "bin" mkdir "bin"
if not exist "data" mkdir "data"

%JAVAC_CMD% --release 17 -d bin src\main\java\com\nammaroute\model\*.java src\main\java\com\nammaroute\storage\*.java src\main\java\com\nammaroute\util\*.java src\main\java\com\nammaroute\service\*.java src\main\java\com\nammaroute\Main.java

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed.
    pause
    exit /b %ERRORLEVEL%
)

echo [SUCCESS] Compilation finished. Launching application...
echo.
%JAVA_CMD% -cp bin com.nammaroute.Main

endlocal
