package com.logue

import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.UUID

/** Tamaño máximo de la foto de un pago predial (10 MB). */
const val MAX_FOTO_BYTES = 10 * 1024 * 1024

/**
 * Detecta el tipo de imagen por sus primeros bytes ("magic bytes"), sin confiar en el nombre
 * ni en el Content-Type que manda el cliente. Devuelve `null` si no es JPEG ni PNG.
 */
fun detectImageType(bytes: ByteArray): String? = when {
    bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
    bytes.size >= 4 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "image/png"
    else -> null
}

private fun extensionFor(type: String) = if (type == "image/png") "png" else "jpg"

/**
 * Guarda en disco las fotos de los pagos prediales: `<id>.jpg|png` con la imagen y `<id>.json`
 * con sus datos. El nombre del archivo lo genera el servidor (un UUID); nunca se usa
 * texto que venga del cliente como parte de una ruta.
 *
 * NOTA: es almacenamiento local de práctica; en un sistema real irían a una base de datos
 * y a un almacenamiento de archivos (por ejemplo S3), con respaldos.
 */
class PagoStore(private val dir: File) {
    private val json = Json { prettyPrint = true }

    init {
        dir.mkdirs()
    }

    fun save(username: String, folio: String, type: String, bytes: ByteArray): PagoPredial {
        val id = UUID.randomUUID().toString()
        val pago = PagoPredial(
            id = id,
            username = username,
            folio = folio,
            fecha = Instant.now().toString(),
            tipo = type,
            tamano = bytes.size.toLong()
        )
        File(dir, "$id.${extensionFor(type)}").writeBytes(bytes)
        File(dir, "$id.json").writeText(json.encodeToString(PagoPredial.serializer(), pago))
        return pago
    }

    /** Pagos de un usuario, del más reciente al más antiguo. */
    fun list(username: String): List<PagoPredial> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { runCatching { json.decodeFromString(PagoPredial.serializer(), it.readText()) }.getOrNull() }
            .filter { it.username == username }
            .sortedByDescending { it.fecha }

    /** Los dos archivos de un pago (foto y datos), para copiarlos a otro equipo. Vacío si el id no existe. */
    fun files(id: String): List<File> {
        val (_, photo) = photo(id) ?: return emptyList()
        return listOf(photo, File(dir, "${photo.nameWithoutExtension}.json"))
    }

    /** Foto de un pago. El id debe ser un UUID válido: así no se puede salir de la carpeta (../). */
    fun photo(id: String): Pair<PagoPredial, File>? {
        val uuid = runCatching { UUID.fromString(id).toString() }.getOrNull() ?: return null
        val metaFile = File(dir, "$uuid.json")
        if (!metaFile.isFile) return null
        val pago = runCatching { json.decodeFromString(PagoPredial.serializer(), metaFile.readText()) }.getOrNull()
            ?: return null
        val file = File(dir, "$uuid.${extensionFor(pago.tipo)}")
        return if (file.isFile) pago to file else null
    }
}
