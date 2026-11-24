# PowerShell Execution Script for NammaRoute Travels
$ErrorActionPreference = "Stop"

Write-Host "=========================================================================" -ForegroundColor Cyan
Write-Host "Compiling NammaRoute Travels (Java 17 target)..." -ForegroundColor Cyan
Write-Host "=========================================================================" -ForegroundColor Cyan

# Detect javac and java
$javac = (Get-Command javac -ErrorAction SilentlyContinue)?.Source
$java = (Get-Command java -ErrorAction SilentlyContinue)?.Source

if (-not $javac) {
    if (Test-Path "C:\Users\Admin\.jdks\openjdk-24.0.2+12-54\bin\javac.exe") {
        $javac = "C:\Users\Admin\.jdks\openjdk-24.0.2+12-54\bin\javac.exe"
        $java = "C:\Users\Admin\.jdks\openjdk-24.0.2+12-54\bin\java.exe"
    } elseif ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
        $javac = "$env:JAVA_HOME\bin\javac.exe"
        $java = "$env:JAVA_HOME\bin\java.exe"
    } else {
        Write-Error "JDK compiler (javac) not found. Please install Java 17+ and set PATH or JAVA_HOME."
        exit 1
    }
}

if (-not (Test-Path "bin")) { New-Item -ItemType Directory -Path "bin" | Out-Null }
if (-not (Test-Path "data")) { New-Item -ItemType Directory -Path "data" | Out-Null }

$sources = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
& $javac --release 17 -d bin $sources

if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed."
    exit $LASTEXITCODE
}

Write-Host "[SUCCESS] Compilation finished. Launching application..." -ForegroundColor Green
Write-Host ""
& $java -cp bin com.nammaroute.Main
