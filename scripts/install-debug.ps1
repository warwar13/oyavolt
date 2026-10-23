$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb not found. Run the bootstrap setup and reopen PowerShell."
}

$apk = Get-ChildItem ".\app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $apk) {
    Write-Host "No debug APK found. Building first..." -ForegroundColor Yellow
    & ".\scripts\build-debug.ps1"
    $apk = Get-ChildItem ".\app\build\outputs\apk\debug\*.apk" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

$deviceLines = adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" }
if (-not $deviceLines) {
    adb devices
    throw "No authorized ADB device detected. Connect/unlock the Poco and accept the USB debugging prompt."
}

Write-Host "Installing $($apk.Name)..." -ForegroundColor Cyan
adb install -r "$($apk.FullName)"
if ($LASTEXITCODE -ne 0) {
    throw "adb install failed (exit $LASTEXITCODE)."
}

Write-Host "Installed successfully." -ForegroundColor Green
