@echo off
rem Sube los cambios de AquaGo a GitHub (rama integracion-aquago). Resultado en resultado_subir.txt
cd /d "%~dp0"
echo === ADD === > resultado_subir.txt
git checkout integracion-aquago >> resultado_subir.txt 2>&1
git add -A >> resultado_subir.txt 2>&1
echo === COMMIT === >> resultado_subir.txt
git commit -m "AquaGo: app movil y panel web del delegado (Google, reCAPTCHA, correo, modo sin conexion)" >> resultado_subir.txt 2>&1
echo === PUSH === >> resultado_subir.txt
git push -u origin integracion-aquago >> resultado_subir.txt 2>&1
echo PUSH_CODIGO=%ERRORLEVEL% >> resultado_subir.txt
git status --short --branch >> resultado_subir.txt 2>&1
git log --oneline -3 >> resultado_subir.txt 2>&1
echo FIN >> resultado_subir.txt
