param(
    [string]$Package = "com.warwar13.gentai"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb not found."
}

$pid = (adb shell pidof $Package).Trim()
if (-not $pid) {
    Write-Host "$Package is not currently running. Showing filtered Gentai/Bluetooth logs instead." -ForegroundColor Yellow
    adb logcat | Select-String -Pattern "Gentai|BluetoothGatt|BLE"
    exit
}

Write-Host "Following logcat for PID $pid ($Package). Ctrl+C to stop." -ForegroundColor Cyan
adb logcat --pid=$pid
