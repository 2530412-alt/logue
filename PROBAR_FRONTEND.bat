@echo off
rem Compila y prueba la app movil (rama integracion-aquago). El resultado queda en resultado_frontend.txt
cd /d "%~dp0"
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo Compilando y probando... (puede tardar varios minutos) > resultado_frontend.txt
echo JAVA_HOME=%JAVA_HOME% >> resultado_frontend.txt
git branch --show-current >> resultado_frontend.txt 2>&1
cd frontend
call gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug --console=plain >> ..\resultado_frontend.txt 2>&1
echo CODIGO_SALIDA=%ERRORLEVEL% >> ..\resultado_frontend.txt
cd ..
echo FIN >> resultado_frontend.txt
