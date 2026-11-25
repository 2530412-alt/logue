package com.logue.frontend

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/*
 * Servicios del teléfono que necesita el modo sin conexión de AquaGo.
 *
 * Se resolvió con "ganchos" (variables con un valor por defecto) en lugar de expect/actual porque el
 * proyecto también compila para iOS, JS y Wasm: esas plataformas siguen funcionando con los valores
 * por defecto (memoria, sin notificaciones) y solo Android los reemplaza al arrancar
 * (PlataformaAndroid.iniciar, llamado desde MainActivity y desde el trabajo de sincronización).
 */

/** Almacenamiento de texto clave-valor que sobrevive al cerrar la app (en Android: SharedPreferences). */
interface AlmacenLocal {
    fun leer(clave: String): String?
    fun guardar(clave: String, valor: String)
    fun borrar(clave: String)
}

/** Por defecto (web / iOS / pruebas): solo en memoria. */
class AlmacenEnMemoria : AlmacenLocal {
    private val datos = mutableMapOf<String, String>()
    override fun leer(clave: String) = datos[clave]
    override fun guardar(clave: String, valor: String) { datos[clave] = valor }
    override fun borrar(clave: String) { datos.remove(clave) }
}

object Plataforma {
    /** Dónde se guardan la sesión, las listas descargadas y la cola de cambios pendientes. */
    var almacen: AlmacenLocal = AlmacenEnMemoria()

    /** `true` si el teléfono tiene red. Solo informa a la pantalla; quien decide es la respuesta del servidor. */
    var hayConexion: () -> Boolean = { true }

    /** Muestra una notificación local (título, texto). */
    var notificar: (String, String) -> Unit = { _, _ -> }

    /** Pide al sistema que suba los cambios pendientes en cuanto haya conexión (Android: WorkManager). */
    var programarSincronizacion: () -> Unit = {}

    /** Abre la dirección en la app de mapas. */
    var abrirMapa: (String) -> Unit = {}

    /** Fecha local `AAAA-MM-DD` dentro de [dias] días (0 = hoy). Por defecto usa UTC. */
    var fechaLocal: (Int) -> String = { dias -> fechaUtc(dias) }

    /** `true` en la app de teléfono: ahí el delegado no tiene acceso (su panel es la página web). */
    var esAppMovil: Boolean = false

    // ---------------- Solo página web (WebPlataforma.kt en jsMain y wasmJsMain) ----------------

    /** `true` en el navegador: la página web es solo para el delegado. */
    var esWeb: Boolean = false

    /** Muestra el botón «Iniciar sesión con Google» (Google Identity Services); llama al callback con el ID token. */
    var botonGoogle: ((clientId: String, alEntrar: (String) -> Unit) -> Unit)? = null

    /** Muestra la casilla «No soy un robot» (reCAPTCHA v2); llama al callback con la respuesta ("" si venció). */
    var casillaCaptcha: ((claveSitio: String, alResponder: (String) -> Unit) -> Unit)? = null

    /** Vuelve a mostrar el captcha sin marcar (cada respuesta sirve una sola vez). */
    var reiniciarCaptcha: () -> Unit = {}

    /**
     * Coloca un elemento HTML (el botón de Google o el captcha) encima del lugar que le reservó la
     * pantalla de Compose. [x], [y], [ancho] y [alto] están en pixeles del lienzo.
     */
    var ubicarElementoWeb: (id: String, x: Float, y: Float, ancho: Float, alto: Float, visible: Boolean) -> Unit = { _, _, _, _, _, _ -> }

    /** Descarga un archivo de texto (CSV del reporte). `null` donde no se puede. */
    var descargarTexto: ((nombre: String, contenido: String) -> Unit)? = null
}

@OptIn(ExperimentalTime::class)
fun ahoraMs(): Long = Clock.System.now().toEpochMilliseconds()

/** Fecha y hora actual en formato ISO-8601 (UTC), la que espera el servidor en `creadaEn` / `actualizadoEn`. */
@OptIn(ExperimentalTime::class)
fun ahoraIso(): String = Clock.System.now().toString()

/** `AAAA-MM-DD` en UTC, [dias] días después de hoy (algoritmo de días civiles, sin bibliotecas extra). */
internal fun fechaUtc(dias: Int): String {
    val z = ahoraMs() / 86_400_000L + dias + 719_468L
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}
