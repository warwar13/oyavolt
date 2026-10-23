@echo off
setlocal
cd /d "%~dp0"

set "PACKAGE=com.warwar13.gentai"
set "APK=app\build\outputs\apk\debug\app-debug.apk"

if not exist "%APK%" (
  echo [ERROR] No existing debug APK found.
  echo Run POCO_BUILD_INSTALL_RUN.cmd first.
  pause
  exit /b 1
)

adb start-server >nul
echo Waiting for Poco...
adb wait-for-device

echo Installing existing APK without rebuilding...
adb install -r "%APK%"
if errorlevel 1 (
  echo [ERROR] Install failed.
  pause
  exit /b 1
)

adb shell monkey -p "%PACKAGE%" -c android.intent.category.LAUNCHER 1 >nul 2>&1

echo Done. Existing APK installed and Gentai launched.
pause
