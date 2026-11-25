@echo off
rem Revisa el estado de git de AquaGo. Resultado en resultado_git.txt
cd /d "%~dp0"
echo === RAMA === > resultado_git.txt
git branch -vv >> resultado_git.txt 2>&1
echo === FETCH === >> resultado_git.txt
git fetch origin >> resultado_git.txt 2>&1
echo === STATUS === >> resultado_git.txt
git status --short --branch >> resultado_git.txt 2>&1
echo === LOG LOCAL === >> resultado_git.txt
git log --oneline -15 >> resultado_git.txt 2>&1
echo === COMMITS SIN SUBIR (vs origin/integracion-aquago) === >> resultado_git.txt
git log --oneline origin/integracion-aquago..HEAD >> resultado_git.txt 2>&1
echo === COMMITS NUEVOS EN MAIN QUE NO TIENES === >> resultado_git.txt
git log --oneline HEAD..origin/main >> resultado_git.txt 2>&1
echo === TUS COMMITS QUE NO ESTAN EN MAIN === >> resultado_git.txt
git log --oneline origin/main..HEAD >> resultado_git.txt 2>&1
echo === RAMAS REMOTAS === >> resultado_git.txt
git branch -r >> resultado_git.txt 2>&1
echo === USUARIO === >> resultado_git.txt
git config user.name >> resultado_git.txt 2>&1
git config user.email >> resultado_git.txt 2>&1
echo FIN >> resultado_git.txt
