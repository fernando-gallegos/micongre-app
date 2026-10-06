package com.micongre.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.micongre.app.MainActivity
import com.micongre.app.MicongreApplication
import com.micongre.app.R
import com.micongre.app.data.api
import com.micongre.app.ui.Fmt
import kotlinx.coroutines.flow.first
import com.micongre.app.ui.screens.plainText
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

const val CHANNEL_ID = "avisos"
private const val WORK_NAME = "micongre-sync"

private val REMINDER_TYPES = setOf("midweek", "weekend", "operational")
private val REMINDER_TITLES = mapOf(
	"midweek" to "reunión de entre semana",
	"weekend" to "reunión de fin de semana",
	"operational" to "tarea operativa en la reunión",
)

/** Crea el canal de notificaciones (Android 8+). */
fun ensureChannel(context: Context) {
	val manager = context.getSystemService(NotificationManager::class.java)
	if (manager.getNotificationChannel(CHANNEL_ID) == null) {
		manager.createNotificationChannel(
			NotificationChannel(CHANNEL_ID, "Avisos y asignaciones", NotificationManager.IMPORTANCE_DEFAULT).apply {
				description = "Avisos de la congregación y nuevas asignaciones"
			},
		)
	}
}

private const val FAST_WORK_NAME = "micongre-sync-fast"
private val NETWORK = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/**
 * Programa la revisión mientras haya sesión iniciada. Android no permite tareas periódicas de menos de 15 minutos, así que
 * para 1, 5 o 10 minutos cada revisión programa la siguiente (y queda una periódica de 15 min de respaldo por si la cadena
 * se corta). Con la pantalla apagada y el teléfono en reposo, Android puede retrasar las revisiones cortas.
 */
object SyncScheduler {
	/** [update] = true reemplaza la programación vigente (al cambiar el intervalo); si no, se respeta la que ya exista. */
	fun schedule(context: Context, minutes: Int = 15, update: Boolean = false) {
		val manager = WorkManager.getInstance(context)
		val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
			.setConstraints(NETWORK)
			.build()
		manager.enqueueUniquePeriodicWork(WORK_NAME, if (update) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, request)
		if (minutes < 15) scheduleNext(context, minutes, replace = update) else manager.cancelUniqueWork(FAST_WORK_NAME)
	}

	/** Siguiente revisión de la cadena corta. */
	fun scheduleNext(context: Context, minutes: Int, replace: Boolean = true) {
		val next = OneTimeWorkRequestBuilder<SyncWorker>()
			.setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
			.setConstraints(NETWORK)
			.build()
		WorkManager.getInstance(context).enqueueUniqueWork(
			FAST_WORK_NAME,
			if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
			next,
		)
	}

	fun cancel(context: Context) {
		val manager = WorkManager.getInstance(context)
		manager.cancelUniqueWork(WORK_NAME)
		manager.cancelUniqueWork(FAST_WORK_NAME)
	}
}

/**
 * Cada sitio de congregación es independiente y no hay un servicio central de push, así que la app revisa por su cuenta
 * lo mismo que ve Mi Panel (avisos y asignaciones próximas) y muestra una notificación por cada cosa nueva. La primera
 * revisión solo toma la foto de lo que ya existe, para no avisar de todo lo anterior.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result {
		val container = (applicationContext as MicongreApplication).container
		// Con push en tiempo real (Firebase) ya no se revisa por cuenta propia.
		if (container.prefs.fcmActive.first()) return Result.success()
		val result = sync()
		// Intervalos cortos (< 15 min): cada revisión programa la siguiente, mientras haya sesión.
		val minutes = container.prefs.currentSyncMinutes()
		if (minutes < 15 && container.prefs.currentServerUrl() != null && container.authRepository.isLoggedIn()) {
			SyncScheduler.scheduleNext(applicationContext, minutes)
		}
		return result
	}

	/** Revisa cada cuenta del teléfono que no use push en tiempo real: así se avisa de todas, esté o no activa. */
	private suspend fun sync(): Result {
		val container = (applicationContext as MicongreApplication).container
		val all = container.accounts.all()
		for (account in all.filter { !it.expired && !it.fcm }) syncAccount(container, account.id, account.name, all.size > 1)
		return Result.success()
	}

	private suspend fun syncAccount(container: com.micongre.app.AppContainer, accountId: String, accountName: String, multiple: Boolean): Result {
		val apiService = container.apiFor(accountId)
		val notifications = api { apiService.notifications() }
		val summary = api { apiService.panelSummary() }
		// Sin red o sin sesión válida: se reintenta en el siguiente ciclo.
		if (notifications.isFailure && summary.isFailure) return Result.success()

		class Item(val key: String, val title: String, val text: String)
		val items = buildList {
			notifications.getOrNull()?.items?.forEach {
				// Los avisos automáticos (recordatorios) empiezan con un título en negritas: se usa como título de la notificación.
				val bold = Regex("^\\*\\*(.+?)\\*\\*\\s*\\n(.*)$", RegexOption.DOT_MATCHES_ALL).find(it.message.trim())
				if (bold != null) add(Item("n:${it.id}", bold.groupValues[1], plainText(bold.groupValues[2])))
				else add(Item("n:${it.id}", it.senderName, plainText(it.message)))
			}
			summary.getOrNull()?.upcoming?.forEach {
				add(Item("a:${it.meetingType}|${it.date}|${it.label}", "Nueva asignación", "${it.label} · ${Fmt.dmy(it.date)}"))
			}
		}

		// Recordatorio: el día antes de la reunión (desde las 8:00), si la persona tiene asignación en ella.
		val tomorrow = LocalDate.now().plusDays(1).toString()
		val reminders = if (LocalTime.now().hour >= 8) {
			summary.getOrNull()?.upcoming.orEmpty()
				.filter { it.date == tomorrow && it.meetingType in REMINDER_TYPES }
				.groupBy { it.meetingType }
				.map { (type, parts) ->
					Item(
						"r:$type|$tomorrow",
						"Mañana: " + (REMINDER_TITLES[type] ?: "tienes una asignación"),
						"Tienes asignación: " + parts.joinToString(", ") { it.label } + " · " + Fmt.dmy(tomorrow),
					)
				}
		} else emptyList()

		val previous = container.prefs.seenIds(accountId)
		val current = (items + reminders).map { it.key }.toSet()
		// Lo ya visto se conserva aunque deje de aparecer un rato (así no se repite si reaparece).
		container.prefs.setSeenIds(accountId, ((previous ?: emptySet()) + current).toList().takeLast(400).toSet())

		// En la primera revisión solo se toma la foto de lo existente, pero un recordatorio de mañana sí se manda.
		val seen = previous ?: emptySet()
		val fresh = (if (previous == null) emptyList() else items.filter { it.key !in seen }) + reminders.filter { it.key !in seen }
		fresh.take(8).forEach { showAccountNotification(applicationContext, accountId, accountName, multiple, it.key, it.title, it.text) }
		return Result.success()
	}

	private fun canNotify(): Boolean =
		android.os.Build.VERSION.SDK_INT < 33 ||
			ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

	private fun show(key: String, title: String, text: String) {
		ensureChannel(applicationContext)
		val open = PendingIntent.getActivity(
			applicationContext, 0,
			Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
		)
		val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_stat_notify)
			.setContentTitle(title)
			.setContentText(text)
			.setStyle(NotificationCompat.BigTextStyle().bigText(text))
			.setContentIntent(open)
			.setAutoCancel(true)
			.build()
		@Suppress("MissingPermission")
		NotificationManagerCompat.from(applicationContext).notify(key.hashCode(), notification)
	}
}
