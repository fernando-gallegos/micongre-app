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
	/** Id de la cuenta (para guardar sus datos en el teléfono); null si no hay sesión. */
	suspend fun accountId(): String?
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
	override suspend fun accountId() = accounts.active()?.id
}

/** Una cuenta concreta, esté o no activa (para revisar sus notificaciones y registrar el teléfono en segundo plano). */
class FixedSession(private val accounts: AccountStore, private val id: String) : AccountSession {
	override suspend fun access() = accounts.accessOf(id)
	override suspend fun refreshToken() = accounts.refreshOf(id)
	override suspend fun update(accessToken: String, refreshToken: String?) = accounts.updateTokens(id, accessToken, refreshToken)
	override suspend fun expire() = accounts.markExpired(id)
	override suspend fun server() = accounts.get(id)?.server
	override suspend fun accountId(): String? = id
}

/** Sin sesión, solo para hablar con un sitio (iniciar sesión o activar una cuenta nueva). */
class AnonymousSession(private val server: String) : AccountSession {
	override suspend fun access(): String? = null
	override suspend fun refreshToken(): String? = null
	override suspend fun update(accessToken: String, refreshToken: String?) {}
	override suspend fun expire() {}
	override suspend fun server() = server
	override suspend fun accountId(): String? = null
}

/** Un access token vive 15 minutos (ver jwt.ts del backend); cuando el servidor devuelve 401 este Authenticator llama a
 * /api/auth/refresh una sola vez y reintenta — igual que tendría que hacerlo cualquier cliente móvil según el contrato
 * ya documentado en el backend. */
class ApiClient(
	private val session: AccountSession,
	/** Con caché, las lecturas se guardan en el teléfono y se pueden ver sin internet. */
	private val cache: OfflineCache? = null,
	private val isConnected: () -> Boolean = { true },
) {
	private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

	private val plainClient = OkHttpClient.Builder().build()

	private val authenticator = Authenticator { _: Route?, response: okhttp3.Response ->
		if (response.request.header("X-Retry") != null) return@Authenticator null
		val accountId = runBlocking { session.accountId() } ?: return@Authenticator null
		val usedAccess = response.request.header("Authorization")?.removePrefix("Bearer ")

		// Una sola renovación a la vez por cuenta, en toda la app: la pantalla, el trabajo de fondo y los widgets usan clientes distintos, y
		// varias peticiones paralelas reciben 401 juntas. Si cada una renovara con el mismo token, el servidor vería un token ya rotado
		// («reusado»), lo tomaría como robo y cerraría TODAS las sesiones de esa persona: eso era la «Sesión vencida» de las cuentas guardadas.
		val newAccess = synchronized(RefreshLocks.of(accountId)) {
			// Otra petición ya la renovó mientras esta esperaba: se usa ese token, sin volver a renovar.
			val current = runBlocking { session.access() }
			if (current != null && current != usedAccess) return@synchronized current

			val refreshToken = runBlocking { session.refreshToken() } ?: return@Authenticator null
			val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
				.toRequestBody("application/json".toMediaType())
			val request = Request.Builder()
				.url((runBlocking { session.server() } ?: BuildConfig.BASE_URL) + "api/auth/refresh")
				.post(body)
				.build()

			val refreshResponse = plainClient.newCall(request).execute()
			if (refreshResponse.code == 401) {
				// El servidor rechazó la sesión (vencida, revocada o reusada): no se puede recuperar sola; se marca vencida y sus datos guardados se borran.
				runBlocking {
					session.expire()
					cache?.clearAccount(accountId)
				}
				return@Authenticator null
			}
			// Cualquier otro error (servidor caído, 5xx de Cloudflare…) NO vence la sesión: la petición falla y se reintenta después.
			if (!refreshResponse.isSuccessful) return@Authenticator null

			val parsed = json.decodeFromString(RefreshResponse.serializer(), refreshResponse.body?.string() ?: "{}")
			runBlocking { session.update(parsed.accessToken, parsed.refreshToken) }
			parsed.accessToken
		}

		response.request.newBuilder()
			.header("Authorization", "Bearer $newAccess")
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
		.connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
		.addInterceptor(authInterceptor)
		.apply { if (cache != null) addInterceptor(OfflineInterceptor(cache, { session.accountId() }, isConnected)) }
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

/** Un candado por cuenta para que la renovación del token sea de una en una (ver el Authenticator de [ApiClient]). */
object RefreshLocks {
	private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
	fun of(accountId: String): Any = locks.getOrPut(accountId) { Any() }
}
