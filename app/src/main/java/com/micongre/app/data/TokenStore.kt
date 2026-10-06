package com.micongre.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "auth")

/** Una cuenta guardada en el teléfono (varias personas de una familia pueden compartir un solo celular). */
@Serializable
data class AccountRecord(
	val id: String,
	val name: String,
	/** Sitio de la congregación de esta cuenta, con barra final. */
	val server: String,
	/** Tokens cifrados con el Keystore (nunca en claro). */
	val access: String,
	val refresh: String,
	/** La sesión venció o fue revocada: hay que iniciar sesión de nuevo. */
	val expired: Boolean = false,
	/** Los avisos de esta cuenta llegan por push en tiempo real (Firebase); si no, la app los revisa por su cuenta. */
	val fcm: Boolean = false,
)

/** Cuenta para mostrar en pantalla (sin tokens). */
data class AccountInfo(val id: String, val name: String, val server: String, val expired: Boolean)

/**
 * Cuentas guardadas y cuál está activa. Todo lo que ve la app (Mi Panel, etc.) es de la cuenta activa; las demás siguen
 * recibiendo sus notificaciones. Los tokens van cifrados con el Keystore.
 */
class AccountStore(private val context: Context) {
	private object Keys {
		val ACCOUNTS = stringPreferencesKey("accounts")
		val ACTIVE = stringPreferencesKey("active_account")

		// Formato anterior (una sola cuenta): se migra la primera vez.
		val ACCESS_TOKEN = stringPreferencesKey("access_token")
		val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
		val USER_ID = stringPreferencesKey("user_id")
		val USER_FULL_NAME = stringPreferencesKey("user_full_name")
	}

	private val json = Json { ignoreUnknownKeys = true }
	private val listSerializer = ListSerializer(AccountRecord.serializer())

	private fun parse(raw: String?): List<AccountRecord> =
		if (raw.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())

	val records: Flow<List<AccountRecord>> = context.dataStore.data.map { parse(it[Keys.ACCOUNTS]) }
	val activeId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE] }
	val accounts: Flow<List<AccountInfo>> = records.map { list -> list.map { AccountInfo(it.id, it.name, it.server, it.expired) } }

	suspend fun all(): List<AccountRecord> = records.first()
	suspend fun get(id: String): AccountRecord? = all().firstOrNull { it.id == id }
	suspend fun activeIdNow(): String? = activeId.first()
	suspend fun active(): AccountRecord? {
		val list = all()
		val id = activeIdNow()
		return list.firstOrNull { it.id == id } ?: list.firstOrNull()
	}

	private suspend fun write(transform: (List<AccountRecord>) -> List<AccountRecord>) {
		context.dataStore.edit { prefs ->
			val next = transform(parse(prefs[Keys.ACCOUNTS]))
			prefs[Keys.ACCOUNTS] = json.encodeToString(listSerializer, next)
		}
	}

	/** Pasa la sesión del formato de una sola cuenta al de varias (una vez). [server] = sitio que tenía configurado. */
	suspend fun migrateLegacy(server: String?) {
		val prefs = context.dataStore.data.first()
		if (!prefs[Keys.ACCOUNTS].isNullOrBlank() || prefs[Keys.ACCESS_TOKEN] == null || server == null) return
		val id = prefs[Keys.USER_ID] ?: return
		val record = AccountRecord(
			id = id,
			name = prefs[Keys.USER_FULL_NAME] ?: "Cuenta",
			server = server,
			access = prefs[Keys.ACCESS_TOKEN] ?: return,
			refresh = prefs[Keys.REFRESH_TOKEN] ?: return,
		)
		context.dataStore.edit {
			it[Keys.ACCOUNTS] = json.encodeToString(listSerializer, listOf(record))
			it[Keys.ACTIVE] = id
			it.remove(Keys.ACCESS_TOKEN)
			it.remove(Keys.REFRESH_TOKEN)
			it.remove(Keys.USER_ID)
			it.remove(Keys.USER_FULL_NAME)
		}
	}

	/** Agrega o actualiza la cuenta (por id de persona) y la deja como activa. */
	suspend fun add(auth: AuthResponse, server: String) {
		val record = AccountRecord(
			id = auth.user.id,
			name = auth.user.fullName,
			server = server,
			access = SecureStore.encrypt(auth.accessToken),
			refresh = SecureStore.encrypt(auth.refreshToken),
		)
		write { list -> list.filter { it.id != record.id } + record }
		context.dataStore.edit { it[Keys.ACTIVE] = record.id }
	}

	suspend fun setActive(id: String) {
		context.dataStore.edit { it[Keys.ACTIVE] = id }
	}

	suspend fun remove(id: String) {
		write { list -> list.filter { it.id != id } }
		context.dataStore.edit { prefs ->
			if (prefs[Keys.ACTIVE] == id) {
				val rest = parse(prefs[Keys.ACCOUNTS])
				if (rest.isEmpty()) prefs.remove(Keys.ACTIVE) else prefs[Keys.ACTIVE] = rest.first().id
			}
		}
	}

	suspend fun accessOf(id: String): String? = get(id)?.takeIf { !it.expired }?.let { SecureStore.decrypt(it.access) }
	suspend fun refreshOf(id: String): String? = get(id)?.takeIf { !it.expired }?.let { SecureStore.decrypt(it.refresh) }

	suspend fun updateTokens(id: String, accessToken: String, refreshToken: String?) {
		write { list ->
			list.map {
				if (it.id != id) it
				else it.copy(access = SecureStore.encrypt(accessToken), refresh = refreshToken?.let(SecureStore::encrypt) ?: it.refresh)
			}
		}
	}

	suspend fun markExpired(id: String) {
		write { list -> list.map { if (it.id == id) it.copy(expired = true, access = "", refresh = "") else it } }
	}

	suspend fun setFcm(id: String, value: Boolean) {
		write { list -> list.map { if (it.id == id) it.copy(fcm = value) else it } }
	}

	suspend fun rename(id: String, name: String) {
		write { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
	}

	/** Cierra todas las sesiones (por ejemplo, al cambiar de congregación desde cero). */
	suspend fun clear() {
		context.dataStore.edit { it.clear() }
	}
}
