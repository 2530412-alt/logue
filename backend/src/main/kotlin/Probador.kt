package com.logue

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * PRUEBAS SOBRE EL SERVIDOR ALOJADO. Manda peticiones reales a un backend ya desplegado,
 * mide cuánto tarda cada una y compara el resultado con lo esperado:
 *
 *     gradlew run --args="probar https://tu-servicio.up.railway.app"
 *
 * También funciona dentro del contenedor, después de entrar con `railway ssh`:
 *
 *     java -Xmx64m -jar app.jar probar http://localhost:$PORT
 *
 * Usa los usuarios de prueba de users.json (contraseña 1234, o la que se pase como tercer
 * argumento). Sube una foto de prueba con el folio «PRUEBA-AUTOMATICA». El caso de bloqueo
 * usa un usuario inventado, así que no bloquea ninguna cuenta real. Hace menos de 20 logins,
 * el límite por minuto: si se vuelve a correr enseguida, espera un minuto.
 */
object Probador {
    private data class Caso(
        val id: String,
        val funcion: String,
        val prueba: String,
        val esperado: String,
        val obtenido: String,
        val ms: Long,
        val aprobado: Boolean
    )

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val casos = mutableListOf<Caso>()

    private class Respuesta(val status: Int, val body: String, val headers: Map<String, List<String>>, val ms: Long) {
        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    }

    private fun enviar(builder: HttpRequest.Builder): Respuesta {
        val inicio = System.nanoTime()
        val res = http.send(builder.timeout(Duration.ofSeconds(90)).build(), HttpResponse.BodyHandlers.ofString())
        return Respuesta(res.statusCode(), res.body(), res.headers().map(), (System.nanoTime() - inicio) / 1_000_000)
    }

    private fun get(url: String, token: String? = null, vararg headers: Pair<String, String>) =
        enviar(HttpRequest.newBuilder(URI(url)).GET().apply {
            token?.let { header("Authorization", "Bearer $it") }
            headers.forEach { (k, v) -> header(k, v) }
        })

    private fun login(base: String, usuario: String, contrasena: String) =
        enviar(
            HttpRequest.newBuilder(URI("$base/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"username":"$usuario","password":"$contrasena"}"""))
        )

    private fun subir(base: String, token: String, foto: ByteArray): Respuesta {
        val limite = "----aquago" + UUID.randomUUID().toString().replace("-", "")
        val cuerpo = buildList {
            add("--$limite\r\nContent-Disposition: form-data; name=\"folio\"\r\n\r\nPRUEBA-AUTOMATICA\r\n".toByteArray())
            add("--$limite\r\nContent-Disposition: form-data; name=\"foto\"; filename=\"prueba.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            add(foto)
            add("\r\n--$limite--\r\n".toByteArray())
        }.reduce { a, b -> a + b }
        return enviar(
            HttpRequest.newBuilder(URI("$base/pagos-prediales"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "multipart/form-data; boundary=$limite")
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo))
        )
    }

    private fun campo(body: String, nombre: String): String? =
        runCatching { json.parseToJsonElement(body).jsonObject[nombre]?.jsonPrimitive?.content }.getOrNull()

    private fun caso(id: String, funcion: String, prueba: String, esperado: String, r: Respuesta?, obtenido: String, ok: Boolean) {
        casos += Caso(id, funcion, prueba, esperado, obtenido, r?.ms ?: 0, ok)
        println(String.format("%-6s %-7s %-6s %5d ms  %s", id, funcion, if (ok) "OK" else "FALLA", r?.ms ?: 0, prueba))
    }

    fun ejecutar(baseUrl: String, contrasena: String = "1234") {
        val base = baseUrl.trimEnd('/')
        val inicio = LocalDateTime.now()
        println("Pruebas sobre $base · ${inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}\n")

        // La primera petición puede despertar al servidor (Render gratuito o el modo Serverless de Railway)
        val primera = get("$base/")
        caso("PS-01", "SRV-01", "La raíz responde (primera petición, incluye el arranque en frío)", "200",
            primera, "${primera.status}", primera.status == 200)

        val salud = get("$base/salud")
        caso("PS-02", "SRV-13", "Chequeo de salud público con plataforma y commit", "200, estado OK",
            salud, "${salud.status} · ${campo(salud.body, "plataforma")} · commit ${campo(salud.body, "commit")}",
            salud.status == 200 && campo(salud.body, "estado") == "OK")

        val tokens = mutableMapOf<String, String>()
        listOf("ciudadano" to "CIUDADANO", "operador" to "OPERADOR", "admin" to "ADMIN").forEachIndexed { i, (u, rol) ->
            val r = login(base, u, contrasena)
            val rolObtenido = campo(r.body, "role")
            campo(r.body, "token")?.let { tokens[u] = it }
            caso("PS-0${3 + i}", "USR-01", "Login de $u devuelve token y rol $rol", "200, rol $rol",
                r, "${r.status} · rol $rolObtenido", r.status == 200 && rolObtenido == rol && tokens[u] != null)
        }

        val mala = login(base, "ciudadano", "contrasena-incorrecta")
        caso("PS-06", "USR-01", "Contraseña incorrecta", "401", mala, "${mala.status}", mala.status == 401)

        val vacia = login(base, "", "")
        caso("PS-07", "SRV-02", "Login con campos vacíos", "400", vacia, "${vacia.status}", vacia.status == 400)

        val inventado = "prueba-bloqueo-" + UUID.randomUUID().toString().take(8)
        val intentos = (1..3).map { login(base, inventado, "x") }
        val tercero = intentos.last()
        caso("PS-08", "USR-20", "Tres intentos fallidos bloquean la cuenta", "401, 401, 429 + Retry-After",
            tercero, intentos.joinToString(", ") { "${it.status}" } + " · Retry-After ${tercero.header("Retry-After")}",
            intentos.map { it.status } == listOf(401, 401, 429) && tercero.header("Retry-After") != null)

        val sinToken = get("$base/pagos-prediales")
        caso("PS-09", "USR-16", "Consultar pagos sin token", "401", sinToken, "${sinToken.status}", sinToken.status == 401)

        val falso = get("$base/pagos-prediales", token = "token.inventado.123")
        caso("PS-10", "USR-16", "Consultar pagos con un token inventado", "401", falso, "${falso.status}", falso.status == 401)

        val ciudadano = tokens["ciudadano"]
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(256)
        var idPago: String? = null
        if (ciudadano != null) {
            val subida = subir(base, ciudadano, jpeg)
            idPago = campo(subida.body, "id")
            caso("PS-11", "PRE-05", "El ciudadano sube la foto de su recibo", "201, a nombre de ciudadano",
                subida, "${subida.status} · ${campo(subida.body, "username")}",
                subida.status == 201 && campo(subida.body, "username") == "ciudadano")

            val noImagen = subir(base, ciudadano, "esto no es una imagen".toByteArray())
            caso("PS-12", "PRE-06", "Un archivo que no es imagen se rechaza", "415", noImagen, "${noImagen.status}", noImagen.status == 415)

            val propios = get("$base/pagos-prediales", ciudadano)
            val soloSuyos = Regex("\"username\":\"([^\"]+)\"").findAll(propios.body).all { it.groupValues[1] == "ciudadano" }
            caso("PS-13", "PRE-14", "El ciudadano ve solo sus pagos", "200, todos de ciudadano",
                propios, "${propios.status} · ${if (soloSuyos) "solo suyos" else "incluye ajenos"}", propios.status == 200 && soloSuyos)

            val ajenos = get("$base/pagos-prediales?username=admin", ciudadano)
            caso("PS-14", "PRE-14", "El ciudadano pide los pagos de otro usuario", "403", ajenos, "${ajenos.status}", ajenos.status == 403)

            if (idPago != null) {
                val foto = get("$base/pagos-prediales/$idPago/foto", ciudadano)
                caso("PS-15", "PRE-11", "El ciudadano descarga la foto de su pago", "200 image/jpeg",
                    foto, "${foto.status} ${foto.header("Content-Type")}", foto.status == 200 && foto.header("Content-Type")?.startsWith("image/jpeg") == true)
            }
        }

        tokens["operador"]?.let { operador ->
            val r = subir(base, operador, jpeg)
            caso("PS-16", "USR-17", "El repartidor no puede registrar pagos", "403", r, "${r.status}", r.status == 403)
        }

        tokens["admin"]?.let { admin ->
            val r = get("$base/pagos-prediales", admin)
            caso("PS-17", "PRE-11", "El delegado consulta todos los pagos", "200", r, "${r.status}", r.status == 200)
            if (idPago != null) {
                val f = get("$base/pagos-prediales/$idPago/foto", admin)
                caso("PS-18", "PRE-11", "El delegado abre la foto de un ciudadano", "200", f, "${f.status}", f.status == 200)
            }
        }

        val cors = enviar(
            HttpRequest.newBuilder(URI("$base/login")).method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://sitio-ajeno.example")
                .header("Access-Control-Request-Method", "POST")
        )
        caso("PS-19", "SRV-07", "CORS rechaza un dominio que no es el del panel web", "sin Access-Control-Allow-Origin",
            cors, "${cors.status} · ${cors.header("Access-Control-Allow-Origin") ?: "sin encabezado"}",
            cors.header("Access-Control-Allow-Origin") == null)

        // ---------------- Rendimiento: series de peticiones seguidas ----------------
        println("\nRendimiento (ms, incluye el viaje por internet):")
        val series = buildList {
            add("GET /salud" to (1..15).map { get("$base/salud") })
            add("POST /login" to (1..8).map { login(base, "admin", contrasena) })
            ciudadano?.let { t -> add("GET /pagos-prediales" to (1..15).map { get("$base/pagos-prediales", t) }) }
        }
        val filasRendimiento = series.map { (ruta, respuestas) ->
            val ok = respuestas.count { it.status in 200..299 }
            val t = respuestas.map { it.ms }.sorted()
            val p95 = t[((t.size - 1) * 0.95).toInt()]
            val fila = listOf<Any>(ruta, "$ok/${t.size}", t.first(), t[t.size / 2], t.average().toLong(), p95, t.last())
            println(String.format("  %-22s exitosas %-6s mín %4d  mediana %4d  promedio %4d  p95 %4d  máx %4d", *fila.toTypedArray<Any>()))
            fila
        }

        val aprobados = casos.count { it.aprobado }
        println("\nResultado: $aprobados de ${casos.size} casos aprobados (${aprobados * 100 / casos.size} %)")

        val archivo = File("pruebas-servidor-${inicio.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.csv")
        // \uFEFF al inicio: así Excel abre el CSV como UTF-8 y muestra bien los acentos
        archivo.writeText("\uFEFF" + buildString {
            appendLine("ID,Función,Prueba,Esperado,Obtenido,ms,Resultado")
            casos.forEach { c ->
                appendLine(listOf(c.id, c.funcion, c.prueba, c.esperado, c.obtenido, c.ms, if (c.aprobado) "Aprobado" else "Fallido")
                    .joinToString(",") { "\"${it.toString().replace("\"", "'")}\"" })
            }
            appendLine()
            appendLine("Ruta,Exitosas,Mín,Mediana,Promedio,p95,Máx")
            filasRendimiento.forEach { appendLine(it.joinToString(",")) }
        })
        println("Tabla guardada en ${archivo.absolutePath}")
    }
}
