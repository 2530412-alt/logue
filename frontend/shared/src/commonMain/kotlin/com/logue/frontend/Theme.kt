package com.logue.frontend

import androidx.compose.ui.graphics.Color

/**
 * Paleta única de AquaGo (rediseño claro "agua"). Todos los colores de la app salen de aquí.
 *
 * - Azul profundo ([Accent]) para acciones principales y encabezados.
 * - Azul claro ([AccentLight] / [AccentSoft]) para fondos de acento y detalles.
 * - Fondos blancos y gris muy suave ([Background], [Surface]).
 * - Estados: verde = entregada, rojo suave = cancelada / no entregada, ámbar = pendiente, azul = en camino.
 *
 * Los nombres son los mismos que tenía la paleta oscura anterior, así todas las pantallas existentes
 * (login, registro, perfil, pagos) toman el nuevo diseño sin cambiar su lógica.
 */
object AppColors {
    val Background = Color(0xFFF3F7FB)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFEEF3F8)
    val Border = Color(0xFFDCE5EE)
    val Accent = Color(0xFF0B5CAD)
    val AccentDark = Color(0xFF083F7A)
    val AccentLight = Color(0xFF3FA9E6)
    val AccentSoft = Color(0xFFE3F1FC)
    val OnAccent = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF13263D)
    val TextMuted = Color(0xFF64748B)
    val Green = Color(0xFF1F9D63)
    val Blue = Color(0xFF2F7FE0)
    val Yellow = Color(0xFFD98300)
    val Red = Color(0xFFD9534F)
}
