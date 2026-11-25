package com.logue.frontend

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * MODO SIN CONEXIÓN (integración AquaGo).
 *
 * - Cada lista que llega del servidor se guarda en el teléfono; si después no hay red, se muestra la
 *   última copia con un aviso.
 * - Lo que el usuario hace sin red (pedir o cancelar una pipa, cerrar una entrega) se guarda en una
 *   cola y se ve al instante en pantalla con la marca «Pendiente de subir».
 * - En cuanto vuelve la red (al abrir la app, al tocar «Actualizar» o con el trabajo de fondo de
 *   Android) la cola se sube con POST /sync. El servidor ignora una operación que ya aplicó, así que
 *   reintentar nunca duplica nada.
 * - Al comparar la lista nueva con la anterior se avisa con notificaciones locales (tu pipa va en
 *   camino, tienes entregas nuevas, se subieron tus cambios).
 *
 * Todo se guarda por usuario, así dos cuentas en el mismo teléfono no se mezclan.
 */

/** Lo que recibe la pantalla al cargar o actualizar. */
data class DatosOffline(
    val lista: List<Solicitud>,
    /** `true` si el servidor no respondió y se muestra la última copia guardada. */
    val desdeCopia: Boolean,
    /** Cambios hechos sin conexión que todavía no llegan al servidor. */
    val pendientes: Int,
    /** Aviso para mostrar (por ejemplo, un cambio que el servidor rechazó al sincronizar). */
    val aviso: String? = null
)

/** Resultado de cargar datos. */
sealed interface CargaOffline {
    data class Listo(val datos: DatosOffline) : CargaOffline
    data class Fallo(val mensaje: String) : CargaOffline
    data object SesionVencida : CargaOffline
}

/** Resultado de una acción (pedir, cancelar, cerrar entrega). */
sealed interface Accion {
    /** Se hizo. [sinConexion] = quedó guardada en el teléfono y se subirá después. */
    data class Hecha(val mensaje: String, val sinConexion: Boolean = false) : Accion
    data class Error(val mensaje: String) : Accion
    data object SesionVencida : Accion
}

object Offline {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listaSerializer = ListSerializer(Solicitud.serializer())
    private val colaSerializer = ListSerializer(OperacionSync.serializer())
    private val candado = Mutex()

    private const val CLAVE_SESION = "sesion"
    private fun claveLista(usuario: String) = "lista_${usuario.lowercase()}"
    private fun claveCola(usuario: String) = "cola_${usuario.lowercase()}"
    private fun clavePerfil(usuario: String) = "perfil_${usuario.lowercase()}"

    private val almacen get() = Plataforma.almacen

    // -------------------------------------------------------------------------------------------
    //  Sesión guardada (para abrir la app sin red). Dura lo mismo que el token del servidor.
    // -------------------------------------------------------------------------------------------

    fun guardarSesion(sesion: LoginResponse) {
        val vence = ahoraMs() + (if (sesion.expiresIn > 0) sesion.expiresIn else 12 * 3600L) * 1000
        almacen.guardar(CLAVE_SESION, json.encodeToString(SesionGuardada.serializer(), SesionGuardada(sesion, vence)))
    }

    /** La sesión guardada si todavía no vence; si venció, se borra. */
    fun sesionGuardada(): LoginResponse? {
        val texto = almacen.leer(CLAVE_SESION) ?: return null
        val guardada = runCatching { json.decodeFromString(SesionGuardada.serializer(), texto) }.getOrNull()
        if (guardada == null || guardada.venceEnMs <= ahoraMs()) {
            almacen.borrar(CLAVE_SESION)
            return null
        }
        return guardada.sesion
    }

    /** Al cerrar sesión se borra el token. La copia de la lista y la cola se quedan, para no perder cambios. */
    fun borrarSesion() = almacen.borrar(CLAVE_SESION)

    // -------------------------------------------------------------------------------------------
    //  Perfil guardado (para llenar el domicilio de «Pedir pipa» sin red)
    // -------------------------------------------------------------------------------------------

    fun guardarPerfil(perfil: Perfil) = almacen.guardar(clavePerfil(perfil.username), json.encodeToString(Perfil.serializer(), perfil))

    fun perfilGuardado(usuario: String): Perfil? =
        almacen.leer(clavePerfil(usuario))?.let { runCatching { json.decodeFromString(Perfil.serializer(), it) }.getOrNull() }

    // -------------------------------------------------------------------------------------------
    //  Copia de la lista y cola de cambios
    // -------------------------------------------------------------------------------------------

    private fun leerLista(usuario: String): List<Solicitud>? =
        almacen.leer(claveLista(usuario))?.let { runCatching { json.decodeFromString(listaSerializer, it) }.getOrNull() }

    private fun guardarLista(usuario: String, lista: List<Solicitud>) =
        almacen.guardar(claveLista(usuario), json.encodeToString(listaSerializer, lista.map { it.copy(pendienteSync = false) }))

    fun cola(usuario: String): List<OperacionSync> =
        almacen.leer(claveCola(usuario))?.let { runCatching { json.decodeFromString(colaSerializer, it) }.getOrNull() } ?: emptyList()

    private fun guardarCola(usuario: String, cola: List<OperacionSync>) =
        if (cola.isEmpty()) almacen.borrar(claveCola(usuario)) else almacen.guardar(claveCola(usuario), json.encodeToString(colaSerializer, cola))

    @OptIn(ExperimentalUuidApi::class)
    private fun nuevoId() = Uuid.random().toString()

    /** Aplica sobre la lista del servidor los cambios que siguen en la cola, para que el usuario los vea. */
    internal fun aplicarCola(lista: List<Solicitud>, cola: List<OperacionSync>, usuario: String): List<Solicitud> {
        var resultado = lista
        for (op in cola) {
            val p = op.payload
            resultado = when (op.tipo) {
                TiposOperacion.CREAR_SOLICITUD -> {
                    if (resultado.any { it.id == op.entidadId }) resultado
                    else listOf(
                        Solicitud(
                            id = op.entidadId,
                            username = usuario,
                            dia = p.texto("dia").orEmpty(),
                            hora = p.texto("hora") ?: "--:--",
                            capacidadLitros = p.texto("capacidadLitros")?.toIntOrNull() ?: 0,
                            domicilio = p.texto("domicilio").orEmpty().ifBlank { "Domicilio de mi cuenta" },
                            colonia = p.texto("colonia").orEmpty(),
                            estado = EstadoSolicitud.RECIBIDA,
                            referencia = p.texto("referencia"),
                            notas = p.texto("notas"),
                            creadaEn = op.creadaEn,
                            pendienteSync = true
                        )
                    ) + resultado
                }
                TiposOperacion.CANCELAR_SOLICITUD -> resultado.map {
                    if (it.id == op.entidadId) it.copy(estado = EstadoSolicitud.CANCELADA, pendienteSync = true) else it
                }
                TiposOperacion.ACTUALIZAR_ENTREGA -> resultado.map {
                    if (it.id != op.entidadId) it
                    else {
                        val estado = p.texto("estado") ?: it.estado
                        it.copy(
                            estado = estado,
                            litrosEntregados = when (estado) {
                                EstadoSolicitud.ENTREGADA -> p.texto("litrosEntregados")?.toIntOrNull() ?: it.capacidadLitros
                                EstadoSolicitud.NO_ENTREGADA -> 0
                                else -> it.litrosEntregados
                            },
                            observacion = p.texto("observacion") ?: it.observacion,
                            pendienteSync = true
                        )
                    }
                }
                else -> resultado
            }
        }
        return resultado
    }

    private fun encolar(usuario: String, op: OperacionSync) {
        guardarCola(usuario, cola(usuario) + op)
        Plataforma.programarSincronizacion()
    }

    // -------------------------------------------------------------------------------------------
    //  Sincronización
    // -------------------------------------------------------------------------------------------

    /**
     * Sube la cola con POST /sync. Devuelve el aviso para la pantalla (o `null`).
     * Las operaciones que el servidor contestó (bien o rechazadas) salen de la cola: el servidor ya las
     * registró y no las aplicaría otra vez. Si no hay red, la cola se queda igual.
     */
    suspend fun sincronizarPendientes(sesion: LoginResponse): ResultadoSincronizacion = candado.withLock {
        val pendientes = cola(sesion.username)
        if (pendientes.isEmpty()) return@withLock ResultadoSincronizacion.NadaPendiente
        when (val r = sincronizar(sesion.token, pendientes.take(200))) {
            is ApiResult.Ok -> {
                val contestadas = r.valor.resultados.map { it.id }.toSet()
                guardarCola(sesion.username, cola(sesion.username).filterNot { it.id in contestadas })
                val rechazadas = r.valor.resultados.filterNot { it.ok }
                val subidas = r.valor.resultados.size - rechazadas.size
                if (subidas > 0) {
                    Plataforma.notificar(
                        "Cambios guardados",
                        if (subidas == 1) "Se subió 1 cambio que hiciste sin conexión" else "Se subieron $subidas cambios que hiciste sin conexión"
                    )
                }
                val aviso = rechazadas.firstOrNull()?.let { "Un cambio no se pudo aplicar: ${it.mensaje ?: "el servidor lo rechazó"}" }
                if (aviso != null) Plataforma.notificar("Revisa tus cambios", aviso)
                ResultadoSincronizacion.Subida(subidas, aviso)
            }
            ApiResult.SesionVencida -> ResultadoSincronizacion.SesionVencida
            else -> ResultadoSincronizacion.SinRed
        }
    }

    sealed interface ResultadoSincronizacion {
        data object NadaPendiente : ResultadoSincronizacion
        data class Subida(val cuantas: Int, val aviso: String?) : ResultadoSincronizacion
        data object SinRed : ResultadoSincronizacion
        data object SesionVencida : ResultadoSincronizacion
    }

    // -------------------------------------------------------------------------------------------
    //  Cargar listas (ciudadano: GET /solicitudes; repartidor: GET /entregas)
    // -------------------------------------------------------------------------------------------

    suspend fun cargarSolicitudes(sesion: LoginResponse) = cargar(sesion) { listarSolicitudes(it) }

    suspend fun cargarRuta(sesion: LoginResponse) = cargar(sesion) { listarEntregas(it) }

    private suspend fun cargar(sesion: LoginResponse, llamada: suspend (String) -> ApiResult<List<Solicitud>>): CargaOffline {
        val sync = sincronizarPendientes(sesion)
        if (sync == ResultadoSincronizacion.SesionVencida) return CargaOffline.SesionVencida
        val aviso = (sync as? ResultadoSincronizacion.Subida)?.aviso
        val usuario = sesion.username
        return when (val r = llamada(sesion.token)) {
            is ApiResult.Ok -> {
                val anterior = leerLista(usuario)
                if (anterior != null) avisarCambios(sesion.role, anterior, r.valor)
                guardarLista(usuario, r.valor)
                val cola = cola(usuario)
                CargaOffline.Listo(DatosOffline(aplicarCola(r.valor, cola, usuario), false, cola.size, aviso))
            }
            ApiResult.SesionVencida -> CargaOffline.SesionVencida
            else -> {
                val copia = leerLista(usuario)
                val cola = cola(usuario)
                if (copia == null && cola.isEmpty()) CargaOffline.Fallo(r.mensajeDeError() ?: "No se pudo cargar la información")
                else CargaOffline.Listo(DatosOffline(aplicarCola(copia.orEmpty(), cola, usuario), true, cola.size, aviso))
            }
        }
    }

    /** Notificaciones locales al detectar cambios entre la copia anterior y la nueva. */
    internal fun avisarCambios(rol: String, anterior: List<Solicitud>, nueva: List<Solicitud>) {
        val antes = anterior.associateBy { it.id }
        if (rol == Roles.OPERADOR) {
            val nuevas = nueva.count { it.id !in antes && it.estado != EstadoSolicitud.ENTREGADA }
            if (nuevas > 0) {
                Plataforma.notificar(
                    "Ruta actualizada",
                    if (nuevas == 1) "Tienes 1 entrega nueva en tu ruta" else "Tienes $nuevas entregas nuevas en tu ruta"
                )
            }
        } else {
            nueva.forEach { s ->
                val previo = antes[s.id] ?: return@forEach
                if (previo.estado != s.estado) {
                    val texto = when (s.estado) {
                        EstadoSolicitud.PROGRAMADA -> "Tu pipa de ${formatLitros(s.capacidadLitros)} L quedó programada para el ${formatDia(s.dia)} a las ${s.hora}"
                        EstadoSolicitud.EN_CAMINO -> "Tu pipa de ${formatLitros(s.capacidadLitros)} L va en camino"
                        EstadoSolicitud.ENTREGADA -> "Tu pipa de ${formatLitros(s.capacidadLitros)} L fue entregada"
                        EstadoSolicitud.NO_ENTREGADA -> "El repartidor no pudo entregar tu pipa" + (s.observacion?.let { ": $it" } ?: "")
                        EstadoSolicitud.CANCELADA -> "Tu solicitud de ${formatLitros(s.capacidadLitros)} L fue cancelada"
                        else -> "Tu solicitud ahora está: ${etiquetaEstado(s.estado)}"
                    }
                    Plataforma.notificar("Tu solicitud de agua", texto)
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    //  Acciones
    // -------------------------------------------------------------------------------------------

    /** Pedir pipa: se intenta en línea; sin red queda en la cola con el mismo id. */
    suspend fun pedirPipa(sesion: LoginResponse, datos: NuevaSolicitud): Accion {
        val conId = datos.copy(id = datos.id ?: nuevoId())
        return when (val r = crearSolicitud(sesion.token, conId)) {
            is ApiResult.Ok -> {
                guardarLista(sesion.username, listOf(r.valor) + leerLista(sesion.username).orEmpty().filterNot { it.id == r.valor.id })
                Accion.Hecha("Listo. Tu solicitud de ${formatLitros(conId.capacidadLitros)} L fue enviada")
            }
            ApiResult.SesionVencida -> Accion.SesionVencida
            is ApiResult.Rechazo -> Accion.Error(r.mensaje.ifBlank { "No se pudo enviar la solicitud" })
            else -> {
                encolar(
                    sesion.username,
                    OperacionSync(
                        id = nuevoId(),
                        tipo = TiposOperacion.CREAR_SOLICITUD,
                        entidadId = conId.id!!,
                        payload = buildJsonObject {
                            put("dia", conId.dia)
                            conId.hora?.let { put("hora", it) }
                            put("capacidadLitros", conId.capacidadLitros)
                            conId.domicilio?.let { put("domicilio", it) }
                            conId.colonia?.let { put("colonia", it) }
                            conId.referencia?.let { put("referencia", it) }
                            conId.notas?.let { put("notas", it) }
                        },
                        creadaEn = ahoraIso()
                    )
                )
                Accion.Hecha("Sin conexión: tu solicitud se guardó en el teléfono y se enviará sola cuando vuelva la señal", sinConexion = true)
            }
        }
    }

    /** Cancelar: si la pipa todavía no llegaba al servidor, basta con quitarla de la cola. */
    suspend fun cancelar(sesion: LoginResponse, solicitud: Solicitud): Accion {
        val usuario = sesion.username
        val enCola = cola(usuario).firstOrNull { it.tipo == TiposOperacion.CREAR_SOLICITUD && it.entidadId == solicitud.id }
        if (enCola != null) {
            guardarCola(usuario, cola(usuario).filterNot { it.entidadId == solicitud.id })
            return Accion.Hecha("Solicitud cancelada")
        }
        return when (val r = cancelarSolicitud(sesion.token, solicitud.id)) {
            is ApiResult.Ok -> {
                guardarLista(usuario, leerLista(usuario).orEmpty().map { if (it.id == r.valor.id) r.valor else it })
                Accion.Hecha("Solicitud cancelada")
            }
            ApiResult.SesionVencida -> Accion.SesionVencida
            is ApiResult.Rechazo -> Accion.Error(r.mensaje.ifBlank { "No se pudo cancelar" })
            else -> {
                encolar(usuario, OperacionSync(nuevoId(), TiposOperacion.CANCELAR_SOLICITUD, solicitud.id, creadaEn = ahoraIso()))
                Accion.Hecha("Sin conexión: la cancelación se enviará cuando vuelva la señal", sinConexion = true)
            }
        }
    }

    /** Repartidor: en camino, entregada (con litros) o no entregada (con observación). */
    suspend fun cerrarEntrega(sesion: LoginResponse, id: String, cambio: CambioEntrega): Accion {
        val usuario = sesion.username
        val conHora = cambio.copy(actualizadoEn = cambio.actualizadoEn ?: ahoraIso())
        // Si ya hay cambios de esta entrega en la cola, este va detrás de ellos para respetar el orden
        val hayPrevios = cola(usuario).any { it.entidadId == id }
        if (!hayPrevios) {
            when (val r = actualizarEntrega(sesion.token, id, conHora)) {
                is ApiResult.Ok -> {
                    guardarLista(usuario, leerLista(usuario).orEmpty().map { if (it.id == r.valor.id) r.valor else it })
                    return Accion.Hecha(mensajeEntrega(conHora.estado))
                }
                ApiResult.SesionVencida -> return Accion.SesionVencida
                is ApiResult.Rechazo -> return Accion.Error(r.mensaje.ifBlank { "No se pudo guardar el cambio" })
                else -> Unit
            }
        }
        encolar(
            usuario,
            OperacionSync(
                id = nuevoId(),
                tipo = TiposOperacion.ACTUALIZAR_ENTREGA,
                entidadId = id,
                payload = buildJsonObject {
                    put("estado", conHora.estado)
                    conHora.litrosEntregados?.let { put("litrosEntregados", it) }
                    conHora.observacion?.let { put("observacion", it) }
                    put("actualizadoEn", conHora.actualizadoEn)
                },
                creadaEn = conHora.actualizadoEn.orEmpty()
            )
        )
        return Accion.Hecha("Guardado en el teléfono. Se subirá solo cuando vuelva la señal", sinConexion = true)
    }

    private fun mensajeEntrega(estado: String) = when (estado) {
        EstadoSolicitud.EN_CAMINO -> "Marcada en camino"
        EstadoSolicitud.ENTREGADA -> "Entrega registrada"
        else -> "Se registró que no se pudo entregar"
    }

    /** Trabajo de fondo (Android WorkManager): sube la cola y revisa si hay cambios para notificar. */
    suspend fun trabajoEnSegundoPlano() {
        val sesion = sesionGuardada() ?: return
        when (sesion.role) {
            Roles.CIUDADANO -> cargarSolicitudes(sesion)
            Roles.OPERADOR -> cargarRuta(sesion)
            else -> Unit
        }
    }
}

@kotlinx.serialization.Serializable
private data class SesionGuardada(val sesion: LoginResponse, val venceEnMs: Long)

private fun kotlinx.serialization.json.JsonObject.texto(clave: String): String? =
    (this[clave] as? kotlinx.serialization.json.JsonPrimitive)?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.content }
