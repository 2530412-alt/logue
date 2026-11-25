@echo off
rem Enciende el backend Ktor en el puerto 8081 (dejar esta ventana abierta)
cd /d "%~dp0backend"
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
call gradlew.bat run
pause
