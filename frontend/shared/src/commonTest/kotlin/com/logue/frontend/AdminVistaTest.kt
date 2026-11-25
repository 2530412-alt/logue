package com.logue.frontend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Cálculos del panel web del delegado (AdminVista.kt). */
class AdminVistaTest {

    private fun s(id: String, estado: String, dia: String = "2026-11-23", hora: String = "09:00", rep: String? = null, orden: Int? = null, litros: Int = 5_000, entregados: Int? = null, colonia: String = "Las Camelinas") =
        Solicitud(id, "ana", dia, hora, litros, "Calle $id", colonia, estado, rep, orden = orden, litrosEntregados = entregados)

    private val lista = listOf(
        s("a", EstadoSolicitud.RECIBIDA),
        s("b", EstadoSolicitud.PROGRAMADA, rep = "luis", orden = 2, hora = "08:00"),
        s("c", EstadoSolicitud.EN_CAMINO, rep = "luis", orden = 1, hora = "10:00"),
        s("d", EstadoSolicitud.ENTREGADA, dia = "2026-11-22", rep = "luis", entregados = 4_000),
        s("e", EstadoSolicitud.NO_ENTREGADA, dia = "2026-11-22", rep = "pepe"),
        s("f", EstadoSolicitud.CANCELADA, dia = "2026-11-21", colonia = "Centro")
    )

    @Test
    fun filtrosYBusqueda() {
        assertEquals(listOf("a"), filtrarSolicitudes(lista, FiltroSolicitudes.POR_ASIGNAR, "").map { it.id })
        assertEquals(listOf("b"), filtrarSolicitudes(lista, FiltroSolicitudes.PROGRAMADAS, "").map { it.id })
        assertEquals(setOf("d", "e", "f"), filtrarSolicitudes(lista, FiltroSolicitudes.TERMINADAS, "").map { it.id }.toSet())
        assertEquals(listOf("e"), filtrarSolicitudes(lista, FiltroSolicitudes.TODAS, "PEPE").map { it.id })
    }

    @Test
    fun rutasEnOrdenYSiguienteParada() {
        val rutas = rutasDelDia(lista, "2026-11-23")
        assertEquals(1, rutas.size)
        assertEquals(listOf("c", "b"), rutas.single().paradas.map { it.id })
        assertEquals(3, siguienteOrden(lista, "luis", "2026-11-23"))
        assertEquals(1, siguienteOrden(lista, "pepe", "2026-11-23"))
    }

    @Test
    fun reporteDelPeriodo() {
        val dias = listOf("2026-11-21", "2026-11-22", "2026-11-23")
        val r = calcularReporte(lista, "2026-11-21", "2026-11-23", dias)
        assertEquals(6, r.total)
        assertEquals(1, r.entregadas)
        assertEquals(1, r.noEntregadas)
        assertEquals(1, r.canceladas)
        assertEquals(4_000, r.litrosEntregados)
        assertEquals(50, r.efectividad)
        assertEquals(3, r.porDia.size)
        assertEquals("luis", r.porRepartidor.first().etiqueta)
        assertEquals("Las Camelinas", r.porColonia.first().etiqueta)
    }

    @Test
    fun csvYValidaciones() {
        val csv = solicitudesCsv(listOf(s("x", EstadoSolicitud.RECIBIDA)))
        assertTrue(csv.startsWith("folio,dia,hora"))
        assertTrue("\"Recibida\"" in csv)
        assertTrue(esFechaValida("2026-11-23"))
        assertFalse(esFechaValida("23/11/2026"))
        assertTrue(esHoraValida("09:30"))
        assertFalse(esHoraValida("25:00"))
    }
}
