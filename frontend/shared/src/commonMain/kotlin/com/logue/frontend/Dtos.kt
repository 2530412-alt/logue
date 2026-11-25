package com.logue.frontend

import kotlinx.serialization.Serializable

/** Cuerpo que se envía a `POST /login`. */
@Serializable
data class LoginRequest(val username: String, val password: String, val captcha: String? = null)

/**
 * Respuesta del backend cuando las credenciales son correctas. El [token] se manda en cada
 * petición como `Authorization: Bearer <token>` (USR-16); dura [expiresIn] segundos.
 */
@Serializable
data class LoginResponse(val username: String, val role: String, val token: String, val expiresIn: Long = 0)

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

/** Respuesta de `POST /registro`: la cuenta queda en revisión hasta que el delegado valide el comprobante (USR-11). */
@Serializable
data class RegistroResponse(val username: String, val estado: String, val pagoId: String)

/** Perfil de la cuenta (`GET /usuarios/yo`, USR-21). */
@Serializable
data class Perfil(
    val username: String,
    val role: String,
    val nombre: String = "",
    val telefono: String = "",
    val calle: String = "",
    val colonia: String = "",
    val estado: String = "",
    // Servicio de correo (opcionales: un servidor anterior no los manda)
    val correo: String = "",
    val correoVerificado: Boolean = false
)

/** Cuerpo de `PATCH /usuarios/yo`: el servidor cambia solo los campos que lleguen. */
@Serializable
data class PerfilCambio(
    val nombre: String? = null,
    val telefono: String? = null,
    val calle: String? = null,
    val colonia: String? = null,
    /** Al cambiarlo hay que volver a verificarlo con el código que llega por correo. */
    val correo: String? = null
)

@Serializable
data class RecuperacionSolicitud(val cuenta: String)

@Serializable
data class RecuperacionConfirmacion(val cuenta: String, val codigo: String, val contrasena: String)

/**
 * Solicitud de agua que devuelve `GET /solicitudes` y `GET /entregas`. [dia] es `2026-11-09` y [hora] es
 * `10:00`, ya en la zona horaria de la colonia. [repartidor] es `null` mientras falte asignarlo.
 */
@Serializable
data class Solicitud(
    val id: String,
    val username: String = "",
    val dia: String,
    val hora: String,
    val capacidadLitros: Int,
    val domicilio: String,
    val colonia: String,
    val estado: String,
    val repartidor: String? = null,
    // --- Integración AquaGo (opcionales: si el servidor no los manda, la app funciona igual) ---
    val referencia: String? = null,
    val notas: String? = null,
    val litrosEntregados: Int? = null,
    val observacion: String? = null,
    val orden: Int? = null,
    val creadaEn: String? = null,
    val actualizadoEn: String? = null,
    /** Solo en el teléfono: el cambio todavía no llega al servidor (se hizo sin conexión). */
    val pendienteSync: Boolean = false
)

/** Estados de una solicitud (deben coincidir con `EstadoSolicitud` del backend). */
object EstadoSolicitud {
    const val RECIBIDA = "RECIBIDA"
    const val EN_PROCESO = "EN_PROCESO"
    const val PROGRAMADA = "PROGRAMADA"
    const val EN_CAMINO = "EN_CAMINO"
    const val ENTREGADA = "ENTREGADA"
    const val CANCELADA = "CANCELADA"
    /** El repartidor llegó pero no pudo entregar. */
    const val NO_ENTREGADA = "NO_ENTREGADA"
}

/**
 * Roles que devuelve el backend (deben coincidir con `users.json`).
 * En el SRS: CIUDADANO = Ciudadano, OPERADOR = Repartidor, ADMIN = Delegado.
 */
object Roles {
    const val CIUDADANO = "CIUDADANO"
    const val OPERADOR = "OPERADOR"
    const val ADMIN = "ADMIN"
}

/** `POST /login/google`: el ID token que da el botón de Google y la respuesta del captcha. */
@Serializable
data class LoginGoogle(val credential: String, val captcha: String? = null)

/** `GET /config/web`: claves PÚBLICAS para el botón de Google y el captcha (vacías = no configurado). */
@Serializable
data class ConfigWeb(val googleClientId: String = "", val recaptchaSiteKey: String = "", val correoActivo: Boolean = false)

/** `POST /usuarios/yo/correo/verificar` y `POST /registro/verificar-correo`. */
@Serializable
data class CodigoCorreo(val codigo: String)

@Serializable
data class VerificarCorreoRegistro(val usuario: String, val codigo: String)

/** `POST /reportes/correo` (delegado). */
@Serializable
data class PedidoReporte(val desde: String? = null, val hasta: String? = null)
