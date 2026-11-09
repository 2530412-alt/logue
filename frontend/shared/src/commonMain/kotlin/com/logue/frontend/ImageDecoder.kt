package com.logue.frontend

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Convierte los bytes de una foto (JPEG o PNG) en una imagen que Compose puede dibujar, o `null` si
 * no se puede leer. Cada plataforma usa su propio decodificador (`actual`): Android, BitmapFactory;
 * iOS y web, Skia.
 */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?
