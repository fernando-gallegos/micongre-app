package com.micongre.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.prefsStore by preferencesDataStore(name = "prefs")

/** Preferencias de interfaz de este dispositivo (igual que en la web, donde el tema y el color viven en el navegador). */
class Prefs(private val context: Context) {
	private val accentKey = stringPreferencesKey("accent")
	private val darkKey = stringPreferencesKey("dark_mode")
	private val serverKey = stringPreferencesKey("server_url")

	/** Clave del color de acento (salvia, anil, terracota, petroleo, carbon). */
	val accent: Flow<String> = context.prefsStore.data.map { it[accentKey] ?: "salvia" }

	/** "system", "light" o "dark". */
	val darkMode: Flow<String> = context.prefsStore.data.map { it[darkKey] ?: "system" }

	/** Dirección del sitio de la congregación ("Mi Congre en la web"), con barra final; null hasta que se configure. */
	val serverUrl: Flow<String?> = context.prefsStore.data.map { it[serverKey] }

	suspend fun currentServerUrl(): String? = serverUrl.first()

	suspend fun setServerUrl(value: String) {
		context.prefsStore.edit { it[serverKey] = value }
	}

	private val syncKey = androidx.datastore.preferences.core.intPreferencesKey("sync_minutes")

	/** Cada cuántos minutos la app revisa avisos y asignaciones nuevas (15 por defecto, el mínimo de Android). */
	val syncMinutes: Flow<Int> = context.prefsStore.data.map { it[syncKey] ?: 15 }

	suspend fun currentSyncMinutes(): Int = syncMinutes.first()

	suspend fun setSyncMinutes(value: Int) {
		context.prefsStore.edit { it[syncKey] = value }
	}

	private val fcmKey = androidx.datastore.preferences.core.booleanPreferencesKey("fcm_active")

	/** true cuando las notificaciones llegan por push en tiempo real (Firebase) y ya no hace falta revisar cada tanto. */
	val fcmActive: Flow<Boolean> = context.prefsStore.data.map { it[fcmKey] ?: false }

	suspend fun setFcmActive(value: Boolean) {
		context.prefsStore.edit { it[fcmKey] = value }
	}

	private val pushPrivKey = stringPreferencesKey("push_priv_enc")
	private val pushPubKey = stringPreferencesKey("push_pub")

	/** Llave del dispositivo para el cifrado de punta a punta de los push: (privada PKCS#8, pública cruda), ambas en base64url. */
	suspend fun pushKeys(): Pair<String, String>? {
		val prefs = context.prefsStore.data.first()
		val priv = SecureStore.decrypt(prefs[pushPrivKey]) ?: return null
		val pub = prefs[pushPubKey] ?: return null
		return priv to pub
	}

	suspend fun savePushKeys(privatePkcs8: String, publicRaw: String) {
		context.prefsStore.edit {
			it[pushPrivKey] = SecureStore.encrypt(privatePkcs8)
			it[pushPubKey] = publicRaw
		}
	}

	private fun seenKey(accountId: String) = androidx.datastore.preferences.core.stringSetPreferencesKey("seen_ids_$accountId")

	/** Avisos y asignaciones ya vistos de una cuenta por la revisión de notificaciones; null = todavía no se ha tomado la foto inicial. */
	suspend fun seenIds(accountId: String): Set<String>? = context.prefsStore.data.first()[seenKey(accountId)]

	suspend fun setSeenIds(accountId: String, ids: Set<String>) {
		context.prefsStore.edit { it[seenKey(accountId)] = ids }
	}

	suspend fun clearSeen(accountId: String) {
		context.prefsStore.edit { it.remove(seenKey(accountId)) }
	}

	private val lockKey = androidx.datastore.preferences.core.booleanPreferencesKey("lock_switch")

	/** Pedir la huella o el PIN del teléfono para cambiar de cuenta (activado por defecto: el celular puede ser de toda la familia). */
	val lockSwitch: Flow<Boolean> = context.prefsStore.data.map { it[lockKey] ?: true }

	suspend fun setLockSwitch(value: Boolean) {
		context.prefsStore.edit { it[lockKey] = value }
	}

	suspend fun setAccent(value: String) {
		context.prefsStore.edit { it[accentKey] = value }
	}

	suspend fun setDarkMode(value: String) {
		context.prefsStore.edit { it[darkKey] = value }
	}
}
