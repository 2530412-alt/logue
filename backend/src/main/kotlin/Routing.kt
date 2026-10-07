package com.logue

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

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

/** Rutas del backend. Los usuarios se cargan una sola vez (se pueden inyectar en pruebas). */
fun Application.configureRouting(users: List<User> = loadUsers()) {
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
    }
}
