package com.logue.frontend

import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.Serializable

/*
 * Llamadas del PANEL WEB DEL DELEGADO (rol ADMIN). Todas existen en el backend:
 * - GET  /usuarios?rol=            lista de cuentas (Operacion.kt)
 * - POST /usuarios                 alta de repartidores / delegados / ciudadanos ya validados
 * - PATCH /usuarios/{u}/estado     activar o desactivar una cuenta (Cuentas.kt, USR-11)
 * - PATCH /solicitudes/{id}/asignar | /desasignar | /cancelar
 */

@Serializable
data class Asignacion(val repartidor: String, val dia: String? = null, val hora: String? = null, val orden: Int? = null)

@Serializable
data class AltaUsuario(
    val username: String,
    val contrasena: String,
    val role: String,
    val nombre: String,
    val telefono: String,
    val calle: String = "",
    val colonia: String = ""
)

@Serializable
data class CambioEstadoCuenta(val estado: String)

/** Estados de una cuenta (deben coincidir con `EstadoCuenta` del backend). */
object EstadoCuenta {
    const val ACTIVA = "ACTIVA"
    const val EN_REVISION = "EN_REVISION"
    const val INACTIVA = "INACTIVA"
}

suspend fun listarUsuarios(token: String, rol: String? = null): ApiResult<List<Perfil>> = peticion(
    hacer = {
        client.get("$BASE_URL/usuarios") {
            bearerAuth(token)
            if (rol != null) parameter("rol", rol)
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.body<List<Perfil>>() }
)

suspend fun crearUsuario(token: String, alta: AltaUsuario): ApiResult<Perfil> = peticion(
    hacer = {
        client.post("$BASE_URL/usuarios") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(alta)
        }
    },
    leer = { it.body<Perfil>() }
)

suspend fun cambiarEstadoCuenta(token: String, username: String, estado: String): ApiResult<Perfil> = peticion(
    hacer = {
        client.patch("$BASE_URL/usuarios/${username.encodeURLPathPart()}/estado") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CambioEstadoCuenta(estado))
        }
    },
    leer = { it.body<Perfil>() }
)

suspend fun asignarSolicitud(token: String, id: String, asignacion: Asignacion): ApiResult<Solicitud> = peticion(
    hacer = {
        client.patch("$BASE_URL/solicitudes/$id/asignar") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(asignacion)
        }
    },
    leer = { it.body<Solicitud>() }
)

suspend fun desasignarSolicitud(token: String, id: String): ApiResult<Solicitud> = peticion(
    hacer = { client.patch("$BASE_URL/solicitudes/$id/desasignar") { bearerAuth(token) } },
    leer = { it.body<Solicitud>() }
)

/** `POST /reportes/correo`: el servidor manda el reporte del periodo al correo verificado del delegado. */
suspend fun enviarReportePorCorreo(token: String, desde: String, hasta: String): ApiResult<String> = peticion(
    hacer = {
        client.post("$BASE_URL/reportes/correo") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PedidoReporte(desde, hasta))
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.bodyAsText() }
)

// ---------------------------------------------------------------------------------------------------
//  Servicio de correo: verificar el correo de la cuenta (perfil)
// ---------------------------------------------------------------------------------------------------

/** `POST /usuarios/yo/correo/codigo`: manda un código de 6 dígitos al correo del perfil. */
suspend fun pedirCodigoCorreo(token: String): ApiResult<String> = peticion(
    hacer = { client.post("$BASE_URL/usuarios/yo/correo/codigo") { bearerAuth(token) } },
    leer = { it.bodyAsText() }
)

/** `POST /usuarios/yo/correo/verificar`. */
suspend fun verificarCorreo(token: String, codigo: String): ApiResult<Perfil> = peticion(
    hacer = {
        client.post("$BASE_URL/usuarios/yo/correo/verificar") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CodigoCorreo(codigo.trim()))
        }
    },
    leer = { it.body<Perfil>() }
)
