package com.micongre.app

import android.app.Application
import com.micongre.app.data.AccountStore
import com.micongre.app.data.ActiveSession
import com.micongre.app.data.ApiClient
import com.micongre.app.data.ApiService
import com.micongre.app.data.AuthRepository
import com.micongre.app.data.FixedSession
import com.micongre.app.data.NetworkStatus
import com.micongre.app.data.OfflineCache
import com.micongre.app.data.Outbox
import com.micongre.app.data.OutboxDb
import com.micongre.app.data.OutboxSender
import com.micongre.app.data.Prefs

/** Contenedor de dependencias a mano — sin Hilt/Koin, no hace falta para el
 * tamaño de esta app. Un solo punto (`AppContainer.of(context)`) desde donde
 * cada pantalla saca lo que necesita. */
class MicongreApplication : Application() {
	lateinit var container: AppContainer
		private set

	override fun onCreate() {
		super.onCreate()
		container = AppContainer(this)
		com.micongre.app.push.ensureChannel(this)
		// Lo que se guardó sin internet y quedó sin enviar (la app se cerró o el teléfono se reinició): se retoma al abrir.
		if (container.outbox.ops.value.any { it.state == com.micongre.app.data.OpState.PENDING }) com.micongre.app.push.OutboxScheduler.kick(this)
	}
}

class AppContainer(val app: Application) {
	val accounts = AccountStore(app)
	val prefs = Prefs(app)
	val authRepository = AuthRepository(accounts, prefs)

	/** Lecturas guardadas en el teléfono (cifradas, por cuenta) para abrir la app sin internet. */
	val offlineCache = OfflineCache(java.io.File(app.filesDir, "offline")).also { cache ->
		// Llegó Mi Panel nuevo: se redibujan los widgets (sin temporizador propio, para no gastar batería).
		cache.onPut = { _, url ->
			if (url.endsWith("/api/panel/summary")) com.micongre.app.widget.WidgetUpdater.request(app, refreshPreaching = true)
			else if (url.contains("/api/preaching?month=") || url.endsWith("/api/reports/s4/service-year")) com.micongre.app.widget.WidgetUpdater.request(app)
		}
	}

	/** Escrituras hechas sin internet, guardadas en el teléfono hasta que el servidor las confirme (informes, asistencia, territorios, avisos). */
	val outbox = Outbox(OutboxDb(app))
	val outboxSender = OutboxSender(this)

	private val connectivity = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

	fun isConnected(): Boolean {
		val network = connectivity.activeNetwork ?: return false
		return connectivity.getNetworkCapabilities(network)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
	}

	init {
		NetworkStatus.setConnected(isConnected())
		try {
			connectivity.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
				override fun onAvailable(network: android.net.Network) {
					NetworkStatus.setConnected(true)
					// Volvió internet: se envía lo que quedó pendiente.
					if (outbox.ops.value.any { it.state == com.micongre.app.data.OpState.PENDING }) com.micongre.app.push.OutboxScheduler.kick(app)
				}
				override fun onLost(network: android.net.Network) = NetworkStatus.setConnected(isConnected())
			})
		} catch (e: Exception) {
			// Sin el aviso de red la app funciona igual: cada petición decide por sí sola.
		}
	}

	/** API de la cuenta activa (lo que muestra la pantalla). */
	val apiService: ApiService = ApiClient(ActiveSession(accounts, prefs), offlineCache, ::isConnected).service

	private val perAccount = mutableMapOf<String, ApiService>()

	/** API de una cuenta concreta (para revisar sus notificaciones y registrar el teléfono aunque no esté activa). */
	@Synchronized
	fun apiFor(accountId: String): ApiService = perAccount.getOrPut(accountId) { ApiClient(FixedSession(accounts, accountId), offlineCache, ::isConnected).service }
}
