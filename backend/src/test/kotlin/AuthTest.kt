package com.logue

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Inicio de sesión, token y bloqueo (USR-15, USR-16, USR-20, SRV-14). */
class AuthTest {

    @Test
    fun `login correcto responde el rol y un token`() = testApplication {
        setupAquaGo()
        val res = login("ciudadano")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.decodeFromString<LoginResponse>(res.bodyAsText())
        assertEquals("ciudadano", body.username)
        assertEquals(Roles.CIUDADANO, body.role)
        assertTrue(body.token.count { it == '.' } == 2, "el token es un JWT (tres partes)")
        assertEquals(12 * 3600L, body.expiresIn)
    }

    @Test
    fun `el usuario no distingue mayusculas ni espacios`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.OK, login("  Ciudadano ").status)
    }

    @Test
    fun `contrasena incorrecta o usuario inexistente responden 401`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.Unauthorized, login("ciudadano", "mal").status)
        assertEquals(HttpStatusCode.Unauthorized, login("nadie").status)
    }

    @Test
    fun `campos vacios o JSON mal formado responden 400`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.BadRequest, login("", "").status)
        val roto = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("{esto no es json")
        }
        assertEquals(HttpStatusCode.BadRequest, roto.status)
    }

    @Test
    fun `las contrasenas solo se guardan como hash BCrypt`() {
        val hash = Passwords.hash("1234")
        assertTrue(hash.startsWith("$2a$10$"))
        assertFalse(hash.contains("1234"))
        assertTrue(Passwords.matches("1234", hash))
        assertFalse(Passwords.matches("12345", hash))
        assertFalse(Passwords.matches("1234", null))
        // Mismo texto, distinto hash: BCrypt usa una sal aleatoria
        assertTrue(Passwords.hash("1234") != hash)
    }

    @Test
    fun `tres intentos fallidos bloquean la cuenta cinco minutos`() = testApplication {
        val clock = MutableClock()
        setupAquaGo(attempts = LoginAttempts(clock = clock))

        assertEquals(HttpStatusCode.Unauthorized, login("ciudadano", "mal1").status)
        assertEquals(HttpStatusCode.Unauthorized, login("ciudadano", "mal2").status)
        val tercero = login("ciudadano", "mal3")
        assertEquals(HttpStatusCode.TooManyRequests, tercero.status)
        assertEquals("300", tercero.headers[HttpHeaders.RetryAfter])

        // Bloqueada: ni la contraseña correcta entra
        assertEquals(HttpStatusCode.TooManyRequests, login("ciudadano").status)
        // El bloqueo es por cuenta: otro usuario entra normal
        assertEquals(HttpStatusCode.OK, login("vecino").status)

        clock.advance(Duration.ofMinutes(5).plusSeconds(1))
        assertEquals(HttpStatusCode.OK, login("ciudadano").status)
    }

    @Test
    fun `un login correcto reinicia el contador de fallos`() = testApplication {
        setupAquaGo()
        login("ciudadano", "mal")
        login("ciudadano", "mal")
        assertEquals(HttpStatusCode.OK, login("ciudadano").status)
        login("ciudadano", "mal")
        login("ciudadano", "mal")
        assertEquals(HttpStatusCode.OK, login("ciudadano").status, "solo 2 fallos desde el último acceso")
    }

    @Test
    fun `los fallos viejos no cuentan para el bloqueo`() = testApplication {
        val clock = MutableClock()
        setupAquaGo(attempts = LoginAttempts(clock = clock))
        login("ciudadano", "mal")
        login("ciudadano", "mal")
        clock.advance(Duration.ofMinutes(20))
        assertEquals(HttpStatusCode.Unauthorized, login("ciudadano", "mal").status, "tercer fallo, pero fuera de la ventana")
    }

    @Test
    fun `cada IP tiene un limite de logins por minuto`() = testApplication {
        setupAquaGo(loginsPerMinute = 5)
        repeat(5) { assertEquals(HttpStatusCode.OK, login("admin", ip = "201.1.1.1").status) }
        assertEquals(HttpStatusCode.TooManyRequests, login("admin", ip = "201.1.1.1").status)
        // Otra IP (otro teléfono) no se ve afectada: por eso se lee X-Forwarded-For
        assertEquals(HttpStatusCode.OK, login("admin", ip = "201.2.2.2").status)
    }

    @Test
    fun `sin token, con token falso o vencido responde 401`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/pagos-prediales").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/pagos-prediales") { bearerAuth("no.es.token") }.status)

        // Token firmado con otra clave (alguien que lo fabricó por su cuenta)
        val ajeno = TokenService("otra-clave-que-no-es-la-del-servidor-1234567").create(testUsers[0])
        assertEquals(HttpStatusCode.Unauthorized, client.get("/pagos-prediales") { bearerAuth(ajeno) }.status)

        // Token emitido hace 13 horas: ya venció (dura 12)
        val haceTrece = Clock.fixed(Instant.now().minus(Duration.ofHours(13)), ZoneOffset.UTC)
        val vencido = TokenService(TEST_SECRET, clock = haceTrece).create(testUsers[0])
        val res = client.get("/pagos-prediales") { bearerAuth(vencido) }
        assertEquals(HttpStatusCode.Unauthorized, res.status)
        assertTrue(res.bodyAsText().contains("sesión expiró"))
    }

    @Test
    fun `JWT_SECRET corto se rechaza al arrancar`() {
        val error = runCatching { TokenService.fromEnvironment(mapOf("JWT_SECRET" to "corta")) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message!!.contains("32"))
        // Sin la variable arranca con una clave temporal
        assertNotNull(TokenService.fromEnvironment(emptyMap()))
    }
}
