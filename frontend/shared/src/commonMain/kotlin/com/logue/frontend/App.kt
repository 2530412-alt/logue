package com.logue.frontend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun App() {
    AquaGoTheme {
        var session by remember { mutableStateOf<LoginResponse?>(null) }
        val logout = { session = null }
        val current = session

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.Background,
            contentColor = AppColors.TextPrimary
        ) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (current == null) {
                    LoginScreen { session = it }
                } else {
                    when (current.role) {
                        "CIUDADANO" -> CiudadanoScreen(current, logout)
                        "OPERADOR" -> OperadorScreen(current, logout)
                        "ADMIN" -> AdminScreen(current, logout)
                        else -> LoginScreen { session = it }
                    }
                }
            }
        }
    }
}