package com.logue.frontend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.Json

/** Pantallas que se ven antes de iniciar sesión. */
enum class AuthScreen { LOGIN, REGISTER, RECOVER_PASSWORD }

/**
 * Guarda la sesión como texto JSON en el estado que Android conserva al girar el teléfono o
 * cambiar el tamaño de letra. Con `remember` normal, esos cambios cerraban la sesión (USR-19).
 */
private val SessionSaver = Saver<LoginResponse?, String>(
    save = { session -> session?.let { Json.encodeToString(LoginResponse.serializer(), it) } ?: "" },
    restore = { text -> if (text.isEmpty()) null else Json.decodeFromString(LoginResponse.serializer(), text) }
)

private val AuthScreenSaver = Saver<AuthScreen, String>(save = { it.name }, restore = { AuthScreen.valueOf(it) })

/**
 * Raíz de la app. Mantiene la sesión:
 * sin sesión -> LoginScreen (o Crear cuenta / Recuperar contraseña);
 * con sesión -> pantalla según el rol.
 */
@Composable
fun App() {
    AquaGoTheme {
        var session by rememberSaveable(stateSaver = SessionSaver) { mutableStateOf<LoginResponse?>(null) }
        var authScreen by rememberSaveable(stateSaver = AuthScreenSaver) { mutableStateOf(AuthScreen.LOGIN) }
        var showPagoPredial by rememberSaveable { mutableStateOf(false) }
        var loginMessage by remember { mutableStateOf<String?>(null) }
        val logout = {
            session = null
            authScreen = AuthScreen.LOGIN
            showPagoPredial = false
        }
        // El servidor respondió 401: el token venció (dura 12 h). Se regresa al login con un aviso.
        val sessionExpired = {
            loginMessage = "Tu sesión expiró. Inicia sesión de nuevo"
            logout()
        }
        val backToLogin = { authScreen = AuthScreen.LOGIN }
        val current = session

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.Background,
            contentColor = AppColors.TextPrimary
        ) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (current == null) {
                    when (authScreen) {
                        AuthScreen.LOGIN -> LoginScreen(
                            onLoggedIn = {
                                loginMessage = null
                                session = it
                            },
                            onCreateAccount = { authScreen = AuthScreen.REGISTER },
                            onForgotPassword = { authScreen = AuthScreen.RECOVER_PASSWORD },
                            initialMessage = loginMessage
                        )
                        AuthScreen.REGISTER -> RegisterScreen(onBack = backToLogin)
                        AuthScreen.RECOVER_PASSWORD -> RecoverPasswordScreen(onBack = backToLogin)
                    }
                } else {
                    when (current.role) {
                        Roles.CIUDADANO ->
                            if (showPagoPredial) {
                                PagoPredialScreen(current, onBack = { showPagoPredial = false }, onSessionExpired = sessionExpired)
                            } else {
                                CiudadanoScreen(current, logout) { showPagoPredial = true }
                            }
                        Roles.OPERADOR -> OperadorScreen(current, logout)
                        Roles.ADMIN -> AdminScreen(current, logout)
                        else -> UnknownRoleScreen(current.role, logout)
                    }
                }
            }
        }
    }
}
