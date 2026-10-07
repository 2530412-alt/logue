package com.logue.frontend

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.coroutines.cancellation.CancellationException

val client = HttpClient {
    install(ContentNegotiation) { json() }
    // Sin esto, si el servidor no responde la app se queda en "Entrando..." mucho tiempo
    install(HttpTimeout) { requestTimeoutMillis = 10_000 }
}

/** Resultado del login: así la pantalla distingue contraseña incorrecta de servidor apagado. */
sealed interface ResultadoLogin {
    data class Exito(val sesion: LoginResponse) : ResultadoLogin
    data object CredencialesIncorrectas : ResultadoLogin
    data object SinConexion : ResultadoLogin
}

// BASE_URL ya no es una constante fija: cada plataforma define la suya (ver Platform.kt)
suspend fun login(username: String, password: String): ResultadoLogin = try {
    val res = client.post("$BASE_URL/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(username.trim(), password))
    }
    when (res.status) {
        HttpStatusCode.OK -> ResultadoLogin.Exito(res.body())
        HttpStatusCode.Unauthorized -> ResultadoLogin.CredencialesIncorrectas
        else -> ResultadoLogin.SinConexion
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    // Throwable y no Exception: en la versión web, Ktor avisa la falla de red con un Error,
    // y un catch (e: Exception) no lo atrapa (el botón se quedaba en "Entrando...")
    ResultadoLogin.SinConexion
}
