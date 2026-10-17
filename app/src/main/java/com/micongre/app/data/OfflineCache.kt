package com.micongre.app.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Intento de guardar algo sin internet: la app lo avisa con un mensaje claro en lugar de un error genérico. */
class OfflineException : IOException("Sin conexión")

/** Estado de la conexión para mostrarlo en pantalla (aviso de "sin conexión") y para refrescar solo al volver internet. */
object NetworkStatus {
	private val _offline = MutableStateFlow(false)
	/** La última lectura salió de lo guardado en el teléfono porque no hubo conexión con el servidor. */
	val offline: StateFlow<Boolean> = _offline.asStateFlow()

	private val _connected = MutableStateFlow(true)
	/** El teléfono tiene red (según el sistema). */
	val connected: StateFlow<Boolean> = _connected.asStateFlow()

	private val _blockedWrites = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
	/** Se intentó guardar o enviar algo y no había conexión. */
	val blockedWrites: SharedFlow<Unit> = _blockedWrites

	fun setOffline(value: Boolean) { _offline.value = value }
	fun setConnected(value: Boolean) { _connected.value = value }
	fun writeBlocked() { _blockedWrites.tryEmit(Unit) }
}

/** Respuestas del servidor guardadas en el teléfono, por cuenta y cifradas con la clave del Keystore (los datos son personales).
 * Sirve para abrir la app y ver lo último conocido sin internet. */
class OfflineCache(private val root: File) {
	private fun dir(account: String) = File(root, account.filter { it.isLetterOrDigit() || it == '-' || it == '_' })

	private fun fileName(url: String): String =
		MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

	fun put(account: String, url: String, body: String) {
		try {
			val d = dir(account).apply { mkdirs() }
			File(d, fileName(url)).writeText(SecureStore.encrypt(body))
			trim(d)
		} catch (e: Exception) {
			// El caché es solo una ayuda: si no se puede guardar, la app sigue igual.
		}
	}

	fun get(account: String, url: String): String? = try {
		val f = File(dir(account), fileName(url))
		if (f.exists()) SecureStore.decrypt(f.readText()) else null
	} catch (e: Exception) {
		null
	}

	/** Borra todo lo guardado de una cuenta (al cerrar sesión o quitarla del teléfono). */
	fun clearAccount(account: String) {
		dir(account).deleteRecursively()
	}

	/** Tope por cuenta: si pasa de ~40 MB se borran las respuestas más viejas. */
	private fun trim(d: File) {
		val files = d.listFiles() ?: return
		var total = files.sumOf { it.length() }
		if (total <= MAX_BYTES) return
		for (f in files.sortedBy { it.lastModified() }) {
			total -= f.length()
			f.delete()
			if (total <= MAX_BYTES * 3 / 4) break
		}
	}

	private companion object {
		const val MAX_BYTES = 40L * 1024 * 1024
	}
}

/** Lecturas (GET): se piden al servidor y, si salen bien, se guardan; sin conexión se responde con lo último guardado. Escrituras
 * (POST, PUT, PATCH, DELETE): sin conexión fallan enseguida con [OfflineException] y se avisa en pantalla. Va después de cambiar el
 * sitio de la petición, para que la clave del caché sea la dirección real. */
class OfflineInterceptor(
	private val cache: OfflineCache,
	private val account: suspend () -> String?,
	private val isConnected: () -> Boolean,
) : Interceptor {
	private val json = "application/json; charset=utf-8".toMediaType()

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()

		if (request.method != "GET") {
			if (!isConnected()) {
				NetworkStatus.writeBlocked()
				throw OfflineException()
			}
			return try {
				chain.proceed(request)
			} catch (e: IOException) {
				NetworkStatus.writeBlocked()
				throw e
			}
		}

		val accountId = runBlocking { account() }
		val key = request.url.toString()

		fun fromCache(cause: IOException): Response {
			val body = accountId?.let { cache.get(it, key) } ?: throw cause
			NetworkStatus.setOffline(true)
			return Response.Builder()
				.request(request)
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK (guardado en el teléfono)")
				.header("X-From-Cache", "1")
				.body(body.toResponseBody(json))
				.build()
		}

		if (!isConnected()) return fromCache(OfflineException())
		return try {
			val response = chain.proceed(request)
			NetworkStatus.setOffline(false)
			if (response.isSuccessful && accountId != null) {
				val type = response.body?.contentType()
				val text = response.body?.string().orEmpty()
				cache.put(accountId, key, text)
				response.newBuilder().body(text.toResponseBody(type)).build()
			} else {
				response
			}
		} catch (e: IOException) {
			fromCache(e)
		}
	}
}
