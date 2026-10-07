package com.logue.frontend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Piezas que comparten las pantallas de acceso: Crear cuenta y Recuperar contraseña.
 * Usan la paleta de AppColors para verse igual que el login.
 */

/**
 * Botón "atrás" del sistema (Android): ejecuta [onBack] en lugar de cerrar la app.
 * BackHandler está marcado como obsoleto a favor de NavigationEventHandler, pero sigue
 * funcionando y es mucho más simple para una app sin librería de navegación.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlatformBackHandler(onBack: () -> Unit) {
    BackHandler(enabled = true, onBack = onBack)
}

/** Colores de los campos de texto (los mismos que el login). */
@Composable
fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.Accent,
    unfocusedBorderColor = AppColors.Border,
    focusedLabelColor = AppColors.Accent,
    unfocusedLabelColor = AppColors.TextMuted,
    cursorColor = AppColors.Accent
)

/** Logo de AquaGo: cuadro de color con las iniciales. */
@Composable
fun AquaGoLogo(size: Dp = 92.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(AppColors.Accent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "AG",
            color = AppColors.OnAccent,
            style = if (size >= 80.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Botón "Volver" con una flecha dibujada (no depende de fuentes ni de librerías de íconos). */
@Composable
fun BackLink(onBack: () -> Unit) {
    TextButton(onClick = onBack, contentPadding = PaddingValues(start = 0.dp, end = 12.dp)) {
        Canvas(Modifier.size(18.dp)) {
            val arrow = Path().apply {
                moveTo(size.width * 0.62f, size.height * 0.2f)
                lineTo(size.width * 0.3f, size.height * 0.5f)
                lineTo(size.width * 0.62f, size.height * 0.8f)
            }
            drawPath(arrow, AppColors.Accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.width(4.dp))
        Text("Volver", color = AppColors.Accent)
    }
}

/** Encabezado de las pantallas secundarias: volver, logo, título y descripción. */
@Composable
fun AuthHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        BackLink(onBack)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AquaGoLogo(40.dp)
            Spacer(Modifier.width(12.dp))
            Text("AquaGo", color = AppColors.Accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = AppColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Título pequeño dentro de una tarjeta del formulario. */
@Composable
fun FormSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
}

/** Campo de texto con el estilo de AquaGo. */
@Composable
fun AquaGoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = authFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
        supportingText = supportingText?.let { { Text(it, color = AppColors.TextMuted) } },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Campo de contraseña con el botón Mostrar / Ocultar. */
@Composable
fun AquaGoPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = authFieldColors(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "Ocultar" else "Mostrar", color = AppColors.Accent)
            }
        },
        supportingText = supportingText?.let { { Text(it, color = AppColors.TextMuted) } },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Botón principal, igual al de "Iniciar sesión". */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Accent,
            contentColor = AppColors.OnAccent
        ),
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/** Mensaje de error del formulario (no muestra nada si es null). */
@Composable
fun FormError(message: String?) {
    if (message != null) {
        Spacer(Modifier.height(10.dp))
        Text(message, color = AppColors.Red, style = MaterialTheme.typography.bodySmall)
    }
}

/** Aviso de que la pantalla todavía es demostrativa. */
@Composable
fun DemoNote(text: String = "Pantalla demostrativa: por ahora los datos no se envían al servidor.") {
    Text(
        text,
        color = AppColors.TextMuted,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Barra de avance "Paso 1 de 3". */
@Composable
fun StepIndicator(current: Int, total: Int) {
    Column(Modifier.fillMaxWidth()) {
        Text("Paso $current de $total", color = AppColors.TextMuted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { index ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50))
                        .background(if (index < current) AppColors.Accent else AppColors.Border)
                )
            }
        }
    }
}

/** Pantalla de confirmación con una palomita verde y un botón para regresar al login. */
@Composable
fun SuccessMessage(
    title: String,
    message: String,
    buttonText: String,
    onClick: () -> Unit,
    extra: @Composable () -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(AppColors.Green.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(38.dp)) {
                val check = Path().apply {
                    moveTo(size.width * 0.14f, size.height * 0.54f)
                    lineTo(size.width * 0.41f, size.height * 0.8f)
                    lineTo(size.width * 0.88f, size.height * 0.24f)
                }
                drawPath(check, AppColors.Green, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, color = AppColors.TextMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        extra()
        Spacer(Modifier.height(28.dp))
        PrimaryButton(buttonText, onClick = onClick)
        Spacer(Modifier.height(16.dp))
        DemoNote()
    }
}
