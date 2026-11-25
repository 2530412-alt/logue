@echo off
rem Compila la app movil y la pagina web y corre las pruebas. Resultado en resultado_compilar.txt
cd /d "%~dp0frontend"
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo Compilando... > ..\resultado_compilar.txt
call gradlew.bat :shared:testAndroidHostTest :webApp:compileKotlinWasmJs :webApp:compileKotlinJs :androidApp:assembleDebug --continue --console=plain >> ..\resultado_compilar.txt 2>&1
echo CODIGO_SALIDA=%ERRORLEVEL% >> ..\resultado_compilar.txt
echo FIN >> ..\resultado_compilar.txt
