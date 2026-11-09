package com.logue.frontend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Los datos de las tres pantallas salen del servidor: GET /solicitudes (ciudadano y delegado) y
// GET /entregas (repartidor). Cómo se convierten en tarjetas y renglones está en SolicitudesVista.kt.

/** Tarjeta de estadística (número grande + etiqueta). */
data class Stat(val value: String, val label: String, val color: Color)
/** Renglón de la lista (hora, título, subtítulo y estado con color). */
data class Entry(val time: String, val title: String, val subtitle: String, val status: String, val color: Color)

/** Estado de una carga de datos del servidor. */
sealed interface Carga<out T> {
    data object Cargando : Carga<Nothing>
    data class Lista<T>(val datos: T) : Carga<T>
    data class Fallo(val mensaje: String) : Carga<Nothing>
}

/**
 * Pide datos al servidor con el token de la sesión y los entrega como [Carga]. Cambiar [recarga]
 * vuelve a pedirlos (botón «Reintentar»). Si el servidor responde 401 se llama a [onSessionExpired].
 */
@Composable
fun <T> cargarDatos(
    session: LoginResponse,
    recarga: Int,
    onSessionExpired: () -> Unit,
    llamada: suspend (String) -> ApiResult<T>
): Carga<T> {
    val estado = produceState<Carga<T>>(Carga.Cargando, session.token, recarga) {
        value = Carga.Cargando
        value = when (val result = llamada(session.token)) {
            is ApiResult.Ok -> Carga.Lista(result.valor)
            ApiResult.SesionVencida -> {
                onSessionExpired()
                Carga.Cargando
            }
            else -> Carga.Fallo(result.mensajeDeError() ?: "No se pudo cargar la información")
        }
    }
    return estado.value
}

/** La lista cargada, o una lista vacía mientras carga o si falló. */
private fun <T> Carga<List<T>>.datosOVacio(): List<T> = when (this) {
    is Carga.Lista -> datos
    else -> emptyList()
}

/** Ancho a partir del cual la pantalla se acomoda para computadora (ADM-07): tarjetas en una fila y renglones en dos columnas. */
private val ANCHO_COMPUTADORA = 640.dp

/** Pantalla genérica reutilizada por los tres roles: cambia solo el contenido que recibe. */
@Composable
fun RoleScreen(
    title: String,
    roleLabel: String,
    name: String,
    stats: List<Stat>,
    sectionTitle: String,
    entries: List<Entry>,
    onLogout: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit = {},
    /** Mientras `true` se muestra «Cargando...» en lugar de las tarjetas y la lista. */
    loading: Boolean = false,
    /** Si no es `null` se muestra este error con el botón «Reintentar». */
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    emptyText: String = "Todavía no hay registros"
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AquaGo", color = AppColors.Accent, style = MaterialTheme.typography.labelLarge)
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onLogout,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Accent,
                    contentColor = AppColors.OnAccent
                )
            ) { Text("Cerrar sesión") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(roleLabel, AppColors.Accent)
            Spacer(Modifier.width(10.dp))
            Text(name, color = AppColors.TextMuted)
        }

        actions()

        when {
            loading -> Text("Cargando...", color = AppColors.TextMuted)
            errorMessage != null -> AppCard {
                Text(errorMessage, color = AppColors.Red)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Accent)
                ) { Text("Reintentar") }
            }
            else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                val ancha = maxWidth >= ANCHO_COMPUTADORA
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    stats.chunked(if (ancha) 4 else 2).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            group.forEach { StatCard(it.value, it.label, it.color, Modifier.weight(1f)) }
                            repeat((if (ancha) 4 else 2) - group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    Text(sectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                    if (entries.isEmpty()) {
                        Text(emptyText, color = AppColors.TextMuted)
                    }
                    entries.chunked(if (ancha) 2 else 1).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            group.forEach { EntryCard(it, Modifier.weight(1f)) }
                            if (ancha && group.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(e: Entry, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.time, color = AppColors.Accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(e.title, fontWeight = FontWeight.SemiBold)
                Text(e.subtitle, color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        Pill(e.status, e.color)
    }
}

@Composable
fun CiudadanoScreen(
    session: LoginResponse,
    onLogout: () -> Unit,
    onPagoPredial: () -> Unit,
    onHistorialPagos: () -> Unit = {},
    onPerfil: () -> Unit = {},
    onSessionExpired: () -> Unit = onLogout
) {
    var recarga by remember { mutableStateOf(0) }
    val carga = cargarDatos(session, recarga, onSessionExpired) { listarSolicitudes(it) }
    val solicitudes = carga.datosOVacio()
    RoleScreen(
        title = "Mis solicitudes",
        roleLabel = "Ciudadano",
        name = session.username,
        stats = contadoresCiudadano(solicitudes),
        sectionTitle = "Solicitudes de agua",
        entries = entradasCiudadano(solicitudes),
        onLogout = onLogout,
        actions = {
            PrimaryButton("Registrar pago predial", onClick = onPagoPredial)
            OutlinedButton(
                onClick = onHistorialPagos,
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            ) { Text("Mis pagos prediales", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onPerfil, modifier = Modifier.fillMaxWidth()) {
                Text("Mi perfil", color = AppColors.Accent)
            }
        },
        loading = carga is Carga.Cargando,
        errorMessage = (carga as? Carga.Fallo)?.mensaje,
        onRetry = { recarga++ },
        emptyText = "Aún no tienes solicitudes de agua"
    )
}

@Composable
fun OperadorScreen(
    session: LoginResponse,
    onLogout: () -> Unit,
    onPerfil: () -> Unit = {},
    onSessionExpired: () -> Unit = onLogout
) {
    var recarga by remember { mutableStateOf(0) }
    val carga = cargarDatos(session, recarga, onSessionExpired) { listarEntregas(it) }
    val entregas = carga.datosOVacio()
    RoleScreen(
        title = "Mis entregas de hoy",
        roleLabel = "Operador",
        name = session.username,
        stats = contadoresOperador(entregas),
        sectionTitle = "Ruta del día",
        entries = entradasOperador(entregas),
        onLogout = onLogout,
        actions = {
            TextButton(onClick = onPerfil, modifier = Modifier.fillMaxWidth()) {
                Text("Mi perfil", color = AppColors.Accent)
            }
        },
        loading = carga is Carga.Cargando,
        errorMessage = (carga as? Carga.Fallo)?.mensaje,
        onRetry = { recarga++ },
        emptyText = "No tienes entregas asignadas para hoy"
    )
}

@Composable
fun AdminScreen(
    session: LoginResponse,
    onLogout: () -> Unit,
    onComprobantes: () -> Unit = {},
    onPerfil: () -> Unit = {},
    onSessionExpired: () -> Unit = onLogout
) {
    var recarga by remember { mutableStateOf(0) }
    val carga = cargarDatos(session, recarga, onSessionExpired) { listarSolicitudes(it) }
    val solicitudes = carga.datosOVacio()
    RoleScreen(
        title = "Panel de solicitudes",
        roleLabel = "Administrador",
        name = session.username,
        stats = contadoresAdmin(solicitudes),
        sectionTitle = "Solicitudes recientes",
        entries = entradasAdmin(solicitudes),
        onLogout = onLogout,
        actions = {
            PrimaryButton("Comprobantes de pago predial", onClick = onComprobantes)
            TextButton(onClick = onPerfil, modifier = Modifier.fillMaxWidth()) {
                Text("Mi perfil", color = AppColors.Accent)
            }
        },
        loading = carga is Carga.Cargando,
        errorMessage = (carga as? Carga.Fallo)?.mensaje,
        onRetry = { recarga++ },
        emptyText = "Todavía no hay solicitudes registradas"
    )
}

/** Se muestra si el backend devuelve un rol que la app no conoce. */
@Composable
fun UnknownRoleScreen(role: String, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Rol no reconocido: $role", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onLogout,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Accent,
                contentColor = AppColors.OnAccent
            )
        ) { Text("Volver al inicio de sesión") }
    }
}
