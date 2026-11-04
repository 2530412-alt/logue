package com.logue

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

/** Contraseña de todos los usuarios de prueba. */
const val TEST_PASSWORD = "1234"

/** Hash calculado una sola vez: BCrypt es lento a propósito. */
private val testHash by lazy { Passwords.hash(TEST_PASSWORD) }

val testUsers: List<User> by lazy {
    listOf(
        User("ciudadano", testHash, Roles.CIUDADANO),
        User("vecino", testHash, Roles.CIUDADANO),
        User("operador", testHash, Roles.OPERADOR),
        User("admin", testHash, Roles.ADMIN)
    )
}

/** Los 3 primeros bytes de un JPEG real (FF D8 FF) seguidos de datos de relleno. */
val testJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4, 5)

/** Reloj que las pruebas pueden adelantar (para el bloqueo por intentos). */
class MutableClock(var now: Instant = Instant.parse("2026-11-04T12:00:00Z")) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant() = now
    fun advance(duration: Duration) { now = now.plus(duration) }
}

/** Clave fija para que las pruebas puedan crear tokens a mano. */
const val TEST_SECRET = "clave-de-pruebas-de-aquago-con-mas-de-32-caracteres"

fun ApplicationTestBuilder.setupAquaGo(
    remote: RemoteCopier? = null,
    attempts: LoginAttempts = LoginAttempts(),
    corsOrigins: List<String> = emptyList(),
    loginsPerMinute: Int = 1_000,
    tokens: TokenService = TokenService(TEST_SECRET)
): PagoStore {
    val store = PagoStore(Files.createTempDirectory("pagos-test").toFile())
    application {
        aquaGoModule(
            users = JsonUserRepository(testUsers),
            pagos = store,
            remote = remote,
            tokens = tokens,
            attempts = attempts,
            corsOrigins = corsOrigins,
            loginsPerMinute = loginsPerMinute
        )
    }
    return store
}

suspend fun ApplicationTestBuilder.login(username: String, password: String = TEST_PASSWORD, ip: String? = null) =
    client.post("/login") {
        contentType(ContentType.Application.Json)
        ip?.let { header("X-Forwarded-For", it) }
        setBody("""{"username":"$username","password":"$password"}""")
    }

/** Inicia sesión y devuelve el token. Falla la prueba si el login no responde 200. */
suspend fun ApplicationTestBuilder.tokenOf(username: String): String {
    val res = login(username)
    assertEquals(HttpStatusCode.OK, res.status, "login de $username")
    return Json.decodeFromString<LoginResponse>(res.bodyAsText()).token
}

suspend fun ApplicationTestBuilder.subir(
    token: String?,
    folio: String = "",
    foto: ByteArray? = testJpeg,
    extraUsername: String? = null
) = client.submitFormWithBinaryData(
    url = "/pagos-prediales",
    formData = formData {
        extraUsername?.let { append("username", it) }
        append("folio", folio)
        if (foto != null) {
            append("foto", foto, Headers.build {
                append(HttpHeaders.ContentType, "image/jpeg")
                append(HttpHeaders.ContentDisposition, "filename=\"pago.jpg\"")
            })
        }
    }
) {
    token?.let { bearerAuth(it) }
}
