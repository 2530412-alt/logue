package com.logue

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Registro, cuenta en revisión, recuperación de contraseña y perfil (USR-08, USR-10, USR-11, USR-12, USR-21, PRE-13). */
class CuentasTest {

    private fun usuariosConMaria() = JsonUserRepository(
        testUsers + User("maria", testPasswordHash(), Roles.CIUDADANO, nombre = "María López", telefono = "7711234567")
    )

    private suspend fun ApplicationTestBuilder.json(path: String, body: String, token: String? = null, method: HttpMethod = HttpMethod.Post) =
        client.request(path) {
            this.method = method
            contentType(ContentType.Application.Json)
            token?.let { bearerAuth(it) }
            setBody(body)
        }

    // ---- Registro (USR-08), usuario único (USR-10), en revisión (USR-11), comprobante (PRE-13) ----

    @Test
    fun `el registro crea la cuenta en revision y guarda el comprobante como primer pago`() = testApplication {
        val pagos = setupAquaGo()
        val res = registrar()
        assertEquals(HttpStatusCode.Created, res.status)
        val body = Json.decodeFromString<RegistroResponse>(res.bodyAsText())
        assertEquals("maria", body.username)
        assertEquals(EstadoCuenta.EN_REVISION, body.estado)

        val guardados = pagos.list("maria")
        assertEquals(1, guardados.size)
        assertEquals(body.pagoId, guardados.first().id)
    }

    @Test
    fun `una cuenta en revision no puede entrar hasta que el delegado la active`() = testApplication {
        setupAquaGo()
        registrar()

        val enRevision = login("maria", "secreto1")
        assertEquals(HttpStatusCode.Forbidden, enRevision.status)
        assertTrue(enRevision.bodyAsText().contains("en revisión"))

        // Con la contraseña mal sigue siendo 401: el estado no se revela a quien no la sabe
        assertEquals(HttpStatusCode.Unauthorized, login("maria", "otra-cosa").status)

        val activada = json("/usuarios/maria/estado", """{"estado":"ACTIVA"}""", tokenOf("admin"), HttpMethod.Patch)
        assertEquals(HttpStatusCode.OK, activada.status)
        assertEquals(HttpStatusCode.OK, login("maria", "secreto1").status)
    }

    @Test
    fun `solo el delegado cambia el estado de una cuenta`() = testApplication {
        setupAquaGo()
        registrar()
        val comoCiudadano = json("/usuarios/maria/estado", """{"estado":"ACTIVA"}""", tokenOf("ciudadano"), HttpMethod.Patch)
        assertEquals(HttpStatusCode.Forbidden, comoCiudadano.status)
        val admin = tokenOf("admin")
        assertEquals(HttpStatusCode.BadRequest, json("/usuarios/maria/estado", """{"estado":"X"}""", admin, HttpMethod.Patch).status)
        assertEquals(HttpStatusCode.NotFound, json("/usuarios/nadie/estado", """{"estado":"ACTIVA"}""", admin, HttpMethod.Patch).status)
    }

    @Test
    fun `una cuenta desactivada no puede entrar`() = testApplication {
        setupAquaGo()
        val res = json("/usuarios/vecino/estado", """{"estado":"INACTIVA"}""", tokenOf("admin"), HttpMethod.Patch)
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(HttpStatusCode.Forbidden, login("vecino").status)
    }

    @Test
    fun `el usuario ya existente responde 409 aunque cambien mayusculas`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.Conflict, registrar(usuario = "ciudadano").status)
        assertEquals(HttpStatusCode.Conflict, registrar(usuario = "Admin").status)
        assertEquals(HttpStatusCode.Created, registrar(usuario = "maria").status)
        assertEquals(HttpStatusCode.Conflict, registrar(usuario = "MARIA", telefono = "7710000000").status)
    }

    @Test
    fun `el telefono repetido responde 409`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.Created, registrar().status)
        assertEquals(HttpStatusCode.Conflict, registrar(usuario = "otra-maria").status)
    }

    @Test
    fun `el registro valida cada campo`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.BadRequest, registrar(nombre = " ").status)
        assertEquals(HttpStatusCode.BadRequest, registrar(telefono = "12345").status)
        assertEquals(HttpStatusCode.BadRequest, registrar(telefono = "77112345ab").status)
        assertEquals(HttpStatusCode.BadRequest, registrar(calle = "").status)
        assertEquals(HttpStatusCode.BadRequest, registrar(colonia = "").status)
        assertEquals(HttpStatusCode.BadRequest, registrar(usuario = "abc").status)
        assertEquals(HttpStatusCode.BadRequest, registrar(usuario = "con espacio").status)
        assertEquals(HttpStatusCode.BadRequest, registrar(contrasena = "12345").status)
        assertEquals(HttpStatusCode.BadRequest, registrar(folio = "x".repeat(41)).status)
        assertEquals(HttpStatusCode.BadRequest, registrar(foto = null).status)
    }

    @Test
    fun `el comprobante debe ser una imagen JPEG o PNG`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.UnsupportedMediaType, registrar(foto = "esto no es una foto".toByteArray()).status)
    }

    @Test
    fun `un registro rechazado no deja la cuenta creada`() = testApplication {
        setupAquaGo()
        registrar(foto = "no es imagen".toByteArray())
        assertEquals(HttpStatusCode.Created, registrar().status, "el usuario seguía libre")
    }

    // ---- Recuperación de contraseña (USR-12) ----

    @Test
    fun `recuperar la contrasena con el codigo enviado al telefono`() = testApplication {
        val sender = RecordingCodeSender()
        setupAquaGo(users = usuariosConMaria(), codeSender = sender)

        val pedido = json("/recuperacion/solicitar", """{"cuenta":"maria"}""")
        assertEquals(HttpStatusCode.Accepted, pedido.status)
        assertEquals("7711234567", sender.sent.single().first)
        assertEquals(6, sender.lastCode.length)

        val cambio = json("/recuperacion/confirmar", """{"cuenta":"maria","codigo":"${sender.lastCode}","contrasena":"nueva-clave"}""")
        assertEquals(HttpStatusCode.NoContent, cambio.status)
        assertEquals(HttpStatusCode.OK, login("maria", "nueva-clave").status)
        assertEquals(HttpStatusCode.Unauthorized, login("maria", TEST_PASSWORD).status)
    }

    @Test
    fun `tambien se puede pedir el codigo con el telefono`() = testApplication {
        val sender = RecordingCodeSender()
        setupAquaGo(users = usuariosConMaria(), codeSender = sender)
        assertEquals(HttpStatusCode.Accepted, json("/recuperacion/solicitar", """{"cuenta":"7711234567"}""").status)
        val cambio = json("/recuperacion/confirmar", """{"cuenta":"7711234567","codigo":"${sender.lastCode}","contrasena":"otra-clave"}""")
        assertEquals(HttpStatusCode.NoContent, cambio.status)
        assertEquals(HttpStatusCode.OK, login("maria", "otra-clave").status)
    }

    @Test
    fun `una cuenta inexistente responde igual y no envia nada`() = testApplication {
        val sender = RecordingCodeSender()
        setupAquaGo(users = usuariosConMaria(), codeSender = sender)
        assertEquals(HttpStatusCode.Accepted, json("/recuperacion/solicitar", """{"cuenta":"nadie"}""").status)
        assertEquals(HttpStatusCode.Accepted, json("/recuperacion/solicitar", """{"cuenta":"7799999999"}""").status)
        assertTrue(sender.sent.isEmpty())
    }

    @Test
    fun `la solicitud valida el dato que se escribe`() = testApplication {
        setupAquaGo(users = usuariosConMaria())
        assertEquals(HttpStatusCode.BadRequest, json("/recuperacion/solicitar", """{"cuenta":" "}""").status)
        assertEquals(HttpStatusCode.BadRequest, json("/recuperacion/solicitar", """{"cuenta":"12345"}""").status)
    }

    @Test
    fun `un codigo incorrecto no cambia la contrasena y despues de cinco intentos deja de servir`() = testApplication {
        val sender = RecordingCodeSender()
        setupAquaGo(users = usuariosConMaria(), codeSender = sender)
        json("/recuperacion/solicitar", """{"cuenta":"maria"}""")
        val correcto = sender.lastCode
        val malo = if (correcto == "000000") "111111" else "000000"

        repeat(5) {
            val res = json("/recuperacion/confirmar", """{"cuenta":"maria","codigo":"$malo","contrasena":"nueva-clave"}""")
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
        val tarde = json("/recuperacion/confirmar", """{"cuenta":"maria","codigo":"$correcto","contrasena":"nueva-clave"}""")
        assertEquals(HttpStatusCode.BadRequest, tarde.status)
        assertEquals(HttpStatusCode.OK, login("maria", TEST_PASSWORD).status, "la contraseña sigue siendo la anterior")
    }

    @Test
    fun `el codigo vence a los diez minutos y solo sirve una vez`() = testApplication {
        val sender = RecordingCodeSender()
        val clock = MutableClock()
        setupAquaGo(users = usuariosConMaria(), codeSender = sender, recovery = RecoveryCodes(clock), clock = clock)

        json("/recuperacion/solicitar", """{"cuenta":"maria"}""")
        clock.advance(Duration.ofMinutes(11))
        val vencido = json("/recuperacion/confirmar", """{"cuenta":"maria","codigo":"${sender.lastCode}","contrasena":"nueva-clave"}""")
        assertEquals(HttpStatusCode.BadRequest, vencido.status)

        json("/recuperacion/solicitar", """{"cuenta":"maria"}""")
        val body = """{"cuenta":"maria","codigo":"${sender.lastCode}","contrasena":"nueva-clave"}"""
        assertEquals(HttpStatusCode.NoContent, json("/recuperacion/confirmar", body).status)
        assertEquals(HttpStatusCode.BadRequest, json("/recuperacion/confirmar", body).status, "el código ya se usó")
    }

    @Test
    fun `la contrasena nueva debe cumplir la misma regla del registro`() = testApplication {
        val sender = RecordingCodeSender()
        setupAquaGo(users = usuariosConMaria(), codeSender = sender)
        json("/recuperacion/solicitar", """{"cuenta":"maria"}""")
        val res = json("/recuperacion/confirmar", """{"cuenta":"maria","codigo":"${sender.lastCode}","contrasena":"123"}""")
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    // ---- Perfil (USR-21) ----

    @Test
    fun `el perfil muestra los datos de la cuenta del token`() = testApplication {
        setupAquaGo(users = usuariosConMaria())
        registrar(usuario = "lucia", telefono = "7712223344", nombre = "Lucía Pérez")
        json("/usuarios/lucia/estado", """{"estado":"ACTIVA"}""", tokenOf("admin"), HttpMethod.Patch)
        val token = Json.decodeFromString<LoginResponse>(login("lucia", "secreto1").bodyAsText()).token

        val res = client.get("/usuarios/yo") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, res.status)
        val perfil = Json.decodeFromString<Perfil>(res.bodyAsText())
        assertEquals("lucia", perfil.username)
        assertEquals("Lucía Pérez", perfil.nombre)
        assertEquals("7712223344", perfil.telefono)
        assertEquals(Roles.CIUDADANO, perfil.role)
    }

    @Test
    fun `se pueden editar el nombre el telefono y el domicilio`() = testApplication {
        setupAquaGo(users = usuariosConMaria())
        val token = tokenOf("vecino")
        val res = json(
            "/usuarios/yo",
            """{"nombre":"Vecino Uno","telefono":"7715550000","calle":"Av. Juárez 5","colonia":"Centro"}""",
            token, HttpMethod.Patch
        )
        assertEquals(HttpStatusCode.OK, res.status)
        val perfil = Json.decodeFromString<Perfil>(client.get("/usuarios/yo") { bearerAuth(token) }.bodyAsText())
        assertEquals("Vecino Uno", perfil.nombre)
        assertEquals("7715550000", perfil.telefono)
        assertEquals("Av. Juárez 5", perfil.calle)
        assertEquals("Centro", perfil.colonia)
    }

    @Test
    fun `editar solo un campo no borra los demas`() = testApplication {
        setupAquaGo(users = usuariosConMaria())
        val token = tokenOf("vecino")
        json("/usuarios/yo", """{"nombre":"Vecino Uno","telefono":"7715550000"}""", token, HttpMethod.Patch)
        json("/usuarios/yo", """{"colonia":"Reforma"}""", token, HttpMethod.Patch)
        val perfil = Json.decodeFromString<Perfil>(client.get("/usuarios/yo") { bearerAuth(token) }.bodyAsText())
        assertEquals("Vecino Uno", perfil.nombre)
        assertEquals("7715550000", perfil.telefono)
        assertEquals("Reforma", perfil.colonia)
    }

    @Test
    fun `el perfil rechaza datos invalidos y telefonos de otra cuenta`() = testApplication {
        setupAquaGo(users = usuariosConMaria())
        val token = tokenOf("vecino")
        assertEquals(HttpStatusCode.BadRequest, json("/usuarios/yo", """{"telefono":"123"}""", token, HttpMethod.Patch).status)
        assertEquals(HttpStatusCode.BadRequest, json("/usuarios/yo", """{"nombre":"  "}""", token, HttpMethod.Patch).status)
        assertEquals(HttpStatusCode.Conflict, json("/usuarios/yo", """{"telefono":"7711234567"}""", token, HttpMethod.Patch).status)
    }

    @Test
    fun `el perfil exige sesion`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/usuarios/yo").status)
        assertEquals(HttpStatusCode.Unauthorized, json("/usuarios/yo", """{"nombre":"X"}""", null, HttpMethod.Patch).status)
    }

    @Test
    fun `los cambios del perfil y la contrasena se guardan en el archivo de usuarios`() {
        val archivo = java.io.File.createTempFile("users", ".json")
        archivo.delete()
        val repo = JsonUserRepository(testUsers, archivo)
        repo.add(User("nueva", testPasswordHash(), Roles.CIUDADANO, nombre = "Nueva Cuenta", telefono = "7711112222", estado = EstadoCuenta.EN_REVISION))

        val releido = Json.decodeFromString<List<User>>(archivo.readText())
        val guardada = releido.single { it.username == "nueva" }
        assertEquals("Nueva Cuenta", guardada.nombre)
        assertEquals(EstadoCuenta.EN_REVISION, guardada.estado)
        assertEquals(testUsers.size + 1, releido.size)
    }
}
