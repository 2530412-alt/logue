package com.logue

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PagosPredialesTest {

    @Test
    fun `subir una foto valida responde 201 y se puede descargar igual`() = testApplication {
        setupAquaGo()
        val token = tokenOf("ciudadano")
        val res = subir(token, folio = "ABC-123")
        assertEquals(HttpStatusCode.Created, res.status)

        val pago = Json.decodeFromString<PagoPredial>(res.bodyAsText())
        assertEquals("ciudadano", pago.username)
        assertEquals("ABC-123", pago.folio)
        assertEquals("image/jpeg", pago.tipo)
        assertEquals(testJpeg.size.toLong(), pago.tamano)

        val foto = client.get("/pagos-prediales/${pago.id}/foto") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, foto.status)
        assertEquals(ContentType.Image.JPEG, foto.contentType()?.withoutParameters())
        assertContentEquals(testJpeg, foto.bodyAsBytes())
    }

    @Test
    fun `la lista muestra solo los pagos del usuario del token`() = testApplication {
        setupAquaGo()
        val token = tokenOf("ciudadano")
        subir(token, folio = "uno")
        subir(token, folio = "dos")

        val lista = Json.decodeFromString<List<PagoPredial>>(
            client.get("/pagos-prediales") { bearerAuth(token) }.bodyAsText()
        )
        assertEquals(setOf("uno", "dos"), lista.map { it.folio }.toSet())

        val delVecino = Json.decodeFromString<List<PagoPredial>>(
            client.get("/pagos-prediales") { bearerAuth(tokenOf("vecino")) }.bodyAsText()
        )
        assertEquals(emptyList(), delVecino)
    }

    @Test
    fun `el usuario sale del token y no del formulario`() = testApplication {
        setupAquaGo()
        // Antes bastaba con mandar username=vecino para guardar un pago a nombre de otro
        val res = subir(tokenOf("ciudadano"), extraUsername = "vecino")
        assertEquals(HttpStatusCode.Created, res.status)
        assertEquals("ciudadano", Json.decodeFromString<PagoPredial>(res.bodyAsText()).username)
    }

    @Test
    fun `un ciudadano no puede ver los pagos ni las fotos de otro`() = testApplication {
        setupAquaGo()
        val pago = Json.decodeFromString<PagoPredial>(subir(tokenOf("ciudadano")).bodyAsText())
        val vecino = tokenOf("vecino")

        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/pagos-prediales?username=ciudadano") { bearerAuth(vecino) }.status
        )
        // 404 y no 403: no se confirma que ese pago exista
        assertEquals(HttpStatusCode.NotFound, client.get("/pagos-prediales/${pago.id}/foto") { bearerAuth(vecino) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/pagos-prediales/${pago.id}/foto").status)
    }

    @Test
    fun `el delegado ve todos los pagos y sus fotos`() = testApplication {
        setupAquaGo()
        val pago = Json.decodeFromString<PagoPredial>(subir(tokenOf("ciudadano")).bodyAsText())
        subir(tokenOf("vecino"))
        val admin = tokenOf("admin")

        val todos = Json.decodeFromString<List<PagoPredial>>(client.get("/pagos-prediales") { bearerAuth(admin) }.bodyAsText())
        assertEquals(setOf("ciudadano", "vecino"), todos.map { it.username }.toSet())

        val deUno = Json.decodeFromString<List<PagoPredial>>(
            client.get("/pagos-prediales?username=vecino") { bearerAuth(admin) }.bodyAsText()
        )
        assertEquals(listOf("vecino"), deUno.map { it.username })

        assertEquals(HttpStatusCode.OK, client.get("/pagos-prediales/${pago.id}/foto") { bearerAuth(admin) }.status)
    }

    @Test
    fun `repartidor y delegado no registran pagos y el repartidor no los consulta`() = testApplication {
        setupAquaGo()
        val operador = tokenOf("operador")
        assertEquals(HttpStatusCode.Forbidden, subir(operador).status)
        assertEquals(HttpStatusCode.Forbidden, subir(tokenOf("admin")).status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/pagos-prediales") { bearerAuth(operador) }.status)
    }

    @Test
    fun `sin token no se puede subir`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.Unauthorized, subir(token = null).status)
    }

    @Test
    fun `un archivo que no es imagen responde 415`() = testApplication {
        setupAquaGo()
        assertEquals(HttpStatusCode.UnsupportedMediaType, subir(tokenOf("ciudadano"), foto = "no es imagen".toByteArray()).status)
    }

    @Test
    fun `sin foto o con folio de mas de 40 caracteres responde 400`() = testApplication {
        setupAquaGo()
        val token = tokenOf("ciudadano")
        assertEquals(HttpStatusCode.BadRequest, subir(token, foto = null).status)
        assertEquals(HttpStatusCode.BadRequest, subir(token, folio = "x".repeat(41)).status)
    }

    @Test
    fun `una foto de mas de 10 MB responde 413`() = testApplication {
        setupAquaGo()
        val grande = ByteArray(MAX_FOTO_BYTES + 1).also {
            it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte()
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, subir(tokenOf("ciudadano"), foto = grande).status)
    }

    @Test
    fun `una foto enorme sin Content-Length se corta en 10 MB y responde 413`() = testApplication {
        setupAquaGo()
        val token = tokenOf("ciudadano")
        // ChannelProvider sin tamaño: el cuerpo viaja por partes, sin Content-Length (como lo haría un atacante)
        val res = client.submitFormWithBinaryData(
            url = "/pagos-prediales",
            formData = formData {
                append("foto", ChannelProvider(size = null) {
                    ByteReadChannel(ByteArray(30 * 1024 * 1024).also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte() })
                }, Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"pago.jpg\"") })
            }
        ) { bearerAuth(token) }
        assertEquals(HttpStatusCode.PayloadTooLarge, res.status)
    }

    @Test
    fun `un id que no es UUID responde 404 sin tocar el disco`() = testApplication {
        setupAquaGo()
        val token = tokenOf("admin")
        assertEquals(HttpStatusCode.NotFound, client.get("/pagos-prediales/..%2F..%2Fetc%2Fpasswd/foto") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/pagos-prediales/no-existe/foto") { bearerAuth(token) }.status)
    }

    @Test
    fun `cada pago guarda la foto y sus datos con un nombre que genera el servidor`() = testApplication {
        val store = setupAquaGo()
        val pago = Json.decodeFromString<PagoPredial>(subir(tokenOf("ciudadano")).bodyAsText())
        val archivos = store.files(pago.id).map { it.name }.toSet()
        assertEquals(setOf("${pago.id}.jpg", "${pago.id}.json"), archivos)
        assertTrue(pago.id.matches(Regex("[0-9a-f-]{36}")))
    }
}
