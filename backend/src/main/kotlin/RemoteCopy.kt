package com.logue

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.xfer.FileSystemFile
import java.io.File

/** Copia un archivo a otro equipo. Es una interfaz para poder probar las rutas sin un servidor SSH real. */
fun interface RemoteCopier {
    fun copy(file: File, remoteName: String)
}

/** Datos de conexión al servidor remoto (se leen de variables de entorno, nunca del código). */
data class SftpConfig(
    val host: String,
    val port: Int,
    val user: String,
    val password: String?,
    val keyPath: String?,
    val remoteDir: String,
    /** Huella de la llave del servidor, por ejemplo `SHA256:abc...`. Evita conectarse a un servidor falso. */
    val hostFingerprint: String
)

/**
 * Copia archivos por SFTP, que viaja dentro de una conexión SSH cifrada.
 * Verifica siempre la huella de la llave del servidor: no hay modo de "ignorar" la verificación.
 */
class SftpCopier(private val config: SftpConfig) : RemoteCopier {
    override fun copy(file: File, remoteName: String) {
        SSHClient().use { ssh ->
            ssh.connectTimeout = 10_000
            ssh.timeout = 30_000
            ssh.addHostKeyVerifier(config.hostFingerprint)
            ssh.connect(config.host, config.port)
            if (config.keyPath != null) {
                ssh.authPublickey(config.user, config.keyPath)
            } else {
                ssh.authPassword(config.user, config.password)
            }
            ssh.newSFTPClient().use { sftp ->
                sftp.mkdirs(config.remoteDir)
                sftp.put(FileSystemFile(file), "${config.remoteDir.trimEnd('/')}/$remoteName")
            }
        }
    }
}

/**
 * Lee la configuración SFTP de las variables de entorno:
 *
 * | Variable | Para qué sirve |
 * |---|---|
 * | `SFTP_HOST` | Dirección del servidor. **Si no existe, la copia remota queda apagada** |
 * | `SFTP_PORT` | Puerto (por defecto 22) |
 * | `SFTP_USER` | Usuario del servidor |
 * | `SFTP_KEY_PATH` o `SFTP_PASSWORD` | Llave privada (recomendado) o contraseña |
 * | `SFTP_REMOTE_DIR` | Carpeta de destino (por defecto `uploads/pagos`) |
 * | `SFTP_HOST_FINGERPRINT` | Huella de la llave del servidor (obligatoria) |
 *
 * Devuelve `null` si SFTP no está configurado. Si está a medias, falla al arrancar con un
 * mensaje claro, en lugar de descubrir el problema cuando llegue el primer pago.
 */
fun loadRemoteCopier(env: Map<String, String> = System.getenv()): RemoteCopier? {
    fun value(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }

    val host = value("SFTP_HOST") ?: return null
    val user = value("SFTP_USER") ?: error("SFTP_HOST está definido pero falta SFTP_USER")
    val fingerprint = value("SFTP_HOST_FINGERPRINT")
        ?: error("Falta SFTP_HOST_FINGERPRINT: sin la huella del servidor no se conecta (evita servidores falsos)")
    val keyPath = value("SFTP_KEY_PATH")
    val password = value("SFTP_PASSWORD")
    if (keyPath == null && password == null) error("Falta SFTP_KEY_PATH o SFTP_PASSWORD")
    val port = value("SFTP_PORT")?.toIntOrNull() ?: 22

    return SftpCopier(
        SftpConfig(
            host = host,
            port = port,
            user = user,
            password = password,
            keyPath = keyPath,
            remoteDir = value("SFTP_REMOTE_DIR") ?: "uploads/pagos",
            hostFingerprint = fingerprint
        )
    )
}
