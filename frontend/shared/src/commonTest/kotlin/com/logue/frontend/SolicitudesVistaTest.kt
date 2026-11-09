package com.logue.frontend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cómo se muestran las solicitudes del servidor (SOL-01, SOL-02, SEG-01, ENT-01, ENT-02, ADM-01, ADM-02). */
class SolicitudesVistaTest {

    private fun sol(id: String, estado: String, hora: String = "10:00", repartidor: String? = null, litros: Int = 10_000) =
        Solicitud(id, "ciudadano", "2026-11-04", hora, litros, "Calle Hidalgo 12", "Las Camelinas", estado, repartidor)

    private val todas = listOf(
        sol("1", EstadoSolicitud.EN_CAMINO, "10:00", "operador"),
        sol("2", EstadoSolicitud.RECIBIDA, "16:30"),
        sol("3", EstadoSolicitud.PROGRAMADA, "17:00", "operador"),
        sol("4", EstadoSolicitud.ENTREGADA, "08:00", "operador"),
        sol("5", EstadoSolicitud.CANCELADA, "09:00")
    )

    @Test
    fun losCuatroEstatusDeLaPropuestaTienenSuTexto() {
        assertEquals("Recibida", etiquetaEstado(EstadoSolicitud.RECIBIDA))
        assertEquals("En proceso", etiquetaEstado(EstadoSolicitud.EN_PROCESO))
        assertEquals("Programada", etiquetaEstado(EstadoSolicitud.PROGRAMADA))
        assertEquals("Entregada", etiquetaEstado(EstadoSolicitud.ENTREGADA))
        assertEquals("Cancelada", etiquetaEstado(EstadoSolicitud.CANCELADA))
    }

    @Test
    fun cadaEstatusTieneSuColor() {
        assertEquals(AppColors.Green, colorEstado(EstadoSolicitud.ENTREGADA))
        assertEquals(AppColors.Blue, colorEstado(EstadoSolicitud.EN_CAMINO))
        assertEquals(AppColors.Red, colorEstado(EstadoSolicitud.CANCELADA))
    }

    @Test
    fun formatoDeLitrosYDeDia() {
        assertEquals("10,000", formatLitros(10_000))
        assertEquals("5,000", formatLitros(5_000))
        assertEquals("500", formatLitros(500))
        assertEquals("04/11/2026", formatDia("2026-11-04"))
        assertEquals("hoy", formatDia("hoy"))
    }

    @Test
    fun contadoresDelCiudadano() {
        val valores = contadoresCiudadano(todas).associate { it.label to it.value }
        assertEquals("1", valores["En camino"])
        assertEquals("2", valores["Pendientes"])
        assertEquals("1", valores["Entregadas"])
    }

    @Test
    fun contadoresDelDelegadoIncluyenCanceladas() {
        val valores = contadoresAdmin(todas).associate { it.label to it.value }
        assertEquals("2", valores["Pendientes"])
        assertEquals("1", valores["En camino"])
        assertEquals("1", valores["Entregadas"])
        assertEquals("1", valores["Canceladas"])
    }

    @Test
    fun sinSolicitudesLosContadoresSonCero() {
        assertTrue(contadoresCiudadano(emptyList()).all { it.value == "0" })
        assertTrue(contadoresAdmin(emptyList()).all { it.value == "0" })
        assertTrue(entradasCiudadano(emptyList()).isEmpty())
    }

    @Test
    fun renglonDelCiudadano() {
        val e = entradasCiudadano(listOf(todas[0])).single()
        assertEquals("10:00", e.time)
        assertEquals("Pipa de 10,000 L", e.title)
        assertEquals("04/11/2026 · Calle Hidalgo 12", e.subtitle)
        assertEquals("En camino", e.status)
    }

    @Test
    fun renglonDelDelegadoMuestraRepartidorOSinAsignar() {
        val e = entradasAdmin(todas)
        assertEquals("Las Camelinas", e[0].title)
        assertTrue(e[0].subtitle.endsWith("Operador: operador"))
        assertTrue(e[1].subtitle.endsWith("Sin asignar"))
    }

    @Test
    fun laListaDelDelegadoSeLimita() {
        val muchas = (1..30).map { sol(it.toString(), EstadoSolicitud.RECIBIDA) }
        assertEquals(20, entradasAdmin(muchas).size)
    }

    @Test
    fun rutaDelRepartidorMarcaLaSiguienteEntrega() {
        val ruta = listOf(
            sol("a", EstadoSolicitud.ENTREGADA, "09:00"),
            sol("b", EstadoSolicitud.PROGRAMADA, "11:30", litros = 5_000),
            sol("c", EstadoSolicitud.PROGRAMADA, "14:00")
        )
        val e = entradasOperador(ruta)
        assertEquals(listOf("Completada", "Siguiente", "Pendiente"), e.map { it.status })
        assertEquals("5,000 L", e[1].subtitle)
        val c = contadoresOperador(ruta).associate { it.label to it.value }
        assertEquals("3", c["Entregas hoy"])
        assertEquals("1", c["Completadas"])
    }

    @Test
    fun elMensajeDeErrorDependeDelResultado() {
        assertEquals(null, ApiResult.Ok(1).mensajeDeError())
        assertEquals(null, ApiResult.SesionVencida.mensajeDeError())
        assertEquals("Ese usuario ya existe, elige otro", ApiResult.Rechazo(409, "Ese usuario ya existe, elige otro").mensajeDeError())
        assertEquals(MENSAJE_SIN_CONEXION, ApiResult.SinConexion.mensajeDeError())
    }

    @Test
    fun fechaLegibleDeUnPago() {
        assertEquals("04/11/2026 18:30", fechaLegible("2026-11-04T18:30:12.345Z"))
    }
}
