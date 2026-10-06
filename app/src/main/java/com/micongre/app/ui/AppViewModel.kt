package com.micongre.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.micongre.app.AppContainer
import com.micongre.app.data.AccountInfo
import com.micongre.app.data.ActivatePreviewResponse
import com.micongre.app.data.ApiException
import com.micongre.app.data.DeleteAccountRequest
import com.micongre.app.data.Me
import com.micongre.app.data.ShortcutRequest
import com.micongre.app.data.api
import com.micongre.app.push.FcmRegistrar
import com.micongre.app.push.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Destinos de la app. Solo existe lo que la app Android ofrece; el resto (territorios, etc.) es solo web. */
sealed interface Dest {
	data object Panel : Dest
	data object Notifications : Dest
	data object More : Dest
	data object Profile : Dest
	data object Accounts : Dest
	data object AddAccount : Dest
	data object Meetings : Dest
	data object Preaching : Dest
	data object Publishers : Dest
	data class PublisherDetail(val id: String) : Dest
	data class Reports(val userId: String? = null) : Dest
}

/** Módulo que se puede poner como botón de la barra inferior (mismo `href` que usa la web al guardarlo). */
class ShortcutOption(val href: String, val label: String, val dest: Dest)

/** Deja solo el origen (esquema, servidor y puerto) con barra final; sin esquema se asume https. */
fun normalizeServerUrl(input: String): String? {
	var text = input.trim()
	if (text.isEmpty()) return null
	if (!text.contains("://")) text = "https://$text"
	val parsed = text.toHttpUrlOrNull() ?: return null
	if (parsed.scheme != "https" && !(parsed.scheme == "http" && com.micongre.app.BuildConfig.DEBUG)) return null
	return parsed.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
}

/** Enlace de activación recibido: sitio (con barra final) y token de un solo uso. */
data class ActivationLink(val server: String, val token: String)

/** https://<sitio>/activar/<token> → sitio y token; cualquier otra cosa es null. */
fun parseActivationLink(uri: Uri?): ActivationLink? {
	if (uri == null) return null
	val segments = uri.pathSegments
	if (segments.size != 2 || segments[0] != "activar" || segments[1].isBlank()) return null
	val host = uri.host ?: return null
	val port = if (uri.port > 0) ":${uri.port}" else ""
	val server = normalizeServerUrl("${uri.scheme}://$host$port") ?: return null
	return ActivationLink(server, segments[1])
}

/** Un sitio de Mi Congre responde JSON en /api/auth/me (401 sin sesión, 409 si aún no tiene administrador). */
private suspend fun checkServer(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
	try {
		val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
		client.newCall(Request.Builder().url(baseUrl + "api/auth/me").build()).execute().use { r ->
			val body = r.body?.string().orEmpty()
			(r.code == 401 || r.code == 409 || r.code == 200) && body.trimStart().startsWith("{") && (body.contains("\"error\"") || body.contains("\"id\""))
		}
	} catch (e: Exception) {
		false
	}
}

class AppViewModel(private val container: AppContainer) : ViewModel() {

	private val _loggedIn = MutableStateFlow<Boolean?>(null)
	val loggedIn: StateFlow<Boolean?> = _loggedIn.asStateFlow()

	private val _loginError = MutableStateFlow<String?>(null)
	val loginError: StateFlow<String?> = _loginError.asStateFlow()

	private val _me = MutableStateFlow<Me?>(null)
	val me: StateFlow<Me?> = _me.asStateFlow()

	val accent: StateFlow<String> = container.prefs.accent.stateIn(viewModelScope, SharingStarted.Eagerly, "salvia")
	val darkMode: StateFlow<String> = container.prefs.darkMode.stateIn(viewModelScope, SharingStarted.Eagerly, "system")

	/** Cuentas guardadas en este teléfono (varias personas de una familia pueden compartirlo) y cuál está activa. */
	val accounts: StateFlow<List<AccountInfo>> = container.accounts.accounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
	val activeId: StateFlow<String?> = container.accounts.activeId.stateIn(viewModelScope, SharingStarted.Eagerly, null)

	/** Pedir la huella o el PIN del teléfono para cambiar de cuenta. */
	val lockSwitch: StateFlow<Boolean> = container.prefs.lockSwitch.stateIn(viewModelScope, SharingStarted.Eagerly, true)
	fun setLockSwitch(value: Boolean) = viewModelScope.launch { container.prefs.setLockSwitch(value) }

	/** Sitio de la congregación; null = todavía no se ha configurado (primer inicio). */
	private val _serverUrl = MutableStateFlow<String?>(null)
	val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()
	private val _editingServer = MutableStateFlow(false)
	val editingServer: StateFlow<Boolean> = _editingServer.asStateFlow()
	private val _serverChecked = MutableStateFlow(false)
	val serverChecked: StateFlow<Boolean> = _serverChecked.asStateFlow()

	init {
		viewModelScope.launch {
			// Instalaciones anteriores guardaban una sola sesión: se pasa al formato de varias cuentas.
			container.accounts.migrateLegacy(container.prefs.currentServerUrl())
			val active = container.accounts.active()?.takeIf { !it.expired } ?: container.accounts.all().firstOrNull { !it.expired }
			if (active != null) {
				container.accounts.setActive(active.id)
				container.prefs.setServerUrl(active.server)
			}
			_serverUrl.value = container.prefs.currentServerUrl()
			_serverChecked.value = true
			if (active != null) {
				loadMe()
				scheduleSync()
			}
			_loggedIn.value = active != null
		}
	}

	val syncMinutes: StateFlow<Int> = container.prefs.syncMinutes.stateIn(viewModelScope, SharingStarted.Eagerly, 15)

	val fcmActive: StateFlow<Boolean> = container.prefs.fcmActive.stateIn(viewModelScope, SharingStarted.Eagerly, false)

	/** Push en tiempo real (Firebase) para las cuentas cuyo sitio lo tiene; para las demás, revisión periódica. */
	private suspend fun scheduleSync(update: Boolean = false) {
		val minutes = container.prefs.currentSyncMinutes()
		if (update) {
			// Cambió el intervalo: solo importa si se está revisando por cuenta propia (sin push en tiempo real).
			if (!container.prefs.fcmActive.first()) SyncScheduler.schedule(container.app, minutes, true)
			return
		}
		FcmRegistrar.sync(container, minutes)
	}

	/** Intervalo de la revisión de notificaciones (Mi perfil); se aplica de inmediato si hay sesión. */
	fun setSyncMinutes(minutes: Int) {
		viewModelScope.launch {
			container.prefs.setSyncMinutes(minutes)
			if (_loggedIn.value == true) scheduleSync(update = true)
		}
	}

	/** Vuelve a pedir identidad, privilegios y nombramientos (pueden cambiar mientras la app está abierta). */
	fun refreshMe() {
		if (_loggedIn.value != true) return
		viewModelScope.launch { loadMe() }
	}

	private suspend fun loadMe() {
		val activeAtRequest = container.accounts.activeIdNow()
		api { container.apiService.me() }
			.onSuccess { if (container.accounts.activeIdNow() == activeAtRequest) _me.value = it }
			.onFailure {
				// Sesión vencida o revocada: esa cuenta queda marcada para iniciar sesión de nuevo y se pasa a otra si hay.
				if (it is ApiException && it.code == 401 && activeAtRequest != null) expireActive(activeAtRequest)
			}
	}

	private suspend fun expireActive(id: String) {
		container.accounts.markExpired(id)
		val next = container.accounts.all().firstOrNull { !it.expired }
		if (next != null) {
			activate(next.id)
		} else {
			_me.value = null
			_loggedIn.value = false
			SyncScheduler.cancel(container.app)
		}
	}

	/** Valida que la dirección sea un sitio de Mi Congre y la guarda. Devuelve un mensaje de error o null si quedó bien. */
	fun saveServer(input: String, onResult: (String?) -> Unit) {
		viewModelScope.launch {
			val url = normalizeServerUrl(input)
			if (url == null) {
				onResult("Escribe una dirección válida, por ejemplo https://micongre.ejemplo.workers.dev")
				return@launch
			}
			val ok = checkServer(url)
			if (!ok) {
				onResult("No se encontró Mi Congre en esa dirección. Revisa el enlace y tu conexión.")
				return@launch
			}
			container.prefs.setServerUrl(url)
			_serverUrl.value = url
			_editingServer.value = false
			onResult(null)
		}
	}

	/** Desde el login: permite volver a escribir otra dirección. */
	fun changeServer() {
		_editingServer.value = true
	}

	fun cancelChangeServer() {
		_editingServer.value = false
	}

	/** Enlace de activación (/activar/<token>) que abrió la app; mientras exista se muestra la pantalla de activación. */
	private val _activation = MutableStateFlow<ActivationLink?>(null)
	val activation: StateFlow<ActivationLink?> = _activation.asStateFlow()

	/** Toma el enlace con el que Android abrió la app (App Link verificado de https://<sitio>/activar/<token>). */
	fun openActivationLink(uri: Uri?) {
		_activation.value = parseActivationLink(uri) ?: return
	}

	fun closeActivation() {
		_activation.value = null
	}

	suspend fun previewActivation(link: ActivationLink): Result<ActivatePreviewResponse> = container.authRepository.previewActivation(link.server, link.token)

	/** Canjea el enlace: queda la sesión iniciada como cuenta activa (las demás cuentas del teléfono siguen). */
	fun confirmActivation(link: ActivationLink, username: String, password: String, onResult: (String?) -> Unit) {
		viewModelScope.launch {
			container.authRepository.activate(link.server, link.token, username, password)
				.onSuccess {
					_serverUrl.value = link.server
					_me.value = null
					loadMe()
					scheduleSync()
					_loggedIn.value = true
					onResult(null)
				}
				.onFailure {
					onResult(
						when (it.message?.substringAfterLast('_')) {
							"409" -> "Ese usuario ya está en uso. Elige otro."
							"410" -> "Este enlace ya se usó o venció. Pide uno nuevo a un administrador."
							"400" -> "Revisa el usuario y la contraseña, o pide un enlace nuevo."
							else -> "No se pudo activar. Revisa tu conexión e intenta de nuevo."
						},
					)
				}
		}
	}

	/** Primer inicio de sesión (con el sitio ya elegido). */
	fun login(username: String, password: String) {
		viewModelScope.launch {
			_loginError.value = null
			val server = container.prefs.currentServerUrl()
			if (server == null) {
				_loginError.value = "Primero elige el sitio de tu congregación."
				return@launch
			}
			container.authRepository.login(server, username, password)
				.onSuccess { _me.value = null; loadMe(); scheduleSync(); _loggedIn.value = true }
				.onFailure { _loginError.value = "Usuario o contraseña incorrectos." }
		}
	}

	/** Agrega otra cuenta (puede ser de otro sitio) y la deja como activa; las demás siguen recibiendo sus avisos. */
	fun addAccount(serverInput: String, username: String, password: String, onResult: (String?) -> Unit) {
		viewModelScope.launch {
			val server = normalizeServerUrl(serverInput)
			if (server == null) {
				onResult("Escribe una dirección válida del sitio de la congregación.")
				return@launch
			}
			if (!checkServer(server)) {
				onResult("No se encontró Mi Congre en esa dirección. Revisa el enlace y tu conexión.")
				return@launch
			}
			container.authRepository.login(server, username, password)
				.onSuccess {
					_serverUrl.value = server
					_me.value = null
					loadMe()
					scheduleSync()
					_loggedIn.value = true
					onResult(null)
				}
				.onFailure { onResult("Usuario o contraseña incorrectos.") }
		}
	}

	/** Cambia la cuenta activa (la pantalla ya pidió la huella o el PIN si estaba activado). */
	fun switchTo(id: String) {
		viewModelScope.launch {
			if (container.accounts.activeIdNow() == id && _me.value?.id == id) return@launch
			activate(id)
		}
	}

	private suspend fun activate(id: String) {
		val account = container.accounts.get(id) ?: return
		container.accounts.setActive(id)
		container.prefs.setServerUrl(account.server)
		_serverUrl.value = account.server
		_me.value = null
		if (account.expired) {
			// Sesión vencida: hay que iniciar sesión de nuevo (se vuelve a la pantalla de acceso con ese sitio).
			_loggedIn.value = false
			return
		}
		_loggedIn.value = true
		loadMe()
	}

	/** Cierra la sesión de una cuenta: ese teléfono deja de recibir sus avisos (los de las demás siguen). */
	fun removeAccount(id: String, serverAlreadyForgotDevice: Boolean = false) {
		viewModelScope.launch {
			// Antes de borrar la sesión: necesita estar autenticado. Si el servidor acaba de borrar la cuenta, ya no hace falta.
			if (!serverAlreadyForgotDevice) FcmRegistrar.unregister(container, id)
			container.accounts.remove(id)
			container.prefs.clearSeen(id)
			val next = container.accounts.all().firstOrNull { !it.expired }
			if (next != null) {
				if (container.accounts.activeIdNow() != next.id || _me.value?.id == id) activate(next.id)
				scheduleSync()
			} else {
				_me.value = null
				_loggedIn.value = false
				SyncScheduler.cancel(container.app)
			}
		}
	}

	/** Elimina la cuenta de acceso de la persona activa en el servidor (pide su contraseña) y la quita de este teléfono. */
	fun deleteMyAccount(password: String, onResult: (String?) -> Unit) {
		val id = _me.value?.id ?: activeId.value ?: return
		viewModelScope.launch {
			api { container.apiService.deleteAccount(DeleteAccountRequest(password)) }
				.onSuccess {
					removeAccount(id, serverAlreadyForgotDevice = true)
					onResult(null)
				}
				.onFailure {
					onResult(
						when ((it as? ApiException)?.code) {
							403 -> "La contraseña no es correcta."
							409 -> "Eres el único administrador con acceso: antes de eliminar tu cuenta, da acceso de administrador a otra persona."
							else -> "No se pudo eliminar la cuenta. Revisa tu conexión e intenta de nuevo."
						},
					)
				}
		}
	}

	/** Cierra la sesión de la cuenta activa. */
	fun logout() {
		val id = _me.value?.id ?: activeId.value ?: return
		removeAccount(id)
	}

	fun setAccent(key: String) = viewModelScope.launch { container.prefs.setAccent(key) }
	fun setDarkMode(mode: String) = viewModelScope.launch { container.prefs.setDarkMode(mode) }

	/** Igual que la web: se guarda en el servidor (`PUT /api/auth/shortcut`) y vuelve a su valor si falla. */
	fun setShortcut(href: String?, onResult: (Boolean) -> Unit) {
		val previous = _me.value ?: return
		_me.value = previous.copy(navShortcut = href)
		viewModelScope.launch {
			val ok = api { container.apiService.setShortcut(ShortcutRequest(href)) }.isSuccess
			if (!ok) _me.value = previous
			onResult(ok)
		}
	}

	// --- Acceso por módulos: mismas reglas de privilegios que el menú de la web ---

	fun canPublishers(me: Me) = me.has("publishers_all", "publishers_group")
	fun canMeetings(me: Me) = me.has("midweek_access", "weekend_access")

	fun shortcutOptions(me: Me): List<ShortcutOption> = buildList {
		if (canPublishers(me)) add(ShortcutOption("/publishers", "Publicadores", Dest.Publishers))
		add(ShortcutOption("/reports", "Informes", Dest.Reports()))
	}

	/** El botón configurable de la barra: solo aparece si está guardado y la persona aún tiene acceso. */
	fun activeShortcut(me: Me): ShortcutOption? {
		val href = me.navShortcut ?: return null
		return shortcutOptions(me).firstOrNull { it.href == href }
	}

	companion object {
		fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
			@Suppress("UNCHECKED_CAST")
			override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
		}
	}
}
