package com.logue

import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PagosPredialesTest {

    private val users = listOf(User("ciudadano", "1234", "CIUDADANO"))

    // Los 3 primeros bytes de un JPEG real (FF D8 FF) seguidos de datos de relleno.
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4, 5)

    private fun ApplicationTestBuilder.setup() {
        val dir = Files.createTempDirectory("pagos-test").toFile()
        application {
            configureSerialization()
            configureRouting(users = users, pagos = PagoStore(dir))
        }
    }

    private suspend fun ApplicationTestBuilder.subir(
        username: String = "ciudadano",
        folio: String = "",
        foto: ByteArray? = jpeg
    ) = client.submitFormWithBinaryData(
        url = "/pagos-prediales",
        formData = formData {
            append("username", username)
            append("folio", folio)
            if (foto != null) {
                append("foto", foto, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"pago.jpg\"")
                })
            }
        }
    )

    @Test
    fun `subir una foto valida responde 201 y se puede descargar igual`() = testApplication {
        setup()
        val res = subir(folio = "ABC-123")
        assertEquals(HttpStatusCode.Created, res.status)

        val pago = Json.decodeFromString<PagoPredial>(res.bodyAsText())
        assertEquals("ciudadano", pago.username)
        assertEquals("ABC-123", pago.folio)
        assertEquals("image/jpeg", pago.tipo)
        assertEquals(jpeg.size.toLong(), pago.tamano)

        val foto = client.get("/pagos-prediales/${pago.id}/foto")
        assertEquals(HttpStatusCode.OK, foto.status)
        assertEquals(ContentType.Image.JPEG, foto.contentType()?.withoutParameters())
        assertContentEquals(jpeg, foto.bodyAsBytes())
    }

    @Test
    fun `la lista muestra solo los pagos del usuario pedido`() = testApplication {
        setup()
        subir(folio = "uno")
        subir(folio = "dos")

        val res = client.get("/pagos-prediales?username=ciudadano")
        assertEquals(HttpStatusCode.OK, res.status)
        val lista = Json.decodeFromString<List<PagoPredial>>(res.bodyAsText())
        assertEquals(setOf("uno", "dos"), lista.map { it.folio }.toSet())

        val otro = Json.decodeFromString<List<PagoPredial>>(client.get("/pagos-prediales?username=otro").bodyAsText())
        assertEquals(emptyList(), otro)
        assertEquals(HttpStatusCode.BadRequest, client.get("/pagos-prediales").status)
    }

    @Test
    fun `un archivo que no es imagen responde 415`() = testApplication {
        setup()
        val res = subir(foto = "esto no es una imagen".toByteArray())
        assertEquals(HttpStatusCode.UnsupportedMediaType, res.status)
    }

    @Test
    fun `sin foto responde 400`() = testApplication {
        setup()
        assertEquals(HttpStatusCode.BadRequest, subir(foto = null).status)
    }

    @Test
    fun `usuario desconocido responde 401`() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, subir(username = "intruso").status)
    }

    @Test
    fun `una foto de mas de 10 MB responde 413`() = testApplication {
        setup()
        val grande = ByteArray(MAX_FOTO_BYTES + 1).also {
            it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte()
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, subir(foto = grande).status)
    }

    @Test
    fun `un id que no es UUID responde 404 sin tocar el disco`() = testApplication {
        setup()
        assertEquals(HttpStatusCode.NotFound, client.get("/pagos-prediales/..%2F..%2Fetc%2Fpasswd/foto").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/pagos-prediales/no-existe/foto").status)
    }
}
