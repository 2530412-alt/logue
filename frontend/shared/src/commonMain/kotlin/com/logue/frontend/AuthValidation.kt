package com.logue.frontend

/*
 * Reglas de validación de Crear cuenta y Recuperar contraseña.
 * Son funciones normales (sin Compose) para poder probarlas en commonTest.
 * Cada una devuelve el mensaje del primer error, o `null` si todo está bien.
 */

const val MIN_PASSWORD_LENGTH = 6

/**
 * Registro del ciudadano: revisa los campos en el mismo orden en que aparecen en la pantalla.
 * Que el usuario ya exista lo responde el servidor (409), porque solo él conoce las cuentas (USR-10).
 */
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

/** Paso 2 de la recuperación: código de 6 dígitos. Si es el correcto lo decide el servidor al cambiar la contraseña. */
fun validateRecoveryCode(code: String): String? = when {
    code.length != 6 || !code.all { it.isDigit() } -> "El código tiene 6 dígitos"
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

/**
 * Perfil (USR-21): revisa solo los campos que cambiaron (los `null` no se envían). Son las mismas
 * reglas que aplica el servidor en `PATCH /usuarios/yo`.
 */
fun validateProfileChange(change: PerfilCambio): String? = when {
    change.nombre != null && change.nombre.isBlank() -> "Escribe tu nombre completo"
    change.telefono != null && (change.telefono.length != 10 || !change.telefono.all { it.isDigit() }) ->
        "El teléfono debe tener 10 dígitos"
    change.calle != null && change.calle.isBlank() -> "Escribe la calle y el número de tu domicilio"
    change.colonia != null && change.colonia.isBlank() -> "Escribe tu colonia o localidad"
    else -> null
}

/** Lo que cambió respecto al perfil guardado, sin espacios sobrantes; `null` en lo que sigue igual. */
fun profileChange(current: Perfil, nombre: String, telefono: String, calle: String, colonia: String) = PerfilCambio(
    nombre = nombre.trim().takeIf { it != current.nombre },
    telefono = telefono.trim().takeIf { it != current.telefono },
    calle = calle.trim().takeIf { it != current.calle },
    colonia = colonia.trim().takeIf { it != current.colonia }
)

/** `true` si no hay ningún cambio que enviar. */
fun PerfilCambio.isEmpty() = nombre == null && telefono == null && calle == null && colonia == null

fun etiquetaRol(role: String): String = when (role) {
    Roles.CIUDADANO -> "Ciudadano"
    Roles.OPERADOR -> "Operador"
    Roles.ADMIN -> "Administrador"
    else -> role
}

fun etiquetaEstadoCuenta(estado: String): String = when (estado) {
    "ACTIVA" -> "Activa"
    "EN_REVISION" -> "En revisión"
    "INACTIVA" -> "Desactivada"
    else -> estado
}
