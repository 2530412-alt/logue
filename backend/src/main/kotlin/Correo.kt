package com.logue

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// =====================================================================================================
//  SERVICIO DE CORREO (requisito de la app web)
//  - Código para verificar el correo (al registrarse o al cambiarlo en el perfil).
//  - Código para recuperar la contraseña (además del teléfono).
//  - Avisos: cuenta aprobada, pipa programada / en camino / entregada / no entregada / cancelada.
//  - Reporte de entregas para el delegado.
//
//  Se envía con la API HTTPS de Brevo (plan gratuito: 300 correos al día, sin dominio propio).
//  No se usa SMTP (Gmail) porque Render gratuito bloquea los puertos 25, 465 y 587 desde 2025.
//
//  VARIABLES DE ENTORNO (nunca se suben a GitHub):
//    BREVO_API_KEY     clave de la API de Brevo (Brevo > SMTP & API > API Keys)
//    CORREO_REMITENTE  correo verificado como remitente en Brevo (ej. aquago.camelinas@gmail.com)
//    CORREO_NOMBRE     nombre que aparece como remitente (por omisión "AquaGo")
//  Si faltan, el servidor funciona igual y solo escribe en el registro lo que habría enviado
//  (sin el código, para no exponerlo).
// =====================================================================================================

private val logCorreo = LoggerFactory.getLogger("com.logue.Correo")

/** Envía un correo. Las implementaciones no lanzan excepciones: un correo fallido nunca tumba una petición. */
interface Correos {
    /** `true` si hay un proveedor real configurado (la app lo usa para decidir qué mensaje mostrar). */
    val activo: Boolean
    fun enviar(para: String, asunto: String, html: String)
}

/** Sin proveedor configurado: solo deja constancia en el registro (sin el contenido). */
class CorreosSoloRegistro : Correos {
    override val activo = false
    override fun enviar(para: String, asunto: String, html: String) {
        logCorreo.warn("Correo no configurado (BREVO_API_KEY). Se habría enviado «$asunto» a ${ocultarCorreo(para)}")
    }
}

/** Brevo (https://developers.brevo.com/reference/sendtransacemail). Se envía en segundo plano. */
class CorreosBrevo(
    private val apiKey: String,
    private val remitente: String,
    private val nombreRemitente: String = "AquaGo",
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
) : Correos {
    override val activo = true

    override fun enviar(para: String, asunto: String, html: String) {
        val cuerpo = buildJsonObject {
            putJsonObject("sender") { put("name", nombreRemitente); put("email", remitente) }
            put("to", buildJsonArray { add(buildJsonObject { put("email", para) }) })
            put("subject", asunto)
            put("htmlContent", html)
        }
        val req = HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
            .timeout(Duration.ofSeconds(20))
            .header("api-key", apiKey)
            .header("accept", "application/json")
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()))
            .build()
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete { res, err ->
            when {
                err != null -> logCorreo.warn("No se pudo enviar «$asunto» a ${ocultarCorreo(para)}: ${err.message}")
                res.statusCode() !in 200..299 -> logCorreo.warn("Brevo respondió ${res.statusCode()} al enviar «$asunto»: ${res.body().take(200)}")
                else -> logCorreo.info("Correo «$asunto» enviado a ${ocultarCorreo(para)}")
            }
        }
    }
}

fun correosDesdeEntorno(env: Map<String, String> = System.getenv()): Correos {
    val clave = env["BREVO_API_KEY"]?.trim().orEmpty()
    val remitente = env["CORREO_REMITENTE"]?.trim().orEmpty()
    return if (clave.isNotEmpty() && remitente.isNotEmpty()) {
        CorreosBrevo(clave, remitente, env["CORREO_NOMBRE"]?.trim()?.ifEmpty { null } ?: "AquaGo")
    } else {
        CorreosSoloRegistro()
    }
}

/** "maria.lopez@gmail.com" -> "ma***@gmail.com" (para el registro del servidor). */
fun ocultarCorreo(correo: String): String {
    val at = correo.indexOf('@')
    return if (at <= 0) "***" else correo.take(minOf(2, at)) + "***" + correo.substring(at)
}

private val FORMATO_CORREO = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

/** `null` si el correo es válido; si no, el mensaje para la app. */
fun validarCorreo(correo: String): String? = when {
    correo.length > 100 -> "El correo es demasiado largo"
    !FORMATO_CORREO.matches(correo) -> "Escribe un correo válido, por ejemplo nombre@gmail.com"
    else -> null
}

// ---------------------------------------------------------------------------------------------------
//  Plantillas
// ---------------------------------------------------------------------------------------------------

private fun escaparHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Correo con el diseño de la app: encabezado azul, tarjeta blanca y pie. */
internal fun plantilla(titulo: String, cuerpoHtml: String): String = """
<div style="background:#F3F7FB;padding:24px;font-family:Arial,Helvetica,sans-serif;color:#13263D">
  <div style="max-width:520px;margin:0 auto;background:#FFFFFF;border-radius:16px;overflow:hidden;border:1px solid #DCE5EE">
    <div style="background:#0B5CAD;color:#FFFFFF;padding:18px 24px;font-size:20px;font-weight:bold">💧 AquaGo</div>
    <div style="padding:24px">
      <h2 style="margin:0 0 12px 0;font-size:18px">${escaparHtml(titulo)}</h2>
      $cuerpoHtml
    </div>
    <div style="padding:14px 24px;background:#EEF3F8;color:#64748B;font-size:12px">
      Servicio de agua por pipa · Las Camelinas. Este es un mensaje automático, no respondas a este correo.
    </div>
  </div>
</div>
""".trimIndent()

private fun bloqueCodigo(codigo: String) =
    """<div style="font-size:32px;letter-spacing:8px;font-weight:bold;color:#0B5CAD;background:#E3F1FC;border-radius:12px;padding:14px;text-align:center;margin:16px 0">$codigo</div>"""

// ---------------------------------------------------------------------------------------------------
//  Avisos de la aplicación
// ---------------------------------------------------------------------------------------------------

/** Arma y envía cada tipo de correo. Solo escribe a correos verificados (excepto el código de verificación). */
class Avisos(private val correos: Correos, private val users: UserRepository) {

    val activo get() = correos.activo

    fun codigoVerificacion(correo: String, nombre: String, codigo: String) = correos.enviar(
        correo,
        "Tu código para verificar el correo: $codigo",
        plantilla(
            "Verifica tu correo",
            "<p>Hola ${escaparHtml(nombre)}, escribe este código en AquaGo para confirmar tu correo:</p>" +
                bloqueCodigo(codigo) + "<p style=\"color:#64748B\">Vence en 10 minutos. Si no fuiste tú, ignora este mensaje.</p>"
        )
    )

    fun codigoRecuperacion(user: User, codigo: String) {
        if (!user.correoVerificado || user.correo.isBlank()) return
        correos.enviar(
            user.correo,
            "Código para recuperar tu contraseña: $codigo",
            plantilla(
                "Recupera tu contraseña",
                "<p>Hola ${escaparHtml(user.nombre.ifBlank { user.username })}, usa este código para crear una contraseña nueva:</p>" +
                    bloqueCodigo(codigo) + "<p style=\"color:#64748B\">Vence en 10 minutos. Si no lo pediste, alguien escribió tu usuario por error: tu contraseña no ha cambiado.</p>"
            )
        )
    }

    fun cuentaActivada(user: User) {
        if (!user.correoVerificado) return
        correos.enviar(
            user.correo,
            "Tu cuenta de AquaGo fue aprobada",
            plantilla(
                "¡Bienvenido a AquaGo!",
                "<p>Hola ${escaparHtml(user.nombre.ifBlank { user.username })}, el delegado validó tu comprobante. Ya puedes iniciar sesión con el usuario <b>${escaparHtml(user.username)}</b> y pedir tu pipa.</p>"
            )
        )
    }

    /** Cambio de estatus de una solicitud: se avisa al ciudadano dueño. */
    fun solicitudActualizada(s: Solicitud) {
        val user = users.find(s.username) ?: return
        if (!user.correoVerificado || user.correo.isBlank()) return
        val litros = miles(s.capacidadLitros)
        val (asunto, texto) = when (s.estado) {
            EstadoSolicitud.PROGRAMADA -> "Tu pipa quedó programada" to "Tu pipa de <b>$litros L</b> llegará el <b>${formatoDia(s.dia)}</b> alrededor de las <b>${s.hora}</b>."
            EstadoSolicitud.EN_CAMINO -> "Tu pipa va en camino" to "El repartidor ya va hacia <b>${escaparHtml(s.domicilio)}</b> con tu pipa de <b>$litros L</b>."
            EstadoSolicitud.ENTREGADA -> "Tu pipa fue entregada" to "Se entregaron <b>${miles(s.litrosEntregados ?: s.capacidadLitros)} L</b> en ${escaparHtml(s.domicilio)}. ¡Gracias por usar AquaGo!"
            EstadoSolicitud.NO_ENTREGADA -> "No se pudo entregar tu pipa" to "El repartidor no pudo entregar tu pipa: ${escaparHtml(s.observacion ?: "no se encontró a nadie")}. Puedes pedir otra desde la app."
            EstadoSolicitud.CANCELADA -> "Tu solicitud fue cancelada" to "La solicitud de <b>$litros L</b> para el ${formatoDia(s.dia)} quedó cancelada."
            else -> return
        }
        correos.enviar(user.correo, asunto, plantilla(asunto, "<p>Hola ${escaparHtml(user.nombre.ifBlank { user.username })}:</p><p>$texto</p>"))
    }

    fun reporte(destino: String, r: ReporteEntregas, generado: String = java.time.LocalDateTime.now(appZone())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))) {
        val total = "padding:6px;background:#0B5CAD;color:#ffffff;font-weight:bold"
        val totalDia = "<tr><td style=\"$total\">Total</td><td style=\"$total;text-align:right\">${r.porDia.sumOf { it.entregadas }} / ${r.porDia.sumOf { it.solicitudes }}</td><td style=\"$total;text-align:right\">${miles(r.porDia.sumOf { it.litros })} L</td></tr>"
        val totalRep = "<tr><td style=\"$total\">Total</td><td style=\"$total;text-align:right\">${r.porRepartidor.sumOf { it.entregadas }}</td><td style=\"$total;text-align:right\">${r.porRepartidor.sumOf { it.noEntregadas }}</td><td style=\"$total;text-align:right\">${miles(r.porRepartidor.sumOf { it.litros })} L</td></tr>"
        val filasDia = r.porDia.joinToString("") {
            "<tr><td style=\"padding:6px;border-bottom:1px solid #EEF3F8\">${formatoDia(it.dia)}</td><td style=\"padding:6px;border-bottom:1px solid #EEF3F8;text-align:right\">${it.entregadas} / ${it.solicitudes}</td><td style=\"padding:6px;border-bottom:1px solid #EEF3F8;text-align:right\">${miles(it.litros)} L</td></tr>"
        }
        val filasRep = r.porRepartidor.joinToString("") {
            "<tr><td style=\"padding:6px;border-bottom:1px solid #EEF3F8\">${escaparHtml(it.repartidor)}</td><td style=\"padding:6px;border-bottom:1px solid #EEF3F8;text-align:right\">${it.entregadas}</td><td style=\"padding:6px;border-bottom:1px solid #EEF3F8;text-align:right\">${it.noEntregadas}</td><td style=\"padding:6px;border-bottom:1px solid #EEF3F8;text-align:right\">${miles(it.litros)} L</td></tr>"
        }
        val cuerpo = """
            <p>Fecha de generación: <b>$generado</b><br>Periodo: del <b>${formatoDia(r.desde)}</b> al <b>${formatoDia(r.hasta)}</b></p>
            <table style="width:100%;border-collapse:collapse;margin:12px 0">
              <tr><td>Solicitudes</td><td style="text-align:right"><b>${r.total}</b></td></tr>
              <tr><td>Entregadas</td><td style="text-align:right;color:#1F9D63"><b>${r.entregadas}</b></td></tr>
              <tr><td>No entregadas</td><td style="text-align:right;color:#D9534F"><b>${r.noEntregadas}</b></td></tr>
              <tr><td>Canceladas</td><td style="text-align:right;color:#D9534F"><b>${r.canceladas}</b></td></tr>
              <tr><td>Pendientes</td><td style="text-align:right;color:#D98300"><b>${r.pendientes}</b></td></tr>
              <tr><td>Litros entregados</td><td style="text-align:right"><b>${miles(r.litrosEntregados)} L</b></td></tr>
            </table>
            <h3 style="font-size:15px">Por día</h3>
            <table style="width:100%;border-collapse:collapse"><tr style="color:#64748B"><td>Día</td><td style="text-align:right">Entregadas / total</td><td style="text-align:right">Litros entregados</td></tr>$filasDia$totalDia</table>
            <h3 style="font-size:15px">Por repartidor</h3>
            <table style="width:100%;border-collapse:collapse"><tr style="color:#64748B"><td>Repartidor</td><td style="text-align:right">Entregadas</td><td style="text-align:right">No entregadas</td><td style="text-align:right">Litros entregados</td></tr>$filasRep$totalRep</table>
        """.trimIndent()
        correos.enviar(destino, "Reporte de entregas AquaGo ${formatoDia(r.desde)} - ${formatoDia(r.hasta)}", plantilla("Reporte de entregas", cuerpo))
    }
}

/** 4500 -> "4,500" (siempre con coma, sin depender del idioma de la computadora). */
private fun miles(n: Int): String = String.format(java.util.Locale.US, "%,d", n)

private fun formatoDia(dia: String): String {
    val p = dia.split('-')
    return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else dia
}

// ---------------------------------------------------------------------------------------------------
//  Reporte de entregas (lo usan el panel web y el envío por correo)
// ---------------------------------------------------------------------------------------------------

@Serializable
data class ReporteDia(val dia: String, val solicitudes: Int, val entregadas: Int, val litros: Int)

@Serializable
data class ReporteRepartidor(val repartidor: String, val entregadas: Int, val noEntregadas: Int, val litros: Int)

@Serializable
data class ReporteEntregas(
    val desde: String,
    val hasta: String,
    val total: Int,
    val pendientes: Int,
    val enCamino: Int,
    val entregadas: Int,
    val noEntregadas: Int,
    val canceladas: Int,
    val litrosEntregados: Int,
    val porDia: List<ReporteDia>,
    val porRepartidor: List<ReporteRepartidor>
)

/** Calcula el reporte de las solicitudes con día entre [desde] y [hasta] (inclusive, formato AAAA-MM-DD). */
fun calcularReporte(todas: List<Solicitud>, desde: String, hasta: String): ReporteEntregas {
    val lista = todas.filter { it.dia in desde..hasta }
    fun litros(s: Solicitud) = if (s.estado == EstadoSolicitud.ENTREGADA) s.litrosEntregados ?: s.capacidadLitros else 0
    val pendientes = setOf(EstadoSolicitud.RECIBIDA, EstadoSolicitud.EN_PROCESO, EstadoSolicitud.PROGRAMADA)
    return ReporteEntregas(
        desde = desde,
        hasta = hasta,
        total = lista.size,
        pendientes = lista.count { it.estado in pendientes },
        enCamino = lista.count { it.estado == EstadoSolicitud.EN_CAMINO },
        entregadas = lista.count { it.estado == EstadoSolicitud.ENTREGADA },
        noEntregadas = lista.count { it.estado == EstadoSolicitud.NO_ENTREGADA },
        canceladas = lista.count { it.estado == EstadoSolicitud.CANCELADA },
        litrosEntregados = lista.sumOf { litros(it) },
        porDia = lista.groupBy { it.dia }.toSortedMap().map { (dia, l) ->
            ReporteDia(dia, l.size, l.count { it.estado == EstadoSolicitud.ENTREGADA }, l.sumOf { litros(it) })
        },
        porRepartidor = lista.filter { !it.repartidor.isNullOrBlank() }.groupBy { it.repartidor!!.lowercase() }.map { (_, l) ->
            ReporteRepartidor(
                l.first().repartidor!!,
                l.count { it.estado == EstadoSolicitud.ENTREGADA },
                l.count { it.estado == EstadoSolicitud.NO_ENTREGADA },
                l.sumOf { litros(it) }
            )
        }.sortedByDescending { it.entregadas }
    )
}
