package com.logue.frontend

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

// Navegador: el backend corre en la misma computadora
actual val BASE_URL: String = "http://localhost:8081"
