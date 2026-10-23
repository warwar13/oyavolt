@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "PACKAGE=com.warwar13.gentai"

echo.
echo ==========================================
echo   Gentai - Live Poco Logcat
echo ==========================================
echo.

where adb >nul 2>&1
if errorlevel 1 (
  echo [ERROR] adb was not found in PATH.
  pause
  exit /b 1
)

adb start-server >nul
adb wait-for-device

for /f "delims=" %%P in ('adb shell pidof %PACKAGE% 2^>nul') do set "PID=%%P"

if defined PID (
  echo Following logs for %PACKAGE% PID !PID!
  echo Press Ctrl+C to stop.
  echo.
  adb logcat --pid=!PID!
) else (
  echo Gentai is not currently running.
  echo Showing filtered BLE/Gentai logs instead.
  echo Press Ctrl+C to stop.
  echo.
  adb logcat | findstr /I "Gentai BluetoothGatt BluetoothLeScanner BLE AndroidRuntime"
)
