package com.logue

import kotlinx.serialization.Serializable

@Serializable
data class User(val username: String, val password: String, val role: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val username: String, val role: String)