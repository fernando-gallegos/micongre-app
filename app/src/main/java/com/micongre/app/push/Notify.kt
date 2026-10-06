package com.micongre.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.micongre.app.MainActivity
import com.micongre.app.R

/** Extra del Intent con la cuenta a la que pertenece la notificación: al tocarla, la app cambia a esa cuenta. */
const val EXTRA_ACCOUNT_ID = "account_id"

/**
 * Notificación de una cuenta. Con varias cuentas en el teléfono el título dice a quién va ("María · Tus asignaciones…"),
 * y es visible también en la pantalla de bloqueo. Al tocarla se abre la app en esa cuenta.
 */
fun showAccountNotification(context: Context, accountId: String?, accountName: String?, multiple: Boolean, key: String, title: String, text: String) {
	if (android.os.Build.VERSION.SDK_INT >= 33 &&
		ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
	) return

	ensureChannel(context)
	val open = PendingIntent.getActivity(
		context, (accountId ?: key).hashCode(),
		Intent(context, MainActivity::class.java)
			.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
			.apply { if (accountId != null) putExtra(EXTRA_ACCOUNT_ID, accountId) },
		PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
	)
	val shownTitle = if (multiple && !accountName.isNullOrBlank()) "$accountName · $title" else title
	val notification = NotificationCompat.Builder(context, CHANNEL_ID)
		.setSmallIcon(R.drawable.ic_stat_notify)
		.setContentTitle(shownTitle)
		.setContentText(text)
		.setStyle(NotificationCompat.BigTextStyle().bigText(text))
		.setContentIntent(open)
		.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
		.setAutoCancel(true)
		.build()
	@Suppress("MissingPermission")
	NotificationManagerCompat.from(context).notify("${accountId.orEmpty()}:$key".hashCode(), notification)
}
