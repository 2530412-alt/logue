package com.logue

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class ServerTest {

    @Test
    fun `test root endpoint`() = testApplication {
        application {
            rootModule()
        }
        // verify server root returns 200
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    @Test
    fun `los usuarios de users_json entran con su contrasena de prueba`() = testApplication {
        application { rootModule() }
        for ((usuario, rol) in listOf("ciudadano" to Roles.CIUDADANO, "operador" to Roles.OPERADOR, "admin" to Roles.ADMIN)) {
            val res = login(usuario)
            assertEquals(HttpStatusCode.OK, res.status, usuario)
            assertEquals(rol, Json.decodeFromString<LoginResponse>(res.bodyAsText()).role)
        }
    }

    @Test
    fun `users_json no guarda contrasenas en texto`() {
        loadUsers().forEach { u ->
            assertTrue(u.passwordHash.startsWith("$2a$"), "${u.username} debe tener hash BCrypt")
        }
    }

    @Test
    fun `salud es publica e indica la plataforma`() = testApplication {
        setupAquaGo()
        val res = client.get("/salud")
        assertEquals(HttpStatusCode.OK, res.status)
        val salud = Json.decodeFromString<Salud>(res.bodyAsText())
        assertEquals("OK", salud.estado)
        assertEquals("local", salud.plataforma)
    }

    @Test
    fun `CORS solo acepta los origenes configurados y permite el token`() = testApplication {
        setupAquaGo(corsOrigins = listOf("https://2530412-alt.github.io"))
        val permitido = client.options("/pagos-prediales") {
            header(HttpHeaders.Origin, "https://2530412-alt.github.io")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "authorization")
        }
        assertEquals(HttpStatusCode.OK, permitido.status)
        assertEquals("https://2530412-alt.github.io", permitido.headers[HttpHeaders.AccessControlAllowOrigin])

        val ajeno = client.options("/login") {
            header(HttpHeaders.Origin, "https://sitio-cualquiera.example")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type")
        }
        assertNull(ajeno.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `CORS_ORIGINS se lee separado por comas`() {
        assertEquals(
            listOf("https://a.example", "https://b.example"),
            corsOriginsFromEnvironment(mapOf("CORS_ORIGINS" to " https://a.example, https://b.example ,"))
        )
        assertEquals(emptyList(), corsOriginsFromEnvironment(emptyMap()))
    }
}
