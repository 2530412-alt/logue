package com.logue

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

/** Nombre del proveedor de autenticación que usan las rutas protegidas: `authenticate(AUTH_JWT) { ... }`. */
const val AUTH_JWT = "jwt"

private val log = LoggerFactory.getLogger("com.logue.Security")

/**
 * TOKEN DE SESIÓN (USR-16). Al iniciar sesión, el servidor firma un JWT con el usuario y su rol;
 * la app lo manda en cada petición como `Authorization: Bearer <token>`. Así el servidor sabe
 * quién hace cada petición sin confiar en un campo `username` que cualquiera puede cambiar.
 *
 * Se eligió JWT (y no un token guardado en memoria) porque Render gratuito se duerme y se
 * reinicia seguido: un JWT firmado con [secret] sigue siendo válido después de un reinicio.
 *
 * VARIABLE DE ENTORNO `JWT_SECRET`: clave para firmar (mínimo 32 caracteres). Si no existe
 * se genera una al azar al arrancar; sirve en local, pero en la nube cerraría todas las
 * sesiones en cada reinicio, por eso hay que definirla en Render o Railway.
 */
class TokenService(
    private val secret: String,
    val validity: Duration = Duration.ofHours(12),
    private val clock: Clock = Clock.systemUTC()
) {
    val issuer = "aquago-backend"
    val audience = "aquago-app"
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier = JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()

    fun create(user: User): String {
        val now = clock.instant()
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(user.username)
            .withClaim("role", user.role)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plus(validity)))
            .sign(algorithm)
    }

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): TokenService {
            val configured = env["JWT_SECRET"]?.trim().orEmpty()
            val secret = when {
                configured.length >= 32 -> configured
                configured.isNotEmpty() -> error("JWT_SECRET debe tener al menos 32 caracteres")
                else -> {
                    log.warn("JWT_SECRET no está definido: se usa una clave temporal y las sesiones se cierran al reiniciar")
                    randomSecret()
                }
            }
            return TokenService(secret)
        }

        fun randomSecret(): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(48).also(SecureRandom()::nextBytes))
    }
}

/** Usuario dueño del token de la petición actual. */
data class Session(val username: String, val role: String)

fun Application.configureSecurity(tokens: TokenService) {
    install(Authentication) {
        jwt(AUTH_JWT) {
            realm = "AquaGo"
            verifier(tokens.verifier)
            validate { credential ->
                val username = credential.payload.subject
                val role = credential.payload.getClaim("role").asString()
                if (username.isNullOrBlank() || role.isNullOrBlank()) null else Session(username, role)
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Tu sesión expiró o no es válida. Inicia sesión de nuevo")
            }
        }
    }
}

/** Sesión de la petición. Solo se llama dentro de `authenticate(AUTH_JWT)`, donde siempre existe. */
val ApplicationCall.session: Session
    get() = principal<Session>() ?: error("Ruta sin authenticate(AUTH_JWT)")

/**
 * CONTROL DE ACCESO POR ROL (USR-17). Responde 403 y devuelve `false` si el rol del usuario
 * no está en [allowed]; la ruta debe terminar ahí (`if (!call.requireRole(...)) return@get`).
 */
suspend fun ApplicationCall.requireRole(vararg allowed: String): Boolean {
    if (session.role in allowed) return true
    respond(HttpStatusCode.Forbidden, "Tu cuenta no tiene permiso para esta acción")
    return false
}

/**
 * BLOQUEO POR INTENTOS FALLIDOS (USR-20, RFM-NN-01 y RFW-NN-01 del SRS): después de
 * [maxFailures] contraseñas incorrectas seguidas, la cuenta queda bloqueada [lockDuration].
 * Mientras dure el bloqueo el login responde 429 aunque la contraseña sea correcta.
 *
 * Vive en memoria: si el servidor se reinicia, el contador vuelve a cero. Es suficiente
 * para frenar a quien prueba contraseñas; en la Fase 2 se puede guardar en MySQL.
 */
class LoginAttempts(
    private val maxFailures: Int = 3,
    private val lockDuration: Duration = Duration.ofMinutes(5),
    /** Si pasa este tiempo sin fallar, el contador vuelve a cero (un error al día no bloquea a nadie). */
    private val failureWindow: Duration = Duration.ofMinutes(15),
    private val clock: Clock = Clock.systemUTC()
) {
    private data class State(val failures: Int, val lastFailure: Long, val lockedUntil: Long)

    private val states = ConcurrentHashMap<String, State>()

    private fun key(username: String) = username.trim().lowercase()

    /** Segundos que faltan para que se desbloquee la cuenta, o `null` si no está bloqueada. */
    fun secondsLocked(username: String): Long? {
        val state = states[key(username)] ?: return null
        val remainingMs = state.lockedUntil - clock.millis()
        return if (remainingMs > 0) (remainingMs + 999) / 1000 else null
    }

    /** Registra una contraseña incorrecta. Devuelve los segundos de bloqueo si con esta se bloqueó la cuenta. */
    fun registerFailure(username: String): Long? {
        val now = clock.millis()
        val updated = states.compute(key(username)) { _, old ->
            val expired = old == null ||
                (old.lockedUntil != 0L && old.lockedUntil <= now) ||
                now - old.lastFailure > failureWindow.toMillis()
            val failures = (if (expired) 0 else old.failures) + 1
            State(failures, now, if (failures >= maxFailures) now + lockDuration.toMillis() else 0L)
        }!!
        return if (updated.lockedUntil != 0L) lockDuration.seconds else null
    }

    fun registerSuccess(username: String) {
        states.remove(key(username))
    }
}

/** Límite para las rutas públicas de cuentas: hoy `POST /login`; en la Fase 2, registro y recuperación. */
val ACCOUNTS_LIMIT = RateLimitName("cuentas")

/**
 * LIMITACIÓN DE PETICIONES (SRV-14): cada dirección IP puede hacer como máximo [perMinute]
 * peticiones por minuto a las rutas de cuentas. Frena la fuerza bruta contra muchas cuentas
 * a la vez (el bloqueo por intentos protege a una sola). Al pasarse, Ktor responde 429.
 */
fun Application.configureRateLimit(perMinute: Int = 20) {
    install(RateLimit) {
        register(ACCOUNTS_LIMIT) {
            rateLimiter(limit = perMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }
    }
}

/**
 * IP real del cliente. Render y Railway ponen un proxy delante del servidor y pasan la IP
 * original en `X-Forwarded-For`; sin esto, todos los usuarios compartirían la IP del proxy
 * y el límite bloquearía a todos a la vez.
 */
fun ApplicationCall.clientIp(): String =
    request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.origin.remoteHost
