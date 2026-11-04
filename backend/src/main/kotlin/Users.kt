package com.logue

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.serialization.json.Json

/**
 * CONTRASEÑAS (USR-15). El servidor guarda solo el hash BCrypt: aunque alguien copie
 * `users.json` o la base de datos, no puede leer las contraseñas. BCrypt agrega una sal
 * aleatoria y es lento a propósito (costo 10 ≈ 1 024 rondas), así que probar contraseñas
 * por fuerza bruta sale muy caro.
 */
object Passwords {
    private const val COST = 10

    /** Se compara contra este hash cuando el usuario no existe: la respuesta tarda lo mismo y no revela qué cuentas hay. */
    private val dummyHash: String by lazy { hash("usuario-que-no-existe") }

    fun hash(plain: String): String = BCrypt.withDefaults().hashToString(COST, plain.toCharArray())

    fun matches(plain: String, hash: String?): Boolean =
        BCrypt.verifyer().verify(plain.toCharArray(), hash ?: dummyHash).verified && hash != null
}

/**
 * De dónde salen los usuarios. Hoy es `users.json` ([JsonUserRepository]); en la Fase 2 se
 * agrega otra implementación que lea la tabla de usuarios de MySQL, y el resto del backend
 * (login, token, permisos) no cambia.
 */
interface UserRepository {
    fun find(username: String): User?
}

class JsonUserRepository(users: List<User>) : UserRepository {
    private val byName = users.associateBy { it.username.lowercase() }

    override fun find(username: String): User? = byName[username.trim().lowercase()]
}

/**
 * Lee los usuarios de prueba desde `resources/users.json` (lo pide la práctica).
 * Cada usuario trae `passwordHash` (BCrypt), no la contraseña.
 */
fun loadUsers(): List<User> {
    val text = User::class.java.getResource("/users.json")?.readText()
        ?: error("No se encontró users.json en src/main/resources")
    return Json.decodeFromString(text)
}
