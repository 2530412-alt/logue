package com.logue.frontend

import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException

/** Posibles resultados de subir la foto de un pago predial. */
sealed interface UploadResult {
    data class Success(val pago: PagoPredialResponse) : UploadResult
    data object TooLarge : UploadResult
    data object NotImage : UploadResult
    data object ServerError : UploadResult
    data object NetworkError : UploadResult
}

/** Llama a `POST /pagos-prediales` con la foto del recibo. Nunca lanza excepciones. */
suspend fun subirPagoPredial(username: String, folio: String, foto: ByteArray): UploadResult = try {
    val res = client.submitFormWithBinaryData(
        url = "$BASE_URL/pagos-prediales",
        formData = formData {
            append("username", username)
            append("folio", folio.trim())
            append("foto", foto, Headers.build {
                append(HttpHeaders.ContentType, "image/jpeg")
                append(HttpHeaders.ContentDisposition, "filename=\"pago.jpg\"")
            })
        }
    ) {
        // El cliente global corta a los 8 s; una foto de varios MB por datos móviles necesita más.
        timeout { requestTimeoutMillis = 60_000 }
    }
    when (res.status) {
        HttpStatusCode.Created -> UploadResult.Success(res.body())
        HttpStatusCode.PayloadTooLarge -> UploadResult.TooLarge
        HttpStatusCode.UnsupportedMediaType -> UploadResult.NotImage
        else -> UploadResult.ServerError
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    UploadResult.NetworkError
}
