package com.micongre.app.data

import retrofit2.Response

/** Error de la API con su código HTTP: 401 sin sesión, 403 sin acceso (se trata como vacío, no como fallo). */
class ApiException(val code: Int, message: String) : Exception(message)

/** Ejecuta una llamada y la devuelve como Result; cualquier respuesta no exitosa queda como [ApiException]. */
suspend fun <T> api(block: suspend () -> Response<T>): Result<T> = try {
	val response = block()
	if (response.isSuccessful) {
		@Suppress("UNCHECKED_CAST")
		Result.success((response.body() ?: Unit) as T)
	} else {
		Result.failure(ApiException(response.code(), "http_${response.code()}"))
	}
} catch (e: Exception) {
	Result.failure(e)
}

val Throwable.isForbidden get() = this is ApiException && code == 403
