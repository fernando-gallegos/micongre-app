package com.micongre.app.data

import com.micongre.app.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** De qué cuenta (tokens y sitio) salen las peticiones de un [ApiClient]. */
interface AccountSession {
	suspend fun access(): String?
	suspend fun refreshToken(): String?
	suspend fun update(accessToken: String, refreshToken: String?)
	/** La sesión no se pudo renovar: se marca como vencida. */
	suspend fun expire()
	suspend fun server(): String?
}

/** La cuenta que esté activa en el momento de cada petición (lo que ve la pantalla). */
class ActiveSession(private val accounts: AccountStore, private val prefs: Prefs) : AccountSession {
	override suspend fun access() = accounts.active()?.let { accounts.accessOf(it.id) }
	override suspend fun refreshToken() = accounts.active()?.let { accounts.refreshOf(it.id) }
	override suspend fun update(accessToken: String, refreshToken: String?) {
		accounts.active()?.let { accounts.updateTokens(it.id, accessToken, refreshToken) }
	}
	override suspend fun expire() {
		accounts.active()?.let { accounts.markExpired(it.id) }
	}
	override suspend fun server() = accounts.active()?.server ?: prefs.currentServerUrl()
}

/** Una cuenta concreta, esté o no activa (para revisar sus notificaciones y registrar el teléfono en segundo plano). */
class FixedSession(private val accounts: AccountStore, private val id: String) : AccountSession {
	override suspend fun access() = accounts.accessOf(id)
	override suspend fun refreshToken() = accounts.refreshOf(id)
	override suspend fun update(accessToken: String, refreshToken: String?) = accounts.updateTokens(id, accessToken, refreshToken)
	override suspend fun expire() = accounts.markExpired(id)
	override suspend fun server() = accounts.get(id)?.server
}

/** Sin sesión, solo para hablar con un sitio (iniciar sesión o activar una cuenta nueva). */
class AnonymousSession(private val server: String) : AccountSession {
	override suspend fun access(): String? = null
	override suspend fun refreshToken(): String? = null
	override suspend fun update(accessToken: String, refreshToken: String?) {}
	override suspend fun expire() {}
	override suspend fun server() = server
}

/** Un access token vive 15 minutos (ver jwt.ts del backend); cuando el servidor devuelve 401 este Authenticator llama a
 * /api/auth/refresh una sola vez y reintenta — igual que tendría que hacerlo cualquier cliente móvil según el contrato
 * ya documentado en el backend. */
class ApiClient(private val session: AccountSession) {
	private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

	private val plainClient = OkHttpClient.Builder().build()

	private val authenticator = Authenticator { _: Route?, response: okhttp3.Response ->
		if (response.request.header("X-Retry") != null) return@Authenticator null

		val refreshToken = runBlocking { session.refreshToken() } ?: return@Authenticator null

		val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
			.toRequestBody("application/json".toMediaType())
		val request = Request.Builder()
			.url((runBlocking { session.server() } ?: BuildConfig.BASE_URL) + "api/auth/refresh")
			.post(body)
			.build()

		val refreshResponse = plainClient.newCall(request).execute()
		if (!refreshResponse.isSuccessful) {
			// refresh_token_reused u otro 401 — la sesión no se puede recuperar sola.
			runBlocking { session.expire() }
			return@Authenticator null
		}

		val parsed = json.decodeFromString(RefreshResponse.serializer(), refreshResponse.body?.string() ?: "{}")
		runBlocking { session.update(parsed.accessToken, parsed.refreshToken) }

		response.request.newBuilder()
			.header("Authorization", "Bearer ${parsed.accessToken}")
			.header("X-Retry", "1")
			.build()
	}

	/** El sitio lo elige la persona (o la cuenta): aquí se cambia el servidor de cada petición por ese. */
	private val authInterceptor = okhttp3.Interceptor { chain ->
		val token = runBlocking { session.access() }
		val builder = chain.request().newBuilder()
		if (token != null) builder.header("Authorization", "Bearer $token")
		val server = runBlocking { session.server() }?.toHttpUrlOrNull()
		if (server != null) {
			builder.url(chain.request().url.newBuilder().scheme(server.scheme).host(server.host).port(server.port).build())
		}
		chain.proceed(builder.build())
	}

	private val httpClient = OkHttpClient.Builder()
		.addInterceptor(authInterceptor)
		.authenticator(authenticator)
		.apply {
			if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
		}
		.build()

	val service: ApiService = Retrofit.Builder()
		.baseUrl("http://localhost/") // se reemplaza por el sitio de la cuenta en authInterceptor
		.client(httpClient)
		.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
		.build()
		.create(ApiService::class.java)
}
