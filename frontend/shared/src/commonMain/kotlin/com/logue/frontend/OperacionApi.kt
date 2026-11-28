package com.logue.frontend

import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Llamadas a los endpoints de operación que se agregaron al backend en la integración con AquaGo
 * (backend/src/main/kotlin/Operacion.kt). Mismo token JWT y mismo manejo de errores (ApiResult) que
 * el resto de la app.
 */

/** Cuerpo de `POST /solicitudes`. [id] lo genera la app para que un reintento no duplique la pipa. */
@Serializable
data class NuevaSolicitud(
    val id: String? = null,
    val dia: String,
    val hora: String? = null,
    val capacidadLitros: Int,
    val domicilio: String? = null,
    val colonia: String? = null,
    val referencia: String? = null,
    val notas: String? = null
)

/** Cuerpo de `PATCH /entregas/{id}`. */
@Serializable
data class CambioEntrega(
    val estado: String,
    val litrosEntregados: Int? = null,
    val observacion: String? = null,
    val actualizadoEn: String? = null
)

/** Un cambio hecho sin conexión, guardado en la cola del teléfono hasta que se sube con `POST /sync`. */
@Serializable
data class OperacionSync(
    val id: String,
    val tipo: String,
    val entidadId: String,
    val payload: JsonObject = JsonObject(emptyMap()),
    val creadaEn: String = ""
)

@Serializable
data class SyncPeticion(val operaciones: List<OperacionSync>)

@Serializable
data class ResultadoSync(val id: String, val ok: Boolean, val mensaje: String? = null)

@Serializable
data class SyncRespuesta(val resultados: List<ResultadoSync>, val servidorEn: String = "")

object TiposOperacion {
    const val CREAR_SOLICITUD = "CREAR_SOLICITUD"
    const val CANCELAR_SOLICITUD = "CANCELAR_SOLICITUD"
    const val ACTUALIZAR_ENTREGA = "ACTUALIZAR_ENTREGA"
}

/** Cantidades que acepta el servidor (LITROS_PERMITIDOS en el backend). */
val OPCIONES_LITROS = listOf(1_000, 2_500, 5_000, 10_000)

/** Estados en los que el ciudadano todavía puede cancelar (los mismos que valida el backend). */
fun esCancelable(estado: String) = esPendiente(estado)

/** `POST /solicitudes` (ciudadano). */
suspend fun crearSolicitud(token: String, nueva: NuevaSolicitud): ApiResult<Solicitud> = peticion(
    hacer = {
        client.post("$BASE_URL/solicitudes") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(nueva)
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.body<Solicitud>() }
)

/** `PATCH /solicitudes/{id}/cancelar` (ciudadano). */
suspend fun cancelarSolicitud(token: String, id: String): ApiResult<Solicitud> = peticion(
    hacer = {
        client.patch("$BASE_URL/solicitudes/$id/cancelar") {
            bearerAuth(token)
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.body<Solicitud>() }
)

/** `PATCH /entregas/{id}` (repartidor): en camino, entregada o no entregada. */
suspend fun actualizarEntrega(token: String, id: String, cambio: CambioEntrega): ApiResult<Solicitud> = peticion(
    hacer = {
        client.patch("$BASE_URL/entregas/$id") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(cambio)
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.body<Solicitud>() }
)

/** `POST /sync`: sube en orden los cambios hechos sin conexión. */
suspend fun sincronizar(token: String, operaciones: List<OperacionSync>): ApiResult<SyncRespuesta> = peticion(
    hacer = {
        client.post("$BASE_URL/sync") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(SyncPeticion(operaciones))
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.body<SyncRespuesta>() }
)

/** `GET /horarios`: días que el ciudadano puede elegir (de 3 a 7 días después de hoy) y sus horas libres. */
@Serializable
data class HorarioDia(val dia: String, val libres: List<String> = emptyList(), val ocupadas: List<String> = emptyList())

@Serializable
data class Agenda(val minimoDias: Int = 3, val maximoDias: Int = 7, val horarios: List<String> = emptyList(), val dias: List<HorarioDia> = emptyList())

/** Horarios de entrega (los mismos que HORARIOS_ENTREGA en el backend). */
val HORARIOS_ENTREGA: List<String> = (8..17).map { if (it < 10) "0$it:00" else "$it:00" }

/** Agenda que se muestra sin conexión: los mismos días calculados en el teléfono, sin saber qué horas están ocupadas. */
fun agendaSinConexion(): Agenda = Agenda(
    horarios = HORARIOS_ENTREGA,
    dias = (3..7).map { HorarioDia(Plataforma.fechaLocal(it), libres = HORARIOS_ENTREGA) }
)

suspend fun obtenerAgenda(token: String): ApiResult<Agenda> = peticion(
    hacer = {
        client.get("$BASE_URL/horarios") {
            bearerAuth(token)
            timeout { requestTimeoutMillis = ESPERA_SERVIDOR_DORMIDO_MS }
        }
    },
    leer = { it.body<Agenda>() }
)
