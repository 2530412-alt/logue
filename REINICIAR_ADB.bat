@echo off
rem Reinicia adb cuando el emulador sale como "unauthorized"
set "SDK=%LOCALAPPDATA%\Android\Sdk"
"%SDK%\platform-tools\adb.exe" kill-server
"%SDK%\platform-tools\adb.exe" start-server
timeout /t 5 >nul
"%SDK%\platform-tools\adb.exe" devices > "%~dp0resultado_adb.txt" 2>&1
