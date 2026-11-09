package com.logue.frontend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/** Pantallas a las que se llega desde la pantalla principal de cada rol. */
enum class SubScreen { HOME, PAGO_PREDIAL, PAGOS, PERFIL }

private val SubScreenSaver = Saver<SubScreen, String>(save = { it.name }, restore = { SubScreen.valueOf(it) })

/**
 * Ancho máximo del contenido (ADM-07). En el teléfono ocupa todo el ancho; en el navegador de una
 * computadora se centra en lugar de estirarse por toda la pantalla.
 */
private val ANCHO_MAXIMO_CONTENIDO = 1080.dp

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
        var sub by rememberSaveable(stateSaver = SubScreenSaver) { mutableStateOf(SubScreen.HOME) }
        var loginMessage by remember { mutableStateOf<String?>(null) }
        val logout = {
            session = null
            authScreen = AuthScreen.LOGIN
            sub = SubScreen.HOME
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
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = ANCHO_MAXIMO_CONTENIDO).fillMaxSize()) {
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
                        val home = { sub = SubScreen.HOME }
                        when {
                            current.role !in setOf(Roles.CIUDADANO, Roles.OPERADOR, Roles.ADMIN) ->
                                UnknownRoleScreen(current.role, logout)
                            sub == SubScreen.PERFIL ->
                                PerfilScreen(current, onBack = home, onSessionExpired = sessionExpired)
                            sub == SubScreen.PAGOS ->
                                PagosPredialesScreen(current, onBack = home, onSessionExpired = sessionExpired)
                            sub == SubScreen.PAGO_PREDIAL && current.role == Roles.CIUDADANO ->
                                PagoPredialScreen(current, onBack = home, onSessionExpired = sessionExpired)
                            current.role == Roles.CIUDADANO -> CiudadanoScreen(
                                current, logout,
                                onPagoPredial = { sub = SubScreen.PAGO_PREDIAL },
                                onHistorialPagos = { sub = SubScreen.PAGOS },
                                onPerfil = { sub = SubScreen.PERFIL },
                                onSessionExpired = sessionExpired
                            )
                            current.role == Roles.OPERADOR -> OperadorScreen(
                                current, logout,
                                onPerfil = { sub = SubScreen.PERFIL },
                                onSessionExpired = sessionExpired
                            )
                            else -> AdminScreen(
                                current, logout,
                                onComprobantes = { sub = SubScreen.PAGOS },
                                onPerfil = { sub = SubScreen.PERFIL },
                                onSessionExpired = sessionExpired
                            )
                        }
                    }
                }
            }
        }
    }
}
