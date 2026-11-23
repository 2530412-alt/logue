package com.logue

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integración con AquaGo: pedir y cancelar pipa (SOL-03), asignar rutas (ADM-03/ADM-04),
 * cerrar entregas, sincronizar cambios hechos sin conexión (/sync) y alta de usuarios por el delegado.
 */
class OperacionTest {

    // El reloj de las pruebas marca 2026-11-04 12:00 UTC = 06:00 en America/Mexico_City
    private val hoy = "2026-11-04"
    private val json = Json { ignoreUnknownKeys = true }

    private fun solicitud(id: String, usuario: String, estado: String, repartidor: String? = null, dia: String = hoy) =
        Solicitud(id, usuario, dia, "10:00", 2_500, "Calle Hidalgo 12", "Las Camelinas", estado, repartidor)

    private fun HttpResponse.comoSolicitud(texto: String) = json.decodeFromString<Solicitud>(texto)

    private suspend fun ApplicationTestBuilder.pedir(token: String, cuerpo: String) = client.post("/solicitudes") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(cuerpo)
    }

    private suspend fun ApplicationTestBuilder.patchJson(url: String, token: String, cuerpo: String = "{}") = client.patch(url) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(cuerpo)
    }

    // ------------------------------- Pedir pipa -------------------------------

    @Test
    fun `el ciudadano pide una pipa y queda recibida`() = testApplication {
        val store = SolicitudStore()
        setupAquaGo(solicitudes = store)
        val res = pedir(tokenOf("ciudadano"), """{"dia":"$hoy","capacidadLitros":2500,"domicilio":"Calle Camelia 12","referencia":"Portón verde"}""")
        assertEquals(HttpStatusCode.Created, res.status)
        val s = res.comoSolicitud(res.bodyAsText())
        assertEquals(EstadoSolicitud.RECIBIDA, s.estado)
        assertEquals("ciudadano", s.username)
        assertEquals("Portón verde", s.referencia)
        assertEquals("09:00", s.hora)
        assertEquals("Las Camelinas", s.colonia)
        assertEquals(1, store.of("ciudadano").size)
    }

    @Test
    fun `pedir con el mismo id dos veces no duplica la solicitud`() = testApplication {
        val store = SolicitudStore()
        setupAquaGo(solicitudes = store)
        val token = tokenOf("ciudadano")
        val cuerpo = """{"id":"abc-123","dia":"$hoy","capacidadLitros":1000,"domicilio":"Calle Camelia 12"}"""
        assertEquals(HttpStatusCode.Created, pedir(token, cuerpo).status)
        assertEquals(HttpStatusCode.OK, pedir(token, cuerpo).status)
        assertEquals(1, store.all().size)
    }

    @Test
    fun `pedir pipa valida litros, fecha y domicilio`() = testApplication {
        setupAquaGo()
        val token = tokenOf("ciudadano")
        assertEquals(HttpStatusCode.BadRequest, pedir(token, """{"dia":"$hoy","capacidadLitros":1234,"domicilio":"Calle 1"}""").status)
        assertEquals(HttpStatusCode.BadRequest, pedir(token, """{"dia":"2026-11-03","capacidadLitros":1000,"domicilio":"Calle 1"}""").status)
        assertEquals(HttpStatusCode.BadRequest, pedir(token, """{"dia":"2026-11-20","capacidadLitros":1000,"domicilio":"Calle 1"}""").status)
        assertEquals(HttpStatusCode.BadRequest, pedir(token, """{"dia":"mañana","capacidadLitros":1000,"domicilio":"Calle 1"}""").status)
        // Sin domicilio y sin calle en el perfil
        assertEquals(HttpStatusCode.BadRequest, pedir(token, """{"dia":"$hoy","capacidadLitros":1000}""").status)
    }

    @Test
    fun `solo el ciudadano pide pipas`() = testApplication {
        setupAquaGo()
        val cuerpo = """{"dia":"$hoy","capacidadLitros":1000,"domicilio":"Calle 1"}"""
        assertEquals(HttpStatusCode.Forbidden, pedir(tokenOf("operador"), cuerpo).status)
        assertEquals(HttpStatusCode.Forbidden, pedir(tokenOf("admin"), cuerpo).status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/solicitudes") {
            contentType(ContentType.Application.Json); setBody(cuerpo)
        }.status)
    }

    // ------------------------------- Cancelar -------------------------------

    @Test
    fun `el ciudadano cancela su solicitud pero no la de otro`() = testApplication {
        val store = SolicitudStore(initial = listOf(solicitud("s1", "ciudadano", EstadoSolicitud.RECIBIDA), solicitud("s2", "vecino", EstadoSolicitud.RECIBIDA)))
        setupAquaGo(solicitudes = store)
        val token = tokenOf("ciudadano")
        assertEquals(HttpStatusCode.OK, patchJson("/solicitudes/s1/cancelar", token).status)
        assertEquals(EstadoSolicitud.CANCELADA, store.find("s1")?.estado)
        assertEquals(HttpStatusCode.NotFound, patchJson("/solicitudes/s2/cancelar", token).status)
        assertEquals(EstadoSolicitud.RECIBIDA, store.find("s2")?.estado)
    }

    @Test
    fun `no se cancela una pipa que ya va en camino`() = testApplication {
        val store = SolicitudStore(initial = listOf(solicitud("s1", "ciudadano", EstadoSolicitud.EN_CAMINO, "operador")))
        setupAquaGo(solicitudes = store)
        assertEquals(HttpStatusCode.Conflict, patchJson("/solicitudes/s1/cancelar", tokenOf("ciudadano")).status)
    }

    // ------------------------------- Asignar ruta -------------------------------

    @Test
    fun `el delegado asigna una solicitud y aparece en la ruta del repartidor`() = testApplication {
        val store = SolicitudStore(initial = listOf(solicitud("s1", "ciudadano", EstadoSolicitud.RECIBIDA)))
        setupAquaGo(solicitudes = store)
        val res = patchJson("/solicitudes/s1/asignar", tokenOf("admin"), """{"repartidor":"operador","orden":1}""")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(EstadoSolicitud.PROGRAMADA, store.find("s1")?.estado)
        val ruta = client.get("/entregas") { bearerAuth(tokenOf("operador")) }
        assertEquals(listOf("s1"), json.decodeFromString<List<Solicitud>>(ruta.bodyAsText()).map { it.id })
    }

    @Test
    fun `solo se asigna a un repartidor y solo lo hace el delegado`() = testApplication {
        val store = SolicitudStore(initial = listOf(solicitud("s1", "ciudadano", EstadoSolicitud.RECIBIDA)))
        setupAquaGo(solicitudes = store)
        assertEquals(HttpStatusCode.BadRequest, patchJson("/solicitudes/s1/asignar", tokenOf("admin"), """{"repartidor":"vecino"}""").status)
        assertEquals(HttpStatusCode.Forbidden, patchJson("/solicitudes/s1/asignar", tokenOf("operador"), """{"repartidor":"operador"}""").status)
        assertEquals(HttpStatusCode.BadRequest, patchJson("/solicitudes/s1/asignar", tokenOf("admin"), """{"repartidor":"operador","dia":"2026-11-01"}""").status)
    }

    @Test
    fun `el delegado quita una entrega que no ha salido`() = testApplication {
        val store = SolicitudStore(initial = listOf(solicitud("s1", "ciudadano", EstadoSolicitud.PROGRAMADA, "operador")))
        setupAquaGo(solicitudes = store)
        assertEquals(HttpStatusCode.OK, patchJson("/solicitudes/s1/desasignar", tokenOf("admin")).status)
        assertEquals(EstadoSolicitud.RECIBIDA, store.find("s1")?.estado)
        assertNull(store.find("s1")?.repartidor)
    }

    // ------------------------------- Entregas -------------------------------

    @Test
    fun `el repartidor cierra una entrega con los litros entregados`() = testApplication {
        val store = SolicitudStore(initial = listOf(solicitud("s1", "ciudadano", EstadoSolicitud.PROGRAMADA, "operador")))
        setupAquaGo(solicitudes = store)
        val token = tokenOf("operador")
        assertEquals(HttpStatusCode.OK, patchJson("/entregas/s1", token, """{"estado":"en_camino"}""").status)
        assertEquals(EstadoSolicitud.EN_CAMINO, store.find("s1")?.estado)
        assertEquals(HttpStatusCode.OK, patchJson("/entregas/s1", token, """{"estado":"ENTREGADA","litrosEntregados":2400}""").status)
        assertEquals(EstadoSolicitud.ENTREGADA, store.find("s1")?.estado)
        assertEquals(2400, store.find("s1")?.litrosEntregados)
    }

    @Test
    fun `no entregada guarda el motivo y otro repartidor no la puede tocar`() = testApplication {
        val users = JsonUserRepository(testUsers + User("otro", testPasswordHash(), Roles.OPERADOR))
        val store = SolicitudStore(initial = listOf(solicitud("s1", "ciudadano", EstadoSolicitud.EN_CAMINO, "operador")))
        setupAquaGo(solicitudes = store, users = users)
        assertEquals(HttpStatusCode.NotFound, patchJson("/entregas/s1", tokenOf("otro"), """{"estado":"ENTREGADA"}""").status)
        assertEquals(HttpStatusCode.OK, patchJson("/entregas/s1", tokenOf("operador"), """{"estado":"NO_ENTREGADA"}""").status)
        assertEquals("No se encontró a nadie", store.find("s1")?.observacion)
        assertEquals(0, store.find("s1")?.litrosEntregados)
    }

    @Test
    fun `una entrega cancelada por el ciudadano no se puede cerrar`() = testApplication {
        val store = SolicitudStore(initial = listOf(solicitud("s1", "ciudadano", EstadoSolicitud.CANCELADA, "operador")))
        setupAquaGo(solicitudes = store)
        assertEquals(HttpStatusCode.Conflict, patchJson("/entregas/s1", tokenOf("operador"), """{"estado":"ENTREGADA"}""").status)
    }

    // ------------------------------- Sincronización sin conexión -------------------------------

    private suspend fun ApplicationTestBuilder.sync(token: String, operaciones: String) =
        client.post("/sync") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"operaciones":[$operaciones]}""")
        }

    @Test
    fun `el repartidor sube los cambios que hizo sin conexion y no se repiten`() = testApplication {
        val store = SolicitudStore(initial = listOf(solicitud("s1", "ciudadano", EstadoSolicitud.PROGRAMADA, "operador")))
        setupAquaGo(solicitudes = store)
        val token = tokenOf("operador")
        val op = """{"id":"op-1","tipo":"ACTUALIZAR_ENTREGA","entidadId":"s1","creadaEn":"2026-11-04T11:00:00Z",
            "payload":{"estado":"entregada","litrosEntregados":2500,"actualizadoEn":"2026-11-04T11:00:00Z"}}"""
        val primera = json.decodeFromString<SyncRespuesta>(sync(token, op).bodyAsText())
        assertTrue(primera.resultados.single().ok)
        assertEquals(EstadoSolicitud.ENTREGADA, store.find("s1")?.estado)
        val segunda = json.decodeFromString<SyncRespuesta>(sync(token, op).bodyAsText())
        assertEquals("Ya se había aplicado", segunda.resultados.single().mensaje)
    }

    @Test
    fun `el ciudadano crea y cancela sin conexion en un solo envio`() = testApplication {
        val store = SolicitudStore()
        setupAquaGo(solicitudes = store)
        val token = tokenOf("ciudadano")
        val ops = """
            {"id":"op-a","tipo":"CREAR_SOLICITUD","entidadId":"sol-1","creadaEn":"2026-11-04T10:00:00Z",
             "payload":{"fechaPreferida":"$hoy","litros":5000,"direccion":"Calle Camelia 12"}},
            {"id":"op-b","tipo":"CANCELAR_SOLICITUD","entidadId":"sol-1","creadaEn":"2026-11-04T10:05:00Z","payload":{}}
        """
        val res = json.decodeFromString<SyncRespuesta>(sync(token, ops).bodyAsText())
        assertTrue(res.resultados.all { it.ok })
        assertEquals(EstadoSolicitud.CANCELADA, store.find("sol-1")?.estado)
        assertEquals(5000, store.find("sol-1")?.capacidadLitros)
    }

    @Test
    fun `sync rechaza lo que no corresponde al rol y lo informa`() = testApplication {
        val store = SolicitudStore(initial = listOf(solicitud("s1", "ciudadano", EstadoSolicitud.PROGRAMADA, "operador")))
        setupAquaGo(solicitudes = store)
        val op = """{"id":"op-x","tipo":"ACTUALIZAR_ENTREGA","entidadId":"s1","payload":{"estado":"ENTREGADA"}}"""
        val res = json.decodeFromString<SyncRespuesta>(sync(tokenOf("ciudadano"), op).bodyAsText())
        assertFalse(res.resultados.single().ok)
        assertEquals(EstadoSolicitud.PROGRAMADA, store.find("s1")?.estado)
        assertEquals(HttpStatusCode.Forbidden, sync(tokenOf("admin"), op).status)
    }

    // ------------------------------- Usuarios (delegado) -------------------------------

    @Test
    fun `el delegado lista usuarios por rol`() = testApplication {
        setupAquaGo()
        val res = client.get("/usuarios?rol=OPERADOR") { bearerAuth(tokenOf("admin")) }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(listOf("operador"), json.decodeFromString<List<Perfil>>(res.bodyAsText()).map { it.username })
        assertEquals(HttpStatusCode.Forbidden, client.get("/usuarios") { bearerAuth(tokenOf("ciudadano")) }.status)
    }

    @Test
    fun `el delegado da de alta un repartidor que ya puede iniciar sesion`() = testApplication {
        setupAquaGo()
        val res = client.post("/usuarios") {
            bearerAuth(tokenOf("admin"))
            contentType(ContentType.Application.Json)
            setBody("""{"username":"carlos","contrasena":"secreto1","role":"OPERADOR","nombre":"Carlos Hernández","telefono":"7759876543"}""")
        }
        assertEquals(HttpStatusCode.Created, res.status)
        assertEquals(HttpStatusCode.OK, login("carlos", "secreto1").status)
        val repetido = client.post("/usuarios") {
            bearerAuth(tokenOf("admin"))
            contentType(ContentType.Application.Json)
            setBody("""{"username":"carlos","contrasena":"secreto1","role":"OPERADOR","nombre":"Otro","telefono":"7750000001"}""")
        }
        assertEquals(HttpStatusCode.Conflict, repetido.status)
    }
}
