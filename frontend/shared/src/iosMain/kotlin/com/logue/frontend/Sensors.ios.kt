package com.logue.frontend

import androidx.compose.runtime.Composable

// La cámara y la huella solo están implementadas en Android por ahora.

@Composable
actual fun rememberPhotoCapture(onPhoto: (ByteArray?) -> Unit): PhotoCapture = UnsupportedPhotoCapture

@Composable
actual fun rememberBiometricAuth(): BiometricAuth = UnsupportedBiometricAuth
