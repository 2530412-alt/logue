package com.logue

import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.toByteArray
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

private val log = LoggerFactory.getLogger("com.logue.Cuentas")

/** Misma regla que la app (MIN_PASSWORD_LENGTH en AuthValidation.kt). */
const val MIN_PASSWORD_LENGTH = 6

/** BCrypt solo usa los primeros 72 bytes de la contraseña; más largas se rechazan para no aceptar algo que no se comprueba. */
const val MAX_PASSWORD_BYTES = 72

// ------------------------------------------------------------------------------------------
//  Validaciones (las mismas reglas que la app, pero ahora también se comprueban en el servidor)
// ------------------------------------------------------------------------------------------

fun validarContrasenaNueva(contrasena: String): String? = when {
    contrasena.length < MIN_PASSWORD_LENGTH -> "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
    contrasena.toByteArray().size > MAX_PASSWORD_BYTES -> "La contraseña es demasiado larga"
    else -> null
}

fun validarTelefono(telefono: String): String? =
    if (telefono.length != 10 || !telefono.all { it.isDigit() }) "El teléfono debe tener 10 dígitos" else null

/** Datos personales y de domicilio. Se usa en el registro y al editar el perfil (campos `null` = sin cambio). */
fun validarDatosPersonales(nombre: String?, telefono: String?, calle: String?, colonia: String?): String? = when {
    nombre != null && nombre.isBlank() -> "Escribe tu nombre completo"
    nombre != null && nombre.length > 100 -> "El nombre es demasiado largo"
    telefono != null && validarTelefono(telefono) != null -> validarTelefono(telefono)
    calle != null && calle.isBlank() -> "Escribe la calle y el número de tu domicilio"
    calle != null && calle.length > 120 -> "La calle es demasiado larga"
    colonia != null && colonia.isBlank() -> "Escribe tu colonia o localidad"
    colonia != null && colonia.length > 120 -> "La colonia es demasiado larga"
    else -> null
}

fun validarUsuarioNuevo(usuario: String): String? = when {
    usuario.length < 4 -> "El usuario debe tener al menos 4 caracteres"
    usuario.length > 40 -> "El usuario es demasiado largo"
    usuario.any { it.isWhitespace() } -> "El usuario no puede llevar espacios"
    else -> null
}

// ------------------------------------------------------------------------------------------
//  Recuperación de contraseña (USR-12)
// ------------------------------------------------------------------------------------------

/** Cómo llega el código de 6 dígitos al ciudadano. La implementación real (SMS) es NOT-05. */
interface CodeSender {
    fun send(phone: String, code: String)
}

/**
 * Sustituto mientras no exista el envío de SMS (NOT-05): deja el código en el registro del servidor
 * (`railway logs` o la pestaña Logs de Render). Para enviar SMS reales basta con otra implementación
 * de [CodeSender]; el resto de la recuperación no cambia.
 */
class LogCodeSender : CodeSender {
    override fun send(phone: String, code: String) {
        log.warn("SMS no configurado (NOT-05). Código de recuperación para el teléfono que termina en ${phone.takeLast(4)}: $code")
    }
}

/**
 * Códigos de recuperación: 6 dígitos al azar, válidos [validity] y con [maxAttempts] intentos.
 * Se guardan en memoria: si el servidor se reinicia, el ciudadano pide un código nuevo.
 */
class RecoveryCodes(
    private val clock: Clock = Clock.systemUTC(),
    private val validity: Duration = Duration.ofMinutes(10),
    private val maxAttempts: Int = 5
) {
    private data class Entry(val code: String, val expiresAt: Long, val attempts: Int)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val random = SecureRandom()

    private fun key(username: String) = username.trim().lowercase()

    /** Genera un código nuevo para la cuenta (el anterior deja de servir). */
    fun issue(username: String): String {
        val code = "%06d".format(random.nextInt(1_000_000))
        entries[key(username)] = Entry(code, clock.millis() + validity.toMillis(), 0)
        return code
    }

    /** `true` si el código es el vigente. Al acertar se consume; al fallar cuenta un intento. */
    fun verify(username: String, code: String): Boolean {
        var ok = false
        entries.computeIfPresent(key(username)) { _, entry ->
            when {
                clock.millis() > entry.expiresAt || entry.attempts >= maxAttempts -> null
                MessageDigest.isEqual(entry.code.toByteArray(), code.trim().toByteArray()) -> {
                    ok = true
                    null
                }
                else -> entry.copy(attempts = entry.attempts + 1)
            }
        }
        return ok
    }
}

/** Busca la cuenta por usuario o por teléfono de 10 dígitos. */
private fun UserRepository.findByAccount(account: String): User? {
    val value = account.trim()
    if (value.isEmpty()) return null
    return find(value) ?: if (value.all { it.isDigit() }) findByPhone(value) else null
}

// ------------------------------------------------------------------------------------------
//  Lectura del formulario multipart (registro con foto del comprobante)
// ------------------------------------------------------------------------------------------

private class Formulario(val campos: Map<String, String>, val foto: ByteArray?)

private suspend fun ApplicationCall.leerFormulario(): Formulario {
    val campos = mutableMapOf<String, String>()
    var foto: ByteArray? = null
    // formFieldLimit: igual que en /pagos-prediales, Ktor deja de leer al pasar 10 MB (StatusPages lo vuelve 413)
    receiveMultipart(formFieldLimit = MAX_FOTO_BYTES.toLong() + 1).forEachPart { part ->
        when (part) {
            is PartData.FormItem -> part.name?.let { campos[it] = part.value }
            is PartData.FileItem -> if (part.name == "foto") foto = part.provider().toByteArray()
            else -> {}
        }
        part.release()
    }
    return Formulario(campos, foto)
}

// ------------------------------------------------------------------------------------------
//  Rutas públicas: registro (USR-08, USR-10, USR-11, PRE-13) y recuperación (USR-12)
// ------------------------------------------------------------------------------------------

fun Route.cuentasPublicas(
    users: UserRepository,
    pagos: PagoStore,
    remote: RemoteCopier?,
    recovery: RecoveryCodes,
    codeSender: CodeSender
) {
    rateLimit(ACCOUNTS_LIMIT) {

        // POST /registro (multipart: nombre, telefono, calle, colonia, usuario, contrasena, folio opcional, foto)
        //   -> 201 + RegistroResponse (cuenta EN_REVISION) | 400 datos inválidos
        //      409 usuario o teléfono ya registrado | 413 foto muy grande | 415 no es JPEG ni PNG
        // La foto del comprobante se guarda como el primer pago predial de la cuenta (PRE-13); el delegado la valida.
        post("/registro") {
            val form = try {
                call.leerFormulario()
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                throw e // configureErrors() la convierte en 413
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Solicitud inválida")
                return@post
            }

            val nombre = form.campos["nombre"].orEmpty().trim()
            val telefono = form.campos["telefono"].orEmpty().trim()
            val calle = form.campos["calle"].orEmpty().trim()
            val colonia = form.campos["colonia"].orEmpty().trim()
            val usuario = form.campos["usuario"].orEmpty().trim()
            val contrasena = form.campos["contrasena"].orEmpty()
            val folio = form.campos["folio"].orEmpty().trim()
            val foto = form.foto ?: ByteArray(0)

            val error = validarDatosPersonales(nombre, telefono, calle, colonia)
                ?: validarUsuarioNuevo(usuario)
                ?: validarContrasenaNueva(contrasena)
                ?: when {
                    folio.length > 40 -> "El folio no puede pasar de 40 caracteres"
                    foto.isEmpty() -> "Adjunta tu comprobante de pago predial"
                    else -> null
                }
            if (error != null) {
                call.respond(HttpStatusCode.BadRequest, error)
                return@post
            }
            if (foto.size > MAX_FOTO_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, "La foto pesa más de 10 MB")
                return@post
            }
            val tipo = detectImageType(foto)
            if (tipo == null) {
                call.respond(HttpStatusCode.UnsupportedMediaType, "La foto debe ser JPEG o PNG")
                return@post
            }
            if (users.find(usuario) != null) {
                call.respond(HttpStatusCode.Conflict, "Ese usuario ya existe, elige otro")
                return@post
            }
            if (users.findByPhone(telefono) != null) {
                call.respond(HttpStatusCode.Conflict, "Ese teléfono ya tiene una cuenta")
                return@post
            }

            val cuenta = User(
                username = usuario,
                passwordHash = Passwords.hash(contrasena),
                role = Roles.CIUDADANO,
                nombre = nombre,
                telefono = telefono,
                calle = calle,
                colonia = colonia,
                estado = EstadoCuenta.EN_REVISION
            )
            if (!users.add(cuenta)) {
                // Otro registro con el mismo usuario llegó un instante antes
                call.respond(HttpStatusCode.Conflict, "Ese usuario ya existe, elige otro")
                return@post
            }
            val pago = pagos.save(usuario, folio, tipo, foto)
            call.copiarARemoto(pagos, remote, pago.id)
            call.respond(HttpStatusCode.Created, RegistroResponse(usuario, EstadoCuenta.EN_REVISION, pago.id))
        }

        // POST /recuperacion/solicitar {cuenta} -> 202 siempre (no revela si la cuenta existe) | 400 dato inválido
        post("/recuperacion/solicitar") {
            val req = try {
                call.receive<RecuperacionSolicitud>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Solicitud inválida")
                return@post
            }
            val cuenta = req.cuenta.trim()
            when {
                cuenta.isEmpty() -> {
                    call.respond(HttpStatusCode.BadRequest, "Escribe tu usuario o teléfono")
                    return@post
                }
                cuenta.all { it.isDigit() } && cuenta.length != 10 -> {
                    call.respond(HttpStatusCode.BadRequest, "El teléfono debe tener 10 dígitos")
                    return@post
                }
            }
            val user = users.findByAccount(cuenta)
            if (user != null && user.telefono.isNotEmpty() && user.estado != EstadoCuenta.INACTIVA) {
                codeSender.send(user.telefono, recovery.issue(user.username))
            }
            call.respond(HttpStatusCode.Accepted, "Si la cuenta existe, enviamos un código al teléfono registrado")
        }

        // POST /recuperacion/confirmar {cuenta, codigo, contrasena} -> 204 contraseña cambiada | 400 código o datos inválidos
        post("/recuperacion/confirmar") {
            val req = try {
                call.receive<RecuperacionConfirmacion>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Solicitud inválida")
                return@post
            }
            validarContrasenaNueva(req.contrasena)?.let {
                call.respond(HttpStatusCode.BadRequest, it)
                return@post
            }
            val user = users.findByAccount(req.cuenta)
            if (user == null || !recovery.verify(user.username, req.codigo)) {
                call.respond(HttpStatusCode.BadRequest, "Código incorrecto o vencido. Pide uno nuevo")
                return@post
            }
            users.update(user.copy(passwordHash = Passwords.hash(req.contrasena)))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ------------------------------------------------------------------------------------------
//  Rutas con sesión: perfil (USR-21) y activación de cuentas por el delegado (USR-11)
// ------------------------------------------------------------------------------------------

private fun User.toPerfil() = Perfil(username, role, nombre, telefono, calle, colonia, estado)

fun Route.cuentasAutenticadas(users: UserRepository) {

    // GET /usuarios/yo -> el perfil de la cuenta del token
    get("/usuarios/yo") {
        val user = users.find(call.session.username)
        if (user == null) call.respond(HttpStatusCode.NotFound) else call.respond(user.toPerfil())
    }

    // PATCH /usuarios/yo {nombre?, telefono?, calle?, colonia?} -> 200 + Perfil | 400 datos inválidos | 409 teléfono repetido
    patch("/usuarios/yo") {
        val cambio = try {
            call.receive<PerfilCambio>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Solicitud inválida")
            return@patch
        }
        val nombre = cambio.nombre?.trim()
        val telefono = cambio.telefono?.trim()
        val calle = cambio.calle?.trim()
        val colonia = cambio.colonia?.trim()
        validarDatosPersonales(nombre, telefono, calle, colonia)?.let {
            call.respond(HttpStatusCode.BadRequest, it)
            return@patch
        }
        val actual = users.find(call.session.username)
        if (actual == null) {
            call.respond(HttpStatusCode.NotFound)
            return@patch
        }
        if (telefono != null && telefono != actual.telefono) {
            val otro = users.findByPhone(telefono)
            if (otro != null && !otro.username.equals(actual.username, ignoreCase = true)) {
                call.respond(HttpStatusCode.Conflict, "Ese teléfono ya tiene una cuenta")
                return@patch
            }
        }
        val nuevo = actual.copy(
            nombre = nombre ?: actual.nombre,
            telefono = telefono ?: actual.telefono,
            calle = calle ?: actual.calle,
            colonia = colonia ?: actual.colonia
        )
        users.update(nuevo)
        call.respond(nuevo.toPerfil())
    }

    // PATCH /usuarios/{username}/estado {estado} -> 200 + Perfil | 400 estado inválido | 403 no es delegado | 404
    // El delegado activa la cuenta cuando valida el comprobante (USR-11). La pantalla del delegado es PRE-12.
    patch("/usuarios/{username}/estado") {
        if (!call.requireRole(Roles.ADMIN)) return@patch
        val cambio = try {
            call.receive<CambioEstado>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Solicitud inválida")
            return@patch
        }
        if (cambio.estado !in setOf(EstadoCuenta.ACTIVA, EstadoCuenta.EN_REVISION, EstadoCuenta.INACTIVA)) {
            call.respond(HttpStatusCode.BadRequest, "Estado no válido")
            return@patch
        }
        val objetivo = users.find(call.parameters["username"].orEmpty())
        if (objetivo == null) {
            call.respond(HttpStatusCode.NotFound)
            return@patch
        }
        val nuevo = objetivo.copy(estado = cambio.estado)
        users.update(nuevo)
        call.respond(nuevo.toPerfil())
    }
}
