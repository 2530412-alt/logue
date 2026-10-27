package com.logue.frontend

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

actual val isCameraSupported: Boolean = true

/**
 * Cámara trasera con CameraX: vista previa en vivo, botón de captura y permiso CAMERA.
 * La foto se guarda un instante en la caché de la app, se lee a memoria y el archivo se borra.
 * CameraX escribe la orientación en el JPEG (EXIF), así que la foto no sale girada.
 */
@Composable
actual fun CameraCaptureScreen(onPhoto: (ByteArray) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        denied = !granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    PlatformBackHandler(onClose)

    when {
        hasPermission -> CameraPreview(onPhoto, onClose)
        denied -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Sin permiso de cámara no se puede fotografiar el recibo. " +
                    "Puedes activarlo en Ajustes → Aplicaciones → AquaGo → Permisos.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Volver", onClick = onClose)
        }
        else -> Box(Modifier.fillMaxSize()) // esperando la respuesta del diálogo de permiso
    }
}

@Composable
private fun CameraPreview(onPhoto: (ByteArray) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    var error by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }

    // Enciende la cámara ligada al ciclo de vida de la pantalla y la suelta al salir
    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                error = "No se pudo abrir la cámara"
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            // Si la cámara aún no terminaba de encender, no hay nada que soltar (y get() bloquearía)
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
        }
    }

    fun takePhoto() {
        if (capturing) return
        capturing = true
        error = null
        val file = File.createTempFile("pago_", ".jpg", context.cacheDir)
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val bytes = runCatching { file.readBytes() }.getOrNull()
                    file.delete()
                    capturing = false
                    if (bytes != null) onPhoto(bytes) else error = "No se pudo leer la foto. Intenta de nuevo"
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    capturing = false
                    error = "No se pudo tomar la foto. Intenta de nuevo"
                }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        error?.let {
            Text(
                it,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f)).padding(12.dp)
            )
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text("Cancelar", color = Color.White)
            }
            // Botón de captura: círculo blanco con aro
            Box(
                Modifier.size(76.dp)
                    .border(3.dp, Color.White, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (capturing) Color.Gray else Color.White)
                    .clickable(enabled = !capturing) { takePhoto() }
            )
            Spacer(Modifier.weight(1f))
        }
    }
}
