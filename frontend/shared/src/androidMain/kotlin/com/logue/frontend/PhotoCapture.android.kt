package com.logue.frontend

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Toma la foto con la app de cámara del sistema (`TakePicture`). Así:
 *  - no hace falta declarar ni pedir el permiso CAMERA;
 *  - la foto llega en resolución completa, legible para un recibo.
 * La foto se guarda un momento en la caché de la app (ver `res/xml/file_paths.xml`),
 * se lee a memoria y el archivo temporal se borra enseguida.
 */
@Composable
actual fun rememberPhotoCapture(onPhoto: (ByteArray?) -> Unit): PhotoCapture {
    val context = LocalContext.current
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    // rememberSaveable: si Android recrea la pantalla mientras la cámara está abierta, no se pierde la ruta
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = pendingPath?.let { File(it) }
        pendingPath = null
        val bytes = if (taken && file != null) runCatching { file.readBytes() }.getOrNull() else null
        file?.delete()
        currentOnPhoto(bytes)
    }

    return remember(context, launcher) {
        object : PhotoCapture {
            override val isSupported = true

            override fun launch() {
                val dir = File(context.cacheDir, "fotos").apply { mkdirs() }
                val file = File.createTempFile("pago_", ".jpg", dir)
                pendingPath = file.absolutePath
                // La autoridad debe coincidir con la del <provider> del AndroidManifest de androidApp
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                launcher.launch(uri)
            }
        }
    }
}
