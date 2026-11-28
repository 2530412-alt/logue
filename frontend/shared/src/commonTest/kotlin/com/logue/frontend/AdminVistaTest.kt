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
        val csv = solicitudesCsv(listOf(s("x", EstadoSolicitud.RECIBIDA)), "2026-11-20", "2026-11-26", "27/11/2026 21:40")
        val lineas = csv.lines()
        assertEquals("\"AquaGo - Reporte de solicitudes de pipas\"", lineas[0])
        assertEquals("\"Fecha de generación: 27/11/2026 21:40\"", lineas[1])
        assertTrue(lineas[4].startsWith("\"Día\",\"Hora\",\"Ciudadano\""))
        assertTrue("\"Recibida\"" in csv)
        assertTrue(csv.contains("Total general (1 solicitudes)"))
        assertTrue(esFechaValida("2026-11-23"))
        assertFalse(esFechaValida("23/11/2026"))
        assertTrue(esHoraValida("09:30"))
        assertFalse(esHoraValida("25:00"))
    }
}

/** Correo en el registro y la recuperación (servicio de correo). */
class CorreoValidacionTest {
    @Test
    fun correoOpcionalEnRegistro() {
        val ok = validateRegistration("Ana", "7711234567", "Calle 1", "Las Camelinas", true, "anita", "secreta1", "secreta1")
        assertEquals(null, ok)
        val malo = validateRegistration("Ana", "7711234567", "Calle 1", "Las Camelinas", true, "anita", "secreta1", "secreta1", "ana@")
        assertEquals("Escribe un correo válido, por ejemplo nombre@gmail.com", malo)
        assertTrue(esCorreoValido("ana.lopez@upt.edu.mx"))
    }

    @Test
    fun recuperarConCorreo() {
        assertEquals(null, validateRecoveryAccount("ana@gmail.com"))
        assertEquals("tu correo ana@gmail.com", recoveryDestination("ana@gmail.com"))
    }
}

/** Lineamientos de reportes: el día solo aparece cuando cambia, subtotal por día y total general. */
class ReporteLineamientosTest {
    private fun sol(id: String, dia: String, hora: String, litros: Int, estado: String) =
        Solicitud(id, "ana", dia, hora, litros, "Calle 1", "Las Camelinas", estado)

    @Test
    fun diaSoloCuandoCambiaYSubtotales() {
        val filas = filasReporte(
            listOf(
                sol("b", "2026-11-20", "10:00", 2_500, EstadoSolicitud.ENTREGADA),
                sol("a", "2026-11-20", "09:00", 5_000, EstadoSolicitud.ENTREGADA),
                sol("c", "2026-11-21", "09:00", 1_000, EstadoSolicitud.CANCELADA)
            )
        )
        assertEquals(listOf("20/11/2026", "", "", "21/11/2026", "", ""), filas.map { it.dia })
        assertEquals("09:00", filas[0].hora)
        assertEquals(TipoFila.SUBTOTAL, filas[2].tipo)
        assertEquals(7_500, filas[2].litrosSolicitados)
        assertEquals(7_500, filas[2].litrosEntregados)
        assertEquals(TipoFila.TOTAL, filas.last().tipo)
        assertEquals(8_500, filas.last().litrosSolicitados)
        assertEquals(7_500, filas.last().litrosEntregados)
    }

    @Test
    fun reporteImprimibleConTituloFechaYPaginas() {
        val html = reporteHtml(listOf(sol("a", "2026-11-20", "09:00", 5_000, EstadoSolicitud.ENTREGADA)), "2026-11-20", "2026-11-26", "27/11/2026 21:40")
        assertTrue("<h1>Reporte de solicitudes de pipas</h1>" in html)
        assertTrue("Fecha de generación: <b>27/11/2026 21:40</b>" in html)
        assertTrue("counter(page)" in html)
        assertTrue("<th>Litros entregados</th>" in html)
        assertTrue("Total general" in html)
    }
}
