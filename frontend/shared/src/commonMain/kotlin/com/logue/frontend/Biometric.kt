package com.logue.frontend

import androidx.compose.runtime.Composable

/** Resultado de pedir la huella digital. */
sealed interface BiometricResult {
    /** La huella coincide. */
    data object Success : BiometricResult

    /** El usuario cerró el diálogo o presionó "Cancelar". */
    data object Cancelled : BiometricResult

    /** El dispositivo no tiene sensor de huella o no hay ninguna registrada. */
    data object Unavailable : BiometricResult

    /** Otro error, por ejemplo demasiados intentos fallidos. */
    data class Error(val message: String) : BiometricResult
}

/**
 * Sensor de huella digital. La app nunca ve ni guarda la huella:
 * el sistema operativo solo responde "coincide" o "no coincide".
 */
interface BiometricAuth {
    /** `true` si el dispositivo tiene sensor y al menos una huella registrada. */
    fun isAvailable(): Boolean

    /** Muestra el diálogo del sistema. El resultado llega a [onResult] en el hilo principal. */
    fun authenticate(title: String, subtitle: String, onResult: (BiometricResult) -> Unit)
}

@Composable
expect fun rememberBiometricAuth(): BiometricAuth

/** Para las plataformas donde la huella no está implementada. */
object UnsupportedBiometricAuth : BiometricAuth {
    override fun isAvailable() = false
    override fun authenticate(title: String, subtitle: String, onResult: (BiometricResult) -> Unit) {
        onResult(BiometricResult.Unavailable)
    }
}
