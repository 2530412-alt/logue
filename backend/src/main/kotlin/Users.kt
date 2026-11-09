package com.logue

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

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

    /** Cuenta con ese teléfono de 10 dígitos (recuperación de contraseña, USR-12). */
    fun findByPhone(phone: String): User?

    /** Agrega una cuenta. Devuelve `false`, sin guardar nada, si el usuario ya existe (USR-10). */
    fun add(user: User): Boolean

    /** Reemplaza los datos de una cuenta que ya existe (perfil, contraseña, estado). `false` si no existe. */
    fun update(user: User): Boolean
}

/**
 * Usuarios en memoria. Si recibe un [file], cada cambio (registro, perfil, contraseña, estado) se
 * escribe ahí, para que las cuentas nuevas sobrevivan a un reinicio mientras el disco sea persistente
 * (en Render gratuito no lo es; ver SRV-09 y SRV-10).
 */
class JsonUserRepository(users: List<User>, private val file: File? = null) : UserRepository {
    private val byName = LinkedHashMap<String, User>()
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    init {
        users.forEach { byName[it.username.trim().lowercase()] = it }
    }

    @Synchronized
    override fun find(username: String): User? = byName[username.trim().lowercase()]

    @Synchronized
    override fun findByPhone(phone: String): User? =
        phone.trim().takeIf { it.isNotEmpty() }?.let { p -> byName.values.firstOrNull { it.telefono == p } }

    @Synchronized
    override fun add(user: User): Boolean {
        val key = user.username.trim().lowercase()
        if (byName.containsKey(key)) return false
        byName[key] = user
        persist()
        return true
    }

    @Synchronized
    override fun update(user: User): Boolean {
        val key = user.username.trim().lowercase()
        if (!byName.containsKey(key)) return false
        byName[key] = user
        persist()
        return true
    }

    private fun persist() {
        val target = file ?: return
        target.absoluteFile.parentFile?.mkdirs()
        target.writeText(json.encodeToString(ListSerializer(User.serializer()), byName.values.toList()))
    }
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

/** Carpeta donde el servidor guarda sus datos (variable de entorno DATA_DIR, o `uploads`). */
fun dataDir(): File = File(System.getenv("DATA_DIR") ?: "uploads")

/**
 * Repositorio real: si ya existe `<DATA_DIR>/users.json` (cuentas creadas o cambiadas desde la app)
 * se usa ese archivo; si no, se parte de los tres usuarios de prueba de `resources/users.json`.
 */
fun defaultUserRepository(): UserRepository {
    val file = File(dataDir(), "users.json")
    val users = if (file.isFile) {
        runCatching { Json.decodeFromString<List<User>>(file.readText()) }.getOrNull() ?: loadUsers()
    } else {
        loadUsers()
    }
    return JsonUserRepository(users, file)
}
