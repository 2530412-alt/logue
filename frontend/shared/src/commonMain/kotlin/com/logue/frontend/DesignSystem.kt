package com.logue.frontend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * SISTEMA DE DISEÑO DE AQUAGO (rediseño UI/UX).
 * Piezas reutilizables para todas las pantallas: encabezado, chips de estado, íconos, tarjetas de
 * solicitud, avisos (sin conexión, vacío, cargando, error) y diálogos de confirmación.
 * Los íconos se dibujan con Canvas para no agregar dependencias nuevas al proyecto.
 */

// ---------------------------------------------------------------------------------------------
//  Íconos
// ---------------------------------------------------------------------------------------------

enum class Icono { GOTA, UBICACION, CALENDARIO, RELOJ, CAMION, NOTA, NUBE_OFF, ALERTA, CHECK, PERFIL, SALIR, RECIBO, ACTUALIZAR, MAPA, MAS, PERSONA }

/** Ícono lineal sencillo, del color que se pida. */
@Composable
fun AppIcon(icono: Icono, color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { dibujarIcono(icono, color) }
}

private fun DrawScope.dibujarIcono(icono: Icono, color: Color) {
    val w = size.width
    val h = size.height
    val trazo = Stroke(w * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (icono) {
        Icono.GOTA -> {
            val p = Path().apply {
                moveTo(w * 0.5f, h * 0.06f)
                cubicTo(w * 0.5f, h * 0.06f, w * 0.12f, h * 0.5f, w * 0.12f, h * 0.64f)
                cubicTo(w * 0.12f, h * 0.86f, w * 0.3f, h * 0.96f, w * 0.5f, h * 0.96f)
                cubicTo(w * 0.7f, h * 0.96f, w * 0.88f, h * 0.86f, w * 0.88f, h * 0.64f)
                cubicTo(w * 0.88f, h * 0.5f, w * 0.5f, h * 0.06f, w * 0.5f, h * 0.06f)
                close()
            }
            drawPath(p, color)
        }
        Icono.UBICACION, Icono.MAPA -> {
            val p = Path().apply {
                moveTo(w * 0.5f, h * 0.95f)
                cubicTo(w * 0.5f, h * 0.95f, w * 0.15f, h * 0.58f, w * 0.15f, h * 0.38f)
                cubicTo(w * 0.15f, h * 0.18f, w * 0.31f, h * 0.05f, w * 0.5f, h * 0.05f)
                cubicTo(w * 0.69f, h * 0.05f, w * 0.85f, h * 0.18f, w * 0.85f, h * 0.38f)
                cubicTo(w * 0.85f, h * 0.58f, w * 0.5f, h * 0.95f, w * 0.5f, h * 0.95f)
                close()
            }
            drawPath(p, color, style = trazo)
            drawCircle(color, radius = w * 0.11f, center = Offset(w * 0.5f, h * 0.38f))
        }
        Icono.CALENDARIO -> {
            drawRoundRect(color, Offset(w * 0.1f, h * 0.18f), Size(w * 0.8f, h * 0.72f), CornerRadius(w * 0.12f), style = trazo)
            drawLine(color, Offset(w * 0.1f, h * 0.4f), Offset(w * 0.9f, h * 0.4f), trazo.width)
            drawLine(color, Offset(w * 0.32f, h * 0.08f), Offset(w * 0.32f, h * 0.26f), trazo.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.68f, h * 0.08f), Offset(w * 0.68f, h * 0.26f), trazo.width, StrokeCap.Round)
        }
        Icono.RELOJ -> {
            drawCircle(color, radius = w * 0.4f, style = trazo)
            drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.26f), trazo.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.68f, h * 0.6f), trazo.width, StrokeCap.Round)
        }
        Icono.CAMION -> {
            drawRoundRect(color, Offset(w * 0.04f, h * 0.28f), Size(w * 0.56f, h * 0.44f), CornerRadius(w * 0.06f), style = trazo)
            val cabina = Path().apply {
                moveTo(w * 0.6f, h * 0.4f); lineTo(w * 0.8f, h * 0.4f); lineTo(w * 0.96f, h * 0.56f)
                lineTo(w * 0.96f, h * 0.72f); lineTo(w * 0.6f, h * 0.72f)
            }
            drawPath(cabina, color, style = trazo)
            drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.24f, h * 0.8f))
            drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.78f, h * 0.8f))
        }
        Icono.NOTA, Icono.RECIBO -> {
            drawRoundRect(color, Offset(w * 0.18f, h * 0.08f), Size(w * 0.64f, h * 0.84f), CornerRadius(w * 0.1f), style = trazo)
            listOf(0.32f, 0.5f, 0.68f).forEach { y ->
                drawLine(color, Offset(w * 0.32f, h * y), Offset(w * 0.68f, h * y), trazo.width, StrokeCap.Round)
            }
        }
        Icono.NUBE_OFF -> {
            val nube = Path().apply {
                moveTo(w * 0.28f, h * 0.74f)
                cubicTo(w * 0.1f, h * 0.74f, w * 0.06f, h * 0.5f, w * 0.24f, h * 0.46f)
                cubicTo(w * 0.26f, h * 0.24f, w * 0.56f, h * 0.18f, w * 0.64f, h * 0.38f)
                cubicTo(w * 0.86f, h * 0.36f, w * 0.96f, h * 0.7f, w * 0.72f, h * 0.74f)
                close()
            }
            drawPath(nube, color, style = trazo)
            drawLine(color, Offset(w * 0.12f, h * 0.12f), Offset(w * 0.88f, h * 0.9f), trazo.width, StrokeCap.Round)
        }
        Icono.ALERTA -> {
            drawCircle(color, radius = w * 0.42f, style = trazo)
            drawLine(color, Offset(w * 0.5f, h * 0.28f), Offset(w * 0.5f, h * 0.56f), trazo.width, StrokeCap.Round)
            drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.72f))
        }
        Icono.CHECK -> {
            val p = Path().apply { moveTo(w * 0.16f, h * 0.54f); lineTo(w * 0.42f, h * 0.78f); lineTo(w * 0.86f, h * 0.26f) }
            drawPath(p, color, style = trazo)
        }
        Icono.PERFIL, Icono.PERSONA -> {
            drawCircle(color, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.32f), style = trazo)
            val p = Path().apply {
                moveTo(w * 0.16f, h * 0.92f)
                cubicTo(w * 0.16f, h * 0.62f, w * 0.84f, h * 0.62f, w * 0.84f, h * 0.92f)
            }
            drawPath(p, color, style = trazo)
        }
        Icono.SALIR -> {
            val puerta = Path().apply { moveTo(w * 0.5f, h * 0.12f); lineTo(w * 0.14f, h * 0.12f); lineTo(w * 0.14f, h * 0.88f); lineTo(w * 0.5f, h * 0.88f) }
            drawPath(puerta, color, style = trazo)
            drawLine(color, Offset(w * 0.4f, h * 0.5f), Offset(w * 0.9f, h * 0.5f), trazo.width, StrokeCap.Round)
            val punta = Path().apply { moveTo(w * 0.74f, h * 0.32f); lineTo(w * 0.9f, h * 0.5f); lineTo(w * 0.74f, h * 0.68f) }
            drawPath(punta, color, style = trazo)
        }
        Icono.ACTUALIZAR -> {
            drawArc(color, 30f, 280f, false, Offset(w * 0.14f, h * 0.14f), Size(w * 0.72f, h * 0.72f), style = trazo)
            val punta = Path().apply { moveTo(w * 0.86f, h * 0.18f); lineTo(w * 0.84f, h * 0.42f); lineTo(w * 0.6f, h * 0.38f) }
            drawPath(punta, color, style = trazo)
        }
        Icono.MAS -> {
            drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), trazo.width * 1.2f, StrokeCap.Round)
            drawLine(color, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), trazo.width * 1.2f, StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  Encabezado
// ---------------------------------------------------------------------------------------------

/** Iniciales para el avatar: "maria.lopez" -> "ML", "Juan" -> "JU". */
fun iniciales(nombre: String): String {
    val partes = nombre.trim().split(' ', '.', '_', '-').filter { it.isNotBlank() }
    return when {
        partes.size >= 2 -> "${partes[0].first()}${partes[1].first()}".uppercase()
        partes.size == 1 -> partes[0].take(2).uppercase()
        else -> "?"
    }
}

/** Acción del menú del encabezado. */
data class AccionMenu(val texto: String, val icono: Icono, val alTocar: () -> Unit, val peligrosa: Boolean = false)

/**
 * Encabezado con degradado azul: saludo, nombre del usuario, rol y menú de cuenta
 * (Mi perfil, pagos, cerrar sesión...). [contenido] va debajo, dentro del mismo bloque azul.
 */
@Composable
fun AppHeader(
    saludo: String,
    nombre: String,
    rol: String,
    acciones: List<AccionMenu>,
    contenido: @Composable ColumnScope.() -> Unit = {}
) {
    var menuAbierto by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(AppColors.AccentDark, AppColors.Accent, AppColors.AccentLight)))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { AppIcon(Icono.GOTA, Color.White, 16.dp) }
            Spacer(Modifier.width(8.dp))
            Text("AquaGo", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Box {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { menuAbierto = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(iniciales(nombre), color = AppColors.Accent, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = menuAbierto,
                    onDismissRequest = { menuAbierto = false },
                    containerColor = AppColors.Surface
                ) {
                    acciones.forEach { a ->
                        val color = if (a.peligrosa) AppColors.Red else AppColors.TextPrimary
                        DropdownMenuItem(
                            text = { Text(a.texto, color = color) },
                            leadingIcon = { AppIcon(a.icono, color) },
                            onClick = { menuAbierto = false; a.alTocar() }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(saludo, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        Text(
            nombre,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            rol,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp)
        )
        contenido()
    }
}

/** Barra superior sencilla para pantallas secundarias (Pedir pipa, etc.). */
@Composable
fun TopBarSecundaria(titulo: String, subtitulo: String? = null, onBack: () -> Unit) {
    PlatformBackHandler(onBack)
    Column(Modifier.fillMaxWidth().background(AppColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
        BackLink(onBack)
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitulo != null) Text(subtitulo, color = AppColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
    }
    HorizontalDivider(color = AppColors.Border)
}

// ---------------------------------------------------------------------------------------------
//  Botones
// ---------------------------------------------------------------------------------------------

/** Botón grande y muy visible ("Pedir pipa"). */
@Composable
fun BotonDestacado(texto: String, detalle: String, icono: Icono, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = AppColors.Accent, spotColor = AppColors.Accent)
            .clip(RoundedCornerShape(22.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(AppColors.Accent, AppColors.AccentLight))),
            contentAlignment = Alignment.Center
        ) { AppIcon(icono, Color.White, 26.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(texto, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Text(detalle, color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).background(AppColors.Accent), contentAlignment = Alignment.Center) {
            AppIcon(Icono.MAS, Color.White, 16.dp)
        }
    }
}

/** Botón secundario con borde. */
@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = AppColors.Accent, icono: Icono? = null, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, if (enabled) color.copy(alpha = 0.6f) else AppColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier.heightIn(min = 46.dp)
    ) {
        if (icono != null) {
            AppIcon(icono, if (enabled) color else AppColors.TextMuted, 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botón relleno de color (para acciones de estado: Entregada, En camino...). */
@Composable
fun BotonColor(texto: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier, icono: Icono? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier.heightIn(min = 46.dp)
    ) {
        if (icono != null) {
            AppIcon(icono, Color.White, 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------------------------
//  Chips y renglones
// ---------------------------------------------------------------------------------------------

/** Chip de estado con color (Entregada verde, Cancelada rojo, Pendiente ámbar...). */
@Composable
fun StatusChip(texto: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(texto, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Renglón con ícono + texto (dirección, fecha, referencia...). */
@Composable
fun InfoRow(icono: Icono, texto: String, color: Color = AppColors.TextMuted, fuerte: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        AppIcon(icono, AppColors.AccentLight, 16.dp, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            texto,
            color = if (fuerte) AppColors.TextPrimary else color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (fuerte) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/** Tarjeta blanca del rediseño (esquinas redondeadas, borde suave). */
@Composable
fun Tarjeta(modifier: Modifier = Modifier, borde: Color = AppColors.Border, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Surface)
            .border(1.dp, borde, RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content
    )
}

/** Contador chico (En camino 1, Pendientes 2...). */
@Composable
fun ContadorChico(valor: String, etiqueta: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(valor, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(etiqueta, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun TituloSeccion(texto: String, detalle: String? = null, accion: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (detalle != null) Text(detalle, color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        accion?.invoke()
    }
}

// ---------------------------------------------------------------------------------------------
//  Estados de la pantalla: sin conexión, vacío, cargando, error
// ---------------------------------------------------------------------------------------------

/** Aviso amarillo: sin conexión / cambios pendientes de subir. */
@Composable
fun AvisoSinConexion(desdeCopia: Boolean, pendientes: Int, onSincronizar: () -> Unit) {
    if (!desdeCopia && pendientes == 0) return
    val color = AppColors.Yellow
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(Icono.NUBE_OFF, color, 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (desdeCopia) "Sin conexión" else "Cambios por subir",
                fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, style = MaterialTheme.typography.bodyMedium
            )
            Text(
                buildString {
                    if (desdeCopia) append("Ves la última información guardada. ")
                    if (pendientes > 0) append(if (pendientes == 1) "1 cambio se subirá solo al volver la señal." else "$pendientes cambios se subirán solos al volver la señal.")
                },
                color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = onSincronizar) { Text("Reintentar", color = AppColors.Accent, fontWeight = FontWeight.SemiBold) }
    }
}

/** Mensaje breve tras una acción (verde si salió bien, ámbar si quedó guardado sin conexión, rojo si falló). */
@Composable
fun AvisoAccion(texto: String, color: Color, onCerrar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(if (color == AppColors.Red) Icono.ALERTA else Icono.CHECK, color, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(texto, Modifier.weight(1f).padding(vertical = 10.dp), color = AppColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onCerrar) { Text("OK", color = color, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun EstadoVacio(icono: Icono, titulo: String, texto: String, accion: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
            AppIcon(icono, AppColors.Accent, 34.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(texto, color = AppColors.TextMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (accion != null) {
            Spacer(Modifier.height(14.dp))
            accion()
        }
    }
}

@Composable
fun EstadoCargando(texto: String = "Cargando...") {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 3.dp)
        Spacer(Modifier.height(12.dp))
        Text(texto, color = AppColors.TextMuted)
    }
}

@Composable
fun EstadoError(mensaje: String, onReintentar: () -> Unit) {
    EstadoVacio(Icono.ALERTA, "No se pudo cargar", mensaje) {
        BotonColor("Reintentar", AppColors.Accent, onReintentar, icono = Icono.ACTUALIZAR)
    }
}

// ---------------------------------------------------------------------------------------------
//  Diálogos
// ---------------------------------------------------------------------------------------------

/** Diálogo de confirmación con textos claros ("Sí, cancelar" / "No, volver"). */
@Composable
fun DialogoConfirmacion(
    titulo: String,
    texto: String,
    confirmar: String,
    onConfirmar: () -> Unit,
    onCerrar: () -> Unit,
    cancelar: String = "Volver",
    peligroso: Boolean = false,
    contenidoExtra: @Composable ColumnScope.() -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(titulo, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(texto, color = AppColors.TextMuted)
                contenidoExtra()
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (peligroso) AppColors.Red else AppColors.Accent, contentColor = Color.White)
            ) { Text(confirmar, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(cancelar, color = AppColors.TextMuted) } }
    )
}
