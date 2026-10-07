package com.logue

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

/** Módulo principal: instala CORS, serialización JSON y las rutas. */
fun Application.rootModule() {
    install(CORS) {
        // Solo para desarrollo: permite que la versión web (otro puerto) llame al backend.
        // En producción se limitaría a los dominios del frontend.
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Post)
    }
    configureSerialization()
    configureRouting()
}
