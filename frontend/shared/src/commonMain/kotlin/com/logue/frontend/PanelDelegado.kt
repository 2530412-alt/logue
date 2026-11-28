package com.logue.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * PANEL WEB DEL DELEGADO (rol ADMIN). Solo en la página web: en el teléfono el delegado ve
 * AdminSoloWebScreen. Todo sale del backend real:
 *  - Solicitudes: GET /solicitudes, asignar / quitar repartidor / cancelar.
 *  - Rutas del día: las mismas solicitudes agrupadas por repartidor (AdminVista.kt).
 *  - Usuarios: GET /usuarios, alta (POST /usuarios) y activar / desactivar cuentas.
 *  - Reportes: calculados con las solicitudes; se pueden descargar (CSV) o mandar al correo.
 */

private enum class Seccion(val titulo: String, val icono: Icono) {
    SOLICITUDES("Solicitudes", Icono.GOTA),
    RUTAS("Rutas del día", Icono.CAMION),
    USUARIOS("Usuarios", Icono.PERSONA),
    REPORTES("Reportes", Icono.NOTA)
}

@Composable
fun PanelDelegado(
    session: LoginResponse,
    onLogout: () -> Unit,
    onPerfil: () -> Unit,
    onComprobantes: () -> Unit,
    onSessionExpired: () -> Unit
) {
    var seccion by remember { mutableStateOf(Seccion.SOLICITUDES) }
    var recarga by remember { mutableStateOf(0) }
    var solicitudes by remember { mutableStateOf<List<Solicitud>?>(null) }
    var usuarios by remember { mutableStateOf<List<Perfil>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<AvisoPantalla?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(recarga) {
        error = null
        when (val r = listarSolicitudes(session.token)) {
            is ApiResult.Ok -> solicitudes = r.valor
            ApiResult.SesionVencida -> { onSessionExpired(); return@LaunchedEffect }
            else -> error = r.mensajeDeError()
        }
        when (val r = listarUsuarios(session.token)) {
            is ApiResult.Ok -> usuarios = r.valor
            ApiResult.SesionVencida -> onSessionExpired()
            else -> if (error == null) error = r.mensajeDeError()
        }
    }

    /** Ejecuta una acción del delegado, muestra el resultado y recarga los datos. */
    fun <T> accion(textoOk: String, llamada: suspend () -> ApiResult<T>) {
        scope.launch {
            when (val r = llamada()) {
                is ApiResult.Ok -> aviso = AvisoPantalla(textoOk, AppColors.Green)
                ApiResult.SesionVencida -> onSessionExpired()
                else -> aviso = AvisoPantalla(r.mensajeDeError() ?: "No se pudo completar", AppColors.Red)
            }
            recarga++
        }
    }

    val repartidores = usuarios.filter { it.role == Roles.OPERADOR && it.estado == EstadoCuenta.ACTIVA }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ancha = maxWidth >= 900.dp
        Row(Modifier.fillMaxSize()) {
            if (ancha) {
                MenuLateral(seccion, session.username, onSeleccion = { seccion = it }, onPerfil, onComprobantes, onLogout)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(if (ancha) 28.dp else 16.dp)) {
                if (!ancha) {
                    EncabezadoAngosto(session.username, onPerfil, onComprobantes, onLogout)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Seccion.entries.forEach { s ->
                            FiltroChip(s.titulo.substringBefore(' '), seccion == s, Modifier.weight(1f)) { seccion = s }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(seccion.titulo, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Hoy es ${formatDia(Plataforma.fechaLocal(0))}", color = AppColors.TextMuted)
                    }
                    BotonSecundario("Actualizar", { recarga++ }, icono = Icono.ACTUALIZAR)
                }
                Spacer(Modifier.height(16.dp))
                aviso?.let {
                    AvisoAccion(it.texto, it.color) { aviso = null }
                    Spacer(Modifier.height(12.dp))
                }
                val lista = solicitudes
                when {
                    error != null && lista == null -> EstadoError(error!!) { recarga++ }
                    lista == null -> EstadoCargando("Cargando información del servidor...")
                    else -> when (seccion) {
                        Seccion.SOLICITUDES -> SeccionSolicitudes(lista, repartidores, ancha,
                            onAsignar = { s, a -> accion("Solicitud asignada a ${a.repartidor}") { asignarSolicitud(session.token, s.id, a) } },
                            onDesasignar = { s -> accion("Se quitó el repartidor") { desasignarSolicitud(session.token, s.id) } },
                            onCancelar = { s -> accion("Solicitud cancelada") { cancelarSolicitud(session.token, s.id) } }
                        )
                        Seccion.RUTAS -> SeccionRutas(lista, ancha)
                        Seccion.USUARIOS -> SeccionUsuarios(usuarios, ancha,
                            onEstado = { u, estado ->
                                accion(if (estado == EstadoCuenta.ACTIVA) "Cuenta de ${u.username} activada" else "Cuenta de ${u.username} desactivada") {
                                    cambiarEstadoCuenta(session.token, u.username, estado)
                                }
                            },
                            onAlta = { alta -> accion("Usuario ${alta.username} creado") { crearUsuario(session.token, alta) } }
                        )
                        Seccion.REPORTES -> SeccionReportes(lista, ancha,
                            onEnviarCorreo = { desde, hasta -> accion("Reporte enviado a tu correo") { enviarReportePorCorreo(session.token, desde, hasta) } }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// =================================================================================================
//  Navegación
// =================================================================================================

@Composable
private fun MenuLateral(
    actual: Seccion,
    usuario: String,
    onSeleccion: (Seccion) -> Unit,
    onPerfil: () -> Unit,
    onComprobantes: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        Modifier.width(250.dp).fillMaxHeight()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(AppColors.AccentDark, AppColors.Accent)))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                AppIcon(Icono.GOTA, Color.White, 20.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("AquaGo", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Panel del delegado", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(28.dp))
        Seccion.entries.forEach { s ->
            ElementoMenu(s.titulo, s.icono, s == actual) { onSeleccion(s) }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
        Spacer(Modifier.height(10.dp))
        ElementoMenu("Comprobantes de pago", Icono.RECIBO, false, onComprobantes)
        ElementoMenu("Mi perfil", Icono.PERFIL, false, onPerfil)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Text(iniciales(usuario), color = AppColors.Accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(usuario, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        ElementoMenu("Cerrar sesión", Icono.SALIR, false, onLogout)
    }
}

@Composable
private fun ElementoMenu(texto: String, icono: Icono, activo: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
            .background(if (activo) Color.White else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(icono, if (activo) AppColors.Accent else Color.White, 18.dp)
        Spacer(Modifier.width(12.dp))
        Text(texto, color = if (activo) AppColors.Accent else Color.White, fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun EncabezadoAngosto(usuario: String, onPerfil: () -> Unit, onComprobantes: () -> Unit, onLogout: () -> Unit) {
    AppHeader(
        saludo = "Panel del delegado",
        nombre = usuario,
        rol = "Delegado",
        acciones = listOf(
            AccionMenu("Mi perfil", Icono.PERFIL, onPerfil),
            AccionMenu("Comprobantes de pago", Icono.RECIBO, onComprobantes),
            AccionMenu("Cerrar sesión", Icono.SALIR, onLogout, peligrosa = true)
        )
    )
}

@Composable
private fun FiltroChip(texto: String, activo: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(if (activo) AppColors.Accent else AppColors.Surface)
            .border(1.dp, if (activo) AppColors.Accent else AppColors.Border, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, color = if (activo) Color.White else AppColors.TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Reparte [items] en filas de [columnas] tarjetas. */
@Composable
private fun <T> Rejilla(items: List<T>, columnas: Int, contenido: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(columnas).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                fila.forEach { contenido(it, Modifier.weight(1f)) }
                repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// =================================================================================================
//  Solicitudes
// =================================================================================================

@Composable
private fun SeccionSolicitudes(
    lista: List<Solicitud>,
    repartidores: List<Perfil>,
    ancha: Boolean,
    onAsignar: (Solicitud, Asignacion) -> Unit,
    onDesasignar: (Solicitud) -> Unit,
    onCancelar: (Solicitud) -> Unit
) {
    var filtro by remember { mutableStateOf(FiltroSolicitudes.POR_ASIGNAR) }
    var texto by remember { mutableStateOf("") }
    var porAsignar by remember { mutableStateOf<Solicitud?>(null) }
    var porCancelar by remember { mutableStateOf<Solicitud?>(null) }

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FiltroSolicitudes.entries.forEach { f ->
            FiltroChip("${f.etiqueta} (${lista.count { coincideFiltro(it, f) }})", filtro == f) { filtro = f }
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = texto,
        onValueChange = { texto = it },
        label = { Text("Buscar por ciudadano, calle, colonia, repartidor o folio") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = authFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(14.dp))

    val visibles = filtrarSolicitudes(lista, filtro, texto)
    if (visibles.isEmpty()) {
        EstadoVacio(Icono.GOTA, "No hay solicitudes aquí", if (texto.isBlank()) "Prueba con otro filtro." else "Nada coincide con «$texto».")
    } else {
        Rejilla(visibles, if (ancha) 2 else 1) { s, mod ->
            SolicitudAdminCard(s, mod,
                onAsignar = { porAsignar = s },
                onDesasignar = { onDesasignar(s) },
                onCancelar = { porCancelar = s }
            )
        }
    }

    porAsignar?.let { s ->
        DialogoAsignar(s, repartidores, lista, onCerrar = { porAsignar = null }) { a ->
            porAsignar = null
            onAsignar(s, a)
        }
    }
    porCancelar?.let { s ->
        DialogoConfirmacion(
            titulo = "¿Cancelar la solicitud?",
            texto = "Pipa de ${formatLitros(s.capacidadLitros)} L de ${s.username} para el ${formatDia(s.dia)}. Se le avisará por correo si lo tiene verificado.",
            confirmar = "Sí, cancelar",
            cancelar = "No",
            peligroso = true,
            onCerrar = { porCancelar = null },
            onConfirmar = { porCancelar = null; onCancelar(s) }
        )
    }
}

@Composable
private fun SolicitudAdminCard(s: Solicitud, modifier: Modifier, onAsignar: () -> Unit, onDesasignar: () -> Unit, onCancelar: () -> Unit) {
    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
                AppIcon(Icono.GOTA, AppColors.Accent, 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${formatLitros(s.capacidadLitros)} L", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(s.username, color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            StatusChip(etiquetaEstado(s.estado), colorEstado(s.estado))
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoRow(Icono.UBICACION, listOf(s.domicilio, s.colonia).filter { it.isNotBlank() }.joinToString(", "), fuerte = true)
            s.referencia?.takeIf { it.isNotBlank() }?.let { InfoRow(Icono.NOTA, "Referencia: $it") }
            InfoRow(Icono.CALENDARIO, "${formatDia(s.dia)} · ${s.hora} h")
            InfoRow(Icono.CAMION, s.repartidor?.let { "Repartidor: $it" + (s.orden?.let { o -> " · parada $o" } ?: "") } ?: "Sin repartidor")
            s.notas?.takeIf { it.isNotBlank() }?.let { InfoRow(Icono.NOTA, "Notas: $it") }
            if (s.estado == EstadoSolicitud.ENTREGADA) InfoRow(Icono.CHECK, "Entregados: ${formatLitros(s.litrosEntregados ?: s.capacidadLitros)} L", color = AppColors.Green)
            if (s.estado == EstadoSolicitud.NO_ENTREGADA) InfoRow(Icono.ALERTA, s.observacion ?: "No se pudo entregar", color = AppColors.Red)
        }
        if (esCancelable(s.estado)) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonColor(if (s.repartidor == null) "Asignar" else "Reasignar", AppColors.Accent, onAsignar, Modifier.weight(1f), icono = Icono.CAMION)
                if (s.repartidor != null) BotonSecundario("Quitar", onDesasignar, Modifier.weight(1f))
                BotonSecundario("Cancelar", onCancelar, Modifier.weight(1f), color = AppColors.Red)
            }
        }
    }
}

@Composable
private fun DialogoAsignar(
    s: Solicitud,
    repartidores: List<Perfil>,
    todas: List<Solicitud>,
    onCerrar: () -> Unit,
    onAsignar: (Asignacion) -> Unit
) {
    var repartidor by remember { mutableStateOf(s.repartidor ?: repartidores.firstOrNull()?.username) }
    var dia by remember { mutableStateOf(s.dia) }
    var hora by remember { mutableStateOf(s.hora) }
    var orden by remember(repartidor, dia) {
        mutableStateOf(
            if (repartidor != null && repartidor.equals(s.repartidor, true) && dia == s.dia && s.orden != null) s.orden.toString()
            else repartidor?.let { siguienteOrden(todas, it, dia).toString() } ?: "1"
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    DialogoConfirmacion(
        titulo = "Asignar repartidor",
        texto = "${formatLitros(s.capacidadLitros)} L · ${s.domicilio}, ${s.colonia}",
        confirmar = "Asignar",
        onCerrar = onCerrar,
        onConfirmar = {
            val r = repartidor
            error = when {
                r == null -> "Primero da de alta un repartidor en Usuarios"
                !esFechaValida(dia) -> "El día debe ser AAAA-MM-DD"
                !esHoraValida(hora) -> "La hora debe ser HH:MM (24 horas)"
                (orden.toIntOrNull() ?: 0) < 1 -> "El orden debe ser 1 o más"
                else -> null
            }
            if (error == null) onAsignar(Asignacion(r!!, dia.trim(), hora.trim(), orden.toInt()))
        }
    ) {
        Text("Repartidor", fontWeight = FontWeight.SemiBold)
        if (repartidores.isEmpty()) Text("No hay repartidores activos.", color = AppColors.Red)
        repartidores.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { repartidor = p.username },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = repartidor == p.username, onClick = { repartidor = p.username }, colors = RadioButtonDefaults.colors(selectedColor = AppColors.Accent))
                Text(p.nombre.ifBlank { p.username } + "  ·  ${todas.count { it.repartidor.equals(p.username, true) && it.dia == dia && !esFinal(it.estado) }} paradas ese día")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoCorto("Día (AAAA-MM-DD)", dia, Modifier.weight(1.4f)) { dia = it }
            CampoCorto("Hora", hora, Modifier.weight(1f)) { hora = it }
            CampoCorto("Orden", orden, Modifier.weight(0.8f), numerico = true) { orden = it.filter(Char::isDigit).take(3) }
        }
        error?.let { Text(it, color = AppColors.Red, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CampoCorto(etiqueta: String, valor: String, modifier: Modifier, numerico: Boolean = false, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta, maxLines = 1) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = authFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = if (numerico) KeyboardType.Number else KeyboardType.Text),
        modifier = modifier
    )
}

// =================================================================================================
//  Rutas del día
// =================================================================================================

@Composable
private fun SeccionRutas(lista: List<Solicitud>, ancha: Boolean) {
    var desplazamiento by remember { mutableStateOf(0) }
    val dia = Plataforma.fechaLocal(desplazamiento)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Ayer" to -1, "Hoy" to 0, "Mañana" to 1, "Pasado mañana" to 2).forEach { (t, d) ->
            FiltroChip(t, desplazamiento == d) { desplazamiento = d }
        }
    }
    Spacer(Modifier.height(8.dp))
    val sinAsignar = lista.count { it.dia == dia && it.repartidor == null && esPendiente(it.estado) }
    Text(
        "Rutas del ${formatDia(dia)}" + if (sinAsignar > 0) " · $sinAsignar solicitudes sin repartidor" else "",
        color = if (sinAsignar > 0) AppColors.Yellow else AppColors.TextMuted
    )
    Spacer(Modifier.height(14.dp))
    val rutas = rutasDelDia(lista, dia)
    if (rutas.isEmpty()) {
        EstadoVacio(Icono.CAMION, "Sin rutas para este día", "Asigna solicitudes a un repartidor desde «Solicitudes».")
        return
    }
    Rejilla(rutas, if (ancha) 2 else 1) { ruta, mod ->
        Tarjeta(mod) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(AppColors.Accent), contentAlignment = Alignment.Center) {
                    AppIcon(Icono.CAMION, Color.White, 20.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ruta.repartidor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${ruta.paradas.size} paradas · ${formatLitros(ruta.litros)} L", color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text("${ruta.terminadas}/${ruta.paradas.size}", color = AppColors.Accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (ruta.paradas.isEmpty()) 0f else ruta.terminadas.toFloat() / ruta.paradas.size },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = AppColors.Green,
                trackColor = AppColors.SurfaceAlt
            )
            Spacer(Modifier.height(10.dp))
            ruta.paradas.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${p.orden ?: (i + 1)}", modifier = Modifier.width(24.dp), color = AppColors.Accent, fontWeight = FontWeight.Bold)
                    Text(p.hora, modifier = Modifier.width(52.dp), color = AppColors.TextMuted)
                    Text("${p.domicilio}, ${p.colonia}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusChip(etiquetaEstado(p.estado), colorEstado(p.estado))
                }
            }
        }
    }
}

// =================================================================================================
//  Usuarios
// =================================================================================================

private enum class FiltroUsuarios(val etiqueta: String) { TODOS("Todos"), REVISION("En revisión"), CIUDADANOS("Ciudadanos"), REPARTIDORES("Repartidores"), DELEGADOS("Delegados") }

@Composable
private fun SeccionUsuarios(usuarios: List<Perfil>, ancha: Boolean, onEstado: (Perfil, String) -> Unit, onAlta: (AltaUsuario) -> Unit) {
    var filtro by remember { mutableStateOf(FiltroUsuarios.TODOS) }
    var alta by remember { mutableStateOf(false) }
    fun pasa(p: Perfil) = when (filtro) {
        FiltroUsuarios.TODOS -> true
        FiltroUsuarios.REVISION -> p.estado == EstadoCuenta.EN_REVISION
        FiltroUsuarios.CIUDADANOS -> p.role == Roles.CIUDADANO
        FiltroUsuarios.REPARTIDORES -> p.role == Roles.OPERADOR
        FiltroUsuarios.DELEGADOS -> p.role == Roles.ADMIN
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FiltroUsuarios.entries.forEach { f -> FiltroChip(f.etiqueta, filtro == f) { filtro = f } }
        }
        Spacer(Modifier.width(8.dp))
        BotonColor("Nuevo usuario", AppColors.Accent, { alta = true }, icono = Icono.MAS)
    }
    Spacer(Modifier.height(14.dp))
    val visibles = usuarios.filter { pasa(it) }
    if (visibles.isEmpty()) EstadoVacio(Icono.PERSONA, "Sin usuarios", "No hay cuentas con este filtro.")
    Rejilla(visibles, if (ancha) 3 else 1) { u, mod ->
        Tarjeta(mod) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
                    Text(iniciales(u.nombre.ifBlank { u.username }), color = AppColors.Accent, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.nombre.ifBlank { u.username }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${u.username} · ${etiquetaRol(u.role)}", color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (u.telefono.isNotBlank()) InfoRow(Icono.PERSONA, u.telefono)
            if (u.correo.isNotBlank()) InfoRow(Icono.NOTA, u.correo + if (u.correoVerificado) " ✓" else " (sin verificar)")
            if (u.calle.isNotBlank()) InfoRow(Icono.UBICACION, listOf(u.calle, u.colonia).filter { it.isNotBlank() }.joinToString(", "))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(etiquetaEstadoCuenta(u.estado), when (u.estado) {
                    EstadoCuenta.ACTIVA -> AppColors.Green
                    EstadoCuenta.EN_REVISION -> AppColors.Yellow
                    else -> AppColors.Red
                })
                Spacer(Modifier.weight(1f))
                if (u.estado != EstadoCuenta.ACTIVA) BotonColor("Activar", AppColors.Green, { onEstado(u, EstadoCuenta.ACTIVA) })
                else if (u.role != Roles.ADMIN) BotonSecundario("Desactivar", { onEstado(u, EstadoCuenta.INACTIVA) }, color = AppColors.Red)
            }
        }
    }
    if (alta) DialogoAlta(onCerrar = { alta = false }) { a -> alta = false; onAlta(a) }
}

@Composable
private fun DialogoAlta(onCerrar: () -> Unit, onCrear: (AltaUsuario) -> Unit) {
    var rol by remember { mutableStateOf(Roles.OPERADOR) }
    var nombre by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var calle by remember { mutableStateOf("") }
    var colonia by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    DialogoConfirmacion(
        titulo = "Nuevo usuario",
        texto = "La cuenta queda activa de inmediato.",
        confirmar = "Crear cuenta",
        onCerrar = onCerrar,
        onConfirmar = {
            error = when {
                nombre.isBlank() -> "Escribe el nombre"
                usuario.isBlank() || usuario.any { it.isWhitespace() } -> "Escribe un usuario sin espacios"
                telefono.length != 10 -> "El teléfono debe tener 10 dígitos"
                contrasena.length < 6 -> "La contraseña debe tener al menos 6 caracteres"
                rol == Roles.CIUDADANO && calle.isBlank() -> "Escribe la calle del ciudadano"
                else -> null
            }
            if (error == null) onCrear(AltaUsuario(usuario.trim(), contrasena, rol, nombre.trim(), telefono, calle.trim(), colonia.trim()))
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Roles.OPERADOR to "Repartidor", Roles.ADMIN to "Delegado", Roles.CIUDADANO to "Ciudadano").forEach { (r, t) ->
                FiltroChip(t, rol == r, Modifier.weight(1f)) { rol = r }
            }
        }
        CampoCorto("Nombre completo", nombre, Modifier.fillMaxWidth()) { nombre = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoCorto("Usuario", usuario, Modifier.weight(1f)) { usuario = it.trim().take(40) }
            CampoCorto("Teléfono", telefono, Modifier.weight(1f), numerico = true) { telefono = it.filter(Char::isDigit).take(10) }
        }
        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it },
            label = { Text("Contraseña inicial") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = authFieldColors(),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (rol == Roles.CIUDADANO) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoCorto("Calle y número", calle, Modifier.weight(1.4f)) { calle = it }
                CampoCorto("Colonia", colonia, Modifier.weight(1f)) { colonia = it }
            }
        }
        error?.let { Text(it, color = AppColors.Red, style = MaterialTheme.typography.bodySmall) }
    }
}

// =================================================================================================
//  Reportes
// =================================================================================================

@Composable
private fun SeccionReportes(lista: List<Solicitud>, ancha: Boolean, onEnviarCorreo: (String, String) -> Unit) {
    var dias by remember { mutableStateOf(7) }
    val periodo = (dias - 1 downTo 0).map { Plataforma.fechaLocal(-it) }
    val desde = periodo.first()
    val hasta = periodo.last()
    val r = calcularReporte(lista, desde, hasta, periodo)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to "Últimos 7 días", 30 to "Últimos 30 días").forEach { (d, t) -> FiltroChip(t, dias == d) { dias = d } }
        }
        Plataforma.imprimirHtml?.let { imprimir ->
            BotonSecundario("Imprimir reporte", {
                imprimir(reporteHtml(lista.filter { it.dia in desde..hasta }, desde, hasta, Plataforma.fechaHoraLocal()))
            }, icono = Icono.RECIBO)
            Spacer(Modifier.width(8.dp))
        }
        Plataforma.descargarTexto?.let { descargar ->
            BotonSecundario("Descargar CSV", {
                descargar(
                    "aquago_solicitudes_${desde}_a_${hasta}.csv",
                    solicitudesCsv(lista.filter { it.dia in desde..hasta }, desde, hasta, Plataforma.fechaHoraLocal())
                )
            }, icono = Icono.NOTA)
            Spacer(Modifier.width(8.dp))
        }
        BotonColor("Enviar a mi correo", AppColors.Accent, { onEnviarCorreo(desde, hasta) }, icono = Icono.RECIBO)
    }
    Spacer(Modifier.height(6.dp))
    Text("Del ${formatDia(desde)} al ${formatDia(hasta)}", color = AppColors.TextMuted)
    Spacer(Modifier.height(14.dp))

    val tarjetas = listOf(
        Triple("Solicitudes", r.total.toString(), AppColors.Accent),
        Triple("Entregadas", r.entregadas.toString(), AppColors.Green),
        Triple("No entregadas", r.noEntregadas.toString(), AppColors.Red),
        Triple("Canceladas", r.canceladas.toString(), AppColors.Red),
        Triple("Litros entregados", formatLitros(r.litrosEntregados), AppColors.Blue),
        Triple("Efectividad", "${r.efectividad} %", AppColors.Green)
    )
    Rejilla(tarjetas, if (ancha) 6 else 2) { (t, v, c), mod ->
        Tarjeta(mod) {
            Text(v, color = c, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(t, color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(14.dp))

    val bloques = listOf<@Composable (Modifier) -> Unit>(
        { mod ->
            Tarjeta(mod) {
                TituloSeccion("Solicitudes por día")
                Spacer(Modifier.height(10.dp))
                val max = (r.porDia.maxOfOrNull { it.solicitudes } ?: 0).coerceAtLeast(1)
                r.porDia.forEach { f -> Barra(formatDia(f.etiqueta).take(5), f.solicitudes, max, "${f.solicitudes}") }
            }
        },
        { mod ->
            Tarjeta(mod) {
                TituloSeccion("Por repartidor")
                Spacer(Modifier.height(10.dp))
                if (r.porRepartidor.isEmpty()) Text("Sin entregas asignadas en el periodo", color = AppColors.TextMuted)
                val max = (r.porRepartidor.maxOfOrNull { it.solicitudes } ?: 0).coerceAtLeast(1)
                r.porRepartidor.forEach { f -> Barra(f.etiqueta, f.extra, max, "${f.extra}/${f.solicitudes} · ${formatLitros(f.litros)} L") }
            }
        },
        { mod ->
            Tarjeta(mod) {
                TituloSeccion("Por estatus")
                Spacer(Modifier.height(10.dp))
                val max = (r.porEstado.maxOfOrNull { it.solicitudes } ?: 0).coerceAtLeast(1)
                r.porEstado.forEach { f -> Barra(f.etiqueta, f.solicitudes, max, "${f.solicitudes}") }
                if (r.porEstado.isEmpty()) Text("Sin solicitudes en el periodo", color = AppColors.TextMuted)
            }
        },
        { mod ->
            Tarjeta(mod) {
                TituloSeccion("Por colonia")
                Spacer(Modifier.height(10.dp))
                val max = (r.porColonia.maxOfOrNull { it.solicitudes } ?: 0).coerceAtLeast(1)
                r.porColonia.take(8).forEach { f -> Barra(f.etiqueta, f.solicitudes, max, "${f.solicitudes} · ${formatLitros(f.litros)} L") }
                if (r.porColonia.isEmpty()) Text("Sin solicitudes en el periodo", color = AppColors.TextMuted)
            }
        }
    )
    Rejilla(bloques, if (ancha) 2 else 1) { bloque, mod -> bloque(mod) }
}

/** Renglón con barra horizontal proporcional (gráfica sencilla sin librerías). */
@Composable
private fun Barra(etiqueta: String, valor: Int, maximo: Int, texto: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(50)).background(AppColors.SurfaceAlt)) {
            if (valor > 0) {
                Box(Modifier.fillMaxWidth(valor.toFloat() / maximo).fillMaxHeight().clip(RoundedCornerShape(50)).background(AppColors.AccentLight))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(texto, modifier = Modifier.widthIn(min = 40.dp), style = MaterialTheme.typography.labelMedium, color = AppColors.TextMuted)
    }
}

/** Si alguien que no es delegado entra a la página web. */
@Composable
fun SoloDelegadoWebScreen(session: LoginResponse, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EstadoVacio(
            Icono.PERSONA,
            "Esta página es para el delegado",
            "Hola ${session.username}. Ciudadanos y repartidores usan la app AquaGo del teléfono para pedir y entregar pipas."
        )
        Box(Modifier.widthIn(max = 360.dp)) { PrimaryButton("Cerrar sesión", onClick = onLogout) }
    }
}
