package com.logue.frontend

/*
 * Cálculos del panel del delegado (filtros, rutas por repartidor y reportes). Funciones normales, sin
 * Compose, para probarlas en commonTest. Los reportes se calculan con las solicitudes que manda el
 * servidor (GET /solicitudes); no se inventa ningún dato.
 */

/** Filtros de la lista de solicitudes. */
enum class FiltroSolicitudes(val etiqueta: String) {
    POR_ASIGNAR("Por asignar"),
    PROGRAMADAS("Programadas"),
    EN_CAMINO("En camino"),
    TERMINADAS("Terminadas"),
    TODAS("Todas")
}

fun coincideFiltro(s: Solicitud, filtro: FiltroSolicitudes): Boolean = when (filtro) {
    FiltroSolicitudes.POR_ASIGNAR -> s.repartidor == null && (s.estado == EstadoSolicitud.RECIBIDA || s.estado == EstadoSolicitud.EN_PROCESO)
    FiltroSolicitudes.PROGRAMADAS -> s.estado == EstadoSolicitud.PROGRAMADA
    FiltroSolicitudes.EN_CAMINO -> s.estado == EstadoSolicitud.EN_CAMINO
    FiltroSolicitudes.TERMINADAS -> esFinal(s.estado)
    FiltroSolicitudes.TODAS -> true
}

/** Filtra por estado y por texto (ciudadano, domicilio, colonia, repartidor o folio) y ordena por día y hora. */
fun filtrarSolicitudes(lista: List<Solicitud>, filtro: FiltroSolicitudes, texto: String): List<Solicitud> {
    val t = texto.trim().lowercase()
    return lista
        .filter { coincideFiltro(it, filtro) }
        .filter {
            t.isEmpty() || listOfNotNull(it.username, it.domicilio, it.colonia, it.repartidor, it.referencia, it.id)
                .any { campo -> campo.lowercase().contains(t) }
        }
        .sortedWith(
            if (filtro == FiltroSolicitudes.TERMINADAS || filtro == FiltroSolicitudes.TODAS)
                compareByDescending<Solicitud> { it.dia }.thenByDescending { it.hora }
            else compareBy<Solicitud>({ it.dia }, { it.hora })
        )
}

/** Ruta de un repartidor en un día: sus solicitudes en orden (orden que puso el delegado y luego la hora). */
data class RutaRepartidor(val repartidor: String, val paradas: List<Solicitud>) {
    val terminadas get() = paradas.count { esFinal(it.estado) }
    val litros get() = paradas.filter { it.estado != EstadoSolicitud.CANCELADA }.sumOf { it.capacidadLitros }
}

fun rutasDelDia(lista: List<Solicitud>, dia: String): List<RutaRepartidor> =
    lista.filter { it.dia == dia && it.repartidor != null }
        .groupBy { it.repartidor!! }
        .map { (rep, paradas) -> RutaRepartidor(rep, paradas.sortedWith(compareBy({ it.orden ?: Int.MAX_VALUE }, { it.hora }))) }
        .sortedBy { it.repartidor.lowercase() }

/** Siguiente número de orden para agregar una parada a la ruta de [repartidor] en [dia]. */
fun siguienteOrden(lista: List<Solicitud>, repartidor: String, dia: String): Int =
    (lista.filter { it.repartidor.equals(repartidor, ignoreCase = true) && it.dia == dia }.maxOfOrNull { it.orden ?: 0 } ?: 0) + 1

/** Fila de un reporte: etiqueta, número de solicitudes y litros. */
data class FilaReporte(val etiqueta: String, val solicitudes: Int, val litros: Int, val extra: Int = 0)

data class Reporte(
    val total: Int,
    val porEstado: List<FilaReporte>,
    val litrosSolicitados: Int,
    val litrosEntregados: Int,
    val entregadas: Int,
    val noEntregadas: Int,
    val canceladas: Int,
    /** etiqueta = repartidor, solicitudes = asignadas, litros = entregados, extra = entregadas. */
    val porRepartidor: List<FilaReporte>,
    val porColonia: List<FilaReporte>,
    /** Una fila por día del periodo (aunque tenga 0). */
    val porDia: List<FilaReporte>
) {
    /** Porcentaje de entregas logradas entre las que ya se cerraron (entregadas + no entregadas). */
    val efectividad: Int get() = if (entregadas + noEntregadas == 0) 0 else entregadas * 100 / (entregadas + noEntregadas)
}

/** Reporte de las solicitudes con día entre [desde] y [hasta] (AAAA-MM-DD, inclusive). [dias] = los días del periodo, en orden. */
fun calcularReporte(lista: List<Solicitud>, desde: String, hasta: String, dias: List<String>): Reporte {
    val periodo = lista.filter { it.dia in desde..hasta }
    val estados = listOf(
        EstadoSolicitud.RECIBIDA, EstadoSolicitud.EN_PROCESO, EstadoSolicitud.PROGRAMADA, EstadoSolicitud.EN_CAMINO,
        EstadoSolicitud.ENTREGADA, EstadoSolicitud.NO_ENTREGADA, EstadoSolicitud.CANCELADA
    )
    val entregadas = periodo.filter { it.estado == EstadoSolicitud.ENTREGADA }
    return Reporte(
        total = periodo.size,
        porEstado = estados.map { e -> periodo.filter { it.estado == e }.let { FilaReporte(etiquetaEstado(e), it.size, it.sumOf { s -> s.capacidadLitros }) } }
            .filter { it.solicitudes > 0 },
        litrosSolicitados = periodo.filter { it.estado != EstadoSolicitud.CANCELADA }.sumOf { it.capacidadLitros },
        litrosEntregados = entregadas.sumOf { it.litrosEntregados ?: it.capacidadLitros },
        entregadas = entregadas.size,
        noEntregadas = periodo.count { it.estado == EstadoSolicitud.NO_ENTREGADA },
        canceladas = periodo.count { it.estado == EstadoSolicitud.CANCELADA },
        porRepartidor = periodo.filter { it.repartidor != null }.groupBy { it.repartidor!! }.map { (rep, l) ->
            val ok = l.filter { it.estado == EstadoSolicitud.ENTREGADA }
            FilaReporte(rep, l.size, ok.sumOf { it.litrosEntregados ?: it.capacidadLitros }, ok.size)
        }.sortedByDescending { it.extra },
        porColonia = periodo.groupBy { it.colonia.ifBlank { "Sin colonia" } }
            .map { (c, l) -> FilaReporte(c, l.size, l.sumOf { it.capacidadLitros }) }
            .sortedByDescending { it.solicitudes },
        porDia = dias.map { d -> periodo.filter { it.dia == d }.let { FilaReporte(d, it.size, it.sumOf { s -> s.capacidadLitros }) } }
    )
}

/** Texto CSV del reporte por solicitud (para copiar a Excel). */
fun solicitudesCsv(lista: List<Solicitud>): String = buildString {
    appendLine("folio,dia,hora,ciudadano,domicilio,colonia,litros,estado,repartidor,litros_entregados,observacion")
    lista.forEach { s ->
        appendLine(
            listOf(s.id, s.dia, s.hora, s.username, s.domicilio, s.colonia, s.capacidadLitros.toString(), etiquetaEstado(s.estado),
                s.repartidor.orEmpty(), s.litrosEntregados?.toString().orEmpty(), s.observacion.orEmpty())
                .joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        )
    }
}

/** ¿Es una fecha AAAA-MM-DD válida a simple vista? */
fun esFechaValida(texto: String): Boolean {
    val p = texto.trim().split('-')
    if (p.size != 3 || p[0].length != 4 || p[1].length != 2 || p[2].length != 2) return false
    val m = p[1].toIntOrNull() ?: return false
    val d = p[2].toIntOrNull() ?: return false
    return p[0].toIntOrNull() != null && m in 1..12 && d in 1..31
}

/** ¿Es una hora HH:MM válida? */
fun esHoraValida(texto: String): Boolean {
    val p = texto.trim().split(':')
    if (p.size != 2 || p[0].length != 2 || p[1].length != 2) return false
    return (p[0].toIntOrNull() ?: -1) in 0..23 && (p[1].toIntOrNull() ?: -1) in 0..59
}
