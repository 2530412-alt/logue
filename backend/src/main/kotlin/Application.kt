package com.logue

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import java.io.IOException

/** Módulo que arranca `main.kt`: toma la configuración real (users.json, variables de entorno). */
fun Application.rootModule() = aquaGoModule()

/**
 * Arma el servidor. Cada pieza se puede reemplazar en las pruebas (usuarios, almacén de fotos,
 * reloj de los tokens, etc.), así las pruebas no dependen de archivos ni de variables de entorno.
 */
fun Application.aquaGoModule(
    users: UserRepository = JsonUserRepository(loadUsers()),
    pagos: PagoStore = defaultPagoStore(),
    remote: RemoteCopier? = loadRemoteCopier(),
    tokens: TokenService = TokenService.fromEnvironment(),
    attempts: LoginAttempts = LoginAttempts(),
    corsOrigins: List<String> = corsOriginsFromEnvironment(),
    loginsPerMinute: Int = 20
) {
    configureLogging()
    configureCors(corsOrigins)
    configureSerialization()
    configureErrors()
    configureSecurity(tokens)
    configureRateLimit(loginsPerMinute)
    configureRouting(users, pagos, remote, tokens, attempts)
}

/**
 * REGISTRO DE PETICIONES (SRV-12): una línea por petición con método, ruta, código, duración e IP.
 * Nunca registra el token ni la contraseña (no se imprimen encabezados ni cuerpos).
 * En la nube se lee con `railway logs` o en la pestaña Logs de Render.
 */
fun Application.configureLogging() {
    install(CallLogging) {
        format { call ->
            val status = call.response.status()?.value ?: "-"
            val ms = call.processingTimeMillis()
            "${call.request.httpMethod.value} ${call.request.path()} -> $status (${ms} ms) desde ${call.clientIp()}"
        }
    }
}

/**
 * CORS (SRV-07): solo los navegadores lo aplican; la app de Android no lo necesita.
 *
 * VARIABLE DE ENTORNO `CORS_ORIGINS`: dominios permitidos separados por comas, por ejemplo
 * `https://2530412-alt.github.io`. Si no existe (desarrollo local), se acepta cualquier origen.
 */
fun Application.configureCors(origins: List<String>) {
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        // Sin esto el navegador no deja mandar el token (Authorization: Bearer ...)
        allowHeader(HttpHeaders.Authorization)
        exposeHeader(HttpHeaders.RetryAfter)
        if (origins.isEmpty()) {
            LoggerFactory.getLogger("com.logue.Cors").warn("CORS_ORIGINS no está definido: se acepta cualquier origen")
            anyHost()
        } else {
            origins.forEach { origin ->
                val scheme = origin.substringBefore("://", "https")
                allowHost(origin.substringAfter("://").trimEnd('/'), schemes = listOf(scheme))
            }
        }
    }
}

fun corsOriginsFromEnvironment(env: Map<String, String> = System.getenv()): List<String> =
    env["CORS_ORIGINS"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Errores que no atrapa cada ruta. Ktor deja de leer una foto al pasar el límite de
 * `receiveMultipart(formFieldLimit)` y lanza una IOException; aquí se convierte en 413.
 */
fun Application.configureErrors() {
    install(StatusPages) {
        exception<IOException> { call, cause ->
            if (cause.message.orEmpty().startsWith("Limit of")) {
                call.respond(HttpStatusCode.PayloadTooLarge, "La foto pesa más de 10 MB")
            } else {
                call.application.log.warn("Error de lectura: ${cause.message}")
                call.respond(HttpStatusCode.BadRequest, "Solicitud inválida")
            }
        }
    }
}
