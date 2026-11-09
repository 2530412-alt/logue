package com.logue.frontend

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Pagos prediales registrados. El ciudadano ve su historial (PRE-10) y el delegado ve los comprobantes
 * de todos (PRE-11); en los dos casos se puede abrir la foto del recibo. El servidor entrega a cada
 * quien solo lo que le corresponde (`GET /pagos-prediales` y `/pagos-prediales/{id}/foto`).
 */
@Composable
fun PagosPredialesScreen(session: LoginResponse, onBack: () -> Unit, onSessionExpired: () -> Unit = onBack) {
    val esDelegado = session.role == Roles.ADMIN
    var recarga by remember { mutableStateOf(0) }
    var abierto by remember { mutableStateOf<PagoPredialResponse?>(null) }
    val carga = cargarDatos(session, recarga, onSessionExpired) { listarPagosPrediales(it) }

    val pago = abierto
    if (pago != null) {
        FotoDePagoScreen(session, pago, onClose = { abierto = null }, onSessionExpired = onSessionExpired)
        return
    }
    PlatformBackHandler(onBack)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AuthHeader(
            title = if (esDelegado) "Comprobantes de pago" else "Mis pagos prediales",
            subtitle = if (esDelegado) "Fotos de recibo que enviaron los ciudadanos" else "Los pagos que has registrado, del más reciente al más antiguo",
            onBack = onBack
        )
        when (carga) {
            Carga.Cargando -> Text("Cargando...", color = AppColors.TextMuted)
            is Carga.Fallo -> AppCard {
                Text(carga.mensaje, color = AppColors.Red)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { recarga++ }) { Text("Reintentar", color = AppColors.Accent) }
            }
            is Carga.Lista -> {
                if (carga.datos.isEmpty()) {
                    Text(
                        if (esDelegado) "Todavía no hay comprobantes" else "Aún no has registrado pagos prediales",
                        color = AppColors.TextMuted
                    )
                }
                carga.datos.forEach { p ->
                    AppCard {
                        Text(fechaLegible(p.fecha), fontWeight = FontWeight.SemiBold)
                        if (esDelegado) {
                            Text("Ciudadano: ${p.username}", color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (p.folio.isNotBlank()) {
                            Text("Folio: ${p.folio}", color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Comprobante ${p.id.take(8)} · ${(p.tamano / 1024).coerceAtLeast(1)} KB",
                            color = AppColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { abierto = p }) { Text("Ver foto", color = AppColors.Accent) }
                    }
                }
            }
        }
    }
}

/** La foto de un comprobante, a pantalla completa. */
@Composable
private fun FotoDePagoScreen(
    session: LoginResponse,
    pago: PagoPredialResponse,
    onClose: () -> Unit,
    onSessionExpired: () -> Unit
) {
    PlatformBackHandler(onClose)
    val descarga = produceState<ApiResult<ByteArray>?>(null, pago.id) {
        value = descargarFotoDePago(session.token, pago.id)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AuthHeader(title = "Comprobante", subtitle = "Pago del ${fechaLegible(pago.fecha)}", onBack = onClose)
        when (val result = descarga.value) {
            null -> Text("Cargando foto...", color = AppColors.TextMuted)
            is ApiResult.Ok -> {
                val imagen = remember(result.valor) { decodeImage(result.valor) }
                if (imagen != null) {
                    Image(
                        bitmap = imagen,
                        contentDescription = "Foto del comprobante de pago predial",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("No se pudo mostrar la imagen", color = AppColors.Red)
                }
            }
            ApiResult.SesionVencida -> LaunchedEffect(Unit) { onSessionExpired() }
            else -> Text(result.mensajeDeError() ?: "No se pudo cargar la foto", color = AppColors.Red)
        }
        PrimaryButton("Cerrar", onClick = onClose)
    }
}

/** "2026-11-04T18:30:12.345Z" -> "04/11/2026 18:30" (hora UTC, tal como la guarda el servidor). */
fun fechaLegible(fecha: String): String {
    val dia = fecha.substringBefore('T')
    val hora = fecha.substringAfter('T', "").take(5)
    return if (hora.isEmpty()) formatDia(dia) else "${formatDia(dia)} $hora"
}
