package com.logue.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * Pantallas del CIUDADANO (integración AquaGo + rediseño):
 * - «Mis solicitudes»: encabezado con saludo y menú de cuenta, botón grande «Pedir pipa», contadores
 *   y tarjetas de cada solicitud (litros, estatus con color, domicilio, referencia, fecha) con «Cancelar».
 * - «Pedir pipa»: formulario guiado (cantidad, día y hora, domicilio) que funciona también sin conexión.
 *   El día se elige entre 3 y 7 días después de hoy y la hora entre las que siguen libres (GET /horarios).
 */

/** Aviso que una pantalla deja para la siguiente (por ejemplo, «Tu solicitud fue enviada»). */
data class AvisoPantalla(val texto: String, val color: Color)

fun Accion.Hecha.comoAviso() = AvisoPantalla(mensaje, if (sinConexion) AppColors.Yellow else AppColors.Green)

/** Estados que ya no cambian (van al final de la lista). */
fun esFinal(estado: String) =
    estado == EstadoSolicitud.ENTREGADA || estado == EstadoSolicitud.CANCELADA || estado == EstadoSolicitud.NO_ENTREGADA

/** Orden de «Mis solicitudes»: primero las activas (la más próxima arriba), después el historial (la más reciente arriba). */
fun ordenarSolicitudes(lista: List<Solicitud>): List<Solicitud> {
    val (finales, activas) = lista.partition { esFinal(it.estado) }
    return activas.sortedWith(compareBy({ it.dia }, { it.hora })) + finales.sortedWith(compareByDescending<Solicitud> { it.dia }.thenByDescending { it.hora })
}

@Composable
fun CiudadanoScreen(
    session: LoginResponse,
    onLogout: () -> Unit,
    onPagoPredial: () -> Unit,
    onHistorialPagos: () -> Unit = {},
    onPerfil: () -> Unit = {},
    onSessionExpired: () -> Unit = onLogout,
    onPedirPipa: () -> Unit = {},
    avisoInicial: AvisoPantalla? = null,
    onAvisoVisto: () -> Unit = {}
) {
    var recarga by remember { mutableStateOf(0) }
    var carga by remember { mutableStateOf<CargaOffline?>(null) }
    var aviso by remember { mutableStateOf(avisoInicial) }
    var porCancelar by remember { mutableStateOf<Solicitud?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf(Offline.perfilGuardado(session.username)?.nombre?.takeIf { it.isNotBlank() } ?: session.username) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(avisoInicial) { if (avisoInicial != null) { aviso = avisoInicial; onAvisoVisto() } }
    LaunchedEffect(session.token) {
        // El perfil da el nombre del saludo y el domicilio de «Pedir pipa» (se guarda para usarlo sin red)
        (obtenerPerfil(session.token) as? ApiResult.Ok)?.valor?.let {
            Offline.guardarPerfil(it)
            if (it.nombre.isNotBlank()) nombre = it.nombre
        }
    }
    LaunchedEffect(session.token, recarga) {
        if (carga !is CargaOffline.Listo) carga = null
        val r = Offline.cargarSolicitudes(session)
        if (r == CargaOffline.SesionVencida) onSessionExpired() else carga = r
        (r as? CargaOffline.Listo)?.datos?.aviso?.let { aviso = AvisoPantalla(it, AppColors.Red) }
    }

    val datos = (carga as? CargaOffline.Listo)?.datos
    val lista = ordenarSolicitudes(datos?.lista.orEmpty())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppHeader(
            saludo = "Hola,",
            nombre = nombre,
            rol = "Ciudadano · Las Camelinas",
            acciones = listOf(
                AccionMenu("Mi perfil", Icono.PERFIL, onPerfil),
                AccionMenu("Registrar pago predial", Icono.RECIBO, onPagoPredial),
                AccionMenu("Mis pagos prediales", Icono.NOTA, onHistorialPagos),
                AccionMenu("Cerrar sesión", Icono.SALIR, onLogout, peligrosa = true)
            )
        ) {
            Spacer(Modifier.height(16.dp))
            val conteo = datos?.lista.orEmpty()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContadorChico(conteo.count { esPendiente(it.estado) }.toString(), "Pendientes", AppColors.Yellow, Modifier.weight(1f))
                ContadorChico(conteo.count { it.estado == EstadoSolicitud.EN_CAMINO }.toString(), "En camino", Color(0xFF9FD3FF), Modifier.weight(1f))
                ContadorChico(conteo.count { it.estado == EstadoSolicitud.ENTREGADA }.toString(), "Entregadas", Color(0xFF7BE0AE), Modifier.weight(1f))
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BotonDestacado("Pedir pipa", "Solicita agua en 3 pasos, aun sin señal", Icono.GOTA, onPedirPipa)

            aviso?.let { AvisoAccion(it.texto, it.color) { aviso = null } }
            if (datos != null) AvisoSinConexion(datos.desdeCopia, datos.pendientes) { recarga++ }

            TituloSeccion(
                "Mis solicitudes",
                datos?.let { if (it.lista.isEmpty()) null else "${it.lista.size} en total" }
            ) {
                TextButton(onClick = { recarga++ }) {
                    AppIcon(Icono.ACTUALIZAR, AppColors.Accent, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Actualizar", color = AppColors.Accent)
                }
            }

            when (val c = carga) {
                null -> EstadoCargando("Buscando tus solicitudes...")
                is CargaOffline.Fallo -> EstadoError(c.mensaje) { recarga++ }
                CargaOffline.SesionVencida -> EstadoCargando()
                is CargaOffline.Listo -> if (lista.isEmpty()) {
                    EstadoVacio(Icono.GOTA, "Aún no tienes solicitudes", "Cuando pidas una pipa aparecerá aquí con su estatus.") {
                        BotonColor("Pedir mi primera pipa", AppColors.Accent, onPedirPipa, icono = Icono.MAS)
                    }
                } else {
                    lista.forEach { s ->
                        SolicitudCard(s, enabled = !trabajando, onCancelar = { porCancelar = s })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    porCancelar?.let { s ->
        DialogoConfirmacion(
            titulo = "¿Cancelar esta solicitud?",
            texto = "Pipa de ${formatLitros(s.capacidadLitros)} L para el ${formatDia(s.dia)}. Si la cancelas, el delegado ya no la programará.",
            confirmar = "Sí, cancelar",
            cancelar = "No, volver",
            peligroso = true,
            onCerrar = { porCancelar = null },
            onConfirmar = {
                porCancelar = null
                trabajando = true
                scope.launch {
                    when (val r = Offline.cancelar(session, s)) {
                        is Accion.Hecha -> aviso = r.comoAviso()
                        is Accion.Error -> aviso = AvisoPantalla(r.mensaje, AppColors.Red)
                        Accion.SesionVencida -> onSessionExpired()
                    }
                    trabajando = false
                    recarga++
                }
            }
        )
    }
}

/** Tarjeta de una solicitud: litros destacados, chip de estatus, domicilio, referencia y fecha. */
@Composable
fun SolicitudCard(s: Solicitud, enabled: Boolean = true, onCancelar: (() -> Unit)? = null) {
    val color = colorEstado(s.estado)
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
                AppIcon(Icono.GOTA, AppColors.Accent, 24.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatLitros(s.capacidadLitros), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = AppColors.TextPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("litros", color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text("Pipa de agua", color = AppColors.TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            StatusChip(etiquetaEstado(s.estado), color)
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = AppColors.Border)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow(Icono.UBICACION, listOf(s.domicilio, s.colonia).filter { it.isNotBlank() }.joinToString(", "), fuerte = true)
            s.referencia?.takeIf { it.isNotBlank() }?.let { InfoRow(Icono.NOTA, "Referencia: $it") }
            InfoRow(Icono.CALENDARIO, "${formatDia(s.dia)} · ${s.hora} h")
            s.repartidor?.takeIf { it.isNotBlank() && !esFinal(s.estado) }?.let { InfoRow(Icono.CAMION, "Repartidor: $it") }
            if (s.estado == EstadoSolicitud.ENTREGADA && s.litrosEntregados != null) {
                InfoRow(Icono.CHECK, "Recibiste ${formatLitros(s.litrosEntregados)} L", color = AppColors.Green)
            }
            if (s.estado == EstadoSolicitud.NO_ENTREGADA) {
                InfoRow(Icono.ALERTA, s.observacion?.let { "No se entregó: $it" } ?: "No se pudo entregar", color = AppColors.Red)
            }
        }
        if (s.pendienteSync) {
            Spacer(Modifier.height(10.dp))
            StatusChip("Pendiente de subir", AppColors.Yellow)
        }
        if (onCancelar != null && esCancelable(s.estado)) {
            Spacer(Modifier.height(12.dp))
            BotonSecundario("Cancelar solicitud", onCancelar, Modifier.fillMaxWidth(), color = AppColors.Red, enabled = enabled)
        }
    }
}

// =================================================================================================
//  PEDIR PIPA
// =================================================================================================

@Composable
fun PedirPipaScreen(
    session: LoginResponse,
    onBack: () -> Unit,
    onEnviada: (AvisoPantalla) -> Unit,
    onSessionExpired: () -> Unit
) {
    val perfil = remember { Offline.perfilGuardado(session.username) }
    var litros by remember { mutableStateOf<Int?>(null) }
    var agenda by remember { mutableStateOf<Agenda?>(null) }
    var agendaSinRed by remember { mutableStateOf(false) }
    var dia by remember { mutableStateOf<String?>(null) }
    var hora by remember { mutableStateOf<String?>(null) }
    var domicilio by remember { mutableStateOf(perfil?.calle.orEmpty()) }
    var colonia by remember { mutableStateOf(perfil?.colonia?.ifBlank { null } ?: "Las Camelinas") }
    var referencia by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf(false) }
    var enviando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        when (val r = obtenerAgenda(session.token)) {
            is ApiResult.Ok -> agenda = r.valor
            ApiResult.SesionVencida -> onSessionExpired()
            else -> { agenda = agendaSinConexion(); agendaSinRed = true }
        }
    }
    val diaElegido = agenda?.dias?.firstOrNull { it.dia == dia }

    fun validar(): String? = when {
        litros == null -> "Elige cuántos litros necesitas"
        dia == null -> "Elige el día de entrega"
        hora == null -> "Elige la hora de entrega"
        domicilio.isBlank() -> "Escribe la calle y el número de tu casa"
        domicilio.length > 120 -> "La calle es demasiado larga"
        colonia.length > 120 -> "La colonia es demasiado larga"
        referencia.length > 100 -> "La referencia es demasiado larga (máximo 100 letras)"
        notas.length > 200 -> "Las notas son demasiado largas (máximo 200 letras)"
        else -> null
    }

    Column(Modifier.fillMaxSize()) {
        TopBarSecundaria("Pedir pipa", "Llena los 3 pasos y envía tu solicitud", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Paso(1, "¿Cuánta agua necesitas?") {
                OPCIONES_LITROS.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        fila.forEach { l ->
                            OpcionLitros(l, seleccionada = litros == l, Modifier.weight(1f)) { litros = l; error = null }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Paso(2, "¿Qué día y a qué hora?") {
                val a = agenda
                if (a == null) {
                    Text("Cargando los horarios disponibles...", color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        "Elige un día de los próximos ${a.minimoDias} a ${a.maximoDias} días (mínimo ${a.minimoDias} días de anticipación).",
                        color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    a.dias.chunked(3).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fila.forEach { d ->
                                OpcionDia(nombreDiaSemana(d.dia), formatDia(d.dia), dia == d.dia, Modifier.weight(1f), libres = d.libres.size) {
                                    dia = d.dia
                                    if (hora != null && hora !in d.libres) hora = null
                                    error = null
                                }
                            }
                            repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (diaElegido != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("Hora de entrega", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        a.horarios.ifEmpty { HORARIOS_ENTREGA }.chunked(4).forEach { fila ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                fila.forEach { h ->
                                    val libre = h in diaElegido.libres
                                    OpcionHora(h, seleccionada = hora == h, libre = libre, Modifier.weight(1f)) { hora = h; error = null }
                                }
                                repeat(4 - fila.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            if (agendaSinRed) "Sin conexión: no sabemos qué horas ya apartaron otros vecinos. Si la que elijas ya está ocupada, te avisaremos al enviarla."
                            else "Las horas en gris ya las apartó otro ciudadano.",
                            color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Paso(3, "¿Dónde la dejamos?") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AquaGoTextField(domicilio, { domicilio = it; error = null }, "Calle y número", capitalization = KeyboardCapitalization.Sentences)
                    AquaGoTextField(colonia, { colonia = it }, "Colonia", capitalization = KeyboardCapitalization.Words)
                    AquaGoTextField(referencia, { referencia = it }, "Referencia (opcional)", capitalization = KeyboardCapitalization.Sentences, supportingText = "Ejemplo: portón verde, junto a la tienda")
                    AquaGoTextField(notas, { notas = it }, "Notas para el repartidor (opcional)", capitalization = KeyboardCapitalization.Sentences)
                }
            }
            FormError(error)
        }
        Column(Modifier.fillMaxWidth().background(AppColors.Surface).padding(16.dp)) {
            if (litros != null) {
                Text(
                    listOfNotNull(
                        "${formatLitros(litros!!)} L",
                        dia?.let { "${nombreDiaSemana(it)} ${formatDia(it)}" },
                        hora?.let { "$it h" }
                    ).joinToString(" · "),
                    color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }
            PrimaryButton(if (enviando) "Enviando..." else "Revisar y enviar", enabled = !enviando) {
                error = validar()
                if (error == null) confirmar = true
            }
        }
    }

    if (confirmar) {
        DialogoConfirmacion(
            titulo = "¿Enviar tu solicitud?",
            texto = "Revisa que los datos sean correctos.",
            confirmar = "Sí, enviar",
            cancelar = "Corregir",
            onCerrar = { confirmar = false },
            onConfirmar = {
                confirmar = false
                enviando = true
                scope.launch {
                    val r = Offline.pedirPipa(
                        session,
                        NuevaSolicitud(
                            dia = dia!!,
                            hora = hora,
                            capacidadLitros = litros!!,
                            domicilio = domicilio.trim(),
                            colonia = colonia.trim().ifBlank { null },
                            referencia = referencia.trim().ifBlank { null },
                            notas = notas.trim().ifBlank { null }
                        )
                    )
                    enviando = false
                    when (r) {
                        is Accion.Hecha -> onEnviada(r.comoAviso())
                        is Accion.Error -> error = r.mensaje
                        Accion.SesionVencida -> onSessionExpired()
                    }
                }
            }
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.SurfaceAlt).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoRow(Icono.GOTA, "${formatLitros(litros ?: 0)} litros", fuerte = true)
                InfoRow(Icono.CALENDARIO, "${nombreDiaSemana(dia.orEmpty())} ${formatDia(dia.orEmpty())} · ${hora.orEmpty()} h")
                InfoRow(Icono.UBICACION, listOf(domicilio.trim(), colonia.trim()).filter { it.isNotBlank() }.joinToString(", "))
                if (referencia.isNotBlank()) InfoRow(Icono.NOTA, referencia.trim())
            }
        }
    }
}

@Composable
private fun Paso(numero: Int, titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(AppColors.Accent), contentAlignment = Alignment.Center) {
                Text("$numero", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(10.dp))
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        contenido()
    }
}

@Composable
private fun OpcionLitros(litros: Int, seleccionada: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val borde = if (seleccionada) AppColors.Accent else AppColors.Border
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (seleccionada) AppColors.AccentSoft else AppColors.Surface)
            .border(if (seleccionada) 2.dp else 1.dp, borde, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(Icono.GOTA, if (seleccionada) AppColors.Accent else AppColors.AccentLight, 22.dp)
        Spacer(Modifier.height(6.dp))
        Text(formatLitros(litros), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = if (seleccionada) AppColors.Accent else AppColors.TextPrimary)
        Text("litros", color = AppColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OpcionDia(texto: String, fecha: String, seleccionada: Boolean, modifier: Modifier, libres: Int? = null, onClick: () -> Unit) {
    val lleno = libres == 0
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (seleccionada) AppColors.Accent else if (lleno) AppColors.SurfaceAlt else AppColors.Surface)
            .border(1.dp, if (seleccionada) AppColors.Accent else AppColors.Border, RoundedCornerShape(14.dp))
            .clickable(enabled = !lleno, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(texto, color = if (seleccionada) Color.White else AppColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(fecha.take(5), color = if (seleccionada) Color.White.copy(alpha = 0.85f) else AppColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        if (libres != null) {
            Text(
                if (lleno) "Sin horas" else "$libres libres",
                color = if (seleccionada) Color.White.copy(alpha = 0.85f) else if (lleno) AppColors.Red else AppColors.Green,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Botón de una hora de entrega: azul si está elegida, gris y sin acción si ya la apartó otro ciudadano. */
@Composable
private fun OpcionHora(hora: String, seleccionada: Boolean, libre: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    seleccionada -> AppColors.Accent
                    !libre -> AppColors.SurfaceAlt
                    else -> AppColors.Surface
                }
            )
            .border(1.dp, if (seleccionada) AppColors.Accent else AppColors.Border, RoundedCornerShape(12.dp))
            .clickable(enabled = libre, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (libre) hora else "$hora\nOcupada",
            color = when {
                seleccionada -> Color.White
                !libre -> AppColors.TextMuted
                else -> AppColors.TextPrimary
            },
            fontWeight = if (seleccionada) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
