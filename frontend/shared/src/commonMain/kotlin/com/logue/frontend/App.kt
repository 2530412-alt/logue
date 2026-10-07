package com.logue.frontend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** Pantallas que se ven antes de iniciar sesión. */
enum class AuthScreen { LOGIN, REGISTER, RECOVER_PASSWORD }

/**
 * Raíz de la app. Mantiene la sesión en memoria:
 * sin sesión -> LoginScreen (o Crear cuenta / Recuperar contraseña);
 * con sesión -> pantalla según el rol.
 */
@Composable
fun App() {
    AquaGoTheme {
        var session by remember { mutableStateOf<LoginResponse?>(null) }
        var authScreen by remember { mutableStateOf(AuthScreen.LOGIN) }
        val logout = {
            session = null
            authScreen = AuthScreen.LOGIN
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
                            onLoggedIn = { session = it },
                            onCreateAccount = { authScreen = AuthScreen.REGISTER },
                            onForgotPassword = { authScreen = AuthScreen.RECOVER_PASSWORD }
                        )
                        AuthScreen.REGISTER -> RegisterScreen(onBack = backToLogin)
                        AuthScreen.RECOVER_PASSWORD -> RecoverPasswordScreen(onBack = backToLogin)
                    }
                } else {
                    when (current.role) {
                        Roles.CIUDADANO -> CiudadanoScreen(current, logout)
                        Roles.OPERADOR -> OperadorScreen(current, logout)
                        Roles.ADMIN -> AdminScreen(current, logout)
                        else -> UnknownRoleScreen(current.role, logout)
                    }
                }
            }
        }
    }
}
