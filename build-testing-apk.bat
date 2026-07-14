@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
set "ANDROID_DIR=%PROJECT_ROOT%android"
set "APK_PATH=%ANDROID_DIR%\app\build\outputs\apk\sandbox\app-sandbox.apk"

echo Building the local-only testing _001 APK...
echo.

if not exist "%ANDROID_DIR%\gradlew.bat" (
    echo ERROR: Android Gradle wrapper was not found.
    goto :failure
)

pushd "%ANDROID_DIR%"
call gradlew.bat :app:assembleSandbox
set "BUILD_RESULT=%ERRORLEVEL%"
popd

if not "%BUILD_RESULT%"=="0" goto :failure
if not exist "%APK_PATH%" (
    echo ERROR: Gradle completed, but the sandbox APK was not found.
    echo Expected: "%APK_PATH%"
    goto :failure
)

echo.
echo testing _001 built successfully.
echo APK: "%APK_PATH%"
goto :finish

:failure
echo.
echo Testing APK build failed. Review the errors above.
set "BUILD_RESULT=1"

:finish
echo.
if /I not "%~1"=="--no-pause" pause
exit /b %BUILD_RESULT%
