package com.logue.frontend

import kotlinx.serialization.Serializable

/** Cuerpo que se envía a `POST /login`. */
@Serializable
data class LoginRequest(val username: String, val password: String)

/** Respuesta del backend cuando las credenciales son correctas. */
@Serializable
data class LoginResponse(val username: String, val role: String)

/** Respuesta del backend al guardar la foto de un pago predial (`POST /pagos-prediales`). */
@Serializable
data class PagoPredialResponse(
    val id: String,
    val username: String,
    val folio: String,
    val fecha: String,
    val tipo: String,
    val tamano: Long
)

/** Roles que devuelve el backend (deben coincidir con `users.json`). */
object Roles {
    const val CIUDADANO = "CIUDADANO"
    const val OPERADOR = "OPERADOR"
    const val ADMIN = "ADMIN"
}
