package com.logue.frontend

// Puente con aquago-web.js (mismo código para la versión JS y la versión Wasm de la página).
// Cada función es una sola llamada a JavaScript, que es lo que permiten ambas versiones.

private fun jsLeer(clave: String): String? = js("window.aquago.leer(clave)")
private fun jsGuardar(clave: String, valor: String): Unit = js("window.aquago.guardar(clave, valor)")
private fun jsBorrar(clave: String): Unit = js("window.aquago.borrar(clave)")
private fun jsFecha(dias: Int): String = js("window.aquago.fecha(dias)")
private fun jsServidor(): String = js("window.aquago.servidor()")
private fun jsEnLinea(): Boolean = js("window.aquago.enLinea()")
private fun jsGoogle(clientId: String, alEntrar: (String) -> Unit): Unit = js("window.aquago.google(clientId, alEntrar)")
private fun jsCaptcha(clave: String, alResponder: (String) -> Unit): Unit = js("window.aquago.captcha(clave, alResponder)")
private fun jsReiniciarCaptcha(): Unit = js("window.aquago.reiniciarCaptcha()")
private fun jsUbicar(id: String, x: Double, y: Double, ancho: Double, alto: Double, visible: Boolean): Unit =
    js("window.aquago.ubicar(id, x, y, ancho, alto, visible)")
private fun jsDescargar(nombre: String, contenido: String): Unit = js("window.aquago.descargar(nombre, contenido)")

actual fun iniciarWeb() {
    // ?servidor=local, o la página abierta desde localhost -> backend de la computadora (localhost:8081)
    configurarServidor(jsServidor())
    Plataforma.esWeb = true
    Plataforma.almacen = object : AlmacenLocal {
        override fun leer(clave: String): String? = jsLeer(clave)
        override fun guardar(clave: String, valor: String) = jsGuardar(clave, valor)
        override fun borrar(clave: String) = jsBorrar(clave)
    }
    Plataforma.fechaLocal = { dias -> jsFecha(dias) }
    Plataforma.hayConexion = { jsEnLinea() }
    Plataforma.botonGoogle = { clientId, alEntrar -> jsGoogle(clientId, alEntrar) }
    Plataforma.casillaCaptcha = { clave, alResponder -> jsCaptcha(clave, alResponder) }
    Plataforma.reiniciarCaptcha = { jsReiniciarCaptcha() }
    Plataforma.ubicarElementoWeb = { id, x, y, ancho, alto, visible ->
        jsUbicar(id, x.toDouble(), y.toDouble(), ancho.toDouble(), alto.toDouble(), visible)
    }
    Plataforma.descargarTexto = { nombre, contenido -> jsDescargar(nombre, contenido) }
}
