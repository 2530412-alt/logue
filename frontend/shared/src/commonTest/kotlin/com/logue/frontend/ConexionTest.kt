package com.logue.frontend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dirección del servidor por ambiente (API-06, API-07) y mensajes del login (USR-20). */
class ConexionTest {

    private val local = "http://10.0.2.2:8081"
    private val nube = "https://servidor.ejemplo.mx"

    @Test
    fun sinSERVIDOR_URLseUsaLaNube() {
        assertEquals(nube, resolverServidor("", local, nube))
        assertEquals(nube, resolverServidor("   ", local, nube))
    }

    @Test
    fun localApuntaAlBackendDeLaComputadora() {
        assertEquals(local, resolverServidor("local", local, nube))
        assertEquals(local, resolverServidor(" LOCAL ", local, nube))
    }

    @Test
    fun unaURLseUsaTalCualSinDiagonalFinal() {
        assertEquals("http://localhost:8081", resolverServidor("http://localhost:8081/", local, nube))
    }

    @Test
    fun elServidorEnLaNubeUsaHttpsYnoTerminaEnDiagonal() {
        assertTrue(SERVIDOR_NUBE.startsWith("https://"))
        assertTrue(!SERVIDOR_NUBE.endsWith("/"))
    }

    @Test
    fun mensajeDeBloqueoEnMinutos() {
        assertEquals("Demasiados intentos. Espera 5 minutos e intenta de nuevo", lockedMessage(300))
        assertEquals("Demasiados intentos. Espera 1 minuto e intenta de nuevo", lockedMessage(20))
        assertEquals("Demasiados intentos. Espera 5 minutos e intenta de nuevo", lockedMessage(null))
    }
}
