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
data class User(
    val username: String,
    val passwordHash: String,
    val role: String,
    // Datos del perfil (USR-21). Los usuarios de prueba de users.json no los traen, por eso tienen valor por omisión.
    val nombre: String = "",
    val telefono: String = "",
    val calle: String = "",
    val colonia: String = "",
    /** ACTIVA, EN_REVISION (registro nuevo, USR-11) o INACTIVA. Ver [EstadoCuenta]. */
    val estado: String = EstadoCuenta.ACTIVA
)

/** Estados de una cuenta. Una cuenta nueva queda EN_REVISION hasta que el delegado valida su comprobante (USR-11). */
object EstadoCuenta {
    const val ACTIVA = "ACTIVA"
    const val EN_REVISION = "EN_REVISION"
    const val INACTIVA = "INACTIVA"
}

/** Respuesta de `POST /registro` (201): la cuenta existe pero queda en revisión (USR-08, USR-11). */
@Serializable
data class RegistroResponse(val username: String, val estado: String, val pagoId: String)

/** Perfil de la cuenta (USR-21): `GET /usuarios/yo`. */
@Serializable
data class Perfil(
    val username: String,
    val role: String,
    val nombre: String,
    val telefono: String,
    val calle: String,
    val colonia: String,
    val estado: String
)

/** Cuerpo de `PATCH /usuarios/yo`: solo se cambian los campos que vengan. */
@Serializable
data class PerfilCambio(
    val nombre: String? = null,
    val telefono: String? = null,
    val calle: String? = null,
    val colonia: String? = null
)

/** Cuerpo de `PATCH /usuarios/{username}/estado` (solo el delegado). */
@Serializable
data class CambioEstado(val estado: String)

/** Cuerpo de `POST /recuperacion/solicitar` (USR-12): usuario o teléfono de 10 dígitos. */
@Serializable
data class RecuperacionSolicitud(val cuenta: String)

/** Cuerpo de `POST /recuperacion/confirmar` (USR-12). */
@Serializable
data class RecuperacionConfirmacion(val cuenta: String, val codigo: String, val contrasena: String)

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
