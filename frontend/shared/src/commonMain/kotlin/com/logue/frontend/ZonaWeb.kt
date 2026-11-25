package com.logue.frontend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp

/**
 * Espacio reservado en la pantalla para un elemento HTML de Google (botón o captcha). La página web
 * dibuja con Compose en un lienzo; Google necesita sus propios elementos HTML, así que se colocan
 * encima, justo en este hueco. Al salir de la pantalla se ocultan.
 */
@Composable
fun ZonaWeb(id: String, alto: Dp, modifier: Modifier = Modifier) {
    DisposableEffect(id) {
        onDispose { Plataforma.ubicarElementoWeb(id, 0f, 0f, 0f, 0f, false) }
    }
    Box(
        modifier.fillMaxWidth().height(alto).onGloballyPositioned { c ->
            val r = c.boundsInWindow()
            Plataforma.ubicarElementoWeb(id, r.left, r.top, r.width, r.height, r.height > 0f)
        }
    )
}
