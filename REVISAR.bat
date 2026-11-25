@echo off
cd /d "%~dp0"
if not exist _revision mkdir _revision
copy /Y frontend\shared\src\webMain\kotlin\com\logue\frontend\Web.kt _revision\Web.kt
copy /Y frontend\shared\src\wasmJsMain\kotlin\com\logue\frontend\WebPlataforma.kt _revision\WebPlataforma.wasm.kt
copy /Y frontend\webApp\src\webMain\kotlin\com\logue\frontend\main.kt _revision\main.web.kt
copy /Y frontend\shared\src\commonMain\kotlin\com\logue\frontend\Plataforma.kt _revision\Plataforma.kt
copy /Y frontend\shared\src\commonMain\kotlin\com\logue\frontend\AuthApi.kt _revision\AuthApi.kt
copy /Y frontend\shared\src\commonMain\kotlin\com\logue\frontend\App.kt _revision\App.kt
copy /Y frontend\shared\src\commonMain\kotlin\com\logue\frontend\Offline.kt _revision\Offline.kt
dir /s /b frontend\shared\src\commonMain\kotlin > _revision\archivos.txt
dir /s /b frontend\shared\src\iosMain frontend\shared\src\jsMain frontend\shared\src\wasmJsMain frontend\shared\src\webMain frontend\shared\src\androidMain >> _revision\archivos.txt
echo FIN > _revision\listo.txt
