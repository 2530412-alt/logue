package com.logue.frontend

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

val client = HttpClient {
    install(ContentNegotiation) { json() }
}

// Emulador Android: http://10.0.2.2:8081 | Web: http://localhost:8081
const val BASE_URL = "http://10.0.2.2:8081"

suspend fun login(username: String, password: String): LoginResponse? {
    val res = client.post("$BASE_URL/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(username, password))
    }
    return if (res.status == HttpStatusCode.OK) res.body() else null
}