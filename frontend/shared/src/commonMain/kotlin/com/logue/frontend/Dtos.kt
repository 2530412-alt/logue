package com.logue.frontend

import kotlinx.serialization.Serializable

/** Cuerpo que se envía a `POST /login`. */
@Serializable
data class LoginRequest(val username: String, val password: String)

/** Respuesta del backend cuando las credenciales son correctas. */
@Serializable
data class LoginResponse(val username: String, val role: String)

/** Roles que devuelve el backend (deben coincidir con `users.json`). */
object Roles {
    const val CIUDADANO = "CIUDADANO"
    const val OPERADOR = "OPERADOR"
    const val ADMIN = "ADMIN"
}
