package com.logue

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

// =====================================================================================================
//  INTEGRACIÓN CON AQUAGO MÓVIL Y WEB
//  Operación diaria del servicio: el ciudadano pide y cancela su pipa (SOL-03), el delegado asigna
//  solicitudes a un repartidor (ADM-03/ADM-04), el repartidor cierra cada entrega, y la app sube en
//  un solo envío los cambios que hizo sin conexión (/sync). Todo pasa por el mismo token JWT y los
//  mismos permisos por rol (requireRole) del resto del backend.
// =====================================================================================================

/** Cantidades de agua que surte una pipa (las mismas opciones que muestra la app). */
val LITROS_PERMITIDOS = setOf(1_000, 2_500, 5_000, 10_000)

/** Agenda del ciudadano: elige día y hora con al menos [DIAS_MINIMOS_ANTICIPACION] días de anticipación
 *  y como máximo [DIAS_MAXIMOS_SOLICITUD] días después de hoy (una semana). */
const val DIAS_MINIMOS_ANTICIPACION = 3L
const val DIAS_MAXIMOS_SOLICITUD = 7L

/** Horarios de entrega que se pueden apartar (uno por solicitud; no se repite día y hora). */
val HORARIOS_ENTREGA: List<String> = (8..17).map { "%02d:00".format(it) }

/** Estados que ya no apartan su día y hora: la pipa se canceló o no se pudo entregar. */
private val ESTADOS_QUE_LIBERAN = setOf(EstadoSolicitud.CANCELADA, EstadoSolicitud.NO_ENTREGADA)

/** Estados en los que el ciudadano o el delegado todavía pueden cancelar. */
private val ESTADOS_CANCELABLES = setOf(EstadoSolicitud.RECIBIDA, EstadoSolicitud.EN_PROCESO, EstadoSolicitud.PROGRAMADA)

/** Estados que el repartidor puede poner desde su ruta. */
private val ESTADOS_DE_ENTREGA = setOf(EstadoSolicitud.EN_CAMINO, EstadoSolicitud.ENTREGADA, EstadoSolicitud.NO_ENTREGADA)

/** `GET /horarios`: días que el ciudadano puede elegir y, en cada uno, qué horas siguen libres. */
@Serializable
data class HorarioDia(val dia: String, val libres: List<String>, val ocupadas: List<String>)

@Serializable
data class Agenda(val minimoDias: Int, val maximoDias: Int, val horarios: List<String>, val dias: List<HorarioDia>)

// ---------------------------------------------------------------------------------------------------
//  Cuerpos de las peticiones
// ---------------------------------------------------------------------------------------------------

/** `POST /solicitudes`. [id] lo puede generar la app (UUID) para que la misma solicitud no se duplique al reintentar. */
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

/** `PATCH /solicitudes/{id}/asignar` (delegado). */
@Serializable
data class Asignacion(
    val repartidor: String,
    val dia: String? = null,
    val hora: String? = null,
    val orden: Int? = null
)

/** `PATCH /entregas/{id}` (repartidor). [actualizadoEn] es la hora del teléfono cuando se hizo el cambio. */
@Serializable
data class CambioEntrega(
    val estado: String,
    val litrosEntregados: Int? = null,
    val observacion: String? = null,
    val actualizadoEn: String? = null
)

/** Un cambio hecho sin conexión: CREAR_SOLICITUD, CANCELAR_SOLICITUD o ACTUALIZAR_ENTREGA. */
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
data class SyncRespuesta(val resultados: List<ResultadoSync>, val servidorEn: String)

/** `POST /usuarios` (delegado): alta de repartidores, delegados o ciudadanos ya validados. */
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

object TiposOperacion {
    const val CREAR_SOLICITUD = "CREAR_SOLICITUD"
    const val CANCELAR_SOLICITUD = "CANCELAR_SOLICITUD"
    const val ACTUALIZAR_ENTREGA = "ACTUALIZAR_ENTREGA"
}

// ---------------------------------------------------------------------------------------------------
//  Registro de operaciones ya aplicadas (para que un reintento no repita un cambio)
// ---------------------------------------------------------------------------------------------------

/** Ids de las operaciones de /sync ya aplicadas. Se guardan en `<DATA_DIR>/sync_operaciones.json`. */
class SyncLog(private val file: File? = null, private val limite: Int = 5_000) {
    private val ids = LinkedHashSet<String>()
    private val serializer = ListSerializer(String.serializer())

    init {
        val archivo = file
        if (archivo != null && archivo.isFile) {
            runCatching { Json.decodeFromString(serializer, archivo.readText()) }.getOrNull()?.let { ids.addAll(it) }
        }
    }

    @Synchronized
    fun contains(id: String) = id in ids

    @Synchronized
    fun add(id: String) {
        ids.add(id)
        while (ids.size > limite) ids.remove(ids.first())
        val target = file ?: return
        target.absoluteFile.parentFile?.mkdirs()
        target.writeText(Json.encodeToString(serializer, ids.toList()))
    }
}

fun defaultSyncLog() = SyncLog(File(dataDir(), "sync_operaciones.json"))

// ---------------------------------------------------------------------------------------------------
//  Reglas del negocio (las usan las rutas normales y /sync)
// ---------------------------------------------------------------------------------------------------

/** Resultado de una operación: código HTTP, mensaje para la app y la solicitud que quedó. */
class Resultado(val status: HttpStatusCode, val mensaje: String? = null, val solicitud: Solicitud? = null) {
    val ok get() = status.isSuccess()
}

private fun error(status: HttpStatusCode, mensaje: String) = Resultado(status, mensaje)

private val ID_VALIDO = Regex("^[A-Za-z0-9-]{1,64}$")

class Operaciones(
    private val solicitudes: SolicitudStore,
    private val users: UserRepository,
    private val clock: Clock
) {
    private fun hoy(): LocalDate = LocalDate.now(clock.withZone(appZone()))

    /** Candado para que dos ciudadanos no aparten el mismo día y hora al mismo tiempo. */
    private val candadoAgenda = Any()

    /** La solicitud que ya apartó ese día y hora (sin contar [excepto]), o `null` si está libre. */
    private fun ocupante(dia: String, hora: String, excepto: String? = null): Solicitud? =
        solicitudes.all().firstOrNull { it.dia == dia && it.hora == hora && it.id != excepto && it.estado !in ESTADOS_QUE_LIBERAN }

    /** Días que se pueden elegir hoy (de hoy + 3 a hoy + 7) con sus horas libres y ocupadas. */
    fun agenda(): Agenda {
        val hoy = hoy()
        val dias = (DIAS_MINIMOS_ANTICIPACION..DIAS_MAXIMOS_SOLICITUD).map { n ->
            val dia = hoy.plusDays(n).toString()
            val (ocupadas, libres) = HORARIOS_ENTREGA.partition { ocupante(dia, it) != null }
            HorarioDia(dia, libres, ocupadas)
        }
        return Agenda(DIAS_MINIMOS_ANTICIPACION.toInt(), DIAS_MAXIMOS_SOLICITUD.toInt(), HORARIOS_ENTREGA, dias)
    }
    private fun ahora(): String = Instant.now(clock).toString()

    /** SOL-03: el ciudadano pide una pipa. [tolerarDiasPasados] solo lo usa /sync (solicitud hecha sin señal ayer). */
    fun crear(username: String, req: NuevaSolicitud, tolerarDiasPasados: Long = 0): Resultado = synchronized(candadoAgenda) {
        crearSinCandado(username, req, tolerarDiasPasados)
    }

    private fun crearSinCandado(username: String, req: NuevaSolicitud, tolerarDiasPasados: Long): Resultado {
        val id = req.id?.trim()?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
        if (!ID_VALIDO.matches(id)) return error(HttpStatusCode.BadRequest, "Identificador de solicitud inválido")
        solicitudes.find(id)?.let { existente ->
            // Reintento de la misma solicitud: se responde la que ya existe en lugar de duplicarla
            return if (existente.username.equals(username, ignoreCase = true)) Resultado(HttpStatusCode.OK, solicitud = existente)
            else error(HttpStatusCode.Conflict, "Ese identificador ya está en uso")
        }
        if (req.capacidadLitros !in LITROS_PERMITIDOS) {
            return error(HttpStatusCode.BadRequest, "Elige 1,000, 2,500, 5,000 o 10,000 litros")
        }
        val dia = runCatching { LocalDate.parse(req.dia.trim()) }.getOrNull()
            ?: return error(HttpStatusCode.BadRequest, "El día debe tener el formato AAAA-MM-DD")
        val hoy = hoy()
        // Una solicitud hecha sin señal se valida contra el día en que se hizo (hasta [tolerarDiasPasados] días antes)
        if (dia.isBefore(hoy.plusDays(DIAS_MINIMOS_ANTICIPACION - tolerarDiasPasados))) {
            return error(HttpStatusCode.BadRequest, "Pide tu pipa con al menos $DIAS_MINIMOS_ANTICIPACION días de anticipación")
        }
        if (dia.isAfter(hoy.plusDays(DIAS_MAXIMOS_SOLICITUD))) {
            return error(HttpStatusCode.BadRequest, "Solo puedes pedir hasta $DIAS_MAXIMOS_SOLICITUD días después de hoy")
        }
        val horaTexto = req.hora?.trim().orEmpty()
        if (horaTexto.isEmpty()) return error(HttpStatusCode.BadRequest, "Elige la hora de entrega")
        val horaLeida = runCatching { LocalTime.parse(horaTexto) }.getOrNull()
            ?: return error(HttpStatusCode.BadRequest, "La hora debe tener el formato HH:MM")
        val hora = horaLeida.toString()
        if (hora !in HORARIOS_ENTREGA) {
            return error(HttpStatusCode.BadRequest, "Elige una hora entre ${HORARIOS_ENTREGA.first()} y ${HORARIOS_ENTREGA.last()} h")
        }
        if (ocupante(dia.toString(), hora) != null) {
            return error(HttpStatusCode.Conflict, "Ese día y hora ya los apartó otro ciudadano. Elige otro horario")
        }

        val usuario = users.find(username)
        val domicilio = req.domicilio?.trim()?.takeIf { it.isNotEmpty() } ?: usuario?.calle.orEmpty()
        val colonia = req.colonia?.trim()?.takeIf { it.isNotEmpty() } ?: usuario?.colonia?.takeIf { it.isNotBlank() } ?: "Las Camelinas"
        val referencia = req.referencia?.trim()?.takeIf { it.isNotEmpty() }
        val notas = req.notas?.trim()?.takeIf { it.isNotEmpty() }
        when {
            domicilio.isBlank() -> return error(HttpStatusCode.BadRequest, "Escribe la calle y el número del domicilio")
            domicilio.length > 120 -> return error(HttpStatusCode.BadRequest, "La calle es demasiado larga")
            colonia.length > 120 -> return error(HttpStatusCode.BadRequest, "La colonia es demasiado larga")
            (referencia?.length ?: 0) > 100 -> return error(HttpStatusCode.BadRequest, "La referencia es demasiado larga")
            (notas?.length ?: 0) > 200 -> return error(HttpStatusCode.BadRequest, "Las notas son demasiado largas")
        }
        val momento = ahora()
        val nueva = Solicitud(
            id = id,
            username = usuario?.username ?: username,
            dia = dia.toString(),
            hora = hora,
            capacidadLitros = req.capacidadLitros,
            domicilio = domicilio,
            colonia = colonia,
            estado = EstadoSolicitud.RECIBIDA,
            referencia = referencia,
            notas = notas,
            creadaEn = momento,
            actualizadoEn = momento
        )
        solicitudes.add(nueva)
        return Resultado(HttpStatusCode.Created, solicitud = nueva)
    }

    /** El ciudadano cancela la suya; el delegado puede cancelar cualquiera. */
    fun cancelar(session: Session, id: String): Resultado {
        val s = solicitudes.find(id)
        if (s == null || (session.role != Roles.ADMIN && !s.username.equals(session.username, ignoreCase = true))) {
            return error(HttpStatusCode.NotFound, "La solicitud no existe")
        }
        if (s.estado == EstadoSolicitud.CANCELADA) return Resultado(HttpStatusCode.OK, solicitud = s)
        if (s.estado !in ESTADOS_CANCELABLES) return error(HttpStatusCode.Conflict, "La pipa ya va en camino, ya no se puede cancelar")
        val nueva = s.copy(estado = EstadoSolicitud.CANCELADA, actualizadoEn = ahora())
        solicitudes.replace(nueva)
        return Resultado(HttpStatusCode.OK, solicitud = nueva)
    }

    /** ADM-03/ADM-04: el delegado asigna la solicitud a la ruta de un repartidor. */
    fun asignar(id: String, req: Asignacion): Resultado = synchronized(candadoAgenda) { asignarSinCandado(id, req) }

    private fun asignarSinCandado(id: String, req: Asignacion): Resultado {
        val s = solicitudes.find(id) ?: return error(HttpStatusCode.NotFound, "La solicitud no existe")
        if (s.estado !in ESTADOS_CANCELABLES) return error(HttpStatusCode.Conflict, "Solo se asignan solicitudes que no han salido")
        val rep = users.find(req.repartidor)
        if (rep == null || rep.role != Roles.OPERADOR) return error(HttpStatusCode.BadRequest, "Elige un repartidor válido")
        if (rep.estado != EstadoCuenta.ACTIVA) return error(HttpStatusCode.BadRequest, "Ese repartidor está desactivado")
        val dia = req.dia?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() ?: return error(HttpStatusCode.BadRequest, "El día debe tener el formato AAAA-MM-DD") }
            ?: LocalDate.parse(s.dia)
        if (dia.isBefore(hoy())) return error(HttpStatusCode.BadRequest, "No se puede asignar a una fecha pasada")
        val hora = req.hora?.trim()?.takeIf { it.isNotEmpty() } ?: s.hora
        if (runCatching { LocalTime.parse(hora) }.isFailure) return error(HttpStatusCode.BadRequest, "La hora debe tener el formato HH:MM")
        if (req.orden != null && req.orden < 1) return error(HttpStatusCode.BadRequest, "El orden debe ser 1 o mayor")
        val horaNueva = LocalTime.parse(hora).toString()
        val cambiaHorario = dia.toString() != s.dia || horaNueva != s.hora
        if (cambiaHorario && ocupante(dia.toString(), horaNueva, excepto = s.id) != null) {
            return error(HttpStatusCode.Conflict, "Ese día y hora ya están apartados por otra solicitud")
        }
        val nueva = s.copy(
            estado = EstadoSolicitud.PROGRAMADA,
            repartidor = rep.username,
            dia = dia.toString(),
            hora = LocalTime.parse(hora).toString(),
            orden = req.orden,
            actualizadoEn = ahora()
        )
        solicitudes.replace(nueva)
        return Resultado(HttpStatusCode.OK, solicitud = nueva)
    }

    /** El delegado quita la solicitud de la ruta (solo si el repartidor no ha salido). */
    fun desasignar(id: String): Resultado {
        val s = solicitudes.find(id) ?: return error(HttpStatusCode.NotFound, "La solicitud no existe")
        if (s.estado != EstadoSolicitud.PROGRAMADA) return error(HttpStatusCode.Conflict, "El repartidor ya inició esta entrega")
        val nueva = s.copy(estado = EstadoSolicitud.RECIBIDA, repartidor = null, orden = null, actualizadoEn = ahora())
        solicitudes.replace(nueva)
        return Resultado(HttpStatusCode.OK, solicitud = nueva)
    }

    /** ENT: el repartidor marca "en camino", "entregada" o "no entregada" una parada de su ruta. */
    fun actualizarEntrega(repartidor: String, id: String, req: CambioEntrega): Resultado {
        val s = solicitudes.find(id)
        if (s == null || !s.repartidor.equals(repartidor, ignoreCase = true)) {
            return error(HttpStatusCode.NotFound, "La entrega ya no está en tu ruta")
        }
        val estado = normalizarEstado(req.estado)
        if (estado !in ESTADOS_DE_ENTREGA) return error(HttpStatusCode.BadRequest, "Estado de entrega no válido")
        if (s.estado == EstadoSolicitud.CANCELADA) return error(HttpStatusCode.Conflict, "El ciudadano canceló esta solicitud")
        // Conflicto entre dos cambios: gana el más reciente según la hora en que se hizo
        val enviado = req.actualizadoEn?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val guardado = s.actualizadoEn?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (enviado != null && guardado != null && enviado.isBefore(guardado)) {
            return error(HttpStatusCode.Conflict, "Había un cambio más reciente en el servidor")
        }
        val litros = req.litrosEntregados
        if (litros != null && litros !in 0..99_999) return error(HttpStatusCode.BadRequest, "Litros entregados no válidos")
        val observacion = req.observacion?.trim()?.takeIf { it.isNotEmpty() }
        if ((observacion?.length ?: 0) > 200) return error(HttpStatusCode.BadRequest, "La observación es demasiado larga")
        val nueva = s.copy(
            estado = estado,
            litrosEntregados = when (estado) {
                EstadoSolicitud.ENTREGADA -> litros ?: s.capacidadLitros
                EstadoSolicitud.NO_ENTREGADA -> 0
                else -> s.litrosEntregados
            },
            observacion = observacion ?: if (estado == EstadoSolicitud.NO_ENTREGADA) "No se encontró a nadie" else s.observacion,
            actualizadoEn = (enviado ?: Instant.now(clock)).toString()
        )
        solicitudes.replace(nueva)
        return Resultado(HttpStatusCode.OK, solicitud = nueva)
    }

    /** Aplica una operación hecha sin conexión. */
    fun aplicar(session: Session, op: OperacionSync): Resultado {
        val p = op.payload
        return when (op.tipo) {
            TiposOperacion.CREAR_SOLICITUD -> {
                if (session.role != Roles.CIUDADANO) return error(HttpStatusCode.Forbidden, "Solo el ciudadano crea solicitudes")
                crear(
                    session.username,
                    NuevaSolicitud(
                        id = op.entidadId,
                        dia = p.texto("dia") ?: p.texto("fechaPreferida") ?: "",
                        hora = p.texto("hora") ?: p.texto("horaPreferida"),
                        capacidadLitros = p.entero("capacidadLitros") ?: p.entero("litros") ?: 0,
                        domicilio = p.texto("domicilio") ?: p.texto("direccion"),
                        colonia = p.texto("colonia"),
                        referencia = p.texto("referencia"),
                        notas = p.texto("notas")
                    ),
                    tolerarDiasPasados = 2
                )
            }
            TiposOperacion.CANCELAR_SOLICITUD -> cancelar(session, op.entidadId)
            TiposOperacion.ACTUALIZAR_ENTREGA -> {
                if (session.role != Roles.OPERADOR) return error(HttpStatusCode.Forbidden, "Solo el repartidor actualiza entregas")
                actualizarEntrega(
                    session.username,
                    op.entidadId,
                    CambioEntrega(
                        estado = p.texto("estado").orEmpty(),
                        litrosEntregados = p.entero("litrosEntregados"),
                        observacion = p.texto("observacion"),
                        actualizadoEn = p.texto("actualizadoEn")
                    )
                )
            }
            else -> error(HttpStatusCode.BadRequest, "Operación desconocida: ${op.tipo}")
        }
    }

    companion object {
        /** Acepta los nombres de la app ("en_camino", "entregada") y los del backend ("EN_CAMINO"). */
        fun normalizarEstado(estado: String) = estado.trim().uppercase()
    }
}

private fun JsonObject.texto(clave: String): String? = (this[clave] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.entero(clave: String): Int? = (this[clave] as? JsonPrimitive)?.intOrNull

private fun User.aPerfil() = toPerfil()

// ---------------------------------------------------------------------------------------------------
//  Rutas (todas dentro de authenticate(AUTH_JWT))
// ---------------------------------------------------------------------------------------------------

private suspend fun ApplicationCall.responder(r: Resultado) {
    if (r.ok && r.solicitud != null) respond(r.status, r.solicitud) else respond(r.status, r.mensaje.orEmpty())
}

private suspend inline fun <reified T : Any> ApplicationCall.leer(): T? = try {
    receive<T>()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    respond(HttpStatusCode.BadRequest, "Solicitud inválida")
    null
}

fun Route.operacionRoutes(
    solicitudes: SolicitudStore,
    users: UserRepository,
    syncLog: SyncLog,
    clock: Clock = Clock.systemUTC(),
    avisos: Avisos? = null
) {
    val ops = Operaciones(solicitudes, users, clock)

    /** Responde y, si el estatus cambió, avisa al ciudadano por correo (Correo.kt). */
    suspend fun ApplicationCall.responderYAvisar(r: Resultado, antes: String?) {
        if (r.ok && r.solicitud != null && r.solicitud.estado != antes) avisos?.solicitudActualizada(r.solicitud)
        responder(r)
    }

    // GET /horarios -> 200 + Agenda: días que se pueden elegir (de 3 a 7 días después de hoy) y sus horas libres
    get("/horarios") {
        call.respond(ops.agenda())
    }

    // POST /solicitudes {dia, capacidadLitros, hora, domicilio?, colonia?, referencia?, notas?, id?}
    //   -> 201 + Solicitud (RECIBIDA) | 200 si ya existía con ese id | 400 datos inválidos | 403 no es ciudadano
    post("/solicitudes") {
        if (!call.requireRole(Roles.CIUDADANO)) return@post
        val req = call.leer<NuevaSolicitud>() ?: return@post
        call.responder(ops.crear(call.session.username, req))
    }

    // PATCH /solicitudes/{id}/cancelar -> 200 + Solicitud | 404 | 409 ya va en camino
    patch("/solicitudes/{id}/cancelar") {
        if (!call.requireRole(Roles.CIUDADANO, Roles.ADMIN)) return@patch
        val id = call.parameters["id"].orEmpty()
        val antes = solicitudes.find(id)?.estado
        call.responderYAvisar(ops.cancelar(call.session, id), antes)
    }

    // PATCH /solicitudes/{id}/asignar {repartidor, dia?, hora?, orden?} -> 200 + Solicitud (PROGRAMADA) | 400 | 404 | 409
    patch("/solicitudes/{id}/asignar") {
        if (!call.requireRole(Roles.ADMIN)) return@patch
        val req = call.leer<Asignacion>() ?: return@patch
        val id = call.parameters["id"].orEmpty()
        val antes = solicitudes.find(id)?.estado
        call.responderYAvisar(ops.asignar(id, req), antes)
    }

    // PATCH /solicitudes/{id}/desasignar -> 200 + Solicitud (RECIBIDA) | 404 | 409 ya inició
    patch("/solicitudes/{id}/desasignar") {
        if (!call.requireRole(Roles.ADMIN)) return@patch
        call.responder(ops.desasignar(call.parameters["id"].orEmpty()))
    }

    // PATCH /entregas/{id} {estado, litrosEntregados?, observacion?, actualizadoEn?} -> 200 + Solicitud | 400 | 404 | 409
    patch("/entregas/{id}") {
        if (!call.requireRole(Roles.OPERADOR)) return@patch
        val req = call.leer<CambioEntrega>() ?: return@patch
        val id = call.parameters["id"].orEmpty()
        val antes = solicitudes.find(id)?.estado
        call.responderYAvisar(ops.actualizarEntrega(call.session.username, id, req), antes)
    }

    // POST /sync {operaciones: [...]} -> 200 + {resultados, servidorEn}
    // Aplica en orden los cambios que la app hizo sin conexión. Una operación ya aplicada no se repite.
    post("/sync") {
        if (!call.requireRole(Roles.CIUDADANO, Roles.OPERADOR)) return@post
        val req = call.leer<SyncPeticion>() ?: return@post
        if (req.operaciones.size > 200) {
            call.respond(HttpStatusCode.PayloadTooLarge, "Demasiados cambios en un solo envío")
            return@post
        }
        val session = call.session
        val resultados = req.operaciones.sortedBy { it.creadaEn }.map { op ->
            if (syncLog.contains(op.id)) {
                ResultadoSync(op.id, true, "Ya se había aplicado")
            } else {
                val antes = solicitudes.find(op.entidadId)?.estado
                val r = ops.aplicar(session, op)
                syncLog.add(op.id)
                if (r.ok && r.solicitud != null && r.solicitud.estado != antes) avisos?.solicitudActualizada(r.solicitud)
                ResultadoSync(op.id, r.ok, if (r.ok) null else r.mensaje)
            }
        }
        call.respond(SyncRespuesta(resultados, Instant.now(clock).toString()))
    }

    // GET /usuarios?rol=OPERADOR -> 200 + [Perfil] (solo el delegado)
    get("/usuarios") {
        if (!call.requireRole(Roles.ADMIN)) return@get
        val rol = call.request.queryParameters["rol"]?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        call.respond(users.all().filter { rol == null || it.role == rol }.sortedBy { it.nombre.ifBlank { it.username } }.map { it.aPerfil() })
    }

    // POST /usuarios {username, contrasena, role, nombre, telefono, calle?, colonia?} -> 201 + Perfil (ACTIVA)
    //   | 400 datos inválidos | 409 usuario o teléfono repetido. Así el delegado da de alta a los repartidores.
    post("/usuarios") {
        if (!call.requireRole(Roles.ADMIN)) return@post
        val req = call.leer<AltaUsuario>() ?: return@post
        val username = req.username.trim()
        val role = req.role.trim().uppercase()
        val nombre = req.nombre.trim()
        val telefono = req.telefono.trim()
        val calle = req.calle.trim()
        val colonia = req.colonia.trim()
        val error = when {
            role !in setOf(Roles.CIUDADANO, Roles.OPERADOR, Roles.ADMIN) -> "Rol no válido"
            else -> validarUsuarioNuevo(username)
                ?: validarContrasenaNueva(req.contrasena)
                ?: validarDatosPersonales(
                    nombre, telefono,
                    calle.takeIf { role == Roles.CIUDADANO },
                    colonia.takeIf { role == Roles.CIUDADANO }
                )
        }
        if (error != null) {
            call.respond(HttpStatusCode.BadRequest, error)
            return@post
        }
        if (users.findByPhone(telefono) != null) {
            call.respond(HttpStatusCode.Conflict, "Ese teléfono ya tiene una cuenta")
            return@post
        }
        val nuevo = User(
            username = username,
            passwordHash = Passwords.hash(req.contrasena),
            role = role,
            nombre = nombre,
            telefono = telefono,
            calle = calle,
            colonia = colonia,
            estado = EstadoCuenta.ACTIVA
        )
        if (!users.add(nuevo)) {
            call.respond(HttpStatusCode.Conflict, "Ese usuario ya existe, elige otro")
            return@post
        }
        call.respond(HttpStatusCode.Created, nuevo.aPerfil())
    }
}
