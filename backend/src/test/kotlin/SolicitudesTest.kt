package com.logue

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lista de solicitudes y ruta del repartidor leídas del servidor (SOL-01, SOL-02, SEG-01, ENT-01, ENT-02, ADM-01, ADM-02). */
class SolicitudesTest {

    // El reloj de las pruebas marca 2026-11-04 12:00 UTC = 06:00 en America/Mexico_City
    private val hoy = "2026-11-04"

    private fun solicitud(id: String, usuario: String, dia: String, hora: String, estado: String, repartidor: String? = null, litros: Int = 10_000) =
        Solicitud(id, usuario, dia, hora, litros, "Calle Hidalgo 12", "Las Camelinas", estado, repartidor)

    private fun almacen() = SolicitudStore(
        initial = listOf(
            solicitud("s1", "ciudadano", hoy, "10:00", EstadoSolicitud.EN_CAMINO, "operador"),
            solicitud("s2", "ciudadano", hoy, "16:30", EstadoSolicitud.RECIBIDA, litros = 5_000),
            solicitud("s3", "ciudadano", "2026-11-01", "08:00", EstadoSolicitud.ENTREGADA, "operador"),
            solicitud("s4", "vecino", hoy, "09:00", EstadoSolicitud.ENTREGADA, "operador"),
            solicitud("s5", "vecino", "2026-11-05", "11:30", EstadoSolicitud.PROGRAMADA, "operador"),
            solicitud("s6", "vecino", hoy, "12:00", EstadoSolicitud.PROGRAMADA, "otro-operador")
        )
    )

    private fun ids(texto: String) = Json.decodeFromString<List<Solicitud>>(texto).map { it.id }

    @Test
    fun `el ciudadano ve solo sus solicitudes, las mas recientes primero`() = testApplication {
        setupAquaGo(solicitudes = almacen())
        val res = client.get("/solicitudes") { bearerAuth(tokenOf("ciudadano")) }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(listOf("s2", "s1", "s3"), ids(res.bodyAsText()))
    }

    @Test
    fun `el ciudadano sin solicitudes recibe una lista vacia`() = testApplication {
        setupAquaGo(solicitudes = SolicitudStore())
        val res = client.get("/solicitudes") { bearerAuth(tokenOf("ciudadano")) }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("[]", res.bodyAsText())
    }

    @Test
    fun `el delegado ve todas las solicitudes con su repartidor y colonia`() = testApplication {
        setupAquaGo(solicitudes = almacen())
        val res = client.get("/solicitudes") { bearerAuth(tokenOf("admin")) }
        assertEquals(HttpStatusCode.OK, res.status)
        val lista = Json.decodeFromString<List<Solicitud>>(res.bodyAsText())
        assertEquals(6, lista.size)
        assertEquals("operador", lista.first { it.id == "s1" }.repartidor)
        assertEquals("Las Camelinas", lista.first { it.id == "s1" }.colonia)
    }

    @Test
    fun `las solicitudes exigen sesion y no son del repartidor`() = testApplication {
        setupAquaGo(solicitudes = almacen())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/solicitudes").status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/solicitudes") { bearerAuth(tokenOf("operador")) }.status)
    }

    @Test
    fun `la ruta del repartidor trae sus entregas de hoy en orden de hora`() = testApplication {
        setupAquaGo(solicitudes = almacen())
        val res = client.get("/entregas") { bearerAuth(tokenOf("operador")) }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(listOf("s4", "s1"), ids(res.bodyAsText()))
    }

    @Test
    fun `la ruta se puede pedir de otro dia`() = testApplication {
        setupAquaGo(solicitudes = almacen())
        val res = client.get("/entregas?dia=2026-11-05") { bearerAuth(tokenOf("operador")) }
        assertEquals(listOf("s5"), ids(res.bodyAsText()))
        assertEquals(HttpStatusCode.BadRequest, client.get("/entregas?dia=ayer") { bearerAuth(tokenOf("operador")) }.status)
    }

    @Test
    fun `las entregas son solo del repartidor`() = testApplication {
        setupAquaGo(solicitudes = almacen())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/entregas").status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/entregas") { bearerAuth(tokenOf("ciudadano")) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/entregas") { bearerAuth(tokenOf("admin")) }.status)
    }

    @Test
    fun `una entrega cancelada sale de la ruta`() = testApplication {
        val store = almacen()
        store.replace(solicitud("s4", "vecino", hoy, "09:00", EstadoSolicitud.CANCELADA, "operador"))
        setupAquaGo(solicitudes = store)
        val res = client.get("/entregas") { bearerAuth(tokenOf("operador")) }
        assertEquals(listOf("s1"), ids(res.bodyAsText()))
    }

    @Test
    fun `las solicitudes se guardan en el archivo y se recuperan al reiniciar`() {
        val archivo = java.io.File.createTempFile("solicitudes", ".json")
        archivo.delete()
        SolicitudStore(archivo).add(solicitud("x1", "ciudadano", hoy, "10:00", EstadoSolicitud.RECIBIDA))
        val reiniciado = SolicitudStore(archivo)
        assertEquals(listOf("x1"), reiniciado.all().map { it.id })
    }
}
