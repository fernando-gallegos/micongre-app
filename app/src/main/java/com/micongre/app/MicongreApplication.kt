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
	}
}

class AppContainer(val app: Application) {
	val accounts = AccountStore(app)
	val prefs = Prefs(app)
	val authRepository = AuthRepository(accounts, prefs)

	/** Lecturas guardadas en el teléfono (cifradas, por cuenta) para abrir la app sin internet. */
	val offlineCache = OfflineCache(java.io.File(app.filesDir, "offline"))

	private val connectivity = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

	fun isConnected(): Boolean {
		val network = connectivity.activeNetwork ?: return false
		return connectivity.getNetworkCapabilities(network)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
	}

	init {
		NetworkStatus.setConnected(isConnected())
		try {
			connectivity.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
				override fun onAvailable(network: android.net.Network) = NetworkStatus.setConnected(true)
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
