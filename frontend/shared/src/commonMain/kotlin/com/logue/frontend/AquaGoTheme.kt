package com.logue.frontend

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Tema Material 3 de la app (claro), construido con la paleta de [AppColors]. */
@Composable
fun AquaGoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppColors.Accent,
            onPrimary = AppColors.OnAccent,
            primaryContainer = AppColors.AccentSoft,
            onPrimaryContainer = AppColors.AccentDark,
            secondary = AppColors.AccentLight,
            onSecondary = AppColors.OnAccent,
            background = AppColors.Background,
            onBackground = AppColors.TextPrimary,
            surface = AppColors.Surface,
            onSurface = AppColors.TextPrimary,
            surfaceVariant = AppColors.SurfaceAlt,
            onSurfaceVariant = AppColors.TextMuted,
            surfaceContainer = AppColors.Surface,
            surfaceContainerHigh = AppColors.Surface,
            surfaceContainerHighest = AppColors.SurfaceAlt,
            outline = AppColors.Border,
            outlineVariant = AppColors.Border,
            error = AppColors.Red
        ),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
