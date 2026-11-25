package com.logue.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de inicio de sesión. Llama a [onLoggedIn] con los datos de la sesión si el login es correcto.
 * [onCreateAccount] y [onForgotPassword] abren las pantallas de Crear cuenta y Recuperar contraseña.
 * [initialMessage] se muestra al abrir la pantalla (por ejemplo, «Tu sesión expiró»).
 */
@Composable
fun LoginScreen(
    onLoggedIn: (LoginResponse) -> Unit,
    onCreateAccount: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
    initialMessage: String? = null
) {
    // rememberSaveable: el usuario escrito no se borra al girar el teléfono o cambiar el tamaño de letra.
    // La contraseña se queda en remember a propósito, para no guardarla en el estado del sistema.
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(initialMessage) }
    var loading by remember { mutableStateOf(false) }
    var waking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Despierta al servidor mientras el usuario escribe (API-08). Render gratuito tarda ~1 min en despertar.
    LaunchedEffect(Unit) { despertarServidor() }

    // ---- Solo página web (panel del delegado): reCAPTCHA y «Iniciar sesión con Google» ----
    val esWeb = Plataforma.esWeb
    var configWeb by remember { mutableStateOf(ConfigWeb()) }
    var captcha by remember { mutableStateOf<String?>(null) }
    var credencialGoogle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (esWeb) configWeb = obtenerConfigWeb() }
    LaunchedEffect(configWeb.recaptchaSiteKey) {
        if (configWeb.recaptchaSiteKey.isNotBlank()) {
            Plataforma.casillaCaptcha?.invoke(configWeb.recaptchaSiteKey) { respuesta -> captcha = respuesta.ifBlank { null } }
        }
    }
    LaunchedEffect(configWeb.googleClientId) {
        if (configWeb.googleClientId.isNotBlank()) {
            Plataforma.botonGoogle?.invoke(configWeb.googleClientId) { credencial -> credencialGoogle = credencial }
        }
    }
    val pideCaptcha = esWeb && configWeb.recaptchaSiteKey.isNotBlank()

    /** Muestra el resultado del login (contraseña o Google). Si falló, el captcha se vuelve a pedir. */
    fun mostrar(result: LoginResult) {
        if (result is LoginResult.Success) {
            onLoggedIn(result.session)
            return
        }
        error = when (result) {
            LoginResult.InvalidCredentials -> "Usuario o contraseña incorrectos"
            is LoginResult.Locked -> lockedMessage(result.retryAfterSeconds)
            is LoginResult.AccountBlocked -> result.mensaje
            is LoginResult.Rechazo -> result.mensaje
            LoginResult.ServerError -> "El servidor tuvo un problema. Intenta de nuevo"
            LoginResult.NetworkError -> MENSAJE_SIN_CONEXION
            is LoginResult.Success -> null
        }
        if (pideCaptcha) {
            captcha = null
            Plataforma.reiniciarCaptcha()
        }
    }

    // El botón de Google entregó la credencial: se valida en el servidor
    LaunchedEffect(credencialGoogle) {
        val credencial = credencialGoogle ?: return@LaunchedEffect
        credencialGoogle = null
        if (pideCaptcha && captcha == null) {
            error = "Primero marca la casilla «No soy un robot»"
            return@LaunchedEffect
        }
        loading = true
        error = null
        val result = loginConGoogle(credencial, captcha)
        loading = false
        mostrar(result)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AppColors.Accent,
        unfocusedBorderColor = AppColors.Border,
        focusedLabelColor = AppColors.Accent,
        unfocusedLabelColor = AppColors.TextMuted,
        cursorColor = AppColors.Accent
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(92.dp).clip(RoundedCornerShape(26.dp)).background(AppColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Text("AG", color = AppColors.OnAccent, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text("AquaGo", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Solicita tu agua desde casa", color = AppColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))

        AppCard {
            Text("Inicia sesión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Usuario") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Contraseña") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPass = !showPass }) {
                        Text(if (showPass) "Ocultar" else "Mostrar", color = AppColors.Accent)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = onForgotPassword,
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text("¿Olvidaste tu contraseña?", color = AppColors.Accent, style = MaterialTheme.typography.bodySmall)
            }
            if (pideCaptcha) {
                // Aquí se dibuja la casilla «No soy un robot» de Google (elemento HTML encima del lienzo)
                ZonaWeb("captcha", 80.dp)
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    // 1) Validar los campos; 2) llamar al backend; 3) mostrar el mensaje que corresponda.
                    val validation = validateCredentials(user, pass)
                        ?: if (pideCaptcha && captcha == null) "Marca la casilla «No soy un robot»" else null
                    if (validation != null) {
                        error = validation
                    } else {
                        scope.launch {
                            loading = true
                            error = null
                            // Si tarda más de 4 s, casi seguro el servidor estaba dormido: se avisa en lugar de parecer trabado
                            val aviso = launch { delay(4_000); waking = true }
                            val result = login(user, pass, captcha)
                            aviso.cancel()
                            waking = false
                            loading = false
                            mostrar(result)
                        }
                    }
                },
                enabled = !loading,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Accent,
                    contentColor = AppColors.OnAccent
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            ) {
                Text(
                    when {
                        waking -> "Despertando el servidor..."
                        loading -> "Entrando..."
                        else -> "Iniciar sesión"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            if (waking) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "La primera conexión después de un rato puede tardar hasta un minuto.",
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (esWeb && configWeb.googleClientId.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = AppColors.Border)
                    Text("  o  ", color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(Modifier.weight(1f), color = AppColors.Border)
                }
                Spacer(Modifier.height(10.dp))
                // Botón oficial «Iniciar sesión con Google»
                ZonaWeb("google", 44.dp)
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = AppColors.Red, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Registro de ciudadanos (repartidores y administradores los da de alta el delegado).
        // La página web es solo para el delegado, así que ahí no se ofrece.
        Spacer(Modifier.height(16.dp))
        if (esWeb) {
            Text("Panel del delegado", color = AppColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¿No tienes cuenta?", color = AppColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCreateAccount) {
                Text("Regístrate", color = AppColors.Accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
