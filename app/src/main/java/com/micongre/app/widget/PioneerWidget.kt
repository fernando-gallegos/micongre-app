package com.micongre.app.widget

import androidx.compose.runtime.CompositionLocalProvider
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
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
import com.micongre.app.data.ServiceYearMonth
import com.micongre.app.data.ServiceYearResponse
import com.micongre.app.data.api
import com.micongre.app.ui.Fmt
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Lo que muestra el widget de precursorado: horas del año de servicio en curso, total y por mes. `regular` = false: no es precursor regular. */
data class PioneerState(
	val signedIn: Boolean,
	val regular: Boolean,
	val hours: Double,
	val months: List<ServiceYearMonth>,
	/** false: no hay datos (primera vez y no se pudieron pedir al servidor). */
	val loaded: Boolean = true,
	/** Motivo corto cuando no se pudo cargar (sin conexión, sesión vencida…). */
	val failure: String? = null,
)

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

object PioneerData {
	private const val STORE = "pioneer"

	/** Pide las horas al servidor y guarda la copia del widget. */
	suspend fun fetchAndStore(container: com.micongre.app.AppContainer, accountId: String): Result<ServiceYearResponse> {
		val result = fetchWithin { api { container.apiFor(accountId).serviceYear() } }
		result.getOrNull()?.let { WidgetStore.put(container, accountId, STORE, json.encodeToString(ServiceYearResponse.serializer(), it)) }
		return result
	}

	/** Lo guardado; si todavía no hay nada (primera vez), se piden al servidor en el momento para que el widget no se quede en «cargando». */
	suspend fun load(context: Context, fetchIfMissing: Boolean = true): PioneerState {
		val container = (context.applicationContext as MicongreApplication).container
		val account = container.accounts.active()
		if (account == null || account.expired) return PioneerState(false, false, 0.0, emptyList())
		var data = WidgetStore.get(container, account.id, STORE)?.let { runCatching { json.decodeFromString(ServiceYearResponse.serializer(), it) }.getOrNull() }
		// Copia de versiones anteriores del widget: lo que guardó el interceptor.
		if (data == null) {
			data = account.server.toHttpUrlOrNull()?.let { server ->
				container.offlineCache.get(account.id, serviceYearUrl(server))?.let { runCatching { json.decodeFromString(ServiceYearResponse.serializer(), it) }.getOrNull() }
			}
		}
		// Datos viejos (más de 3 horas): se intenta ponerlos al día en el momento; si falla, se muestra lo guardado. No depende de que un trabajo de fondo haya corrido.
		if (data != null && fetchIfMissing && PioneerSchedule.isStale(context)) {
			PioneerData.fetchAndStore(container, account.id).getOrNull()?.let {
				data = it
				PioneerSchedule.markFetched(context)
			}
		}
		var failure: String? = null
		if (data == null && fetchIfMissing) {
			val fetched = fetchAndStore(container, account.id)
			data = fetched.getOrNull()
			if (data != null) PioneerSchedule.markFetched(context)
			failure = if (data == null) failureReason(fetched.exceptionOrNull()) else null
			if (data == null) android.util.Log.w("PioneerWidget", "No se pudieron pedir las horas: ${fetched.exceptionOrNull()}")
		}
		return if (data == null) PioneerState(true, true, 0.0, emptyList(), loaded = false, failure = failure)
		else PioneerState(true, data.isRegularPioneer, data.hours, data.months)
	}
}

/** Dirección con la que se guarda la respuesta (ver OfflineInterceptor). */
internal fun serviceYearUrl(server: HttpUrl): String =
	HttpUrl.Builder().scheme(server.scheme).host(server.host).port(server.port).encodedPath("/api/reports/s4/service-year").build().toString()

private val BG = ColorProvider(day = Color(0xFFF7F5F0), night = Color(0xFF1C1F23))
private val INK = ColorProvider(day = Color(0xFF1C1B17), night = Color(0xFFEEEAE1))
private val SOFT = ColorProvider(day = Color(0xFF6C6658), night = Color(0xFF9A9488))

private fun hoursText(hours: Double): String = Fmt.hours(hours).ifBlank { "0" }

@androidx.compose.runtime.Composable
private fun openApp(): android.content.Intent =
	android.content.Intent(LocalContext.current, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)

@androidx.compose.runtime.Composable
private fun PioneerContent(state: PioneerState) {
	val context = LocalContext.current
	val size: DpSize = LocalSize.current
	val density = context.resources.displayMetrics.density
	val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
	val colors = ChartColors.of(night, LocalWidgetAccentOption.current)
	val layout = pioneerLayout(cellsOf(size.width.value), cellsOf(size.height.value))
	val currentMonth = LocalDate.now().monthValue

	Box(
		modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(20.dp).padding(10.dp).clickable(actionStartActivity(openApp())),
		contentAlignment = Alignment.Center,
	) {
		when {
			!state.signedIn -> Message("Abre la app para ver tus horas")
			// Sin datos: se dice por qué y al tocar se vuelve a intentar (no se queda para siempre en «cargando»).
			!state.loaded -> Box(modifier = GlanceModifier.fillMaxSize().clickable(actionRunCallback<RetryPioneerAction>()), contentAlignment = Alignment.Center) {
				Message(if (state.failure != null) "No se pudieron cargar tus horas (${state.failure}). Toca para reintentar" else "Cargando tus horas…")
			}
			!state.regular -> Message("Anímate a tomar el precursorado regular, es una buena meta")
			else -> {
				// Tamaño útil en píxeles de cada zona (el widget tiene 10 dp de margen por lado).
				val innerW = ((size.width.value - 20f) * density).toInt().coerceAtLeast(40)
				val innerH = ((size.height.value - 20f) * density).toInt().coerceAtLeast(40)
				when (layout) {
					PioneerLayout.GAUGE -> GaugeWithNumber(innerW, innerH, state, colors, showBigNumber = true, diameterDp = minOf(size.width.value, size.height.value) - 20f)
					PioneerLayout.CHART -> Image(
						provider = ImageProvider(drawMonthBars(innerW, innerH, state.months, currentMonth, colors)),
						contentDescription = "Horas por mes del año de servicio",
						contentScale = ContentScale.Fit,
						modifier = GlanceModifier.fillMaxSize(),
					)
					PioneerLayout.COMBINED -> Column(modifier = GlanceModifier.fillMaxSize()) {
						val topH = innerH / 2
						Image(
							provider = ImageProvider(drawMonthBars(innerW, topH, state.months, currentMonth, colors)),
							contentDescription = "Horas por mes del año de servicio",
							contentScale = ContentScale.Fit,
							modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
						)
						// Abajo, el medidor siempre lleva el número en grande al centro; con espacio sale «43/600» (el 43 grande y «/600» más pequeño).
						Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
							GaugeWithNumber(innerW, innerH - topH, state, colors, showBigNumber = true, diameterDp = minOf(size.width.value - 20f, (size.height.value - 20f) / 2f))
						}
					}
				}
			}
		}
	}
}

@androidx.compose.runtime.Composable
private fun Message(text: String) {
	Text(text, style = TextStyle(color = INK, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center))
}

/**
 * Horas en grande al centro del medidor. Con espacio (`withGoal`) sale como «43/600»: el 43 grande y «/600» más pequeño, en la misma línea y
 * alineados por abajo; si el medidor es chico (2×2) solo el número, con «horas» en chiquito debajo. `sizeSp` crece con el medidor.
 */
@androidx.compose.runtime.Composable
private fun BigHours(hours: Double, withGoal: Boolean, sizeSp: Float) {
	Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
		if (withGoal) {
			Row(verticalAlignment = Alignment.Vertical.Bottom) {
				Text(hoursText(hours), style = TextStyle(color = INK, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold), maxLines = 1)
				Text(
					"/600",
					modifier = GlanceModifier.padding(bottom = (sizeSp * 0.12f).dp),
					style = TextStyle(color = SOFT, fontSize = (sizeSp * 0.45f).sp, fontWeight = FontWeight.Medium),
					maxLines = 1,
				)
			}
		} else {
			Text(hoursText(hours), style = TextStyle(color = INK, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
			Text("horas", style = TextStyle(color = SOFT, fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 1)
		}
	}
}

/** Medidor de 0 a 600 con, opcionalmente, las horas en grande al centro. */
@androidx.compose.runtime.Composable
private fun GaugeWithNumber(widthPx: Int, heightPx: Int, state: PioneerState, colors: ChartColors, showBigNumber: Boolean, diameterDp: Float) {
	Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		Image(
			provider = ImageProvider(drawGauge(widthPx, heightPx, gaugeFraction(state.hours), colors)),
			contentDescription = "Horas del año de servicio: ${hoursText(state.hours)} de 600",
			contentScale = ContentScale.Fit,
			modifier = GlanceModifier.fillMaxSize(),
		)
		// El número crece con el medidor; con espacio de sobra se le agrega «/600» en la misma línea.
		if (showBigNumber) BigHours(state.hours, withGoal = diameterDp >= 120f, sizeSp = (diameterDp * 0.27f).coerceIn(28f, 54f))
	}
}

/**
 * Widget de precursorado, redimensionable. 2×2: medidor de 0 a 600 con las horas del año de servicio en el centro. Más grande: gráfica de horas por
 * mes. De 4 filas: la gráfica arriba y, abajo, el medidor (sin número grande) a la izquierda y las horas en grande con «/600» a la derecha.
 * Sin temporizador propio: se redibuja cuando llegan horas nuevas (informe enviado, app abierta o revisión de fondo).
 */
class PioneerWidget : GlanceAppWidget() {
	override val sizeMode = SizeMode.Exact

	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val state = PioneerData.load(context)
		val accent = widgetAccent(context)
		provideContent { CompositionLocalProvider(LocalWidgetAccent provides accent.provider(), LocalWidgetAccentOption provides accent) { GlanceTheme { PioneerContent(state) } } }
	}
}

/** Toque en «no se pudieron cargar»: vuelve a pedir las horas y redibuja. */
class RetryPioneerAction : ActionCallback {
	override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
		val container = (context.applicationContext as MicongreApplication).container
		container.accounts.active()?.takeIf { !it.expired }?.let { PioneerData.fetchAndStore(container, it.id) }
		PioneerWidget().update(context, glanceId)
	}
}

class PioneerWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = PioneerWidget()

	/** Al agregar el primer widget se piden las horas para que no salga vacío. */
	override fun onEnabled(context: Context) {
		super.onEnabled(context)
		PioneerSchedule.fetchNow(context)
	}
}

suspend fun updatePioneerWidgets(context: Context) {
	runCatching { PioneerWidget().updateAll(context) }
}

/** ¿Hay algún widget de precursorado puesto? (para no pedir datos al servidor si nadie los ve). */
suspend fun hasPioneerWidget(context: Context): Boolean =
	runCatching { GlanceAppWidgetManager(context).getGlanceIds(PioneerWidget::class.java).isNotEmpty() }.getOrDefault(false)

object PioneerSchedule {
	private const val FETCH_WORK = "micongre-pioneer-fetch"
	private const val PREFS = "pioneer_widget"
	private const val MIN_FETCH_GAP_MS = 3 * 60 * 60 * 1000L // las horas cambian al enviar un informe: como mucho cada 3 horas

	private val NETWORK = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()

	fun isStale(context: Context): Boolean =
		System.currentTimeMillis() - context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("lastFetch", 0L) >= MIN_FETCH_GAP_MS

	fun fetchIfStale(context: Context) {
		if (isStale(context)) fetchNow(context)
	}

	fun fetchNow(context: Context) {
		val request = OneTimeWorkRequestBuilder<PioneerFetchWorker>()
			.setConstraints(NETWORK)
			.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
			.build()
		WorkManager.getInstance(context).enqueueUniqueWork(FETCH_WORK, ExistingWorkPolicy.KEEP, request)
	}

	fun markFetched(context: Context) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("lastFetch", System.currentTimeMillis()).apply()
	}
}

class PioneerFetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
	override suspend fun doWork(): Result {
		val container = (applicationContext as MicongreApplication).container
		val account = container.accounts.active()
		if (account == null || account.expired) return Result.success()
		if (PioneerData.fetchAndStore(container, account.id).isFailure) return Result.retry()
		PioneerSchedule.markFetched(applicationContext)
		updatePioneerWidgets(applicationContext)
		return Result.success()
	}
}
