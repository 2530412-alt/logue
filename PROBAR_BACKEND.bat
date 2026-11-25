@echo off
rem Corre las pruebas del backend. El resultado queda en resultado_backend.txt
cd /d "%~dp0backend"
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo Probando backend... > ..\resultado_backend.txt
call gradlew.bat test --console=plain >> ..\resultado_backend.txt 2>&1
echo CODIGO_SALIDA=%ERRORLEVEL% >> ..\resultado_backend.txt
echo FIN >> ..\resultado_backend.txt
