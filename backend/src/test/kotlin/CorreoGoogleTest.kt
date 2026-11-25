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

/** Servicio de correo, «Iniciar sesión con Google» y reCAPTCHA (requisitos de la app web). */
class CorreoGoogleTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val hoy = "2026-11-04"

    private fun usuarios(vararg extra: User) = JsonUserRepository(testUsers + extra.toList())

    private val anaVerificada = User(
        "ana", testPasswordHash(), Roles.CIUDADANO, nombre = "Ana", telefono = "7710000001",
        correo = "ana@gmail.com", correoVerificado = true
    )

    private val delegadaConCorreo = User(
        "delegada", testPasswordHash(), Roles.ADMIN, nombre = "Rosa", correo = "rosa@gmail.com", correoVerificado = true
    )

    /** Verificador falso: "token-<correo>" es un ID token válido de Google para ese correo. */
    private val googleFalso = VerificadorGoogle { token ->
        if (token.startsWith("token-")) IdentidadGoogle(token.removePrefix("token-"), "Nombre Google") else null
    }

    /** Captcha falso: solo "ok" pasa. */
    private val captchaFalso = object : Captcha {
        override val activo = true
        override fun valido(respuesta: String?, ip: String) = respuesta == "ok"
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, token: String? = null, origin: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            token?.let { bearerAuth(it) }
            origin?.let { header(HttpHeaders.Origin, it) }
            setBody(body)
        }

    // ---- Verificación del correo ----

    @Test
    fun `el registro con correo manda un codigo y verificarlo marca el correo`() = testApplication {
        val correos = RecordingCorreos()
        val users = usuarios()
        setupAquaGo(users = users, integraciones = Integraciones(correos = correos))
        assertEquals(HttpStatusCode.Created, registrar(correo = "maria@gmail.com").status)
        assertEquals("maria@gmail.com", correos.enviados.single().para)
        assertFalse(users.find("maria")!!.correoVerificado)

        assertEquals(HttpStatusCode.BadRequest, post("/registro/verificar-correo", """{"usuario":"maria","codigo":"000000x"}""").status)
        val res = post("/registro/verificar-correo", """{"usuario":"maria","codigo":"${correos.ultimoCodigo}"}""")
        assertEquals(HttpStatusCode.NoContent, res.status)
        assertTrue(users.find("maria")!!.correoVerificado)
    }

    @Test
    fun `el registro rechaza un correo invalido o repetido`() = testApplication {
        setupAquaGo(users = usuarios(anaVerificada))
        assertEquals(HttpStatusCode.BadRequest, registrar(correo = "no-es-correo").status)
        assertEquals(HttpStatusCode.Conflict, registrar(correo = "ANA@gmail.com").status)
    }

    @Test
    fun `cambiar el correo en el perfil obliga a verificarlo de nuevo`() = testApplication {
        val correos = RecordingCorreos()
        val users = usuarios(anaVerificada)
        setupAquaGo(users = users, integraciones = Integraciones(correos = correos))
        val token = tokenOf("ana")
        val cambio = client.patch("/usuarios/yo") {
            contentType(ContentType.Application.Json); bearerAuth(token); setBody("""{"correo":"ana.nueva@gmail.com"}""")
        }
        assertEquals(HttpStatusCode.OK, cambio.status)
        assertFalse(users.find("ana")!!.correoVerificado)

        assertEquals(HttpStatusCode.Accepted, post("/usuarios/yo/correo/codigo", "{}", token).status)
        assertEquals("ana.nueva@gmail.com", correos.enviados.last().para)
        val ok = post("/usuarios/yo/correo/verificar", """{"codigo":"${correos.ultimoCodigo}"}""", token)
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue(json.decodeFromString<Perfil>(ok.bodyAsText()).correoVerificado)
        assertEquals(HttpStatusCode.Conflict, post("/usuarios/yo/correo/codigo", "{}", token).status)
    }

    // ---- Recuperación por correo ----

    @Test
    fun `la recuperacion manda el codigo al correo verificado y acepta el correo como cuenta`() = testApplication {
        val correos = RecordingCorreos()
        val sms = RecordingCodeSender()
        setupAquaGo(users = usuarios(anaVerificada), codeSender = sms, integraciones = Integraciones(correos = correos))
        assertEquals(HttpStatusCode.Accepted, post("/recuperacion/solicitar", """{"cuenta":"ana@gmail.com"}""").status)
        assertEquals("ana@gmail.com", correos.enviados.single().para)
        assertEquals(sms.lastCode, correos.ultimoCodigo)
        val res = post("/recuperacion/confirmar", """{"cuenta":"ana","codigo":"${correos.ultimoCodigo}","contrasena":"nueva123"}""")
        assertEquals(HttpStatusCode.NoContent, res.status)
    }

    // ---- Avisos ----

    @Test
    fun `asignar y entregar avisan por correo solo si el correo esta verificado`() = testApplication {
        val correos = RecordingCorreos()
        val sinVerificar = User("beto", testPasswordHash(), Roles.CIUDADANO, correo = "beto@gmail.com")
        val solicitudes = SolicitudStore(
            initial = listOf(
                Solicitud("s1", "ana", hoy, "09:00", 5_000, "Calle 1", "Las Camelinas", EstadoSolicitud.RECIBIDA),
                Solicitud("s2", "beto", hoy, "10:00", 1_000, "Calle 2", "Las Camelinas", EstadoSolicitud.RECIBIDA)
            )
        )
        setupAquaGo(users = usuarios(anaVerificada, sinVerificar), solicitudes = solicitudes, integraciones = Integraciones(correos = correos))
        val admin = tokenOf("admin")
        listOf("s1", "s2").forEach { id ->
            val r = client.patch("/solicitudes/$id/asignar") { contentType(ContentType.Application.Json); bearerAuth(admin); setBody("""{"repartidor":"operador"}""") }
            assertEquals(HttpStatusCode.OK, r.status)
        }
        assertEquals(1, correos.enviados.size)
        assertEquals("Tu pipa quedó programada", correos.enviados.single().asunto)

        val op = tokenOf("operador")
        client.patch("/entregas/s1") { contentType(ContentType.Application.Json); bearerAuth(op); setBody("""{"estado":"ENTREGADA","litrosEntregados":4500}""") }
        assertEquals("Tu pipa fue entregada", correos.enviados.last().asunto)
        assertTrue("4,500 L" in correos.enviados.last().html)
    }

    @Test
    fun `activar la cuenta avisa por correo`() = testApplication {
        val correos = RecordingCorreos()
        val pendiente = anaVerificada.copy(estado = EstadoCuenta.EN_REVISION)
        setupAquaGo(users = usuarios(pendiente), integraciones = Integraciones(correos = correos))
        val r = client.patch("/usuarios/ana/estado") { contentType(ContentType.Application.Json); bearerAuth(tokenOf("admin")); setBody("""{"estado":"ACTIVA"}""") }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("Tu cuenta de AquaGo fue aprobada", correos.enviados.single().asunto)
    }

    // ---- Reportes ----

    @Test
    fun `el reporte cuenta entregas y litros y se manda al correo del delegado`() = testApplication {
        val correos = RecordingCorreos()
        val solicitudes = SolicitudStore(
            initial = listOf(
                Solicitud("a", "ana", hoy, "09:00", 5_000, "C", "L", EstadoSolicitud.ENTREGADA, repartidor = "operador", litrosEntregados = 4_000),
                Solicitud("b", "ana", hoy, "10:00", 1_000, "C", "L", EstadoSolicitud.NO_ENTREGADA, repartidor = "operador"),
                Solicitud("c", "ana", "2026-11-03", "10:00", 2_500, "C", "L", EstadoSolicitud.CANCELADA),
                Solicitud("d", "ana", "2026-10-01", "10:00", 2_500, "C", "L", EstadoSolicitud.ENTREGADA)
            )
        )
        setupAquaGo(users = usuarios(anaVerificada, delegadaConCorreo), solicitudes = solicitudes, integraciones = Integraciones(correos = correos))
        val res = client.get("/reportes/resumen?desde=2026-11-01&hasta=2026-11-04") { bearerAuth(tokenOf("delegada")) }
        assertEquals(HttpStatusCode.OK, res.status)
        val r = json.decodeFromString<ReporteEntregas>(res.bodyAsText())
        assertEquals(3, r.total)
        assertEquals(1, r.entregadas)
        assertEquals(1, r.noEntregadas)
        assertEquals(1, r.canceladas)
        assertEquals(4_000, r.litrosEntregados)
        assertEquals("operador", r.porRepartidor.single().repartidor)

        assertEquals(HttpStatusCode.Forbidden, client.get("/reportes/resumen") { bearerAuth(tokenOf("ana")) }.status)
        // admin de prueba no tiene correo verificado
        assertEquals(HttpStatusCode.BadRequest, post("/reportes/correo", "{}", tokenOf("admin")).status)
        assertEquals(HttpStatusCode.Accepted, post("/reportes/correo", """{"desde":"2026-11-01","hasta":"2026-11-04"}""", tokenOf("delegada")).status)
        assertEquals("rosa@gmail.com", correos.enviados.last().para)
    }

    @Test
    fun `el reporte rechaza fechas invalidas`() = testApplication {
        setupAquaGo()
        val admin = tokenOf("admin")
        assertEquals(HttpStatusCode.BadRequest, client.get("/reportes/resumen?desde=ayer") { bearerAuth(admin) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/reportes/resumen?desde=2026-11-05&hasta=2026-11-01") { bearerAuth(admin) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/reportes/resumen") { bearerAuth(admin) }.status)
    }

    // ---- Iniciar sesión con Google ----

    @Test
    fun `iniciar sesion con Google entrega el token de la cuenta con ese correo`() = testApplication {
        val users = usuarios(delegadaConCorreo.copy(correoVerificado = false))
        setupAquaGo(users = users, integraciones = Integraciones(google = googleFalso))
        val res = post("/login/google", """{"credential":"token-ROSA@gmail.com"}""")
        assertEquals(HttpStatusCode.OK, res.status)
        val login = json.decodeFromString<LoginResponse>(res.bodyAsText())
        assertEquals("delegada", login.username)
        assertEquals(Roles.ADMIN, login.role)
        assertTrue(users.find("delegada")!!.correoVerificado)

        assertEquals(HttpStatusCode.NotFound, post("/login/google", """{"credential":"token-nadie@gmail.com"}""").status)
        assertEquals(HttpStatusCode.BadRequest, post("/login/google", """{"credential":"falso"}""").status)
    }

    @Test
    fun `Google respeta las cuentas en revision y sin configurar responde 503`() = testApplication {
        setupAquaGo(users = usuarios(anaVerificada.copy(estado = EstadoCuenta.EN_REVISION)), integraciones = Integraciones(google = googleFalso))
        assertEquals(HttpStatusCode.Forbidden, post("/login/google", """{"credential":"token-ana@gmail.com"}""").status)
    }

    @Test
    fun `sin GOOGLE_CLIENT_ID el inicio con Google no esta disponible`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.ServiceUnavailable, post("/login/google", """{"credential":"token-x@gmail.com"}""").status)
        assertNull(verificadorGoogleDesdeEntorno(emptyMap()))
    }

    // ---- reCAPTCHA ----

    @Test
    fun `el captcha se exige al navegador pero no a la app movil`() = testApplication {
        setupAquaGo(integraciones = Integraciones(captcha = captchaFalso))
        val web = "https://panel.example"
        assertEquals(HttpStatusCode.BadRequest, post("/login", """{"username":"admin","password":"$TEST_PASSWORD"}""", origin = web).status)
        assertEquals(HttpStatusCode.BadRequest, post("/login", """{"username":"admin","password":"$TEST_PASSWORD","captcha":"malo"}""", origin = web).status)
        assertEquals(HttpStatusCode.OK, post("/login", """{"username":"admin","password":"$TEST_PASSWORD","captcha":"ok"}""", origin = web).status)
        // La app de Android no manda Origin
        assertEquals(HttpStatusCode.OK, login("admin").status)
    }

    @Test
    fun `config web publica el id de Google y la clave del sitio`() = testApplication {
        setupAquaGo(integraciones = Integraciones(googleClientId = "123.apps.googleusercontent.com", recaptchaSiteKey = "sitio"))
        val c = json.decodeFromString<ConfigWeb>(client.get("/config/web").bodyAsText())
        assertEquals("123.apps.googleusercontent.com", c.googleClientId)
        assertEquals("sitio", c.recaptchaSiteKey)
        assertFalse(c.correoActivo)
    }

    @Test
    fun `utilidades de correo`() {
        assertEquals("ma***@gmail.com", ocultarCorreo("maria@gmail.com"))
        assertNull(validarCorreo("maria.lopez@upt.edu.mx"))
        assertTrue(validarCorreo("maria@") != null)
        assertTrue(correosDesdeEntorno(emptyMap()) is CorreosSoloRegistro)
        assertTrue(captchaDesdeEntorno(emptyMap()) === CaptchaDesactivado)
    }
}
