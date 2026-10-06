package com.micongre.app

import android.app.Application
import com.micongre.app.data.AccountStore
import com.micongre.app.data.ActiveSession
import com.micongre.app.data.ApiClient
import com.micongre.app.data.ApiService
import com.micongre.app.data.AuthRepository
import com.micongre.app.data.FixedSession
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

	/** API de la cuenta activa (lo que muestra la pantalla). */
	val apiService: ApiService = ApiClient(ActiveSession(accounts, prefs)).service

	private val perAccount = mutableMapOf<String, ApiService>()

	/** API de una cuenta concreta (para revisar sus notificaciones y registrar el teléfono aunque no esté activa). */
	@Synchronized
	fun apiFor(accountId: String): ApiService = perAccount.getOrPut(accountId) { ApiClient(FixedSession(accounts, accountId)).service }
}
