package com.logue.frontend

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pruebas del modo sin conexión y de las funciones del rediseño (integración AquaGo). */
class OfflineTest {

    private fun solicitud(id: String, estado: String, dia: String = "2026-11-20", hora: String = "09:00") =
        Solicitud(id, "ana", dia, hora, 5_000, "Calle 1", "Las Camelinas", estado)

    @Test
    fun aplicarColaMuestraLaPipaCreadaSinConexion() {
        val op = OperacionSync(
            id = "op1", tipo = TiposOperacion.CREAR_SOLICITUD, entidadId = "nueva",
            payload = buildJsonObject { put("dia", "2026-11-23"); put("capacidadLitros", 2_500); put("domicilio", "Av. 5") },
            creadaEn = "2026-11-23T10:00:00Z"
        )
        val lista = Offline.aplicarCola(listOf(solicitud("a", EstadoSolicitud.RECIBIDA)), listOf(op), "ana")
        assertEquals(2, lista.size)
        val nueva = lista.first()
        assertEquals("nueva", nueva.id)
        assertEquals(2_500, nueva.capacidadLitros)
        assertEquals(EstadoSolicitud.RECIBIDA, nueva.estado)
        assertTrue(nueva.pendienteSync)
    }

    @Test
    fun aplicarColaNoDuplicaSiElServidorYaLaTiene() {
        val op = OperacionSync("op1", TiposOperacion.CREAR_SOLICITUD, "a", buildJsonObject { put("dia", "2026-11-23") })
        assertEquals(1, Offline.aplicarCola(listOf(solicitud("a", EstadoSolicitud.RECIBIDA)), listOf(op), "ana").size)
    }

    @Test
    fun aplicarColaCancelacionYEntrega() {
        val base = listOf(solicitud("a", EstadoSolicitud.RECIBIDA), solicitud("b", EstadoSolicitud.EN_CAMINO))
        val cola = listOf(
            OperacionSync("1", TiposOperacion.CANCELAR_SOLICITUD, "a"),
            OperacionSync("2", TiposOperacion.ACTUALIZAR_ENTREGA, "b", buildJsonObject { put("estado", EstadoSolicitud.ENTREGADA); put("litrosEntregados", 4_000) })
        )
        val r = Offline.aplicarCola(base, cola, "ana").associateBy { it.id }
        assertEquals(EstadoSolicitud.CANCELADA, r.getValue("a").estado)
        assertEquals(EstadoSolicitud.ENTREGADA, r.getValue("b").estado)
        assertEquals(4_000, r.getValue("b").litrosEntregados)
        assertTrue(r.getValue("b").pendienteSync)
    }

    @Test
    fun noEntregadaDejaCeroLitros() {
        val cola = listOf(OperacionSync("1", TiposOperacion.ACTUALIZAR_ENTREGA, "a", buildJsonObject { put("estado", EstadoSolicitud.NO_ENTREGADA); put("observacion", "No había nadie") }))
        val r = Offline.aplicarCola(listOf(solicitud("a", EstadoSolicitud.EN_CAMINO)), cola, "ana").single()
        assertEquals(0, r.litrosEntregados)
        assertEquals("No había nadie", r.observacion)
    }

    @Test
    fun sesionGuardadaYVencida() {
        Plataforma.almacen = AlmacenEnMemoria()
        Offline.guardarSesion(LoginResponse("ana", Roles.CIUDADANO, "t", expiresIn = 3600))
        assertEquals("ana", Offline.sesionGuardada()?.username)
        Offline.guardarSesion(LoginResponse("ana", Roles.CIUDADANO, "t", expiresIn = -10))
        // expiresIn <= 0 usa 12 h por defecto
        assertEquals("ana", Offline.sesionGuardada()?.username)
        Offline.borrarSesion()
        assertNull(Offline.sesionGuardada())
    }

    @Test
    fun ordenPrimeroActivasDespuesHistorial() {
        val lista = listOf(
            solicitud("e", EstadoSolicitud.ENTREGADA, "2026-11-10"),
            solicitud("p2", EstadoSolicitud.PROGRAMADA, "2026-11-25"),
            solicitud("p1", EstadoSolicitud.RECIBIDA, "2026-11-24"),
            solicitud("c", EstadoSolicitud.CANCELADA, "2026-11-12")
        )
        assertEquals(listOf("p1", "p2", "c", "e"), ordenarSolicitudes(lista).map { it.id })
    }

    @Test
    fun cancelableSoloMientrasEstaPendiente() {
        assertTrue(esCancelable(EstadoSolicitud.RECIBIDA))
        assertTrue(esCancelable(EstadoSolicitud.PROGRAMADA))
        assertTrue(!esCancelable(EstadoSolicitud.EN_CAMINO))
        assertTrue(!esCancelable(EstadoSolicitud.ENTREGADA))
    }

    @Test
    fun estadoNoEntregada() {
        assertEquals("No entregada", etiquetaEstado(EstadoSolicitud.NO_ENTREGADA))
        assertEquals(AppColors.Red, colorEstado(EstadoSolicitud.NO_ENTREGADA))
    }

    @Test
    fun inicialesDelAvatar() {
        assertEquals("ML", iniciales("maria.lopez"))
        assertEquals("JP", iniciales("Juan Pérez"))
        assertEquals("AN", iniciales("ana"))
        assertEquals("?", iniciales("  "))
    }

    @Test
    fun fechaUtcTieneFormatoYAvanza() {
        val hoy = fechaUtc(0)
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(hoy))
        assertTrue(fechaUtc(1) > hoy)
    }
}
