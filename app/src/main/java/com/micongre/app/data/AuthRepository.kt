package com.micongre.app.data

/** Inicio de sesión de una cuenta nueva: se guarda junto a las demás y queda como la activa. */
class AuthRepository(private val accounts: AccountStore, private val prefs: Prefs) {

	suspend fun login(server: String, username: String, password: String): Result<AuthResponse> = runCatching {
		val response = ApiClient(AnonymousSession(server)).service.login(LoginRequest(username, password))
		if (!response.isSuccessful) error("login_failed_${response.code()}")
		val auth = response.body()!!
		accounts.add(auth, server)
		prefs.setServerUrl(server)
		auth
	}

	suspend fun previewActivation(server: String, token: String): Result<ActivatePreviewResponse> = runCatching {
		val response = ApiClient(AnonymousSession(server)).service.previewActivation(token)
		if (!response.isSuccessful) error("invalid_token_${response.code()}")
		response.body()!!
	}

	suspend fun activate(server: String, token: String, username: String, password: String): Result<AuthResponse> = runCatching {
		val response = ApiClient(AnonymousSession(server)).service.activate(ActivateRequest(token, username, password))
		if (!response.isSuccessful) error("activation_failed_${response.code()}")
		val auth = response.body()!!
		accounts.add(auth, server)
		prefs.setServerUrl(server)
		auth
	}

	/** Hay al menos una cuenta con sesión vigente. */
	suspend fun isLoggedIn(): Boolean = accounts.all().any { !it.expired }
}
