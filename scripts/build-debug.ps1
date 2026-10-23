$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

if (-not (Test-Path ".\gradlew.bat")) {
    throw "gradlew.bat not found in project root."
}

.\gradlew.bat --no-daemon --console=plain assembleDebug
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed (exit $LASTEXITCODE)."
}

$apk = Get-ChildItem ".\app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $apk) {
    throw "Build succeeded but no debug APK was found."
}

Write-Host ""
Write-Host "APK: $($apk.FullName)" -ForegroundColor Green
