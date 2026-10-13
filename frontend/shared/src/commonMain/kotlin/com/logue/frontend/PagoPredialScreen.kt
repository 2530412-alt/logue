package com.logue.frontend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Registro de un pago predial: el ciudadano fotografía su recibo, confirma con su huella
 * (si el teléfono la tiene) y la foto se envía al backend, que la guarda.
 */
@Composable
fun PagoPredialScreen(session: LoginResponse, onBack: () -> Unit) {
    var folio by remember { mutableStateOf("") }
    var foto by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<PagoPredialResponse?>(null) }
    val scope = rememberCoroutineScope()

    val camera = rememberPhotoCapture { bytes ->
        // null = el usuario canceló: se conserva la foto anterior, si había
        if (bytes != null) {
            foto = bytes
            error = null
        }
    }
    val biometric = rememberBiometricAuth()
    PlatformBackHandler(onBack)

    fun upload(bytes: ByteArray) {
        scope.launch {
            sending = true
            error = null
            when (val result = subirPagoPredial(session.username, folio, bytes)) {
                is UploadResult.Success -> {
                    saved = result.pago
                    foto = null
                    folio = ""
                }
                UploadResult.TooLarge -> error = "La foto pesa más de 10 MB. Toma otra más ligera"
                UploadResult.NotImage -> error = "El archivo no es una imagen válida"
                UploadResult.ServerError -> error = "El servidor tuvo un problema. Intenta de nuevo"
                UploadResult.NetworkError -> error = "No se pudo conectar con el servidor. ¿Está encendido el backend?"
            }
            sending = false
        }
    }

    fun send() {
        val bytes = foto ?: return
        if (!biometric.isAvailable()) {
            upload(bytes)
            return
        }
        biometric.authenticate(
            title = "Confirma tu identidad",
            subtitle = "Usa tu huella para enviar el pago predial"
        ) { result ->
            when (result) {
                BiometricResult.Success -> upload(bytes)
                BiometricResult.Cancelled -> error = "Envío cancelado"
                BiometricResult.Unavailable -> error = "La huella ya no está disponible. Intenta de nuevo"
                is BiometricResult.Error -> error = "No se pudo verificar la huella: ${result.message}"
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AuthHeader(
            title = "Pago predial",
            subtitle = "Toma una foto de tu recibo de pago para registrarlo",
            onBack = onBack
        )

        val pago = saved
        if (pago != null) {
            AppCard {
                Pill("Pago registrado", AppColors.Green)
                Spacer(Modifier.height(10.dp))
                Text("Tu foto se guardó correctamente.", fontWeight = FontWeight.SemiBold)
                if (pago.folio.isNotBlank()) {
                    Text("Folio: ${pago.folio}", color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Tamaño: ${formatSize(pago.tamano)}",
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Comprobante: ${pago.id.take(8)}", color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            PrimaryButton("Registrar otro pago") { saved = null }
            SecondaryButton("Volver", onBack)
        } else {
            AppCard {
                FormSectionTitle("Datos del recibo")
                AquaGoTextField(
                    value = folio,
                    onValueChange = { if (it.length <= 40) folio = it },
                    label = "Folio o clave catastral (opcional)"
                )
                Spacer(Modifier.height(14.dp))

                if (!camera.isSupported) {
                    Text(
                        "La cámara solo está disponible en la app de Android por ahora.",
                        color = AppColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val bytes = foto
                    if (bytes != null) {
                        Pill("Foto lista · ${formatSize(bytes.size.toLong())}", AppColors.Green)
                        Spacer(Modifier.height(10.dp))
                    }
                    SecondaryButton(
                        text = if (bytes == null) "Tomar foto del recibo" else "Tomar otra foto",
                        enabled = !sending,
                        onClick = { camera.launch() }
                    )
                }
            }

            PrimaryButton(
                text = if (sending) "Enviando..." else "Enviar pago",
                enabled = foto != null && !sending,
                onClick = { send() }
            )
            FormError(error)
            Text(
                if (biometric.isAvailable()) "Para enviar se te pedirá tu huella digital."
                else "Este dispositivo no tiene huella registrada: el pago se enviará sin confirmar.",
                color = AppColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Botón secundario (contorno), para acciones que no son la principal de la pantalla. */
@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Accent),
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/** "850 KB" o "2.4 MB". */
private fun formatSize(bytes: Long): String =
    if (bytes < 1024 * 1024) "${maxOf(1L, bytes / 1024)} KB"
    else "${(bytes * 10 / (1024 * 1024)) / 10.0} MB"
