package com.logue.frontend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pruebas de las reglas de Crear cuenta y Recuperar contraseña. */
class AuthValidationTest {

    private fun registro(
        nombre: String = "Brenda Martínez",
        telefono: String = "7711234567",
        calle: String = "Hidalgo 12",
        colonia: String = "Centro",
        comprobante: Boolean = true,
        usuario: String = "brenda",
        contrasena: String = "agua2026",
        confirmacion: String = "agua2026"
    ) = validateRegistration(nombre, telefono, calle, colonia, comprobante, usuario, contrasena, confirmacion)

    @Test
    fun registroCompletoEsValido() = assertNull(registro())

    @Test
    fun registroPideNombre() = assertEquals("Escribe tu nombre completo", registro(nombre = "  "))

    @Test
    fun registroPideTelefonoDe10Digitos() =
        assertEquals("El teléfono debe tener 10 dígitos", registro(telefono = "77112"))

    @Test
    fun registroPideComprobantePredial() =
        assertEquals("Adjunta tu comprobante de pago predial", registro(comprobante = false))

    @Test
    fun registroPideUsuarioDeAlMenos4Caracteres() =
        assertEquals("El usuario debe tener al menos 4 caracteres", registro(usuario = "abc"))

    @Test
    fun elUsuarioRepetidoLoResponde409ElServidorYNoUnaListaFijaEnLaApp() {
        // USR-10: antes la app comparaba contra los tres usuarios de prueba; ahora cualquier nombre pasa
        // la validación local y el servidor responde 409 si ya existe (CuentasTest en el backend).
        assertNull(registro(usuario = "Admin"))
    }

    @Test
    fun registroRevisaQueLasContrasenasCoincidan() =
        assertEquals("Las contraseñas no coinciden", registro(confirmacion = "otra2026"))

    @Test
    fun contrasenaCorta() =
        assertEquals("La contraseña debe tener al menos 6 caracteres", validateNewPassword("123", "123"))

    @Test
    fun recuperacionAceptaUsuarioOTelefono() {
        assertNull(validateRecoveryAccount("ciudadano"))
        assertNull(validateRecoveryAccount("7711234567"))
        assertEquals("El teléfono debe tener 10 dígitos", validateRecoveryAccount("771"))
        assertEquals("Escribe tu usuario o teléfono", validateRecoveryAccount(" "))
    }

    @Test
    fun codigoDeRecuperacionSoloRevisaQueSean6Digitos() {
        assertNull(validateRecoveryCode("123456"))
        assertNull(validateRecoveryCode("000000"))
        assertEquals("El código tiene 6 dígitos", validateRecoveryCode("12"))
        assertEquals("El código tiene 6 dígitos", validateRecoveryCode("12345a"))
    }

    private val perfil = Perfil("ciudadano", Roles.CIUDADANO, "Brenda Martínez", "7711234567", "Hidalgo 12", "Centro", "ACTIVA")

    @Test
    fun perfilSinCambiosNoEnviaNada() {
        val cambio = profileChange(perfil, " Brenda Martínez ", "7711234567", "Hidalgo 12", "Centro")
        assertTrue(cambio.isEmpty())
        assertNull(validateProfileChange(cambio))
    }

    @Test
    fun perfilEnviaSoloLoQueCambio() {
        val cambio = profileChange(perfil, "Brenda Martínez", "7711234567", "Hidalgo 12", " Reforma ")
        assertEquals(PerfilCambio(colonia = "Reforma"), cambio)
    }

    @Test
    fun perfilRevisaLosCamposQueCambian() {
        assertEquals("Escribe tu nombre completo", validateProfileChange(PerfilCambio(nombre = " ")))
        assertEquals("El teléfono debe tener 10 dígitos", validateProfileChange(PerfilCambio(telefono = "771")))
        assertEquals("Escribe tu colonia o localidad", validateProfileChange(PerfilCambio(colonia = "")))
        assertNull(validateProfileChange(PerfilCambio(telefono = "7715550000")))
    }

    @Test
    fun etiquetasDeRolYDeEstadoDeCuenta() {
        assertEquals("Administrador", etiquetaRol(Roles.ADMIN))
        assertEquals("En revisión", etiquetaEstadoCuenta("EN_REVISION"))
        assertEquals("Desactivada", etiquetaEstadoCuenta("INACTIVA"))
    }

    @Test
    fun destinoDelCodigoOcultaElTelefono() {
        assertEquals("el teléfono que termina en 4567", recoveryDestination("7711234567"))
        assertEquals("el teléfono registrado en la cuenta ciudadano", recoveryDestination("ciudadano"))
    }

    @Test
    fun formatoDeTelefono() = assertEquals("771 123 4567", formatPhone("7711234567"))
}
