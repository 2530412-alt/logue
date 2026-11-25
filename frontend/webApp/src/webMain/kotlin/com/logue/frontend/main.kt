package com.logue.frontend

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Sesión en el navegador, servidor, botón de Google y captcha (WebPlataforma.kt)
    iniciarWeb()
    ComposeViewport {
        App()
    }
}