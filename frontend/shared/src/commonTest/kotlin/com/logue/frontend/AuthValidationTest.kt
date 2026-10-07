package com.logue.frontend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun registroNoAceptaUsuarioExistente() =
        assertEquals("Ese usuario ya existe, elige otro", registro(usuario = "Admin"))

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
    fun codigoDeRecuperacion() {
        assertNull(validateRecoveryCode(DEMO_RECOVERY_CODE))
        assertEquals("El código tiene 6 dígitos", validateRecoveryCode("12"))
        assertEquals("Código incorrecto. Revisa el SMS e intenta de nuevo", validateRecoveryCode("000000"))
    }

    @Test
    fun destinoDelCodigoOcultaElTelefono() {
        assertEquals("el teléfono que termina en 4567", recoveryDestination("7711234567"))
        assertEquals("el teléfono registrado en la cuenta ciudadano", recoveryDestination("ciudadano"))
    }

    @Test
    fun formatoDeTelefono() = assertEquals("771 123 4567", formatPhone("7711234567"))
}
