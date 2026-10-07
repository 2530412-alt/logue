package com.logue.frontend


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AquaTarjeta = Color(0xFF121A2B)
val AquaBorde = Color(0xFF2A3350)
val AquaPrincipal = Color(0xFF22C6E8)
val AquaTextoSuave = Color(0xFF9AA4BF)

@Composable
fun AquaGoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AquaPrincipal,
            onPrimary = Color(0xFF04222B),
            background = Color(0xFF0A0E1A),
            onBackground = Color.White,
            surface = AquaTarjeta,
            onSurface = Color.White,
            error = Color(0xFFF87171)
        ),
        content = content
    )
}