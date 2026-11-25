@echo off
rem Compila la app web (Wasm), la copia al backend, prueba, sube a GitHub y la pasa a main (Render se redespliega solo).
rem Resultado en resultado_publicar.txt
cd /d "%~dp0"
set "LOG=%~dp0resultado_publicar.txt"
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo === 1. COMPILAR WEB === > "%LOG%"
cd /d "%~dp0frontend"
call gradlew.bat :webApp:wasmJsBrowserDistribution --console=plain >> "%LOG%" 2>&1
if errorlevel 1 (echo ERROR_COMPILAR_WEB >> "%LOG%" & goto fin)
set "DIST=%~dp0frontend\webApp\build\dist\wasmJs\productionExecutable"
if not exist "%DIST%\index.html" (echo ERROR_NO_HAY_DIST >> "%LOG%" & goto fin)
echo === 2. COPIAR AL BACKEND === >> "%LOG%"
set "WEB=%~dp0backend\src\main\resources\web"
if exist "%WEB%" rmdir /s /q "%WEB%"
xcopy "%DIST%" "%WEB%\" /E /I /Y >> "%LOG%" 2>&1
dir "%WEB%" >> "%LOG%" 2>&1
echo === 3. PRUEBAS DEL BACKEND === >> "%LOG%"
cd /d "%~dp0backend"
call gradlew.bat test --console=plain > "%~dp0resultado_backend.txt" 2>&1
if errorlevel 1 (echo ERROR_PRUEBAS_BACKEND ver resultado_backend.txt >> "%LOG%" & goto fin)
echo PRUEBAS_OK >> "%LOG%"
echo === 4. GIT === >> "%LOG%"
cd /d "%~dp0"
git add -A >> "%LOG%" 2>&1
git commit -m "Web app de AquaGo (Wasm) servida por el backend en /web" >> "%LOG%" 2>&1
git push origin integracion-aquago >> "%LOG%" 2>&1
if errorlevel 1 (echo ERROR_PUSH_RAMA >> "%LOG%" & goto fin)
git fetch origin >> "%LOG%" 2>&1
git push origin integracion-aquago:main >> "%LOG%" 2>&1
if errorlevel 1 (echo ERROR_PUSH_MAIN >> "%LOG%" & goto fin)
echo PUBLICADO_OK >> "%LOG%"
git log --oneline -3 >> "%LOG%" 2>&1
:fin
echo FIN >> "%LOG%"
