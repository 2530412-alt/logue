package com.logue

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

// =====================================================================================================
//  Rutas del servicio de correo y de la validación con Google (ver Correo.kt y Google.kt)
// =====================================================================================================

/** Cuerpo de `POST /registro/verificar-correo`. */
@Serializable
data class VerificarCorreoRegistro(val usuario: String, val codigo: String)

/** Cuerpo de `POST /registro/reenviar-codigo`. */
@Serializable
data class ReenviarCodigo(val usuario: String)

/** Cuerpo de `POST /usuarios/yo/correo/verificar`. */
@Serializable
data class CodigoCorreo(val codigo: String)

/** Cuerpo de `POST /reportes/correo` (delegado). Sin fechas = los últimos 7 días. */
@Serializable
data class PedidoReporte(val desde: String? = null, val hasta: String? = null)

private suspend inline fun <reified T : Any> ApplicationCall.leerCuerpo(): T? = try {
    receive<T>()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    respond(HttpStatusCode.BadRequest, "Solicitud inválida")
    null
}

/**
 * reCAPTCHA: si está configurado y la petición viene de un navegador, exige una respuesta válida.
 * Devuelve `false` (y ya respondió 400) si no pasó.
 */
suspend fun ApplicationCall.captchaAprobado(captcha: Captcha, respuesta: String?): Boolean {
    if (!captcha.activo || !vieneDeNavegador(request.headers[HttpHeaders.Origin])) return true
    val ok = withContext(Dispatchers.IO) { captcha.valido(respuesta, clientIp()) }
    if (!ok) respond(HttpStatusCode.BadRequest, "Marca la casilla «No soy un robot»")
    return ok
}

/** Mismas reglas que /login para el estado de la cuenta. `null` si puede entrar. */
private fun mensajeEstado(user: User): String? = when (user.estado) {
    EstadoCuenta.EN_REVISION -> "Tu cuenta está en revisión. Te avisaremos cuando el delegado valide tu comprobante"
    EstadoCuenta.INACTIVA -> "Tu cuenta está desactivada. Comunícate con el delegado"
    else -> null
}

/** Valida "AAAA-MM-DD". */
private fun fecha(texto: String?): LocalDate? = texto?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun Route.correoGooglePublicas(
    users: UserRepository,
    tokens: TokenService,
    attempts: LoginAttempts,
    integraciones: Integraciones,
    avisos: Avisos
) {
    // GET /config/web -> datos públicos para la página web (ID de cliente de Google, clave de sitio del captcha)
    get("/config/web") {
        call.respond(ConfigWeb(integraciones.googleClientId, integraciones.recaptchaSiteKey, integraciones.correos.activo))
    }

    rateLimit(ACCOUNTS_LIMIT) {

        // POST /login/google {credential, captcha?} -> 200 + LoginResponse | 400 token o captcha inválido
        //   403 cuenta en revisión / desactivada | 404 ningún usuario tiene ese correo | 503 sin GOOGLE_CLIENT_ID
        post("/login/google") {
            val verificador = integraciones.google
            if (verificador == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, "El inicio con Google no está configurado en el servidor")
                return@post
            }
            val req = call.leerCuerpo<LoginGoogle>() ?: return@post
            if (req.credential.isBlank() || req.credential.length > 8_000) {
                call.respond(HttpStatusCode.BadRequest, "Solicitud inválida")
                return@post
            }
            if (!call.captchaAprobado(integraciones.captcha, req.captcha)) return@post
            val identidad = withContext(Dispatchers.IO) { verificador.verificar(req.credential) }
            if (identidad == null) {
                call.respond(HttpStatusCode.BadRequest, "Google no pudo confirmar tu cuenta. Intenta de nuevo")
                return@post
            }
            val user = users.findByCorreo(identidad.correo)
            if (user == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    "Ninguna cuenta de AquaGo tiene el correo ${identidad.correo}. Agrégalo en tu perfil o pide al delegado que lo registre"
                )
                return@post
            }
            // Mientras la cuenta esté bloqueada por intentos fallidos tampoco entra con Google (USR-20)
            attempts.secondsLocked(user.username)?.let { seconds ->
                call.response.header(HttpHeaders.RetryAfter, seconds.toString())
                call.respond(HttpStatusCode.TooManyRequests, "Demasiados intentos. Espera unos minutos")
                return@post
            }
            mensajeEstado(user)?.let {
                call.respond(HttpStatusCode.Forbidden, it)
                return@post
            }
            // Google ya comprobó que el correo es suyo
            val cuenta = if (user.correoVerificado) user else user.copy(correoVerificado = true).also { users.update(it) }
            call.respond(LoginResponse(cuenta.username, cuenta.role, tokens.create(cuenta), tokens.validity.seconds))
        }

        // POST /registro/verificar-correo {usuario, codigo} -> 204 | 400 código incorrecto o vencido
        // Se usa justo después de crear la cuenta (todavía no puede iniciar sesión porque está en revisión).
        post("/registro/verificar-correo") {
            val req = call.leerCuerpo<VerificarCorreoRegistro>() ?: return@post
            val user = users.find(req.usuario)
            if (user == null || user.correo.isBlank() || !integraciones.verificaciones.verify(user.username, req.codigo)) {
                call.respond(HttpStatusCode.BadRequest, "Código incorrecto o vencido. Pide uno nuevo")
                return@post
            }
            users.update(user.copy(correoVerificado = true))
            call.respond(HttpStatusCode.NoContent)
        }

        // POST /registro/reenviar-codigo {usuario} -> 202 siempre (no revela si la cuenta existe)
        post("/registro/reenviar-codigo") {
            val req = call.leerCuerpo<ReenviarCodigo>() ?: return@post
            val user = users.find(req.usuario)
            if (user != null && user.correo.isNotBlank() && !user.correoVerificado) {
                avisos.codigoVerificacion(user.correo, user.nombre.ifBlank { user.username }, integraciones.verificaciones.issue(user.username))
            }
            call.respond(HttpStatusCode.Accepted, "Si la cuenta tiene un correo sin verificar, enviamos un código nuevo")
        }
    }
}

fun Route.correoAutenticadas(
    users: UserRepository,
    solicitudes: SolicitudStore,
    integraciones: Integraciones,
    avisos: Avisos,
    clock: Clock
) {
    rateLimit(ACCOUNTS_LIMIT) {
        // POST /usuarios/yo/correo/codigo -> 202 | 400 la cuenta no tiene correo | 409 ya está verificado
        post("/usuarios/yo/correo/codigo") {
            val user = users.find(call.session.username)
            when {
                user == null -> call.respond(HttpStatusCode.NotFound)
                user.correo.isBlank() -> call.respond(HttpStatusCode.BadRequest, "Primero agrega un correo en tu perfil")
                user.correoVerificado -> call.respond(HttpStatusCode.Conflict, "Tu correo ya está verificado")
                else -> {
                    avisos.codigoVerificacion(user.correo, user.nombre.ifBlank { user.username }, integraciones.verificaciones.issue(user.username))
                    call.respond(HttpStatusCode.Accepted, "Te enviamos un código a ${user.correo}")
                }
            }
        }

        // POST /usuarios/yo/correo/verificar {codigo} -> 200 + Perfil | 400 código incorrecto o vencido
        post("/usuarios/yo/correo/verificar") {
            val req = call.leerCuerpo<CodigoCorreo>() ?: return@post
            val user = users.find(call.session.username)
            if (user == null || user.correo.isBlank() || !integraciones.verificaciones.verify(user.username, req.codigo)) {
                call.respond(HttpStatusCode.BadRequest, "Código incorrecto o vencido. Pide uno nuevo")
                return@post
            }
            val nuevo = user.copy(correoVerificado = true)
            users.update(nuevo)
            call.respond(nuevo.toPerfil())
        }

        // POST /reportes/correo {desde?, hasta?} -> 202 | 400 fechas o correo no verificado | 403 no es delegado
        post("/reportes/correo") {
            if (!call.requireRole(Roles.ADMIN)) return@post
            val req = call.leerCuerpo<PedidoReporte>() ?: return@post
            val periodo = periodo(req.desde, req.hasta, clock) ?: run {
                call.respond(HttpStatusCode.BadRequest, "Las fechas deben tener el formato AAAA-MM-DD y no pasar de 93 días")
                return@post
            }
            val yo = users.find(call.session.username)
            if (yo == null || yo.correo.isBlank() || !yo.correoVerificado) {
                call.respond(HttpStatusCode.BadRequest, "Agrega y verifica tu correo en el perfil para recibir reportes")
                return@post
            }
            avisos.reporte(yo.correo, calcularReporte(solicitudes.all(), periodo.first, periodo.second))
            call.respond(HttpStatusCode.Accepted, "Enviamos el reporte a ${yo.correo}")
        }
    }

    // GET /reportes/resumen?desde=AAAA-MM-DD&hasta=AAAA-MM-DD -> 200 + ReporteEntregas (delegado; sin fechas = 7 días)
    get("/reportes/resumen") {
        if (!call.requireRole(Roles.ADMIN)) return@get
        val q = call.request.queryParameters
        val periodo = periodo(q["desde"], q["hasta"], clock) ?: run {
            call.respond(HttpStatusCode.BadRequest, "Las fechas deben tener el formato AAAA-MM-DD y no pasar de 93 días")
            return@get
        }
        call.respond(calcularReporte(solicitudes.all(), periodo.first, periodo.second))
    }
}

/** Periodo del reporte: por omisión los últimos 7 días; máximo 93 días (un trimestre). */
private fun periodo(desdeTexto: String?, hastaTexto: String?, clock: Clock): Pair<String, String>? {
    val hoy = LocalDate.now(clock.withZone(appZone()))
    val hasta = if (hastaTexto.isNullOrBlank()) hoy else fecha(hastaTexto) ?: return null
    val desde = if (desdeTexto.isNullOrBlank()) hasta.minusDays(6) else fecha(desdeTexto) ?: return null
    if (desde.isAfter(hasta) || desde.plusDays(93).isBefore(hasta)) return null
    return desde.toString() to hasta.toString()
}
