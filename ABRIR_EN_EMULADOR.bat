@echo off
rem Abre el emulador (si no esta abierto), instala la app integrada y la inicia. Bitacora en resultado_emulador.txt
cd /d "%~dp0"
set "SDK=%LOCALAPPDATA%\Android\Sdk"
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo Emuladores disponibles: > resultado_emulador.txt
"%SDK%\emulator\emulator.exe" -list-avds >> resultado_emulador.txt 2>&1
"%SDK%\platform-tools\adb.exe" devices >> resultado_emulador.txt 2>&1
"%SDK%\platform-tools\adb.exe" devices | findstr /R "emulator-[0-9]*.device" >nul
if errorlevel 1 (
  for /f "usebackq delims=" %%A in (`"%SDK%\emulator\emulator.exe" -list-avds`) do (
    if not defined AVD set "AVD=%%A"
  )
)
if defined AVD (
  echo Iniciando emulador %AVD% >> resultado_emulador.txt
  start "" "%SDK%\emulator\emulator.exe" -avd %AVD%
)
"%SDK%\platform-tools\adb.exe" wait-for-device
:esperar
"%SDK%\platform-tools\adb.exe" shell getprop sys.boot_completed 2>nul | findstr 1 >nul
if errorlevel 1 ( timeout /t 3 >nul & goto esperar )
cd frontend
call gradlew.bat :androidApp:installDebug --console=plain >> ..\resultado_emulador.txt 2>&1
cd ..
"%SDK%\platform-tools\adb.exe" shell am start -n com.logue.frontend/.MainActivity >> resultado_emulador.txt 2>&1
echo FIN >> resultado_emulador.txt
