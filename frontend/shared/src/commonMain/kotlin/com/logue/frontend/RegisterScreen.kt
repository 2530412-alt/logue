package com.logue.frontend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Crear cuenta: registro del ciudadano (USR-08).
 *
 * Pide nombre, teléfono de contacto, domicilio y la foto del comprobante de pago predial, que se
 * toma con la cámara de la app (PRE-13). Todo se envía a `POST /registro`: el servidor rechaza un
 * usuario repetido (USR-10) y deja la cuenta "en revisión" hasta que el delegado valida el
 * comprobante (USR-11).
 *
 * Solo los ciudadanos se registran desde la app; las cuentas de repartidor y de
 * administrador las da de alta el delegado.
 */
@Composable
fun RegisterScreen(onBack: () -> Unit) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var street by rememberSaveable { mutableStateOf("") }
    var neighborhood by rememberSaveable { mutableStateOf("") }
    // La foto se queda en remember: varios MB no caben en el estado que Android guarda al girar la pantalla
    var receiptPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var registered by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // La cámara ocupa toda la pantalla. Si el usuario cancela se conserva la foto anterior, si había.
    if (showCamera) {
        CameraCaptureScreen(
            onPhoto = { bytes ->
                receiptPhoto = bytes
                error = null
                showCamera = false
            },
            onClose = { showCamera = false }
        )
        return
    }
    PlatformBackHandler(onBack = onBack)

    if (registered) {
        val firstName = fullName.trim().substringBefore(' ')
        SuccessMessage(
            title = "¡Cuenta creada!",
            message = "Gracias, $firstName. El delegado revisará tu comprobante de pago predial. " +
                "Cuando valide tu cuenta podrás iniciar sesión con el usuario que elegiste; " +
                "si tienes dudas, el delegado te contactará al ${formatPhone(phone)}.",
            buttonText = "Volver a iniciar sesión",
            onClick = onBack
        ) {
            Spacer(Modifier.height(16.dp))
            Pill("En revisión", AppColors.Yellow)
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        AuthHeader(
            title = "Crear cuenta",
            subtitle = "Registro de ciudadano. Las cuentas de repartidor y de administrador las da de alta el delegado.",
            onBack = onBack
        )
        Spacer(Modifier.height(20.dp))

        AppCard {
            FormSectionTitle("Datos personales")
            AquaGoTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = "Nombre completo",
                capitalization = KeyboardCapitalization.Words
            )
            Spacer(Modifier.height(12.dp))
            AquaGoTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() }.take(10) },
                label = "Teléfono de contacto",
                keyboardType = KeyboardType.Phone,
                supportingText = "10 dígitos"
            )
        }
        Spacer(Modifier.height(14.dp))

        AppCard {
            FormSectionTitle("Domicilio de entrega")
            AquaGoTextField(
                value = street,
                onValueChange = { street = it },
                label = "Calle y número",
                capitalization = KeyboardCapitalization.Sentences
            )
            Spacer(Modifier.height(12.dp))
            AquaGoTextField(
                value = neighborhood,
                onValueChange = { neighborhood = it },
                label = "Colonia o localidad",
                capitalization = KeyboardCapitalization.Words
            )
        }
        Spacer(Modifier.height(14.dp))

        AppCard {
            FormSectionTitle("Comprobante de pago predial")
            Text(
                "Sirve para validar que el domicilio está al corriente.",
                color = AppColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            val photo = receiptPhoto
            if (!isCameraSupported) {
                Text(
                    "La cámara solo está disponible en la app de Android por ahora.",
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (photo == null) {
                OutlinedButton(
                    onClick = { showCamera = true },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, AppColors.Accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tomar foto del comprobante", color = AppColors.Accent)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Foto del comprobante", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Pill("Lista · ${(photo.size / 1024).coerceAtLeast(1)} KB", AppColors.Green)
                    }
                    TextButton(onClick = { showCamera = true }) {
                        Text("Repetir", color = AppColors.Accent)
                    }
                    TextButton(onClick = { receiptPhoto = null }) {
                        Text("Quitar", color = AppColors.Red)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        AppCard {
            FormSectionTitle("Datos de acceso")
            AquaGoTextField(
                value = username,
                onValueChange = { username = it.filterNot { c -> c.isWhitespace() } },
                label = "Usuario"
            )
            Spacer(Modifier.height(12.dp))
            AquaGoPasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Contraseña",
                supportingText = "Mínimo $MIN_PASSWORD_LENGTH caracteres"
            )
            Spacer(Modifier.height(12.dp))
            AquaGoPasswordField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = "Confirmar contraseña"
            )
        }
        Spacer(Modifier.height(20.dp))

        PrimaryButton(if (loading) "Creando cuenta..." else "Crear cuenta", enabled = !loading) {
            val photo = receiptPhoto
            val validation = validateRegistration(
                fullName, phone, street, neighborhood, photo != null, username, password, confirmation
            )
            if (validation != null || photo == null) {
                error = validation
            } else {
                scope.launch {
                    loading = true
                    error = null
                    val result = registrarCuenta(
                        DatosRegistro(fullName, phone, street, neighborhood, username, password),
                        photo
                    )
                    loading = false
                    when (result) {
                        is ApiResult.Ok -> registered = true
                        // 409 si el usuario o el teléfono ya existen, 400 si el servidor rechaza un dato
                        else -> error = result.mensajeDeError() ?: "No se pudo crear la cuenta"
                    }
                }
            }
        }
        FormError(error)
    }
}
