package com.logue

import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.coroutines.cancellation.CancellationException

/** Carpeta donde se guardan las fotos de pagos prediales (variable de entorno PAGOS_DIR, o `uploads/pagos`). */
fun defaultPagoStore() = PagoStore(File(System.getenv("PAGOS_DIR") ?: "uploads/pagos"))

/** Plataforma donde corre el servidor, según las variables que inyecta cada una. */
private fun platform(): String = when {
    System.getenv("RAILWAY_ENVIRONMENT_NAME") != null -> "railway"
    System.getenv("RENDER") != null -> "render"
    else -> "local"
}

/** Commit desplegado: Railway y Render lo pasan como variable de entorno cuando despliegan desde GitHub. */
private fun deployedCommit(): String? =
    (System.getenv("RAILWAY_GIT_COMMIT_SHA") ?: System.getenv("RENDER_GIT_COMMIT"))?.take(7)

/** Rutas del backend. Cada dependencia se inyecta para poder probarla. */
fun Application.configureRouting(
    users: UserRepository,
    pagos: PagoStore,
    remote: RemoteCopier?,
    tokens: TokenService,
    attempts: LoginAttempts,
    recovery: RecoveryCodes = RecoveryCodes(),
    codeSender: CodeSender = LogCodeSender(),
    solicitudes: SolicitudStore = defaultSolicitudStore(),
    clock: java.time.Clock = java.time.Clock.systemUTC()
) {
    routing {
        get("/") {
            call.respondText("Logue backend funcionando prueba")
        }

        // GET /salud -> estado, plataforma y commit desplegado (SRV-13). Público: lo usa la app para despertar al servidor
        get("/salud") {
            call.respond(
                Salud(
                    estado = "OK",
                    servicio = "AquaGo API",
                    plataforma = platform(),
                    commit = deployedCommit(),
                    segundosActivo = ManagementFactory.getRuntimeMXBean().uptime / 1000
                )
            )
        }

        // POST /login  ->  200 + {username, role, token, expiresIn} | 400 solicitud inválida
        //                  401 credenciales incorrectas | 403 cuenta en revisión o desactivada
        //                  429 cuenta bloqueada o demasiadas peticiones
        rateLimit(ACCOUNTS_LIMIT) {
            post("/login") {
                val req = try {
                    call.receive<LoginRequest>()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Solicitud inválida")
                    return@post
                }

                val username = req.username.trim()
                if (username.isBlank() || req.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Usuario y contraseña son obligatorios")
                    return@post
                }

                // Mientras la cuenta esté bloqueada ni siquiera se revisa la contraseña (USR-20)
                attempts.secondsLocked(username)?.let { seconds ->
                    call.response.header(HttpHeaders.RetryAfter, seconds.toString())
                    call.respond(HttpStatusCode.TooManyRequests, "Demasiados intentos. Espera unos minutos")
                    return@post
                }

                val user = users.find(username)
                if (user == null || !Passwords.matches(req.password, user.passwordHash)) {
                    val lockedFor = attempts.registerFailure(username)
                    if (lockedFor != null) {
                        call.response.header(HttpHeaders.RetryAfter, lockedFor.toString())
                        call.respond(HttpStatusCode.TooManyRequests, "Demasiados intentos. Espera unos minutos")
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, "Usuario o contraseña incorrectos")
                    }
                    return@post
                }

                attempts.registerSuccess(username)
                // Solo después de comprobar la contraseña: así no se revela el estado de cuentas ajenas (USR-11)
                when (user.estado) {
                    EstadoCuenta.EN_REVISION ->
                        call.respond(HttpStatusCode.Forbidden, "Tu cuenta está en revisión. Te avisaremos cuando el delegado valide tu comprobante")
                    EstadoCuenta.INACTIVA ->
                        call.respond(HttpStatusCode.Forbidden, "Tu cuenta está desactivada. Comunícate con el delegado")
                    else ->
                        call.respond(LoginResponse(user.username, user.role, tokens.create(user), tokens.validity.seconds))
                }
            }
        }

        // Registro y recuperación de contraseña: públicos, con el mismo límite de peticiones que el login
        cuentasPublicas(users, pagos, remote, recovery, codeSender)

        // Todo lo de aquí adentro exige "Authorization: Bearer <token>" (USR-16); sin token responde 401
        authenticate(AUTH_JWT) {

            cuentasAutenticadas(users)
            solicitudesRoutes(solicitudes, clock)

            // POST /pagos-prediales (multipart: folio opcional, foto). El usuario sale del token, no del formulario.
            //   -> 201 + PagoPredial | 400 datos inválidos | 401 sin sesión | 403 no es ciudadano
            //      413 foto muy grande | 415 no es JPEG ni PNG
            post("/pagos-prediales") {
                if (!call.requireRole(Roles.CIUDADANO)) return@post

                val declared = call.request.contentLength()
                if (declared != null && declared > MAX_FOTO_BYTES + 64 * 1024) {
                    call.respond(HttpStatusCode.PayloadTooLarge, "La foto pesa más de 10 MB")
                    return@post
                }

                var folio = ""
                var fotoRecibida: ByteArray? = null
                try {
                    // formFieldLimit: Ktor deja de leer al pasar 10 MB, aunque la app no mande Content-Length.
                    // Sin él aceptaba hasta 50 MB por petición en memoria antes de revisar el tamaño.
                    call.receiveMultipart(formFieldLimit = MAX_FOTO_BYTES.toLong() + 1).forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> if (part.name == "folio") folio = part.value.trim()
                            is PartData.FileItem -> if (part.name == "foto") fotoRecibida = part.provider().toByteArray()
                            else -> {}
                        }
                        part.release()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: java.io.IOException) {
                    throw e // configureErrors() la convierte en 413
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Solicitud inválida")
                    return@post
                }

                val username = call.session.username
                val foto = fotoRecibida ?: ByteArray(0)
                when {
                    folio.length > 40 ->
                        call.respond(HttpStatusCode.BadRequest, "El folio no puede pasar de 40 caracteres")
                    foto.isEmpty() ->
                        call.respond(HttpStatusCode.BadRequest, "Falta la foto")
                    foto.size > MAX_FOTO_BYTES ->
                        call.respond(HttpStatusCode.PayloadTooLarge, "La foto pesa más de 10 MB")
                    else -> {
                        val tipo = detectImageType(foto)
                        if (tipo == null) {
                            call.respond(HttpStatusCode.UnsupportedMediaType, "La foto debe ser JPEG o PNG")
                        } else {
                            val pago = pagos.save(username, folio, tipo, foto)
                            // El disco local es la copia principal; la copia por SFTP se hace aparte
                            call.copiarARemoto(pagos, remote, pago.id)
                            call.respond(HttpStatusCode.Created, pago)
                        }
                    }
                }
            }

            // GET /pagos-prediales -> el ciudadano recibe solo sus pagos (PRE-14).
            // El delegado (ADMIN) recibe todos, o los de un ciudadano con ?username=...
            get("/pagos-prediales") {
                if (!call.requireRole(Roles.CIUDADANO, Roles.ADMIN)) return@get
                val me = call.session
                val requested = call.request.queryParameters["username"]?.trim()?.takeIf { it.isNotEmpty() }
                when {
                    me.role == Roles.ADMIN ->
                        call.respond(if (requested == null) pagos.listAll() else pagos.list(requested))
                    requested != null && !requested.equals(me.username, ignoreCase = true) ->
                        call.respond(HttpStatusCode.Forbidden, "Solo puedes consultar tus propios pagos")
                    else ->
                        call.respond(pagos.list(me.username))
                }
            }

            // GET /pagos-prediales/{id}/foto -> la imagen, solo para su dueño o el delegado.
            // A los demás se les responde 404 (y no 403) para no confirmar que ese pago existe.
            get("/pagos-prediales/{id}/foto") {
                val me = call.session
                val found = pagos.photo(call.parameters["id"].orEmpty())
                if (found == null || (me.role != Roles.ADMIN && found.first.username != me.username)) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val (pago, file) = found
                    call.respondBytes(file.readBytes(), ContentType.parse(pago.tipo))
                }
            }
        }
    }
}

/**
 * Si hay SFTP configurado, copia la foto y sus datos al servidor remoto en segundo plano, para no
 * hacer esperar al usuario ni fallar el pago si el servidor remoto no responde.
 */
fun ApplicationCall.copiarARemoto(pagos: PagoStore, remote: RemoteCopier?, pagoId: String) {
    val copier = remote ?: return
    val archivos = pagos.files(pagoId)
    val app = application
    app.launch(Dispatchers.IO) {
        for (archivo in archivos) {
            try {
                copier.copy(archivo, archivo.name)
            } catch (e: Exception) {
                app.log.warn("No se pudo copiar ${archivo.name} por SFTP: ${e.message}")
            }
        }
    }
}
