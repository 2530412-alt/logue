package com.logue.frontend

import androidx.compose.ui.graphics.Color

/*
 * Cómo se muestran las solicitudes que manda el servidor (SOL-01, SOL-02, SEG-01, ENT-01, ENT-02, ADM-01, ADM-02).
 * Son funciones normales, sin Compose, para poder probarlas en commonTest.
 */

/** Texto del estatus (SEG-01): Recibida, En proceso, Programada, En camino, Entregada o Cancelada. */
fun etiquetaEstado(estado: String): String = when (estado) {
    EstadoSolicitud.RECIBIDA -> "Recibida"
    EstadoSolicitud.EN_PROCESO -> "En proceso"
    EstadoSolicitud.PROGRAMADA -> "Programada"
    EstadoSolicitud.EN_CAMINO -> "En camino"
    EstadoSolicitud.ENTREGADA -> "Entregada"
    EstadoSolicitud.CANCELADA -> "Cancelada"
    else -> estado
}

/** Color de la etiqueta de estatus (SEG-01). */
fun colorEstado(estado: String): Color = when (estado) {
    EstadoSolicitud.RECIBIDA, EstadoSolicitud.EN_PROCESO -> AppColors.Yellow
    EstadoSolicitud.PROGRAMADA -> AppColors.Accent
    EstadoSolicitud.EN_CAMINO -> AppColors.Blue
    EstadoSolicitud.ENTREGADA -> AppColors.Green
    EstadoSolicitud.CANCELADA -> AppColors.Red
    else -> AppColors.TextMuted
}

/** Están esperando atención: recibidas, en proceso o ya programadas. */
fun esPendiente(estado: String): Boolean =
    estado == EstadoSolicitud.RECIBIDA || estado == EstadoSolicitud.EN_PROCESO || estado == EstadoSolicitud.PROGRAMADA

/** 10000 -> "10,000". */
fun formatLitros(litros: Int): String {
    val texto = litros.toString().reversed().chunked(3).joinToString(",").reversed()
    return texto
}

/** "2026-11-04" -> "04/11/2026". Si no tiene ese formato se muestra tal cual. */
fun formatDia(dia: String): String {
    val partes = dia.split('-')
    return if (partes.size == 3 && partes[0].length == 4) "${partes[2]}/${partes[1]}/${partes[0]}" else dia
}

/** Tarjetas del ciudadano (SOL-02): en camino, pendientes y entregadas. */
fun contadoresCiudadano(solicitudes: List<Solicitud>) = listOf(
    Stat(solicitudes.count { it.estado == EstadoSolicitud.EN_CAMINO }.toString(), "En camino", AppColors.Blue),
    Stat(solicitudes.count { esPendiente(it.estado) }.toString(), "Pendientes", AppColors.Yellow),
    Stat(solicitudes.count { it.estado == EstadoSolicitud.ENTREGADA }.toString(), "Entregadas", AppColors.Green)
)

/** Renglones de «Mis solicitudes» (SOL-01, SEG-01). */
fun entradasCiudadano(solicitudes: List<Solicitud>) = solicitudes.map {
    Entry(
        time = it.hora,
        title = "Pipa de ${formatLitros(it.capacidadLitros)} L",
        subtitle = "${formatDia(it.dia)} · ${it.domicilio}",
        status = etiquetaEstado(it.estado),
        color = colorEstado(it.estado)
    )
}

/** Tarjetas del repartidor (ENT-02): entregas del día y completadas. */
fun contadoresOperador(entregas: List<Solicitud>) = listOf(
    Stat(entregas.size.toString(), "Entregas hoy", AppColors.Accent),
    Stat(entregas.count { it.estado == EstadoSolicitud.ENTREGADA }.toString(), "Completadas", AppColors.Green)
)

/** Ruta del día (ENT-01): la primera entrega sin completar es «Siguiente». */
fun entradasOperador(entregas: List<Solicitud>): List<Entry> {
    val siguiente = entregas.firstOrNull { it.estado != EstadoSolicitud.ENTREGADA }?.id
    return entregas.map {
        val (texto, color) = when {
            it.estado == EstadoSolicitud.ENTREGADA -> "Completada" to AppColors.Green
            it.id == siguiente -> "Siguiente" to AppColors.Blue
            else -> "Pendiente" to AppColors.Yellow
        }
        Entry(it.hora, it.colonia, "${formatLitros(it.capacidadLitros)} L", texto, color)
    }
}

/** Tarjetas del delegado (ADM-01): pendientes, en camino, entregadas y canceladas. */
fun contadoresAdmin(solicitudes: List<Solicitud>) = listOf(
    Stat(solicitudes.count { esPendiente(it.estado) }.toString(), "Pendientes", AppColors.Yellow),
    Stat(solicitudes.count { it.estado == EstadoSolicitud.EN_CAMINO }.toString(), "En camino", AppColors.Blue),
    Stat(solicitudes.count { it.estado == EstadoSolicitud.ENTREGADA }.toString(), "Entregadas", AppColors.Green),
    Stat(solicitudes.count { it.estado == EstadoSolicitud.CANCELADA }.toString(), "Canceladas", AppColors.Red)
)

/** Solicitudes recientes del delegado (ADM-02): hora, colonia, repartidor asignado y estatus. */
fun entradasAdmin(solicitudes: List<Solicitud>, limite: Int = 20) = solicitudes.take(limite).map {
    Entry(
        time = it.hora,
        title = it.colonia,
        subtitle = "${formatDia(it.dia)} · " + (it.repartidor?.let { r -> "Operador: $r" } ?: "Sin asignar"),
        status = etiquetaEstado(it.estado),
        color = colorEstado(it.estado)
    )
}
