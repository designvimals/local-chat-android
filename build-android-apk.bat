@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
set "ANDROID_DIR=%PROJECT_ROOT%android"
set "APK_PATH=%ANDROID_DIR%\app\build\outputs\apk\debug\app-debug.apk"

echo Building the latest Android debug APK...
echo.

if not exist "%ANDROID_DIR%\gradlew.bat" (
    echo ERROR: Android Gradle wrapper was not found.
    echo Expected: "%ANDROID_DIR%\gradlew.bat"
    goto :failure
)

pushd "%ANDROID_DIR%"
call gradlew.bat assembleDebug
set "BUILD_RESULT=%ERRORLEVEL%"
popd

if not "%BUILD_RESULT%"=="0" goto :failure
if not exist "%APK_PATH%" (
    echo ERROR: Gradle completed, but the APK was not found.
    echo Expected: "%APK_PATH%"
    goto :failure
)

echo.
echo APK build completed successfully.
echo APK: "%APK_PATH%"
goto :finish

:failure
echo.
echo APK build failed. Review the errors above.
set "BUILD_RESULT=1"

:finish
echo.
if /I not "%~1"=="--no-pause" pause
exit /b %BUILD_RESULT%
