package com.logue.frontend

/**
 * URL base del backend Ktor (puerto 8081).
 *
 * Cada plataforma define su propio valor (`actual`), porque la forma de
 * "ver" la computadora donde corre el backend cambia según dónde corre la app.
 */
expect val BASE_URL: String
