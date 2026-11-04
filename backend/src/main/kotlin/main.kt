package com.logue

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer

/**
 * Punto de entrada. Usa el puerto 8081 (o el de la variable de entorno PORT, si existe).
 *
 * Además tiene dos comandos de apoyo:
 *     gradlew run --args="hash MiContraseña"            hash BCrypt para agregar usuarios a users.json
 *     gradlew run --args="probar https://servidor..."   pruebas funcionales y de rendimiento (Probador.kt)
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "probar") {
        val url = args.getOrNull(1) ?: run {
            System.err.println("Uso: gradlew run --args=\"probar https://tu-servicio.up.railway.app\"")
            return
        }
        Probador.ejecutar(url, args.getOrNull(2) ?: "1234")
        return
    }
    if (args.firstOrNull() == "hash") {
        val plain = args.getOrNull(1) ?: run {
            System.err.println("Uso: gradlew run --args=\"hash MiContraseña\"")
            return
        }
        println(Passwords.hash(plain))
        return
    }

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = port,
        host = "0.0.0.0",
        module = Application::rootModule
    ).start(wait = true)
}
