package com.logue.frontend

/*
 * Reglas de validación de Crear cuenta y Recuperar contraseña.
 * Son funciones normales (sin Compose) para poder probarlas en commonTest.
 * Cada una devuelve el mensaje del primer error, o `null` si todo está bien.
 */

/** Código que acepta la recuperación de contraseña mientras sea demostrativa. */
const val DEMO_RECOVERY_CODE = "123456"

/** Usuarios que ya existen en el backend (users.json). Sirve para avisar en el registro de prueba. */
val EXISTING_USERNAMES = setOf("ciudadano", "operador", "admin")

const val MIN_PASSWORD_LENGTH = 6

/** Registro del ciudadano: revisa los campos en el mismo orden en que aparecen en la pantalla. */
fun validateRegistration(
    fullName: String,
    phone: String,
    street: String,
    neighborhood: String,
    hasPropertyTaxReceipt: Boolean,
    username: String,
    password: String,
    confirmation: String
): String? = when {
    fullName.isBlank() -> "Escribe tu nombre completo"
    phone.length != 10 || !phone.all { it.isDigit() } -> "El teléfono debe tener 10 dígitos"
    street.isBlank() -> "Escribe la calle y el número de tu domicilio"
    neighborhood.isBlank() -> "Escribe tu colonia o localidad"
    !hasPropertyTaxReceipt -> "Adjunta tu comprobante de pago predial"
    username.isBlank() -> "Escribe un nombre de usuario"
    username.length < 4 -> "El usuario debe tener al menos 4 caracteres"
    username.lowercase() in EXISTING_USERNAMES -> "Ese usuario ya existe, elige otro"
    else -> validateNewPassword(password, confirmation)
}

/** Contraseña nueva (registro y recuperación). */
fun validateNewPassword(password: String, confirmation: String): String? = when {
    password.length < MIN_PASSWORD_LENGTH -> "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
    password != confirmation -> "Las contraseñas no coinciden"
    else -> null
}

/** Paso 1 de la recuperación: usuario o teléfono. */
fun validateRecoveryAccount(account: String): String? {
    val value = account.trim()
    return when {
        value.isEmpty() -> "Escribe tu usuario o teléfono"
        value.all { it.isDigit() } && value.length != 10 -> "El teléfono debe tener 10 dígitos"
        else -> null
    }
}

/** Paso 2 de la recuperación: código de 6 dígitos. */
fun validateRecoveryCode(code: String): String? = when {
    code.length != 6 -> "El código tiene 6 dígitos"
    code != DEMO_RECOVERY_CODE -> "Código incorrecto. Revisa el SMS e intenta de nuevo"
    else -> null
}

/** A dónde "se envió" el código, sin mostrar el teléfono completo. */
fun recoveryDestination(account: String): String {
    val value = account.trim()
    return if (value.isNotEmpty() && value.all { it.isDigit() }) {
        "el teléfono que termina en ${value.takeLast(4)}"
    } else {
        "el teléfono registrado en la cuenta $value"
    }
}

/** 7711234567 -> "771 123 4567". */
fun formatPhone(phone: String): String =
    if (phone.length == 10) "${phone.substring(0, 3)} ${phone.substring(3, 6)} ${phone.substring(6)}" else phone
