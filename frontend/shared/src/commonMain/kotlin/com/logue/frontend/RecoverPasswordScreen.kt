package com.logue.frontend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Pasos de la recuperación de contraseña. */
enum class RecoverStep { ACCOUNT, CODE, NEW_PASSWORD, DONE }

/** Guarda el paso por su nombre para que sobreviva al giro del teléfono en todas las plataformas. */
private val RecoverStepSaver = Saver<RecoverStep, String>(save = { it.name }, restore = { RecoverStep.valueOf(it) })

/**
 * Recuperar contraseña (USR-12) en 3 pasos:
 * 1) usuario o teléfono, 2) código de 6 dígitos que manda el servidor, 3) contraseña nueva.
 *
 * El servidor genera el código (`POST /recuperacion/solicitar`) y solo cambia la contraseña si el
 * código es el vigente (`POST /recuperacion/confirmar`). El envío del código por SMS depende de
 * NOT-05: mientras no exista, el código queda en el registro del servidor.
 */
@Composable
fun RecoverPasswordScreen(onBack: () -> Unit) {
    var step by rememberSaveable(stateSaver = RecoverStepSaver) { mutableStateOf(RecoverStep.ACCOUNT) }
    var account by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
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
                                val result = pedirCodigoDeRecuperacion(account)
                                loading = false
                                if (result is ApiResult.Ok) {
                                    code = ""
                                    goTo(RecoverStep.CODE)
                                } else {
                                    error = result.mensajeDeError()
                                }
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
                        supportingText = "Escribe el código de 6 dígitos"
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Verificar código") {
                        val validation = validateRecoveryCode(code)
                        if (validation != null) error = validation else goTo(RecoverStep.NEW_PASSWORD)
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                error = null
                                info = null
                                val result = pedirCodigoDeRecuperacion(account)
                                if (result is ApiResult.Ok) info = "Te enviamos un código nuevo" else error = result.mensajeDeError()
                            }
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
                                val result = cambiarContrasenaConCodigo(account, code, password)
                                loading = false
                                if (result is ApiResult.Ok) {
                                    goTo(RecoverStep.DONE)
                                } else {
                                    val mensaje = result.mensajeDeError()
                                    // Un código incorrecto o vencido se corrige en el paso anterior
                                    if (result is ApiResult.Rechazo) goTo(RecoverStep.CODE)
                                    error = mensaje
                                }
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
    }
}
