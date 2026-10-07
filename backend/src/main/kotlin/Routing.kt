package com.logue

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Application.configureRouting() {
    val users: List<User> = Json.decodeFromString(
        object {}.javaClass.getResource("/users.json")!!.readText()
    )

    routing {
        get("/") {
            call.respondText("Logue backend funcionando")
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val user = users.find { it.username == req.username && it.password == req.password }
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(LoginResponse(user.username, user.role))
            }
        }
    }
}