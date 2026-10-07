package com.logue.frontend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// NOTA: los datos de las pantallas (estadísticas y solicitudes) son de DEMOSTRACIÓN.
// El backend actual solo implementa el login; las listas se conectarían a nuevos endpoints.

/** Tarjeta de estadística (número grande + etiqueta). */
data class Stat(val value: String, val label: String, val color: Color)
/** Renglón de la lista (hora, título, subtítulo y estado con color). */
data class Entry(val time: String, val title: String, val subtitle: String, val status: String, val color: Color)

/** Pantalla genérica reutilizada por los tres roles: cambia solo el contenido que recibe. */
@Composable
fun RoleScreen(
    title: String,
    roleLabel: String,
    name: String,
    stats: List<Stat>,
    sectionTitle: String,
    entries: List<Entry>,
    onLogout: () -> Unit
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

        stats.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { StatCard(it.value, it.label, it.color, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Text(sectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        entries.forEach { e ->
            AppCard {
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
    }
}

@Composable
fun CiudadanoScreen(session: LoginResponse, onLogout: () -> Unit) = RoleScreen(
    title = "Mis solicitudes",
    roleLabel = "Ciudadano",
    name = session.username,
    stats = listOf(
        Stat("1", "En camino", AppColors.Blue),
        Stat("1", "Pendientes", AppColors.Yellow),
        Stat("3", "Entregadas", AppColors.Green)
    ),
    sectionTitle = "Solicitudes de agua",
    entries = listOf(
        Entry("10:00", "Pipa de 10,000 L", "Calle Hidalgo #12", "En camino", AppColors.Blue),
        Entry("16:30", "Pipa de 5,000 L", "Calle Hidalgo #12", "Pendiente", AppColors.Yellow),
        Entry("08:00", "Pipa de 10,000 L", "Calle Hidalgo #12", "Entregada", AppColors.Green)
    ),
    onLogout = onLogout
)

@Composable
fun OperadorScreen(session: LoginResponse, onLogout: () -> Unit) = RoleScreen(
    title = "Mis entregas de hoy",
    roleLabel = "Operador",
    name = session.username,
    stats = listOf(
        Stat("3", "Entregas hoy", AppColors.Accent),
        Stat("1", "Completadas", AppColors.Green)
    ),
    sectionTitle = "Ruta del día",
    entries = listOf(
        Entry("09:00", "Colonia Centro", "10,000 L", "Completada", AppColors.Green),
        Entry("11:30", "Colonia Reforma", "5,000 L", "Siguiente", AppColors.Blue),
        Entry("14:00", "Barrio Alto", "10,000 L", "Pendiente", AppColors.Yellow)
    ),
    onLogout = onLogout
)

@Composable
fun AdminScreen(session: LoginResponse, onLogout: () -> Unit) = RoleScreen(
    title = "Panel de solicitudes",
    roleLabel = "Administrador",
    name = session.username,
    stats = listOf(
        Stat("2", "Pendientes", AppColors.Yellow),
        Stat("2", "En camino", AppColors.Blue),
        Stat("5", "Entregadas", AppColors.Green),
        Stat("1", "Canceladas", AppColors.Red)
    ),
    sectionTitle = "Solicitudes recientes",
    entries = listOf(
        Entry("09:00", "Colonia Centro", "Operador: Luis", "Entregada", AppColors.Green),
        Entry("11:30", "Colonia Reforma", "Operador: Luis", "En camino", AppColors.Blue),
        Entry("12:30", "Barrio Alto", "Sin asignar", "Pendiente", AppColors.Yellow)
    ),
    onLogout = onLogout
)

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
