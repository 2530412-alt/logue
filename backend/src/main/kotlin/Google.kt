package com.logue

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.concurrent.TimeUnit

// =====================================================================================================
//  VALIDACIÓN CON GOOGLE (requisito de la app web)
//
//  1) «Iniciar sesión con Google»: la página web muestra el botón de Google (Google Identity Services).
//     Google le entrega a la página un ID token (JWT firmado por Google) y la página lo manda a
//     POST /login/google. Aquí se comprueba la firma con las llaves públicas de Google, que el token
//     sea para NUESTRA app (aud = GOOGLE_CLIENT_ID), que no haya vencido y que Google haya verificado
//     el correo. Si una cuenta de AquaGo tiene ese correo, se entrega el token de AquaGo de siempre.
//     Nunca se crea una cuenta nueva por entrar con Google: el delegado sigue controlando el alta.
//
//  2) reCAPTCHA v2 («No soy un robot») en el login de la página web. La página manda la respuesta del
//     captcha y aquí se valida con Google (siteverify) usando la clave secreta.
//
//  VARIABLES DE ENTORNO (nunca se suben a GitHub):
//    GOOGLE_CLIENT_ID   ID de cliente OAuth de tipo "Aplicación web" (termina en .apps.googleusercontent.com)
//    RECAPTCHA_SECRET   clave secreta de reCAPTCHA v2 (la clave del sitio va en la página web)
//  Sin GOOGLE_CLIENT_ID, /login/google responde 503. Sin RECAPTCHA_SECRET no se exige captcha.
// =====================================================================================================

private val logGoogle = LoggerFactory.getLogger("com.logue.Google")

/** Datos que se usan del ID token de Google. */
data class IdentidadGoogle(val correo: String, val nombre: String)

/** Comprueba un ID token de Google. `null` si no es válido. */
fun interface VerificadorGoogle {
    fun verificar(idToken: String): IdentidadGoogle?
}

/** Implementación real: firma RS256 con las llaves públicas de Google (se guardan en caché). */
class VerificadorGoogleJwks(private val clientId: String) : VerificadorGoogle {
    private val llaves = JwkProviderBuilder(URI("https://www.googleapis.com/oauth2/v3/certs").toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    override fun verificar(idToken: String): IdentidadGoogle? = try {
        val sinVerificar = JWT.decode(idToken)
        val llave = llaves.get(sinVerificar.keyId).publicKey as RSAPublicKey
        val jwt = JWT.require(Algorithm.RSA256(llave, null))
            .withIssuer("accounts.google.com", "https://accounts.google.com")
            .withAudience(clientId)
            .acceptLeeway(60)
            .build()
            .verify(idToken)
        val correo = jwt.getClaim("email").asString().orEmpty()
        val verificado = jwt.getClaim("email_verified").asBoolean() == true
        if (correo.isBlank() || !verificado) null
        else IdentidadGoogle(correo, jwt.getClaim("name").asString().orEmpty())
    } catch (e: Exception) {
        logGoogle.warn("ID token de Google rechazado: ${e.javaClass.simpleName}")
        null
    }
}

fun verificadorGoogleDesdeEntorno(env: Map<String, String> = System.getenv()): VerificadorGoogle? =
    env["GOOGLE_CLIENT_ID"]?.trim()?.takeIf { it.isNotEmpty() }?.let { VerificadorGoogleJwks(it) }

/** Cuerpo de `POST /login/google`. [credential] es el ID token que da el botón de Google. */
@Serializable
data class LoginGoogle(val credential: String, val captcha: String? = null)

// ---------------------------------------------------------------------------------------------------
//  reCAPTCHA
// ---------------------------------------------------------------------------------------------------

/** Valida la respuesta del captcha. */
interface Captcha {
    /** `false` cuando no hay clave configurada: entonces no se exige captcha. */
    val activo: Boolean
    fun valido(respuesta: String?, ip: String): Boolean
}

object CaptchaDesactivado : Captcha {
    override val activo = false
    override fun valido(respuesta: String?, ip: String) = true
}

class Recaptcha(
    private val secreto: String,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
) : Captcha {
    override val activo = true

    override fun valido(respuesta: String?, ip: String): Boolean {
        if (respuesta.isNullOrBlank() || respuesta.length > 4_000) return false
        fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
        val form = "secret=${enc(secreto)}&response=${enc(respuesta)}&remoteip=${enc(ip)}"
        return try {
            val res = http.send(
                HttpRequest.newBuilder(URI.create("https://www.google.com/recaptcha/api/siteverify"))
                    .timeout(Duration.ofSeconds(10))
                    .header("content-type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            Json.parseToJsonElement(res.body()).let { it as? JsonObject }?.get("success")?.jsonPrimitive?.booleanOrNull == true
        } catch (e: Exception) {
            logGoogle.warn("No se pudo validar el captcha con Google: ${e.javaClass.simpleName}")
            false
        }
    }
}

fun captchaDesdeEntorno(env: Map<String, String> = System.getenv()): Captcha =
    env["RECAPTCHA_SECRET"]?.trim()?.takeIf { it.isNotEmpty() }?.let { Recaptcha(it) } ?: CaptchaDesactivado

/**
 * El captcha solo se le pide a la página web: los navegadores siempre mandan el encabezado Origin
 * en estas peticiones y la app de Android no. La app móvil queda protegida por el límite de
 * peticiones y el bloqueo por intentos fallidos (USR-20).
 */
fun vieneDeNavegador(origin: String?): Boolean = !origin.isNullOrBlank()

// ---------------------------------------------------------------------------------------------------
//  Todo lo de correo y Google junto, para pasarlo al servidor (y reemplazarlo en las pruebas)
// ---------------------------------------------------------------------------------------------------

class Integraciones(
    val correos: Correos = CorreosSoloRegistro(),
    val google: VerificadorGoogle? = null,
    val captcha: Captcha = CaptchaDesactivado,
    /** Códigos de 6 dígitos para verificar el correo (mismas reglas que la recuperación: 10 min, 5 intentos). */
    val verificaciones: RecoveryCodes = RecoveryCodes(),
    /** Valores PÚBLICOS que necesita la página web (no son secretos). */
    val googleClientId: String = "",
    val recaptchaSiteKey: String = ""
)

fun integracionesDesdeEntorno(env: Map<String, String> = System.getenv()) = Integraciones(
    correos = correosDesdeEntorno(env),
    google = verificadorGoogleDesdeEntorno(env),
    captcha = captchaDesdeEntorno(env),
    googleClientId = env["GOOGLE_CLIENT_ID"]?.trim().orEmpty(),
    recaptchaSiteKey = env["RECAPTCHA_SITE_KEY"]?.trim().orEmpty()
)

/** `GET /config/web`: lo que la página web necesita saber para mostrar el botón de Google y el captcha. */
@Serializable
data class ConfigWeb(val googleClientId: String, val recaptchaSiteKey: String, val correoActivo: Boolean)
