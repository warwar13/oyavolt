@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

echo.
echo ==========================================
echo   Gentai - Run Android Device Tests
echo ==========================================
echo.

where adb >nul 2>&1
if errorlevel 1 (
  echo [ERROR] adb was not found in PATH.
  pause
  exit /b 1
)

if not exist "gradlew.bat" (
  echo [ERROR] gradlew.bat was not found.
  echo Put this CMD file in the ROOT of your GentaiAndroid project.
  pause
  exit /b 1
)

adb start-server >nul
echo Waiting for Poco...
echo Unlock the phone and accept USB debugging if asked.
adb wait-for-device

adb devices
echo.

echo Running connectedDebugAndroidTest...
call gradlew.bat --no-daemon --console=plain connectedDebugAndroidTest
if errorlevel 1 (
  echo.
  echo [FAILED] One or more device/instrumented tests failed.
  pause
  exit /b 1
)

echo.
echo [PASS] Connected Android tests completed successfully.
pause
