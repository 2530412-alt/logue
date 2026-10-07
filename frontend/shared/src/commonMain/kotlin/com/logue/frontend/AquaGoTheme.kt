package com.logue.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** Tema Material 3 de la app, construido con la paleta de [AppColors]. */
@Composable
fun AquaGoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppColors.Accent,
            onPrimary = AppColors.OnAccent,
            background = AppColors.Background,
            onBackground = AppColors.TextPrimary,
            surface = AppColors.Surface,
            onSurface = AppColors.TextPrimary,
            error = AppColors.Red
        ),
        content = content
    )
}
