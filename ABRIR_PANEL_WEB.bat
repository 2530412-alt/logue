@echo off
rem Prueba y abre el panel web del delegado (http://localhost:8080). Dejar esta ventana abierta.
rem Antes enciende el backend con INICIAR_BACKEND.bat
cd /d "%~dp0frontend"
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
call gradlew.bat :shared:testAndroidHostTest --console=plain > ..\resultado_web.txt 2>&1
echo PRUEBAS_CODIGO=%ERRORLEVEL% >> ..\resultado_web.txt
call gradlew.bat :webApp:wasmJsBrowserDevelopmentRun --console=plain
pause
