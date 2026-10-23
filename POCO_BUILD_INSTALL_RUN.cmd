@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "PACKAGE=com.warwar13.gentai"
set "APK=app\build\outputs\apk\debug\app-debug.apk"

echo.
echo ==========================================
echo   Gentai - Build, Install and Run on Poco
echo ==========================================
echo.

where adb >nul 2>&1
if errorlevel 1 (
  echo [ERROR] adb was not found in PATH.
  echo Run the Android bootstrap setup first, then reopen Command Prompt.
  pause
  exit /b 1
)

if not exist "gradlew.bat" (
  echo [ERROR] gradlew.bat was not found.
  echo Put this CMD file in the ROOT of your GentaiAndroid project.
  pause
  exit /b 1
)

echo [1/6] Starting ADB...
adb start-server
if errorlevel 1 goto :fail

echo.
echo [2/6] Waiting for an authorized Android device...
echo Unlock the Poco and accept "Allow USB debugging" if prompted.
echo Press Ctrl+C to cancel.
adb wait-for-device

for /f "tokens=1,2" %%A in ('adb devices ^| findstr /R /C:"	device$"') do (
  set "DEVICE=%%A"
)

if not defined DEVICE (
  echo.
  echo [ERROR] No authorized ADB device was found.
  echo.
  adb devices
  echo.
  echo If you see "unauthorized", unlock the phone and accept the RSA prompt.
  echo If nothing appears, use a USB data cable and File Transfer/MTP mode.
  pause
  exit /b 1
)

echo Found device: !DEVICE!

echo.
echo [3/6] Building debug APK...
call gradlew.bat --no-daemon --console=plain assembleDebug
if errorlevel 1 (
  echo.
  echo [ERROR] Gradle build failed.
  pause
  exit /b 1
)

if not exist "%APK%" (
  echo.
  echo [ERROR] Build finished but APK was not found:
  echo %APK%
  pause
  exit /b 1
)

echo.
echo [4/6] Installing/updating Gentai...
adb install -r "%APK%"
if errorlevel 1 (
  echo.
  echo [ERROR] APK installation failed.
  echo Check the Poco screen for any install/debugging prompt.
  pause
  exit /b 1
)

echo.
echo [5/6] Launching Gentai...
adb shell monkey -p "%PACKAGE%" -c android.intent.category.LAUNCHER 1 >nul 2>&1
if errorlevel 1 (
  echo [WARNING] Automatic launch failed. The APK is installed; open Gentai manually.
) else (
  echo Gentai launched.
)

echo.
echo [6/6] Done.
echo.
echo Device : !DEVICE!
echo APK    : %APK%
echo Package: %PACKAGE%
echo.
echo You can now test BLE on the Poco.
echo Use POCO_LOGS.cmd if you need live logs.
echo.
pause
exit /b 0

:fail
echo.
echo [ERROR] ADB command failed.
pause
exit /b 1
