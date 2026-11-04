package com.logue.frontend

/**
 * Backend publicado en la nube (HTTPS). Es el que usan por defecto todas las plataformas,
 * así la app funciona en cualquier teléfono y con cualquier red (API-06).
 * Si el backend se mueve a Railway, solo cambia esta línea.
 */
const val SERVIDOR_NUBE = "https://logue-backend.onrender.com"

/**
 * Backend local (puerto 8081) visto desde cada plataforma (`actual`): el emulador de Android
 * llega a la computadora por 10.0.2.2; el simulador de iOS y el navegador, por localhost.
 */
expect val SERVIDOR_LOCAL: String

/** Dirección que usa la app. Por defecto la nube; Android la cambia al arrancar con [configurarServidor]. */
var BASE_URL: String = SERVIDOR_NUBE
    private set

/**
 * VARIABLE DE ENTORNO `SERVIDOR_URL` (API-07). Se escribe en `frontend/local.properties`, que no se
 * sube a GitHub, y Gradle la entrega a la app como `BuildConfig.SERVIDOR_URL`. Así cada integrante
 * cambia de servidor sin editar ningún archivo de código.
 */
fun configurarServidor(valor: String) {
    BASE_URL = resolverServidor(valor)
}

/**
 * - vacío o sin definir -> servidor en la nube
 * - `local`             -> backend en la computadora ([SERVIDOR_LOCAL])
 * - una URL             -> esa dirección (por ejemplo `http://localhost:8081` con `adb reverse`)
 */
fun resolverServidor(valor: String, local: String = SERVIDOR_LOCAL, nube: String = SERVIDOR_NUBE): String {
    val v = valor.trim().trimEnd('/')
    return when {
        v.isEmpty() -> nube
        v.equals("local", ignoreCase = true) -> local
        else -> v
    }
}
