package com.logue.frontend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Perfil de la cuenta (USR-21): consulta y edición del nombre, el teléfono y el domicilio.
 * Lee `GET /usuarios/yo` y guarda con `PATCH /usuarios/yo`. Sirve a los tres roles.
 */
@Composable
fun PerfilScreen(session: LoginResponse, onBack: () -> Unit, onSessionExpired: () -> Unit = onBack) {
    PlatformBackHandler(onBack)
    var recarga by remember { mutableStateOf(0) }
    val carga = cargarDatos(session, recarga, onSessionExpired) { obtenerPerfil(it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AuthHeader(title = "Mi perfil", subtitle = "Consulta y corrige tus datos de contacto", onBack = onBack)
        when (carga) {
            Carga.Cargando -> Text("Cargando...", color = AppColors.TextMuted)
            is Carga.Fallo -> AppCard {
                Text(carga.mensaje, color = AppColors.Red)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { recarga++ }) { Text("Reintentar", color = AppColors.Accent) }
            }
            is Carga.Lista -> PerfilFormulario(session, carga.datos, onSessionExpired)
        }
    }
}

@Composable
private fun PerfilFormulario(session: LoginResponse, perfil: Perfil, onSessionExpired: () -> Unit) {
    // Lo que se muestra como "guardado" es el último perfil que confirmó el servidor
    var guardado by remember(perfil) { mutableStateOf(perfil) }
    var nombre by remember(perfil) { mutableStateOf(perfil.nombre) }
    var telefono by remember(perfil) { mutableStateOf(perfil.telefono) }
    var calle by remember(perfil) { mutableStateOf(perfil.calle) }
    var colonia by remember(perfil) { mutableStateOf(perfil.colonia) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AppCard {
        FormSectionTitle("Cuenta")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(etiquetaRol(guardado.role), AppColors.Accent)
            Spacer(Modifier.width(10.dp))
            Text(guardado.username, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Estado de la cuenta: ${etiquetaEstadoCuenta(guardado.estado)}",
            color = AppColors.TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }

    AppCard {
        FormSectionTitle("Datos personales")
        AquaGoTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = "Nombre completo",
            capitalization = KeyboardCapitalization.Words
        )
        Spacer(Modifier.height(12.dp))
        AquaGoTextField(
            value = telefono,
            onValueChange = { telefono = it.filter { c -> c.isDigit() }.take(10) },
            label = "Teléfono de contacto",
            keyboardType = KeyboardType.Phone,
            supportingText = "10 dígitos"
        )
    }

    // Servicio de correo: avisos de tus pipas y recuperar la contraseña por correo
    TarjetaCorreo(session, guardado, onActualizado = { guardado = it }, onSessionExpired = onSessionExpired)

    AppCard {
        FormSectionTitle("Domicilio")
        AquaGoTextField(
            value = calle,
            onValueChange = { calle = it },
            label = "Calle y número",
            capitalization = KeyboardCapitalization.Sentences
        )
        Spacer(Modifier.height(12.dp))
        AquaGoTextField(
            value = colonia,
            onValueChange = { colonia = it },
            label = "Colonia o localidad",
            capitalization = KeyboardCapitalization.Words
        )
    }

    PrimaryButton(if (saving) "Guardando..." else "Guardar cambios", enabled = !saving) {
        val cambio = profileChange(guardado, nombre, telefono, calle, colonia)
        val validation = validateProfileChange(cambio)
        info = null
        when {
            validation != null -> error = validation
            cambio.isEmpty() -> {
                error = null
                info = "No hay cambios por guardar"
            }
            else -> scope.launch {
                saving = true
                error = null
                when (val result = guardarPerfil(session.token, cambio)) {
                    is ApiResult.Ok -> {
                        guardado = result.valor
                        nombre = result.valor.nombre
                        telefono = result.valor.telefono
                        calle = result.valor.calle
                        colonia = result.valor.colonia
                        info = "Cambios guardados"
                    }
                    ApiResult.SesionVencida -> onSessionExpired()
                    else -> error = result.mensajeDeError()
                }
                saving = false
            }
        }
    }
    FormError(error)
    info?.let { Text(it, color = AppColors.Green, style = MaterialTheme.typography.bodySmall) }
}

/** Correo de la cuenta: guardarlo, pedir el código y verificarlo. */
@Composable
private fun TarjetaCorreo(
    session: LoginResponse,
    perfil: Perfil,
    onActualizado: (Perfil) -> Unit,
    onSessionExpired: () -> Unit
) {
    var correo by remember(perfil.correo) { mutableStateOf(perfil.correo) }
    var codigo by remember { mutableStateOf("") }
    var esperandoCodigo by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun <T> manejar(r: ApiResult<T>, alOk: (T) -> Unit) {
        when (r) {
            is ApiResult.Ok -> alOk(r.valor)
            ApiResult.SesionVencida -> onSessionExpired()
            else -> mensaje = (r.mensajeDeError() ?: "No se pudo completar") to false
        }
    }

    AppCard {
        FormSectionTitle("Correo electrónico")
        AquaGoTextField(
            value = correo,
            onValueChange = { correo = it.trim().take(100) },
            label = "Correo",
            keyboardType = KeyboardType.Email,
            supportingText = when {
                perfil.correo.isBlank() -> "Para recibir avisos de tus pipas y recuperar tu contraseña"
                perfil.correoVerificado -> "Verificado ✓"
                else -> "Sin verificar: pide un código y escríbelo abajo"
            }
        )
        Spacer(Modifier.height(10.dp))
        if (!correo.equals(perfil.correo, ignoreCase = true)) {
            PrimaryButton(if (trabajando) "Guardando..." else "Guardar correo", enabled = !trabajando) {
                trabajando = true
                scope.launch {
                    manejar(guardarPerfil(session.token, PerfilCambio(correo = correo))) {
                        onActualizado(it)
                        mensaje = (if (correo.isBlank()) "Correo eliminado" else "Correo guardado. Ahora verifícalo") to true
                    }
                    trabajando = false
                }
            }
        } else if (perfil.correo.isNotBlank() && !perfil.correoVerificado) {
            OutlinedButton(
                onClick = {
                    trabajando = true
                    scope.launch {
                        manejar(pedirCodigoCorreo(session.token)) {
                            esperandoCodigo = true
                            mensaje = "Te enviamos un código a ${perfil.correo}" to true
                        }
                        trabajando = false
                    }
                },
                enabled = !trabajando,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (esperandoCodigo) "Enviar otro código" else "Enviarme un código", color = AppColors.Accent) }
            if (esperandoCodigo) {
                Spacer(Modifier.height(10.dp))
                AquaGoTextField(
                    value = codigo,
                    onValueChange = { codigo = it.filter(Char::isDigit).take(6) },
                    label = "Código de 6 dígitos",
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Verificar correo", enabled = codigo.length == 6 && !trabajando) {
                    trabajando = true
                    scope.launch {
                        manejar(verificarCorreo(session.token, codigo)) {
                            onActualizado(it)
                            esperandoCodigo = false
                            codigo = ""
                            mensaje = "¡Listo! Tu correo quedó verificado" to true
                        }
                        trabajando = false
                    }
                }
            }
        }
        mensaje?.let { (texto, ok) ->
            Spacer(Modifier.height(8.dp))
            Text(texto, color = if (ok) AppColors.Green else AppColors.Red, style = MaterialTheme.typography.bodySmall)
        }
    }
}
