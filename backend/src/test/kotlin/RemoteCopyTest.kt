package com.logue

import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteCopyTest {

    @Test
    fun `al guardar un pago se copian la foto y los datos al servidor remoto`() = testApplication {
        val copiados = ConcurrentLinkedQueue<String>()
        val listo = CountDownLatch(2)
        setupAquaGo(remote = { file, remoteName ->
            assertTrue(file.isFile, "el archivo local debe existir al copiarse")
            copiados += remoteName
            listo.countDown()
        })

        assertEquals(HttpStatusCode.Created, subir(tokenOf("ciudadano"), folio = "X-1").status)
        // La copia corre en segundo plano: se espera hasta 5 s a que lleguen los dos archivos
        val llego = withContext(Dispatchers.IO) { listo.await(5, TimeUnit.SECONDS) }
        assertTrue(llego, "no se copiaron los 2 archivos: $copiados")
        assertEquals(setOf("jpg", "json"), copiados.map { it.substringAfterLast('.') }.toSet())
        assertEquals(1, copiados.map { it.substringBeforeLast('.') }.toSet().size, "ambos con el mismo id")
    }

    @Test
    fun `si la copia remota falla el pago igual se guarda y responde 201`() = testApplication {
        val intentos = CountDownLatch(2)
        setupAquaGo(remote = { _, _ ->
            intentos.countDown()
            error("servidor remoto apagado")
        })

        assertEquals(HttpStatusCode.Created, subir(tokenOf("ciudadano")).status)
        assertTrue(withContext(Dispatchers.IO) { intentos.await(5, TimeUnit.SECONDS) })
    }

    @Test
    fun `sin SFTP configurado el pago se guarda solo en el disco local`() = testApplication {
        setupAquaGo(remote = null)
        assertEquals(HttpStatusCode.Created, subir(tokenOf("ciudadano")).status)
    }

    @Test
    fun `sin SFTP_HOST la copia remota queda apagada`() {
        assertNull(loadRemoteCopier(emptyMap()))
        assertNull(loadRemoteCopier(mapOf("SFTP_HOST" to "  ")))
    }

    @Test
    fun `con la configuracion completa se crea el copiador`() {
        val env = mapOf(
            "SFTP_HOST" to "servidor.ejemplo.mx",
            "SFTP_USER" to "aquago",
            "SFTP_KEY_PATH" to "/ruta/id_ed25519",
            "SFTP_HOST_FINGERPRINT" to "SHA256:abc123"
        )
        assertNotNull(loadRemoteCopier(env))
    }

    @Test
    fun `configuracion a medias falla al arrancar con un mensaje claro`() {
        val base = mapOf("SFTP_HOST" to "servidor.ejemplo.mx")
        assertTrue(assertFailsWith<IllegalStateException> { loadRemoteCopier(base) }.message!!.contains("SFTP_USER"))

        val sinHuella = base + mapOf("SFTP_USER" to "aquago", "SFTP_PASSWORD" to "x")
        assertTrue(
            assertFailsWith<IllegalStateException> { loadRemoteCopier(sinHuella) }.message!!.contains("SFTP_HOST_FINGERPRINT")
        )

        val sinCredencial = base + mapOf("SFTP_USER" to "aquago", "SFTP_HOST_FINGERPRINT" to "SHA256:abc")
        assertTrue(
            assertFailsWith<IllegalStateException> { loadRemoteCopier(sinCredencial) }.message!!.contains("SFTP_KEY_PATH")
        )
    }
}
