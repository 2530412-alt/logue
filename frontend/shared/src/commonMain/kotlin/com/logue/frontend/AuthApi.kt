package com.logue.frontend

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.coroutines.cancellation.CancellationException

val client = HttpClient {
    install(ContentNegotiation) { json() }
    // Si el backend no responde, no dejamos la pantalla en "Entrando..." para siempre.
    install(HttpTimeout) { requestTimeoutMillis = 8_000 }
}

/** Posibles resultados de un intento de login, para mostrar un mensaje distinto en cada caso. */
sealed interface LoginResult {
    data class Success(val session: LoginResponse) : LoginResult
    data object InvalidCredentials : LoginResult
    data object ServerError : LoginResult
    data object NetworkError : LoginResult
}

/** Valida los campos antes de llamar al backend. Devuelve el mensaje de error, o `null` si todo está bien. */
fun validateCredentials(username: String, password: String): String? = when {
    username.isBlank() -> "Escribe tu usuario"
    password.isBlank() -> "Escribe tu contraseña"
    else -> null
}

/** Llama a `POST /login`. Nunca lanza excepciones: siempre devuelve un [LoginResult]. */
suspend fun login(username: String, password: String): LoginResult = try {
    val res = client.post("$BASE_URL/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(username.trim(), password))
    }
    when (res.status) {
        HttpStatusCode.OK -> LoginResult.Success(res.body())
        HttpStatusCode.Unauthorized -> LoginResult.InvalidCredentials
        else -> LoginResult.ServerError
    }
} catch (e: CancellationException) {
    throw e // no tragarse la cancelación de la corrutina
} catch (e: Exception) {
    LoginResult.NetworkError
}
