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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.micongre.app.MainActivity
import com.micongre.app.MicongreApplication
import com.micongre.app.R
import com.micongre.app.data.OpState
import com.micongre.app.data.OutboxOp
import java.util.concurrent.TimeUnit

private const val OUTBOX_WORK = "micongre-outbox"
private const val SYNC_CHANNEL = "sincronizacion"
private const val SYNC_NOTIFICATION_ID = 7001

/** Envía lo guardado sin internet. WorkManager lo conserva aunque se cierre la app o se reinicie el teléfono y lo corre en cuanto hay red. */
object OutboxScheduler {
	fun kick(context: Context) {
		val request = OneTimeWorkRequestBuilder<OutboxWorker>()
			.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
			// Reintentos con espera creciente (30 s, 1 min, 2 min…): no se gasta batería insistiendo.
			.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
			.build()
		// KEEP: si ya hay una ronda programada o corriendo, no se duplica.
		WorkManager.getInstance(context).enqueueUniqueWork(OUTBOX_WORK, ExistingWorkPolicy.KEEP, request)
	}
}

class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
	override suspend fun doWork(): Result {
		val container = (applicationContext as MicongreApplication).container
		var retry = false
		val problems = mutableListOf<OutboxOp>()
		for (account in container.accounts.all()) {
			if (container.outbox.forAccount(account.id).none { it.state == OpState.PENDING }) continue
			val result = container.outboxSender.drain(account.id)
			retry = retry || result.retry
			problems += result.newProblems
		}
		if (problems.isNotEmpty()) notifyProblems(applicationContext, problems.size)
		return if (retry) Result.retry() else Result.success()
	}
}

/** Aviso en el propio teléfono (no pasa por el servidor, así funciona sin internet): hay cambios que no se pudieron sincronizar. */
fun notifyProblems(context: Context, count: Int) {
	if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
	val manager = context.getSystemService(NotificationManager::class.java)
	if (manager.getNotificationChannel(SYNC_CHANNEL) == null) {
		manager.createNotificationChannel(
			NotificationChannel(SYNC_CHANNEL, "Sincronización", NotificationManager.IMPORTANCE_DEFAULT).apply {
				description = "Avisa cuando algo guardado sin internet no se pudo enviar"
			},
		)
	}
	val open = PendingIntent.getActivity(
		context,
		SYNC_NOTIFICATION_ID,
		Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
		PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
	)
	val text = if (count == 1) "Uno de tus últimos cambios no se pudo sincronizar. Abre Mi Congre, revísalo y vuelve a intentarlo."
	else "$count de tus últimos cambios no se pudieron sincronizar. Abre Mi Congre, revísalos y vuelve a intentarlo."
	val notification = NotificationCompat.Builder(context, SYNC_CHANNEL)
		.setSmallIcon(R.drawable.ic_stat_notify)
		.setContentTitle("No se pudo sincronizar")
		.setContentText(text)
		.setStyle(NotificationCompat.BigTextStyle().bigText(text))
		.setContentIntent(open)
		.setAutoCancel(true)
		.build()
	NotificationManagerCompat.from(context).notify(SYNC_NOTIFICATION_ID, notification)
}
