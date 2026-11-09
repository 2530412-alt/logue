package com.logue

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Estados de una solicitud de pipa. Los cuatro de la propuesta (SEG-01) más "en camino" y "cancelada". */
object EstadoSolicitud {
    const val RECIBIDA = "RECIBIDA"
    const val EN_PROCESO = "EN_PROCESO"
    const val PROGRAMADA = "PROGRAMADA"
    const val EN_CAMINO = "EN_CAMINO"
    const val ENTREGADA = "ENTREGADA"
    const val CANCELADA = "CANCELADA"
}

/**
 * Solicitud de agua. [dia] (`2026-11-09`) y [hora] (`10:00`) son la fecha y la hora de la cita en
 * la zona horaria de la colonia (America/Mexico_City), guardadas como texto para que la app las
 * muestre sin convertir zonas. [repartidor] es el usuario del operador asignado, o `null` si falta asignar.
 */
@kotlinx.serialization.Serializable
data class Solicitud(
    val id: String,
    val username: String,
    val dia: String,
    val hora: String,
    val capacidadLitros: Int,
    val domicilio: String,
    val colonia: String,
    val estado: String,
    val repartidor: String? = null
)

/**
 * Guarda las solicitudes en `<DATA_DIR>/solicitudes.json`. Hoy solo se leen (SOL-01, ENT-01, ADM-01);
 * crearlas es SOL-03 y asignarlas ADM-03/ADM-04, que usarán [add] y [replace].
 */
class SolicitudStore(private val file: File? = null, initial: List<Solicitud> = emptyList()) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val items = ArrayList<Solicitud>()

    init {
        val archivo = file
        if (archivo != null && archivo.isFile) {
            runCatching { json.decodeFromString(ListSerializer(Solicitud.serializer()), archivo.readText()) }
                .getOrNull()?.let { items.addAll(it) }
        }
        initial.forEach { items.add(it) }
    }

    @Synchronized
    fun add(solicitud: Solicitud) {
        items.add(solicitud)
        persist()
    }

    @Synchronized
    fun replace(solicitud: Solicitud): Boolean {
        val i = items.indexOfFirst { it.id == solicitud.id }
        if (i < 0) return false
        items[i] = solicitud
        persist()
        return true
    }

    /** Más recientes primero. */
    @Synchronized
    fun all(): List<Solicitud> = items.sortedWith(compareByDescending<Solicitud> { it.dia }.thenByDescending { it.hora })

    fun of(username: String): List<Solicitud> = all().filter { it.username.equals(username, ignoreCase = true) }

    /** Entregas de un repartidor en un día, en el orden de la ruta (por hora). */
    fun route(repartidor: String, dia: String): List<Solicitud> =
        all().filter { it.repartidor.equals(repartidor, ignoreCase = true) && it.dia == dia && it.estado != EstadoSolicitud.CANCELADA }
            .sortedBy { it.hora }

    private fun persist() {
        val target = file ?: return
        target.absoluteFile.parentFile?.mkdirs()
        target.writeText(json.encodeToString(ListSerializer(Solicitud.serializer()), items))
    }
}

fun defaultSolicitudStore() = SolicitudStore(File(dataDir(), "solicitudes.json"))

/** Zona horaria de la colonia (variable de entorno APP_TZ, por omisión America/Mexico_City). */
fun appZone(): ZoneId = ZoneId.of(System.getenv("APP_TZ") ?: "America/Mexico_City")

fun Route.solicitudesRoutes(solicitudes: SolicitudStore, clock: Clock = Clock.systemUTC()) {

    // GET /solicitudes -> el ciudadano recibe solo las suyas; el delegado, todas. Más recientes primero.
    //   -> 200 + [Solicitud] | 401 sin sesión | 403 operador
    get("/solicitudes") {
        if (!call.requireRole(Roles.CIUDADANO, Roles.ADMIN)) return@get
        val me = call.session
        call.respond(if (me.role == Roles.ADMIN) solicitudes.all() else solicitudes.of(me.username))
    }

    // GET /entregas?dia=2026-11-09 -> la ruta del repartidor (por omisión, la de hoy), en orden de hora.
    //   -> 200 + [Solicitud] | 400 día inválido | 403 si no es operador
    get("/entregas") {
        if (!call.requireRole(Roles.OPERADOR)) return@get
        val pedido = call.request.queryParameters["dia"]?.trim()?.takeIf { it.isNotEmpty() }
        val dia = if (pedido == null) {
            LocalDate.now(clock.withZone(appZone())).toString()
        } else {
            runCatching { LocalDate.parse(pedido).toString() }.getOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, "El día debe tener el formato AAAA-MM-DD")
                return@get
            }
        }
        call.respond(solicitudes.route(call.session.username, dia))
    }
}
