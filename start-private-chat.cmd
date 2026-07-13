@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-private-chat.ps1" -RelayUrl "%~1"
endlocal
