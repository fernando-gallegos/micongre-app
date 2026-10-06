package com.micongre.app.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.micongre.app.AppContainer
import com.micongre.app.MicongreApplication
import com.micongre.app.R
import com.micongre.app.data.RegisterDeviceRequest
import com.micongre.app.data.api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Push en tiempo real con Firebase Cloud Messaging. Es opcional y se decide solo, cuenta por cuenta:
 *  - la app trae Firebase si se compiló con app/google-services.json (si no, FirebaseApp no existe y todo esto se omite), y
 *  - el sitio de la cuenta dice (GET /api/devices/config) que usa FCM.
 * Cada cuenta guardada en el teléfono registra el mismo dispositivo en su propio sitio, así el teléfono recibe los avisos de
 * todas las cuentas. Las cuentas sin push en tiempo real siguen con la revisión periódica.
 */
object FcmRegistrar {
	private fun firebaseAvailable(context: Context): Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

	private suspend fun currentToken(): String? = suspendCoroutine { cont ->
		runCatching {
			FirebaseMessaging.getInstance().token.addOnCompleteListener { task -> cont.resume(if (task.isSuccessful) task.result else null) }
		}.onFailure { cont.resume(null) }
	}

	/** Llave pública del dispositivo (se crea la primera vez): con ella cada servidor cifra los push de punta a punta. */
	suspend fun publicKey(container: AppContainer): String {
		container.prefs.pushKeys()?.let { return it.second }
		val fresh = PushCrypto.generate()
		container.prefs.savePushKeys(fresh.privatePkcs8, fresh.publicRaw)
		return fresh.publicRaw
	}

	/** Registra el teléfono en el sitio de cada cuenta que use FCM y decide si hace falta la revisión periódica para las demás. */
	suspend fun sync(container: AppContainer, minutes: Int) {
		val context = container.app
		val accounts = container.accounts.all().filter { !it.expired }
		val token = if (firebaseAvailable(context)) currentToken() else null
		val key = if (token != null) publicKey(container) else null
		var allFcm = accounts.isNotEmpty()
		for (account in accounts) {
			val api = container.apiFor(account.id)
			val useFcm = token != null &&
				api { api.deviceConfig() }.getOrNull()?.fcm == true &&
				api { api.registerDevice(RegisterDeviceRequest(token, key)) }.isSuccess
			container.accounts.setFcm(account.id, useFcm)
			if (!useFcm) allFcm = false
		}
		container.prefs.setFcmActive(allFcm)
		if (allFcm) SyncScheduler.cancel(context) else if (accounts.isNotEmpty()) SyncScheduler.schedule(context, minutes)
	}

	/** Al cerrar la sesión de una cuenta: este teléfono deja de recibir los avisos de esa persona (las demás siguen). */
	suspend fun unregister(container: AppContainer, accountId: String) {
		if (!firebaseAvailable(container.app)) return
		val token = currentToken() ?: return
		val api = container.apiFor(accountId)
		api { api.unregisterDevice(RegisterDeviceRequest(token)) }
	}
}

class MicongreFirebaseMessagingService : FirebaseMessagingService() {

	override fun onNewToken(token: String) {
		super.onNewToken(token)
		val container = (application as MicongreApplication).container
		CoroutineScope(Dispatchers.IO).launch {
			val key = FcmRegistrar.publicKey(container)
			for (account in container.accounts.all().filter { !it.expired && it.fcm }) {
				val api = container.apiFor(account.id)
				runCatching { api { api.registerDevice(RegisterDeviceRequest(token, key)) } }
			}
		}
	}

	override fun onMessageReceived(message: RemoteMessage) {
		super.onMessageReceived(message)
		val container = (application as MicongreApplication).container
		var title = message.data["title"] ?: message.notification?.title ?: getString(R.string.app_name)
		var body = message.data["body"] ?: message.notification?.body ?: ""
		var uid = message.data["uid"]
		// Push cifrado de punta a punta: solo esta app (su llave privada) puede leerlo; el relay y Firebase ven un bloque ilegible.
		val ct = message.data["ct"]
		if (ct != null) {
			val keys = kotlinx.coroutines.runBlocking { container.prefs.pushKeys() }
			val plain = keys?.let { PushCrypto.decrypt(it.first, message.data["ek"].orEmpty(), message.data["iv"].orEmpty(), ct) }
			val json = plain?.let { runCatching { Json.parseToJsonElement(it) as JsonObject }.getOrNull() }
			fun field(name: String) = (json?.get(name) as? JsonPrimitive)?.content
			title = field("title") ?: getString(R.string.app_name)
			body = field("body") ?: "Tienes un aviso nuevo"
			uid = field("uid")
		}

		// A qué cuenta del teléfono va (varias personas de una familia pueden compartir el celular).
		val accounts = kotlinx.coroutines.runBlocking { container.accounts.all() }
		val account = accounts.firstOrNull { it.id == uid } ?: accounts.singleOrNull()
		showAccountNotification(this, account?.id, account?.name, accounts.size > 1, (title + body).hashCode().toString(), title, body)
	}
}
