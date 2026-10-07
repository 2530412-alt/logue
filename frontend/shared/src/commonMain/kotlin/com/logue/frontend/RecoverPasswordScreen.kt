package com.logue.frontend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pasos de la recuperación de contraseña. */
enum class RecoverStep { ACCOUNT, CODE, NEW_PASSWORD, DONE }

/**
 * Recuperar contraseña (pantalla DEMOSTRATIVA) en 3 pasos:
 * 1) usuario o teléfono, 2) código que llega por SMS, 3) contraseña nueva.
 *
 * Por ahora no llama al backend: el código válido es [DEMO_RECOVERY_CODE]
 * y la contraseña del servidor no cambia.
 */
@Composable
fun RecoverPasswordScreen(onBack: () -> Unit) {
    var step by remember { mutableStateOf(RecoverStep.ACCOUNT) }
    var account by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun goTo(next: RecoverStep) {
        step = next
        error = null
        info = null
    }

    // "Volver" regresa un paso; desde el primero regresa al login.
    val goBack: () -> Unit = {
        when (step) {
            RecoverStep.ACCOUNT, RecoverStep.DONE -> onBack()
            RecoverStep.CODE -> goTo(RecoverStep.ACCOUNT)
            RecoverStep.NEW_PASSWORD -> goTo(RecoverStep.CODE)
        }
    }
    PlatformBackHandler(onBack = goBack)

    if (step == RecoverStep.DONE) {
        SuccessMessage(
            title = "Contraseña actualizada",
            message = "Ya puedes iniciar sesión con tu nueva contraseña.",
            buttonText = "Ir a iniciar sesión",
            onClick = onBack
        )
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        AuthHeader(
            title = "Recuperar contraseña",
            subtitle = "Te ayudamos a entrar de nuevo a tu cuenta.",
            onBack = goBack
        )
        Spacer(Modifier.height(20.dp))
        StepIndicator(current = step.ordinal + 1, total = 3)
        Spacer(Modifier.height(16.dp))

        AppCard {
            when (step) {
                RecoverStep.ACCOUNT -> {
                    Text(
                        "Escribe tu usuario o el teléfono con el que te registraste. Te enviaremos un código por SMS.",
                        color = AppColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    AquaGoTextField(
                        value = account,
                        onValueChange = { account = it },
                        label = "Usuario o teléfono"
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(if (loading) "Enviando..." else "Enviar código", enabled = !loading) {
                        val validation = validateRecoveryAccount(account)
                        if (validation != null) {
                            error = validation
                        } else {
                            scope.launch {
                                loading = true
                                error = null
                                delay(900) // simula el envío del SMS
                                loading = false
                                code = ""
                                goTo(RecoverStep.CODE)
                            }
                        }
                    }
                }

                RecoverStep.CODE -> {
                    Text(
                        "Enviamos un código de 6 dígitos a ${recoveryDestination(account)}.",
                        color = AppColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    AquaGoTextField(
                        value = code,
                        onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                        label = "Código de verificación",
                        keyboardType = KeyboardType.NumberPassword,
                        supportingText = "Demostración: usa el código $DEMO_RECOVERY_CODE"
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Verificar código") {
                        val validation = validateRecoveryCode(code)
                        if (validation != null) error = validation else goTo(RecoverStep.NEW_PASSWORD)
                    }
                    TextButton(
                        onClick = {
                            error = null
                            info = "Te enviamos un código nuevo"
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Reenviar código", color = AppColors.Accent)
                    }
                }

                RecoverStep.NEW_PASSWORD -> {
                    Text(
                        "Crea una contraseña nueva para tu cuenta.",
                        color = AppColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    AquaGoPasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Nueva contraseña",
                        supportingText = "Mínimo $MIN_PASSWORD_LENGTH caracteres"
                    )
                    Spacer(Modifier.height(12.dp))
                    AquaGoPasswordField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = "Confirmar contraseña"
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(if (loading) "Guardando..." else "Guardar contraseña", enabled = !loading) {
                        val validation = validateNewPassword(password, confirmation)
                        if (validation != null) {
                            error = validation
                        } else {
                            scope.launch {
                                loading = true
                                error = null
                                delay(900) // simula el guardado en el servidor
                                loading = false
                                goTo(RecoverStep.DONE)
                            }
                        }
                    }
                }

                RecoverStep.DONE -> Unit
            }
            FormError(error)
            info?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = AppColors.Green, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        DemoNote()
    }
}
