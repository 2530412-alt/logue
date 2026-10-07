package com.logue

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer

/** Punto de entrada. Usa el puerto 8081 (o el de la variable de entorno PORT, si existe). */
fun main(args: Array<String>) {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = port,
        host = "0.0.0.0",
        module = Application::rootModule
    ).start(wait = true)
}
