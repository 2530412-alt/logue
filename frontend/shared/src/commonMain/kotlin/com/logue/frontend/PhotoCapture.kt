package com.logue.frontend

import androidx.compose.runtime.Composable

/**
 * Cámara del teléfono. Cada plataforma decide cómo tomar la foto (`actual`).
 * En Android abre la app de cámara del sistema; en las demás plataformas todavía no existe.
 */
interface PhotoCapture {
    /** `false` si esta plataforma no tiene cámara implementada (iOS y web por ahora). */
    val isSupported: Boolean

    /** Abre la cámara. El resultado llega al callback de [rememberPhotoCapture]. */
    fun launch()
}

/**
 * Prepara la cámara. [onPhoto] recibe los bytes de la foto (JPEG),
 * o `null` si el usuario canceló o la foto no se pudo leer.
 */
@Composable
expect fun rememberPhotoCapture(onPhoto: (ByteArray?) -> Unit): PhotoCapture

/** Para las plataformas donde la cámara no está implementada. */
object UnsupportedPhotoCapture : PhotoCapture {
    override val isSupported = false
    override fun launch() {}
}
