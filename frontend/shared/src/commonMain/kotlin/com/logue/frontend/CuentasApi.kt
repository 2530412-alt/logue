package com.logue.frontend

import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resultado de una llamada al servidor. Ninguna función de este archivo lanza excepciones:
 * siempre devuelven uno de estos casos, para que cada pantalla muestre el mensaje que corresponde.
 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val valor: T) : ApiResult<T>

    /** El servidor entendió la petición y la rechazó (400, 403, 404, 409, 413, 415...). [mensaje] viene del servidor. */
    data class Rechazo(val codigo: Int, val mensaje: String) : ApiResult<Nothing>

    /** 401: el token venció (dura 12 horas). La app regresa al login. */
    data object SesionVencida : ApiResult<Nothing>

    data object ErrorServidor : ApiResult<Nothing>
    data object SinConexion : ApiResult<Nothing>
}

/** Mensaje para mostrar cuando una llamada no salió bien; `null` si fue [ApiResult.Ok] o la sesión venció. */
fun ApiResult<*>.mensajeDeError(): String? = when (this) {
    is ApiResult.Ok -> null
    is ApiResult.Rechazo -> mensaje.ifBlank { "No se pudo completar la acción" }
    ApiResult.SesionVencida -> null
    ApiResult.ErrorServidor -> "El servidor tuvo un problema. Intenta de nuevo"
    ApiResult.SinConexion -> MENSAJE_SIN_CONEXION
}

private suspend fun <T> peticion(
    hacer: suspend () -> HttpResponse,
    leer: suspend (HttpResponse) -> T
): ApiResult<T> = try {
    val res = hacer()
    when {
        res.status.isSuccess() -> ApiResult.Ok(leer(res))
        res.status == HttpStatusCode.Unauthorized -> ApiResult.SesionVencida
        res.status.value in 400..499 -> ApiResult.Rechazo(res.status.value, res.bodyAsText())
        else -> ApiResult.ErrorServidor
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    // Throwable y no Exception: en la versión web Ktor avisa la falla de red con un Error
    ApiResult.SinConexion
}

/** Datos del formulario de Crear cuenta. */
data class DatosRegistro(
    val nombre: String,
    val telefono: String,
    val calle: String,
    val colonia: String,
    val usuario: String,
    val contrasena: String
)

/** `POST /registro` con la foto del comprobante predial (USR-08, PRE-13). La cuenta queda en revisión (USR-11). */
suspend fun registrarCuenta(datos: DatosRegistro, comprobante: ByteArray): ApiResult<RegistroResponse> = peticion(
    hacer = {
        client.submitFormWithBinaryData(
            url = "$BASE_URL/registro",
            formData = formData {
                append("nombre", datos.nombre.trim())
                append("telefono", datos.telefono.trim())
                append("calle", datos.calle.trim())
                append("colonia", datos.colonia.trim())
                append("usuario", datos.usuario.trim())
                append("contrasena", datos.contrasena)
                append("foto", comprobante, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"comprobante.jpg\"")
                })
            }
        ) {
            // Foto de varios MB por datos móviles, y el servidor gratuito puede estar dormido
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.body<RegistroResponse>() }
)

/** `POST /recuperacion/solicitar`: el servidor manda un código de 6 dígitos al teléfono de la cuenta (USR-12). */
suspend fun pedirCodigoDeRecuperacion(cuenta: String): ApiResult<Unit> = peticion(
    hacer = {
        client.post("$BASE_URL/recuperacion/solicitar") {
            contentType(ContentType.Application.Json)
            setBody(RecuperacionSolicitud(cuenta.trim()))
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { }
)

/** `POST /recuperacion/confirmar`: cambia la contraseña si el código es el vigente (USR-12). */
suspend fun cambiarContrasenaConCodigo(cuenta: String, codigo: String, contrasena: String): ApiResult<Unit> = peticion(
    hacer = {
        client.post("$BASE_URL/recuperacion/confirmar") {
            contentType(ContentType.Application.Json)
            setBody(RecuperacionConfirmacion(cuenta.trim(), codigo.trim(), contrasena))
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { }
)

/** `GET /usuarios/yo` (USR-21). */
suspend fun obtenerPerfil(token: String): ApiResult<Perfil> = peticion(
    hacer = {
        client.get("$BASE_URL/usuarios/yo") {
            bearerAuth(token)
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.body<Perfil>() }
)

/** `PATCH /usuarios/yo` (USR-21). */
suspend fun guardarPerfil(token: String, cambio: PerfilCambio): ApiResult<Perfil> = peticion(
    hacer = {
        client.patch("$BASE_URL/usuarios/yo") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(cambio)
        }
    },
    leer = { it.body<Perfil>() }
)

/** `GET /solicitudes`: las del ciudadano, o todas si es el delegado (SOL-01, ADM-01). */
suspend fun listarSolicitudes(token: String): ApiResult<List<Solicitud>> = peticion(
    hacer = {
        client.get("$BASE_URL/solicitudes") {
            bearerAuth(token)
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.body<List<Solicitud>>() }
)

/** `GET /entregas`: la ruta de hoy del repartidor (ENT-01). */
suspend fun listarEntregas(token: String): ApiResult<List<Solicitud>> = peticion(
    hacer = {
        client.get("$BASE_URL/entregas") {
            bearerAuth(token)
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.body<List<Solicitud>>() }
)

/** `GET /pagos-prediales`: los del ciudadano, o todos si es el delegado (PRE-10, PRE-11). */
suspend fun listarPagosPrediales(token: String): ApiResult<List<PagoPredialResponse>> = peticion(
    hacer = {
        client.get("$BASE_URL/pagos-prediales") {
            bearerAuth(token)
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.body<List<PagoPredialResponse>>() }
)

/** `GET /pagos-prediales/{id}/foto`: los bytes de la foto del recibo (PRE-11). */
suspend fun descargarFotoDePago(token: String, id: String): ApiResult<ByteArray> = peticion(
    hacer = {
        client.get("$BASE_URL/pagos-prediales/$id/foto") {
            bearerAuth(token)
            timeout { requestTimeoutMillis = 60_000 }
        }
    },
    leer = { it.body<ByteArray>() }
)
