package com.logue.frontend

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * Dirección del backend Ktor. Cambia según dónde corre la app:
 * - Emulador Android: 10.0.2.2 es la computadora vista desde el emulador.
 * - Web (navegador): localhost, porque el navegador corre en la misma computadora.
 */
expect val BASE_URL: String
