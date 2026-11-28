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

/**
 * Filas del reporte de solicitudes con los lineamientos de reportes: ordenadas por día y hora, el día
 * solo aparece cuando cambia, un subtotal por día y un total general de las columnas numéricas.
 */
data class FilaDetalle(
    val dia: String,            // vacío cuando el día se repite
    val hora: String,
    val ciudadano: String,
    val domicilio: String,
    val colonia: String,
    val litrosSolicitados: Int,
    val estado: String,
    val repartidor: String,
    val litrosEntregados: Int,
    val tipo: TipoFila = TipoFila.DETALLE
)

enum class TipoFila { DETALLE, SUBTOTAL, TOTAL }

/** Litros que cuentan como entregados en el reporte (0 si no se entregó). */
private fun litrosEntregadosDe(s: Solicitud) = if (s.estado == EstadoSolicitud.ENTREGADA) s.litrosEntregados ?: s.capacidadLitros else 0

fun filasReporte(lista: List<Solicitud>): List<FilaDetalle> {
    val filas = mutableListOf<FilaDetalle>()
    lista.sortedWith(compareBy({ it.dia }, { it.hora })).groupBy { it.dia }.forEach { (dia, delDia) ->
        delDia.forEachIndexed { i, s ->
            filas += FilaDetalle(
                dia = if (i == 0) formatDia(dia) else "",
                hora = s.hora,
                ciudadano = s.username,
                domicilio = s.domicilio,
                colonia = s.colonia,
                litrosSolicitados = s.capacidadLitros,
                estado = etiquetaEstado(s.estado),
                repartidor = s.repartidor ?: "Sin asignar",
                litrosEntregados = litrosEntregadosDe(s)
            )
        }
        filas += FilaDetalle("", "", "Subtotal del ${formatDia(dia)} (${delDia.size} solicitudes)", "", "",
            delDia.sumOf { it.capacidadLitros }, "", "", delDia.sumOf { litrosEntregadosDe(it) }, TipoFila.SUBTOTAL)
    }
    filas += FilaDetalle("", "", "Total general (${lista.size} solicitudes)", "", "",
        lista.sumOf { it.capacidadLitros }, "", "", lista.sumOf { litrosEntregadosDe(it) }, TipoFila.TOTAL)
    return filas
}

/** Encabezados de las columnas del reporte (sin abreviaturas; lo más importante a la izquierda). */
val ENCABEZADOS_REPORTE = listOf(
    "Día", "Hora", "Ciudadano", "Domicilio", "Colonia", "Litros solicitados", "Estado", "Repartidor", "Litros entregados"
)

/** Texto CSV del reporte (se abre en Excel): título, fecha de generación, periodo, columnas con encabezado, subtotales y total. */
fun solicitudesCsv(lista: List<Solicitud>, desde: String, hasta: String, generado: String): String = buildString {
    fun linea(celdas: List<String>) = appendLine(celdas.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" })
    linea(listOf("AquaGo - Reporte de solicitudes de pipas"))
    linea(listOf("Fecha de generación: $generado"))
    linea(listOf("Periodo: del ${formatDia(desde)} al ${formatDia(hasta)}"))
    appendLine()
    linea(ENCABEZADOS_REPORTE)
    filasReporte(lista).forEach { f ->
        linea(listOf(f.dia, f.hora, f.ciudadano, f.domicilio, f.colonia, f.litrosSolicitados.toString(), f.estado, f.repartidor, f.litrosEntregados.toString()))
    }
}

private fun escaparHtml(texto: String) = texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/**
 * Reporte imprimible (HTML) con los lineamientos de reportes: título, fecha de generación en el
 * encabezado, columnas con encabezado que se repiten en cada hoja, día solo cuando cambia, subtotales
 * por día, total general, resumen por repartidor con total y número de página en cada hoja.
 */
fun reporteHtml(lista: List<Solicitud>, desde: String, hasta: String, generado: String): String {
    val filas = filasReporte(lista).joinToString("") { f ->
        val clase = when (f.tipo) { TipoFila.DETALLE -> ""; TipoFila.SUBTOTAL -> " class=\"subtotal\""; TipoFila.TOTAL -> " class=\"total\"" }
        "<tr$clase><td>${escaparHtml(f.dia)}</td><td>${escaparHtml(f.hora)}</td><td>${escaparHtml(f.ciudadano)}</td>" +
            "<td>${escaparHtml(f.domicilio)}</td><td>${escaparHtml(f.colonia)}</td><td class=\"num\">${formatLitros(f.litrosSolicitados)}</td>" +
            "<td>${escaparHtml(f.estado)}</td><td>${escaparHtml(f.repartidor)}</td><td class=\"num\">${formatLitros(f.litrosEntregados)}</td></tr>"
    }
    val porRepartidor: List<Pair<String, List<Solicitud>>> = lista.filter { it.repartidor != null }.groupBy { it.repartidor!! }.toList().sortedBy { it.first }
    val filasRep: String = porRepartidor.joinToString("") { (rep, l) ->
        "<tr><td>${escaparHtml(rep)}</td><td class=\"num\">${l.size}</td><td class=\"num\">${l.count { it.estado == EstadoSolicitud.ENTREGADA }}</td>" +
            "<td class=\"num\">${l.count { it.estado == EstadoSolicitud.NO_ENTREGADA }}</td><td class=\"num\">${formatLitros(l.sumOf { litrosEntregadosDe(it) })}</td></tr>"
    }
    val asignadas = porRepartidor.flatMap { it.second }
    val totalRep = "<tr class=\"total\"><td>Total</td><td class=\"num\">${asignadas.size}</td><td class=\"num\">${asignadas.count { it.estado == EstadoSolicitud.ENTREGADA }}</td>" +
        "<td class=\"num\">${asignadas.count { it.estado == EstadoSolicitud.NO_ENTREGADA }}</td><td class=\"num\">${formatLitros(asignadas.sumOf { litrosEntregadosDe(it) })}</td></tr>"
    val encabezados = ENCABEZADOS_REPORTE.joinToString("") { "<th>$it</th>" }
    return """<!DOCTYPE html><html lang="es"><head><meta charset="utf-8"><title>Reporte de solicitudes de pipas</title><style>
@page { size: letter landscape; margin: 1.6cm 1.4cm 1.8cm; @bottom-right { content: "Página " counter(page) " de " counter(pages); font: 9pt Arial; color: #64748B; } @bottom-left { content: "AquaGo · Reporte generado el $generado"; font: 9pt Arial; color: #64748B; } }
body { font-family: Arial, sans-serif; font-size: 10pt; color: #13263D; margin: 0; }
h1 { font-size: 16pt; color: #0B5CAD; margin: 0 0 4px; } h2 { font-size: 12pt; color: #0B5CAD; margin: 18px 0 6px; }
.meta { color: #64748B; margin: 0 0 2px; }
table { width: 100%; border-collapse: collapse; } thead { display: table-header-group; } tr { page-break-inside: avoid; }
th { background: #0B5CAD; color: #fff; text-align: left; padding: 5px 6px; font-size: 9pt; }
td { padding: 4px 6px; border-bottom: 1px solid #DCE5EE; font-size: 9pt; }
.num { text-align: right; } .subtotal td { background: #E3F1FC; font-weight: bold; } .total td { background: #0B5CAD; color: #fff; font-weight: bold; }
</style></head><body>
<h1>Reporte de solicitudes de pipas</h1>
<p class="meta">AquaGo · Colonia Las Camelinas, Santiago Tulantepec de Lugo Guerrero, Hidalgo</p>
<p class="meta">Fecha de generación: <b>$generado</b> · Periodo: del <b>${formatDia(desde)}</b> al <b>${formatDia(hasta)}</b></p>
<h2>Detalle de solicitudes</h2>
<table><thead><tr>$encabezados</tr></thead><tbody>$filas</tbody></table>
<h2>Resumen por repartidor</h2>
<table><thead><tr><th>Repartidor</th><th class="num">Solicitudes asignadas</th><th class="num">Entregadas</th><th class="num">No entregadas</th><th class="num">Litros entregados</th></tr></thead><tbody>${filasRep.ifEmpty { "<tr><td colspan=\"5\">Sin solicitudes asignadas en el periodo</td></tr>" }}$totalRep</tbody></table>
</body></html>"""
}

/** «30/11/2026» -> «2026-11-30»; `null` si no tiene la forma dd/mm/aaaa. */
fun fechaDesdeDdMmAaaa(texto: String): String? {
    val p = texto.trim().split('/')
    if (p.size != 3 || p[0].length != 2 || p[1].length != 2 || p[2].length != 4) return null
    val iso = "${p[2]}-${p[1]}-${p[0]}"
    return iso.takeIf { esFechaValida(it) }
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
