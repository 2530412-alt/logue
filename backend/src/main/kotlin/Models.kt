package com.logue

import kotlinx.serialization.Serializable

@Serializable
data class User(val username: String, val password: String, val role: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val username: String, val role: String)

/** Datos de un pago predial guardado: la foto del recibo vive en disco, con este mismo id. */
@Serializable
data class PagoPredial(
    val id: String,
    val username: String,
    val folio: String,
    val fecha: String,
    val tipo: String,
    val tamano: Long
)