@echo off
rem Prueba el backend, sube a GitHub y lo pasa a main. Resultado en resultado_publicar.txt
cd /d "%~dp0"
set "LOG=%~dp0resultado_publicar.txt"
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo === PRUEBAS DEL BACKEND === > "%LOG%"
cd /d "%~dp0backend"
call gradlew.bat test --console=plain > "%~dp0resultado_backend.txt" 2>&1
if errorlevel 1 (echo ERROR_PRUEBAS_BACKEND ver resultado_backend.txt >> "%LOG%" & goto fin)
echo PRUEBAS_OK >> "%LOG%"
cd /d "%~dp0"
git add -A >> "%LOG%" 2>&1
git commit -m "CORS: aceptar la URL propia de Render para la web app en /web" >> "%LOG%" 2>&1
git push origin integracion-aquago >> "%LOG%" 2>&1
if errorlevel 1 (echo ERROR_PUSH_RAMA >> "%LOG%" & goto fin)
git push origin integracion-aquago:main >> "%LOG%" 2>&1
if errorlevel 1 (echo ERROR_PUSH_MAIN >> "%LOG%" & goto fin)
echo PUBLICADO_OK >> "%LOG%"
git log --oneline -2 >> "%LOG%" 2>&1
:fin
echo FIN >> "%LOG%"
