package com.logue.frontend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

// La cámara y la huella solo están implementadas en Android por ahora.

actual val isCameraSupported: Boolean = false

@Composable
actual fun CameraCaptureScreen(onPhoto: (ByteArray) -> Unit, onClose: () -> Unit) {
    // No debería llegar aquí (la pantalla no ofrece el botón), pero por si acaso se cierra sola
    LaunchedEffect(Unit) { onClose() }
}

@Composable
actual fun rememberBiometricAuth(): BiometricAuth = UnsupportedBiometricAuth
