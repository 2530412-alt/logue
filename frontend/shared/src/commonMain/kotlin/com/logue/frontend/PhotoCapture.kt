package com.logue.frontend

import androidx.compose.runtime.Composable

/**
 * Cámara dentro de la app (vista previa en vivo y botón de captura).
 * Cada plataforma la implementa con su propia API (`actual`); por ahora solo existe en Android.
 */

/** `false` si esta plataforma todavía no tiene la cámara implementada (iOS y web). */
expect val isCameraSupported: Boolean

/**
 * Pantalla completa con la cámara. Al tomar la foto llama a [onPhoto] con los bytes (JPEG);
 * [onClose] se llama si el usuario cancela, si niega el permiso o si el botón "atrás" se presiona.
 */
@Composable
expect fun CameraCaptureScreen(onPhoto: (ByteArray) -> Unit, onClose: () -> Unit)
