package com.logue.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * Pantalla del REPARTIDOR (rol OPERADOR): «Mi ruta de hoy».
 * Las entregas llegan ya ordenadas por el servidor (orden que puso el delegado y luego la hora).
 * Cada entrega se marca En camino / Entregada (con litros) / No entregada (con observación).
 * Sin señal, el cambio se guarda en el teléfono y se sube solo después (ver Offline.kt).
 *
 * El backend no guarda coordenadas, así que en lugar de dibujar un mapa inventado se ofrece
 * «Cómo llegar», que abre la dirección en la app de mapas del teléfono.
 */

/** Zona de servicio del proyecto: se agrega a la búsqueda de «Cómo llegar». */
private const val ZONA_SERVICIO = "Santiago Tulantepec, Hidalgo"

private sealed interface DialogoEntrega {
    val entrega: Solicitud
    data class Entregada(override val entrega: Solicitud) : DialogoEntrega
    data class NoEntregada(override val entrega: Solicitud) : DialogoEntrega
}

@Composable
fun OperadorScreen(
    session: LoginResponse,
    onLogout: () -> Unit,
    onPerfil: () -> Unit = {},
    onSessionExpired: () -> Unit = onLogout
) {
    var recarga by remember { mutableStateOf(0) }
    var carga by remember { mutableStateOf<CargaOffline?>(null) }
    var aviso by remember { mutableStateOf<AvisoPantalla?>(null) }
    var dialogo by remember { mutableStateOf<DialogoEntrega?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session.token, recarga) {
        if (carga !is CargaOffline.Listo) carga = null
        val r = Offline.cargarRuta(session)
        if (r == CargaOffline.SesionVencida) onSessionExpired() else carga = r
        (r as? CargaOffline.Listo)?.datos?.aviso?.let { aviso = AvisoPantalla(it, AppColors.Red) }
    }

    fun guardar(id: String, cambio: CambioEntrega) {
        trabajando = true
        scope.launch {
            when (val r = Offline.cerrarEntrega(session, id, cambio)) {
                is Accion.Hecha -> aviso = r.comoAviso()
                is Accion.Error -> aviso = AvisoPantalla(r.mensaje, AppColors.Red)
                Accion.SesionVencida -> onSessionExpired()
            }
            trabajando = false
            recarga++
        }
    }

    val datos = (carga as? CargaOffline.Listo)?.datos
    val ruta = datos?.lista.orEmpty()
    val terminadas = ruta.count { esFinal(it.estado) }
    val siguiente = ruta.firstOrNull { !esFinal(it.estado) }?.id

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppHeader(
            saludo = "Mi ruta de hoy",
            nombre = session.username,
            rol = "Repartidor · ${formatDia(Plataforma.fechaLocal(0))}",
            acciones = listOf(
                AccionMenu("Mi perfil", Icono.PERFIL, onPerfil),
                AccionMenu("Actualizar ruta", Icono.ACTUALIZAR, { recarga++ }),
                AccionMenu("Cerrar sesión", Icono.SALIR, onLogout, peligrosa = true)
            )
        ) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContadorChico(ruta.size.toString(), "Entregas", Color.White, Modifier.weight(1f))
                ContadorChico((ruta.size - terminadas).toString(), "Por hacer", AppColors.Yellow, Modifier.weight(1f))
                ContadorChico(ruta.count { it.estado == EstadoSolicitud.ENTREGADA }.toString(), "Entregadas", Color(0xFF7BE0AE), Modifier.weight(1f))
            }
            if (ruta.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { terminadas.toFloat() / ruta.size },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
                Spacer(Modifier.height(6.dp))
                Text("$terminadas de ${ruta.size} terminadas", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            aviso?.let { AvisoAccion(it.texto, it.color) { aviso = null } }
            if (datos != null) AvisoSinConexion(datos.desdeCopia, datos.pendientes) { recarga++ }

            TituloSeccion("Paradas", "En el orden en que debes visitarlas") {
                TextButton(onClick = { recarga++ }) {
                    AppIcon(Icono.ACTUALIZAR, AppColors.Accent, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Actualizar", color = AppColors.Accent)
                }
            }

            when (val c = carga) {
                null -> EstadoCargando("Cargando tu ruta...")
                is CargaOffline.Fallo -> EstadoError(c.mensaje) { recarga++ }
                CargaOffline.SesionVencida -> EstadoCargando()
                is CargaOffline.Listo -> if (ruta.isEmpty()) {
                    EstadoVacio(Icono.CAMION, "Sin entregas para hoy", "Cuando el delegado te asigne pipas aparecerán aquí, en orden.")
                } else {
                    ruta.forEachIndexed { i, e ->
                        ParadaCard(
                            numero = e.orden ?: (i + 1),
                            e = e,
                            esSiguiente = e.id == siguiente,
                            enabled = !trabajando,
                            onComoLlegar = { Plataforma.abrirMapa(listOf(e.domicilio, e.colonia, ZONA_SERVICIO).filter { it.isNotBlank() }.joinToString(", ")) },
                            onEnCamino = { guardar(e.id, CambioEntrega(EstadoSolicitud.EN_CAMINO)) },
                            onEntregada = { dialogo = DialogoEntrega.Entregada(e) },
                            onNoEntregada = { dialogo = DialogoEntrega.NoEntregada(e) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    when (val d = dialogo) {
        is DialogoEntrega.Entregada -> {
            var litros by remember(d) { mutableStateOf(d.entrega.capacidadLitros.toString()) }
            var observacion by remember(d) { mutableStateOf("") }
            var error by remember(d) { mutableStateOf<String?>(null) }
            DialogoConfirmacion(
                titulo = "Confirmar entrega",
                texto = "${d.entrega.domicilio}, ${d.entrega.colonia}",
                confirmar = "Registrar entrega",
                onCerrar = { dialogo = null },
                onConfirmar = {
                    val l = litros.trim().toIntOrNull()
                    if (l == null || l !in 0..99_999) error = "Escribe los litros con números"
                    else {
                        dialogo = null
                        guardar(d.entrega.id, CambioEntrega(EstadoSolicitud.ENTREGADA, litrosEntregados = l, observacion = observacion.trim().ifBlank { null }))
                    }
                }
            ) {
                OutlinedTextField(
                    value = litros,
                    onValueChange = { litros = it.filter(Char::isDigit).take(5); error = null },
                    label = { Text("Litros entregados") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = authFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Pedidos: ${formatLitros(d.entrega.capacidadLitros)} L", color = AppColors.TextMuted) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = observacion,
                    onValueChange = { observacion = it.take(200) },
                    label = { Text("Observación (opcional)") },
                    shape = RoundedCornerShape(14.dp),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = AppColors.Red, style = MaterialTheme.typography.bodySmall) }
            }
        }
        is DialogoEntrega.NoEntregada -> {
            val motivos = listOf("No se encontró a nadie", "No se encontró el domicilio", "No hubo paso a la calle")
            var observacion by remember(d) { mutableStateOf(motivos.first()) }
            DialogoConfirmacion(
                titulo = "No se pudo entregar",
                texto = "Elige o escribe el motivo. El ciudadano lo verá en su solicitud.",
                confirmar = "Guardar",
                peligroso = true,
                onCerrar = { dialogo = null },
                onConfirmar = {
                    dialogo = null
                    guardar(d.entrega.id, CambioEntrega(EstadoSolicitud.NO_ENTREGADA, observacion = observacion.trim().ifBlank { null }))
                }
            ) {
                motivos.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { observacion = m }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = observacion == m, onClick = { observacion = m }, colors = RadioButtonDefaults.colors(selectedColor = AppColors.Accent))
                        Text(m)
                    }
                }
                OutlinedTextField(
                    value = observacion,
                    onValueChange = { observacion = it.take(200) },
                    label = { Text("Motivo") },
                    shape = RoundedCornerShape(14.dp),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        null -> Unit
    }
}

@Composable
private fun ParadaCard(
    numero: Int,
    e: Solicitud,
    esSiguiente: Boolean,
    enabled: Boolean,
    onComoLlegar: () -> Unit,
    onEnCamino: () -> Unit,
    onEntregada: () -> Unit,
    onNoEntregada: () -> Unit
) {
    val terminada = esFinal(e.estado)
    Tarjeta(borde = if (esSiguiente) AppColors.Accent else AppColors.Border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(if (terminada) colorEstado(e.estado) else AppColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                if (e.estado == EstadoSolicitud.ENTREGADA) AppIcon(Icono.CHECK, Color.White, 18.dp)
                else Text("$numero", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${e.hora} h", color = AppColors.Accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (esSiguiente) Text("Siguiente parada", color = AppColors.AccentLight, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            StatusChip(etiquetaEstado(e.estado), colorEstado(e.estado))
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow(Icono.UBICACION, listOf(e.domicilio, e.colonia).filter { it.isNotBlank() }.joinToString(", "), fuerte = true)
            e.referencia?.takeIf { it.isNotBlank() }?.let { InfoRow(Icono.NOTA, "Referencia: $it") }
            InfoRow(Icono.GOTA, "${formatLitros(e.capacidadLitros)} L" + (e.litrosEntregados?.takeIf { e.estado == EstadoSolicitud.ENTREGADA }?.let { " · entregados ${formatLitros(it)} L" } ?: ""), fuerte = true)
            InfoRow(Icono.PERSONA, "Ciudadano: ${e.username}")
            e.notas?.takeIf { it.isNotBlank() }?.let { InfoRow(Icono.NOTA, "Notas: $it") }
            if (e.estado == EstadoSolicitud.NO_ENTREGADA || (e.estado == EstadoSolicitud.ENTREGADA && e.observacion != null)) {
                e.observacion?.let { InfoRow(Icono.ALERTA, it, color = if (e.estado == EstadoSolicitud.NO_ENTREGADA) AppColors.Red else AppColors.TextMuted) }
            }
            if (e.estado == EstadoSolicitud.CANCELADA) InfoRow(Icono.ALERTA, "El ciudadano canceló esta solicitud", color = AppColors.Red)
        }
        if (e.pendienteSync) {
            Spacer(Modifier.height(10.dp))
            StatusChip("Pendiente de subir", AppColors.Yellow)
        }
        if (!terminada) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario("Cómo llegar", onComoLlegar, Modifier.weight(1f), icono = Icono.MAPA)
                if (e.estado != EstadoSolicitud.EN_CAMINO) {
                    BotonColor("En camino", AppColors.Blue, onEnCamino, Modifier.weight(1f), icono = Icono.CAMION, enabled = enabled)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonColor("Entregada", AppColors.Green, onEntregada, Modifier.weight(1f), icono = Icono.CHECK, enabled = enabled)
                BotonSecundario("No entregada", onNoEntregada, Modifier.weight(1f), color = AppColors.Red, enabled = enabled)
            }
        }
    }
}

/** En el teléfono, el delegado no tiene app: su panel es la página web (requerimiento del proyecto). */
@Composable
fun AdminSoloWebScreen(session: LoginResponse, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AquaGoLogo(80.dp)
        Spacer(Modifier.height(20.dp))
        Text("Hola, ${session.username}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "La app del teléfono es para ciudadanos y repartidores. Como delegado, administra las solicitudes, rutas y usuarios desde el panel web de AquaGo en tu computadora.",
            color = AppColors.TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Cerrar sesión", onClick = onLogout)
    }
}
