package com.micongre.app.widget

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import com.micongre.app.AppContainer
import com.micongre.app.MicongreApplication
import com.micongre.app.ui.theme.ACCENTS
import com.micongre.app.ui.theme.AccentOption
import kotlinx.coroutines.flow.first
import com.micongre.app.data.ApiException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

/** Segundos que se espera la respuesta del servidor al dibujar un widget sin datos guardados (el sistema da pocos segundos a un widget). */
private const val INLINE_FETCH_SECONDS = 8L

/**
 * Los widgets guardan una copia propia de lo que muestran (cifrada, por cuenta, con claves fijas): así no dependen de que la dirección exacta con que
 * el interceptor guarda sus lecturas coincida, ni de que un trabajo de fondo haya corrido (algunos teléfonos los retrasan o los detienen).
 */
object WidgetStore {
	private fun key(name: String) = "widget:$name"

	fun put(container: AppContainer, accountId: String, name: String, text: String) = container.offlineCache.put(accountId, key(name), text)

	fun get(container: AppContainer, accountId: String, name: String): String? = container.offlineCache.get(accountId, key(name))
}

/** Pide algo al servidor esperando como máximo unos segundos; nunca lanza: el fallo se devuelve como Result. */
suspend fun <T> fetchWithin(block: suspend () -> Result<T>): Result<T> = try {
	withTimeout(INLINE_FETCH_SECONDS * 1000) { block() }
} catch (e: TimeoutCancellationException) {
	Result.failure(IOException("tardó demasiado"))
} catch (e: Exception) {
	Result.failure(e)
}

/** Motivo corto y en español de un fallo al cargar (para mostrarlo en el widget). */
fun failureReason(error: Throwable?): String = when {
	error is ApiException && error.code == 401 -> "la sesión venció"
	error is ApiException && error.code == 403 -> "sin permiso"
	error is ApiException -> "error ${error.code} del servidor"
	error is IOException && error.message == "tardó demasiado" -> "tardó demasiado"
	error is IOException -> "sin conexión"
	else -> "error inesperado"
}

/** El color de acento que la persona eligió en la app (Más → Color); si no se puede leer, salvia. */
suspend fun widgetAccent(context: Context): AccentOption {
	val key = runCatching { (context.applicationContext as MicongreApplication).container.prefs.accent.first() }.getOrDefault("salvia")
	return ACCENTS.firstOrNull { it.key == key } ?: ACCENTS.first()
}

/** El acento como color de texto del widget (claro y oscuro); los widgets lo reciben por aquí en vez de usar un verde fijo. */
fun AccentOption.provider() = ColorProvider(day = Color(light.accent), night = Color(dark.accent))

val LocalWidgetAccent = compositionLocalOf { ACCENTS.first().provider() }

val LocalWidgetAccentOption = compositionLocalOf { ACCENTS.first() }
