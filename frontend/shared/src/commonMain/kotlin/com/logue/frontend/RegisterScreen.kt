package com.logue.frontend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Crear cuenta: registro del ciudadano (pantalla DEMOSTRATIVA).
 *
 * Pide los datos del registro real: nombre, teléfono de contacto, domicilio y comprobante
 * de pago predial. Por ahora no llama al backend: al terminar muestra que la cuenta quedó
 * "en revisión", porque el delegado debe validar el comprobante antes de activarla.
 *
 * Solo los ciudadanos se registran desde la app; las cuentas de repartidor y de
 * administrador las da de alta el delegado.
 */
@Composable
fun RegisterScreen(onBack: () -> Unit) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var neighborhood by remember { mutableStateOf("") }
    var receiptFile by remember { mutableStateOf<String?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var registered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PlatformBackHandler(onBack = onBack)

    if (registered) {
        val firstName = fullName.trim().substringBefore(' ')
        SuccessMessage(
            title = "¡Cuenta creada!",
            message = "Gracias, $firstName. El delegado revisará tu comprobante de pago predial " +
                "y te avisaremos al ${formatPhone(phone)} cuando tu cuenta esté activa.",
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
            val file = receiptFile
            if (file == null) {
                // Demostración: simula que se eligió un archivo (todavía no abre la galería).
                OutlinedButton(
                    onClick = { receiptFile = "comprobante_predial_2026.pdf" },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, AppColors.Accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Adjuntar comprobante", color = AppColors.Accent)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(file, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Pill("Adjuntado", AppColors.Green)
                    }
                    TextButton(onClick = { receiptFile = null }) {
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
            val validation = validateRegistration(
                fullName, phone, street, neighborhood, receiptFile != null, username, password, confirmation
            )
            if (validation != null) {
                error = validation
            } else {
                scope.launch {
                    loading = true
                    error = null
                    delay(1200) // simula el envío al servidor
                    loading = false
                    registered = true
                }
            }
        }
        FormError(error)
        Spacer(Modifier.height(16.dp))
        DemoNote()
    }
}
