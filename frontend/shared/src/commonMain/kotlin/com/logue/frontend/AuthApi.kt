package com.logue.frontend

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

val client = HttpClient {
    // ignoreUnknownKeys: si el servidor agrega campos nuevos (Fase 2), una app ya instalada no deja de funcionar
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    // Si el backend no responde, no dejamos la pantalla en "Entrando..." para siempre.
    install(HttpTimeout) { requestTimeoutMillis = 8_000 }
}

/**
 * Render gratuito se duerme tras 15 minutos sin uso y tarda cerca de un minuto en despertar
 * (API-08). Las peticiones que pueden encontrarlo dormido esperan hasta este tiempo.
 */
const val ESPERA_SERVIDOR_DORMIDO_MS = 75_000L

/** Mensaje para el ciudadano cuando no hay conexión (antes decía «¿Está encendido el backend?»). */
const val MENSAJE_SIN_CONEXION = "No pudimos conectar con AquaGo. Revisa tu internet e intenta de nuevo"

/** Posibles resultados de un intento de login, para mostrar un mensaje distinto en cada caso. */
sealed interface LoginResult {
    data class Success(val session: LoginResponse) : LoginResult
    data object InvalidCredentials : LoginResult
    /** 429: la cuenta se bloqueó por intentos fallidos o se pasó el límite de peticiones (USR-20). */
    data class Locked(val retryAfterSeconds: Long?) : LoginResult
    /** 403: la cuenta está en revisión (USR-11) o desactivada. [mensaje] viene del servidor. */
    data class AccountBlocked(val mensaje: String) : LoginResult
    data object ServerError : LoginResult
    data object NetworkError : LoginResult
}

/** Valida los campos antes de llamar al backend. Devuelve el mensaje de error, o `null` si todo está bien. */
fun validateCredentials(username: String, password: String): String? = when {
    username.isBlank() -> "Escribe tu usuario"
    password.isBlank() -> "Escribe tu contraseña"
    else -> null
}

/** "Espera 5 minutos" a partir del encabezado Retry-After (segundos). */
fun lockedMessage(retryAfterSeconds: Long?): String {
    val minutes = ((retryAfterSeconds ?: 300L) + 59) / 60
    return "Demasiados intentos. Espera ${maxOf(1L, minutes)} " +
        (if (minutes <= 1) "minuto" else "minutos") + " e intenta de nuevo"
}

/** Llama a `POST /login`. Nunca lanza excepciones: siempre devuelve un [LoginResult]. */
suspend fun login(username: String, password: String): LoginResult = try {
    val res = client.post("$BASE_URL/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(username.trim(), password))
        timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
    }
    when (res.status) {
        HttpStatusCode.OK -> LoginResult.Success(res.body())
        HttpStatusCode.Unauthorized -> LoginResult.InvalidCredentials
        HttpStatusCode.TooManyRequests -> LoginResult.Locked(res.headers[HttpHeaders.RetryAfter]?.toLongOrNull())
        HttpStatusCode.Forbidden -> LoginResult.AccountBlocked(res.bodyAsText())
        else -> LoginResult.ServerError
    }
} catch (e: CancellationException) {
    throw e // no tragarse la cancelación de la corrutina
} catch (e: Throwable) {
    // Throwable y no Exception: en la versión web, Ktor avisa la falla de red con un Error,
    // y un catch (e: Exception) no lo atrapa (el botón se quedaba en "Entrando...").
    LoginResult.NetworkError
}

/**
 * Despierta al servidor mientras el usuario escribe su usuario y contraseña (API-08):
 * así, al presionar «Iniciar sesión», normalmente ya está listo. Ignora cualquier error.
 */
suspend fun despertarServidor() {
    try {
        client.get("$BASE_URL/salud") { timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // Sin conexión: el login mostrará el mensaje cuando el usuario lo intente
    }
}
