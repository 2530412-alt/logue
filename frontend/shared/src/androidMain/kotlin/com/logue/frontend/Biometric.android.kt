package com.logue.frontend

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
actual fun rememberBiometricAuth(): BiometricAuth {
    val context = LocalContext.current
    return remember(context) { AndroidBiometricAuth(context.findFragmentActivity()) }
}

/** BiometricPrompt exige una FragmentActivity; MainActivity la extiende. */
private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * Huella con `BiometricPrompt`. Se pide autenticación biométrica FUERTE (huella, o rostro
 * si el teléfono lo cuenta como fuerte); sin PIN de respaldo, porque la idea es confirmar con la huella.
 */
private class AndroidBiometricAuth(private val activity: FragmentActivity?) : BiometricAuth {
    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG

    override fun isAvailable(): Boolean =
        activity != null &&
            BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    override fun authenticate(title: String, subtitle: String, onResult: (BiometricResult) -> Unit) {
        val host = activity
        if (host == null || !isAvailable()) {
            onResult(BiometricResult.Unavailable)
            return
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> BiometricResult.Cancelled
                        else -> BiometricResult.Error(errString.toString())
                    }
                )
            }
            // onAuthenticationFailed (huella que no coincide) no se maneja: el diálogo deja reintentar solo.
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("Cancelar")
            .build()

        BiometricPrompt(host, ContextCompat.getMainExecutor(host), callback).authenticate(info)
    }
}
