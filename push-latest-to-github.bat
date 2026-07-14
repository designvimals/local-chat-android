@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "PROJECT_ROOT=%~dp0"
set "DEFAULT_COMMIT_MESSAGE=Update Android chat app"

if /I "%~1"=="--help" goto :help

where git >nul 2>&1
if errorlevel 1 (
    echo ERROR: Git was not found on PATH.
    echo Install Git for Windows or enable Git in Android Studio, then try again.
    goto :failure
)

pushd "%PROJECT_ROOT%"

git rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 (
    echo ERROR: "%PROJECT_ROOT%" is not a Git repository.
    goto :failure_in_repo
)

git remote get-url origin >nul 2>&1
if errorlevel 1 (
    echo ERROR: This repository does not have an "origin" GitHub remote.
    goto :failure_in_repo
)

for /f "delims=" %%B in ('git branch --show-current') do set "CURRENT_BRANCH=%%B"
if not defined CURRENT_BRANCH (
    echo ERROR: Git is in detached HEAD state. Switch to a branch before publishing.
    goto :failure_in_repo
)

if /I not "%~1"=="--skip-build" (
    if not exist "%PROJECT_ROOT%build-android-apk.bat" (
        echo ERROR: build-android-apk.bat was not found.
        goto :failure_in_repo
    )
    echo Verifying the Android app before publishing...
    echo.
    call "%PROJECT_ROOT%build-android-apk.bat" --no-pause
    if errorlevel 1 goto :failure_in_repo
    echo.
)

echo GitHub remote:
git remote get-url origin
echo Current branch: %CURRENT_BRANCH%
echo.
echo Changes that will be published:
git status --short
if errorlevel 1 goto :failure_in_repo
echo.

choice /C YN /N /M "Stage, commit, and push all changes shown above? [Y/N] "
if errorlevel 2 (
    echo.
    echo Nothing was changed or pushed.
    goto :success_in_repo
)

set "COMMIT_MESSAGE="
set /P "COMMIT_MESSAGE=Commit message [%DEFAULT_COMMIT_MESSAGE%]: "
if not defined COMMIT_MESSAGE set "COMMIT_MESSAGE=%DEFAULT_COMMIT_MESSAGE%"
set "COMMIT_MESSAGE=%COMMIT_MESSAGE:"='%"

git add -A
if errorlevel 1 goto :failure_in_repo

git diff --cached --quiet
if errorlevel 1 (
    git commit -m "%COMMIT_MESSAGE%"
    if errorlevel 1 goto :failure_in_repo
) else (
    echo No uncommitted changes were found. Checking for unpushed commits...
)

git rev-parse --abbrev-ref --symbolic-full-name "@{u}" >nul 2>&1
if errorlevel 1 (
    git push --set-upstream origin "%CURRENT_BRANCH%"
) else (
    git push
)
if errorlevel 1 (
    echo.
    echo ERROR: GitHub rejected or could not complete the push.
    echo No automatic pull, merge, or rebase was attempted.
    goto :failure_in_repo
)

echo.
echo Published branch "%CURRENT_BRANCH%" to GitHub successfully.

:success_in_repo
popd
echo.
pause
exit /b 0

:failure_in_repo
popd

:failure
echo.
echo Publish stopped. Review the error above; no automatic history changes were made.
echo.
pause
exit /b 1

:help
echo Usage: push-latest-to-github.bat [--skip-build]
echo.
echo Double-click the file to build the Android APK, review all pending changes,
echo commit them, and push the current branch to the configured origin remote.
echo Use --skip-build only when an Android build has already been verified.
exit /b 0
