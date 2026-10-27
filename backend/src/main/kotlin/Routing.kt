package com.logue

import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Lee los usuarios de prueba desde `resources/users.json`.
 * NOTA: las contraseñas están en texto plano porque es un backend de práctica;
 * en un sistema real se guardarían con hash (por ejemplo bcrypt) y en una base de datos.
 */
fun loadUsers(): List<User> {
    val text = User::class.java.getResource("/users.json")?.readText()
        ?: error("No se encontró users.json en src/main/resources")
    return Json.decodeFromString(text)
}

/** Carpeta donde se guardan las fotos de pagos prediales (variable de entorno PAGOS_DIR, o `uploads/pagos`). */
fun defaultPagoStore() = PagoStore(File(System.getenv("PAGOS_DIR") ?: "uploads/pagos"))

/** Rutas del backend. Los usuarios y el almacén de fotos se pueden inyectar en pruebas. */
fun Application.configureRouting(
    users: List<User> = loadUsers(),
    pagos: PagoStore = defaultPagoStore(),
    remote: RemoteCopier? = loadRemoteCopier()
) {
    routing {
        get("/") {
            call.respondText("Logue backend funcionando")
        }

        // POST /login  ->  200 + {username, role} | 400 solicitud inválida | 401 credenciales incorrectas
        post("/login") {
            val req = try {
                call.receive<LoginRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Solicitud inválida")
                return@post
            }

            if (req.username.isBlank() || req.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Usuario y contraseña son obligatorios")
                return@post
            }

            val user = users.find { it.username == req.username.trim() && it.password == req.password }
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(LoginResponse(user.username, user.role))
            }
        }

        // POST /pagos-prediales (multipart: username, folio opcional, foto)
        //   -> 201 + PagoPredial | 400 datos inválidos | 401 usuario desconocido
        //      413 foto muy grande | 415 no es JPEG ni PNG
        post("/pagos-prediales") {
            val declared = call.request.contentLength()
            if (declared != null && declared > MAX_FOTO_BYTES + 64 * 1024) {
                call.respond(HttpStatusCode.PayloadTooLarge, "La foto pesa más de 10 MB")
                return@post
            }

            var username = ""
            var folio = ""
            var fotoRecibida: ByteArray? = null
            try {
                call.receiveMultipart().forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> when (part.name) {
                            "username" -> username = part.value.trim()
                            "folio" -> folio = part.value.trim()
                        }
                        is PartData.FileItem -> if (part.name == "foto") fotoRecibida = part.provider().toByteArray()
                        else -> {}
                    }
                    part.dispose()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Solicitud inválida")
                return@post
            }

            val foto = fotoRecibida ?: ByteArray(0)
            when {
                username.isBlank() || username.length > 50 ->
                    call.respond(HttpStatusCode.BadRequest, "Usuario inválido")
                folio.length > 40 ->
                    call.respond(HttpStatusCode.BadRequest, "El folio no puede pasar de 40 caracteres")
                users.none { it.username == username } ->
                    call.respond(HttpStatusCode.Unauthorized, "Usuario desconocido")
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
                        // El disco local es la copia principal. Si hay SFTP configurado, la copia al
                        // servidor remoto se hace aparte para no hacer esperar al usuario ni fallar el pago.
                        remote?.let { copier ->
                            val archivos = pagos.files(pago.id)
                            val app = call.application
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
                        call.respond(HttpStatusCode.Created, pago)
                    }
                }
            }
        }

        // GET /pagos-prediales?username=...  ->  lista de pagos de ese usuario
        get("/pagos-prediales") {
            val username = call.request.queryParameters["username"]?.trim()
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Falta el parámetro username")
            } else {
                call.respond(pagos.list(username))
            }
        }

        // GET /pagos-prediales/{id}/foto  ->  la imagen guardada (404 si no existe)
        get("/pagos-prediales/{id}/foto") {
            val found = pagos.photo(call.parameters["id"].orEmpty())
            if (found == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val (pago, file) = found
                call.respondBytes(file.readBytes(), ContentType.parse(pago.tipo))
            }
        }
    }
}
