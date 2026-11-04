package com.logue

import kotlinx.serialization.Serializable

/** Roles del sistema. En el SRS: CIUDADANO = Ciudadano, OPERADOR = Repartidor, ADMIN = Delegado. */
object Roles {
    const val CIUDADANO = "CIUDADANO"
    const val OPERADOR = "OPERADOR"
    const val ADMIN = "ADMIN"
}

/**
 * Usuario guardado en `users.json`. Solo se guarda el hash BCrypt de la contraseña (USR-15),
 * nunca el texto. Para generar un hash: `gradlew run --args="hash MiContraseña"`.
 */
@Serializable
data class User(val username: String, val passwordHash: String, val role: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

/**
 * Respuesta de `POST /login`. La app guarda el [token] y lo manda en cada petición como
 * `Authorization: Bearer <token>` (USR-16). [expiresIn] son los segundos que dura el token.
 */
@Serializable
data class LoginResponse(val username: String, val role: String, val token: String, val expiresIn: Long)

/** Respuesta de `GET /salud`: confirma que el servidor está vivo y qué commit quedó desplegado (SRV-13). */
@Serializable
data class Salud(
    val estado: String,
    val servicio: String,
    val plataforma: String,
    val commit: String?,
    val segundosActivo: Long
)

/** Datos de un pago predial guardado: la foto del recibo vive en disco, con este mismo id. */
@Serializable
data class PagoPredial(
    val id: String,
    val username: String,
    val folio: String,
    val fecha: String,
    val tipo: String,
    val tamano: Long
)
