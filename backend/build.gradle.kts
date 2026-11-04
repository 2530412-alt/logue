
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.logue"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "com.logue.MainKt"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.cors)
    // Fase 1 · seguridad: token JWT (USR-16), límite de peticiones (SRV-14) y respuestas de error (413)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.rateLimit)
    implementation(ktorLibs.server.statusPages)
    // Registro de cada petición (método, ruta, código y duración): lo que se ve con `railway logs` (SRV-12)
    implementation(ktorLibs.server.callLogging)
    implementation(libs.logback.classic)
    // Contraseñas con hash BCrypt (USR-15)
    implementation(libs.bcrypt)
    // SSH / SFTP: copia de las fotos de pagos prediales a un servidor remoto
    implementation("com.hierynomus:sshj:0.39.0")

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
