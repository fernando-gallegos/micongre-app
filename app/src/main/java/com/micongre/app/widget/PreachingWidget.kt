package com.micongre.app.widget

import androidx.compose.runtime.CompositionLocalProvider
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
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
import com.micongre.app.data.PreachingMonthResponse
import com.micongre.app.data.PreachingSession
import com.micongre.app.data.PreachingTerritory
import com.micongre.app.data.api
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Lo que muestra el widget de predicación. `session` = null: no hay ninguna registrada (se invita a predicar por cartas). */
data class PreachingState(
	val signedIn: Boolean,
	val session: PreachingSession?,
	val territories: List<PreachingTerritory>,
	/** Motivo corto si no hay datos y no se pudieron pedir (sin conexión, sesión vencida…); null si todo bien. */
	val failure: String? = null,
)

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private fun monthsToRead(today: LocalDate): List<String> =
	listOf(today, today.plusMonths(1)).map { "%04d-%02d".format(it.year, it.monthValue) }

/** Dirección con la que se guarda la respuesta de un mes (ver OfflineInterceptor). */
private fun monthUrl(server: HttpUrl, month: String): String =
	HttpUrl.Builder().scheme(server.scheme).host(server.host).port(server.port).encodedPath("/api/preaching").addQueryParameter("month", month).build().toString()

object PreachingData {
	private fun store(month: String) = "preaching:$month"

	/** Pide el mes al servidor y guarda la copia del widget. */
	suspend fun fetchAndStore(container: com.micongre.app.AppContainer, accountId: String, month: String): Result<PreachingMonthResponse> {
		val result = fetchWithin { api { container.apiFor(accountId).preachingMonth(month) } }
		result.getOrNull()?.let { WidgetStore.put(container, accountId, store(month), json.encodeToString(PreachingMonthResponse.serializer(), it)) }
		return result
	}

	/** Lo guardado de los dos meses; si no hay nada de ninguno (primera vez), se piden al servidor en el momento. */
	suspend fun load(context: Context, now: LocalDateTime = LocalDateTime.now(), fetchIfMissing: Boolean = true): PreachingState {
		val container = (context.applicationContext as MicongreApplication).container
		val account = container.accounts.active()
		if (account == null || account.expired) return PreachingState(false, null, emptyList())
		val months = monthsToRead(now.toLocalDate())
		val server = account.server.toHttpUrlOrNull()

		fun cached(month: String): PreachingMonthResponse? =
			(WidgetStore.get(container, account.id, store(month)) ?: server?.let { container.offlineCache.get(account.id, monthUrl(it, month)) })
				?.let { runCatching { json.decodeFromString(PreachingMonthResponse.serializer(), it) }.getOrNull() }

		val loaded = months.associateWith { cached(it) }.toMutableMap()
		// Datos viejos (más de 3 horas): se intentan poner al día en el momento; si falla, se muestra lo guardado.
		if (loaded.values.any { it != null } && fetchIfMissing && PreachingSchedule.isStale(context)) {
			var ok = true
			for (month in months) {
				val fetched = PreachingData.fetchAndStore(container, account.id, month)
				fetched.getOrNull()?.let { loaded[month] = it } ?: run { ok = false }
			}
			if (ok) PreachingSchedule.markFetched(context)
		}
		var failure: String? = null
		if (loaded.values.all { it == null } && fetchIfMissing) {
			for (month in months) {
				val fetched = fetchAndStore(container, account.id, month)
				loaded[month] = fetched.getOrNull()
				if (fetched.isFailure) {
					failure = failureReason(fetched.exceptionOrNull())
					android.util.Log.w("PreachingWidget", "No se pudieron pedir las salidas de $month: ${fetched.exceptionOrNull()}")
				}
			}
			// Si al menos uno salió bien no hay fallo que mostrar.
			if (loaded.values.any { it != null }) {
				failure = null
				PreachingSchedule.markFetched(context)
			}
		}
		val next = pickNextSession(loaded.values.flatMap { it?.items.orEmpty() }, now)
		return PreachingState(true, next, next?.territories.orEmpty(), failure = failure.takeIf { loaded.values.all { it == null } })
	}
}

private val CAROUSEL_SESSION = stringPreferencesKey("carousel_session")
private val CAROUSEL_INDEX = intPreferencesKey("carousel_index")
private val DELTA = ActionParameters.Key<Int>("delta")

private val BG = ColorProvider(day = Color(0xFFF7F5F0), night = Color(0xFF1C1F23))
private val INK = ColorProvider(day = Color(0xFF1C1B17), night = Color(0xFFEEEAE1))
private val SOFT = ColorProvider(day = Color(0xFF6C6658), night = Color(0xFF9A9488))

@androidx.compose.runtime.Composable
private fun openApp(): android.content.Intent =
	android.content.Intent(LocalContext.current, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)

@androidx.compose.runtime.Composable
private fun PreachingContent(state: PreachingState) {
	val prefs = currentState<Preferences>()
	val session = state.session
	// El carrusel vuelve al primer territorio cuando cambia la predicación mostrada.
	val index = if (session != null && prefs[CAROUSEL_SESSION] == session.id) (prefs[CAROUSEL_INDEX] ?: 0).coerceIn(0, (state.territories.size - 1).coerceAtLeast(0)) else 0

	Column(modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(20.dp).padding(12.dp)) {
		// Arriba: la fecha (y la hora) de la predicación; tocar abre la app.
		Row(modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(openApp())), verticalAlignment = Alignment.Vertical.CenterVertically) {
			val header = when {
				!state.signedIn -> "Predicación"
				session == null -> "Predicación"
				else -> "${dateLabel(session, LocalDate.now())} · ${timeLabel(session.time)}"
			}
			Text(header, style = TextStyle(color = LocalWidgetAccent.current, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
		}
		Spacer(GlanceModifier.height(6.dp))

		when {
			!state.signedIn -> Body("Abre la app para ver la predicación", null)
			// Sin datos (no se pudieron pedir): se dice por qué y al tocar se vuelve a intentar.
			state.failure != null -> Box(modifier = GlanceModifier.fillMaxSize().clickable(actionRunCallback<RetryPreachingAction>()), contentAlignment = Alignment.Center) {
				Body("No se pudo cargar (${state.failure}). Toca para reintentar", null)
			}
			session == null || state.territories.isEmpty() -> Body("Aprovecha a predicar por cartas", null)
			else -> {
				val territory = state.territories[index]
				val many = state.territories.size > 1
				Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
					if (many) {
						Text("‹", modifier = GlanceModifier.width(22.dp).fillMaxSize().clickable(actionRunCallback<StepTerritoryAction>(actionParametersOf(DELTA to -1))),
							style = TextStyle(color = LocalWidgetAccent.current, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
					}
					Box(
						modifier = GlanceModifier.defaultWeight().fillMaxSize().then(
							if (many) GlanceModifier.clickable(actionRunCallback<StepTerritoryAction>(actionParametersOf(DELTA to 1))) else GlanceModifier,
						),
						contentAlignment = Alignment.Center,
					) {
						Body(territory.name?.takeIf { it.isNotBlank() } ?: "Territorio ${territory.number}", territory.assigneeName)
					}
					if (many) {
						Text("›", modifier = GlanceModifier.width(22.dp).fillMaxSize().clickable(actionRunCallback<StepTerritoryAction>(actionParametersOf(DELTA to 1))),
							style = TextStyle(color = LocalWidgetAccent.current, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
					}
				}
				if (many) {
					Text(
						state.territories.indices.joinToString(" ") { if (it == index) "●" else "○" },
						modifier = GlanceModifier.fillMaxWidth(),
						style = TextStyle(color = SOFT, fontSize = 10.sp, textAlign = TextAlign.Center),
					)
				}
			}
		}
	}
}

/** Nombre grande y, debajo y en chiquito, quién lo tiene asignado. */
@androidx.compose.runtime.Composable
private fun Body(title: String, assignee: String?) {
	Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally, verticalAlignment = Alignment.Vertical.CenterVertically) {
		Text(title, style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 3)
		if (!assignee.isNullOrBlank()) {
			Spacer(GlanceModifier.height(2.dp))
			Text(assignee, style = TextStyle(color = SOFT, fontSize = 10.sp, textAlign = TextAlign.Center), maxLines = 1)
		}
	}
}

/** Widget 2×2 de predicación: dónde es la siguiente salida, con carrusel entre sus territorios (toca ‹ › o el nombre; sin temporizador, no gasta batería). */
class PreachingWidget : GlanceAppWidget() {
	override val sizeMode = SizeMode.Single
	override val stateDefinition = PreferencesGlanceStateDefinition

	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val state = PreachingData.load(context)
		// Un solo aviso programado: cuando a la predicación mostrada le pasen las 2 horas, el widget cambia a la siguiente (no hay revisiones periódicas).
		state.session?.let { PreachingSchedule.scheduleSwitch(context, it) }
		val accent = widgetAccent(context).provider()
		provideContent { CompositionLocalProvider(LocalWidgetAccent provides accent) { GlanceTheme { PreachingContent(state) } } }
	}
}

/** Toque en «no se pudo cargar»: vuelve a pedir las salidas y redibuja. */
class RetryPreachingAction : ActionCallback {
	override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
		val container = (context.applicationContext as MicongreApplication).container
		container.accounts.active()?.takeIf { !it.expired }?.let { account ->
			for (month in monthsToRead(LocalDate.now())) PreachingData.fetchAndStore(container, account.id, month)
		}
		PreachingWidget().update(context, glanceId)
	}
}

class PreachingWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = PreachingWidget()

	/** Al agregar el primer widget se piden las salidas del mes para que no salga vacío. */
	override fun onEnabled(context: Context) {
		super.onEnabled(context)
		PreachingSchedule.fetchNow(context)
	}
}

/** Avanza o retrocede el carrusel de territorios. */
class StepTerritoryAction : ActionCallback {
	override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
		val delta = parameters[DELTA] ?: 1
		val state = PreachingData.load(context)
		val session = state.session ?: return
		val count = state.territories.size
		if (count < 2) return
		updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
			val current = if (prefs[CAROUSEL_SESSION] == session.id) prefs[CAROUSEL_INDEX] ?: 0 else 0
			prefs.toMutablePreferences().apply {
				this[CAROUSEL_SESSION] = session.id
				this[CAROUSEL_INDEX] = ((current + delta) % count + count) % count
			}
		}
		PreachingWidget().update(context, glanceId)
	}
}

/** Redibuja el widget de predicación (también lo hace [WidgetUpdater] junto con los demás). */
suspend fun updatePreachingWidgets(context: Context) {
	runCatching { PreachingWidget().updateAll(context) }
}

/** Trabajos de fondo del widget de predicación: pedir las salidas del mes (poco a poco) y cambiar a la siguiente cuando pasan las 2 horas. */
object PreachingSchedule {
	private const val FETCH_WORK = "micongre-preaching-fetch"
	private const val SWITCH_WORK = "micongre-preaching-switch"
	private const val PREFS = "preaching_widget"
	private const val MIN_FETCH_GAP_MS = 3 * 60 * 60 * 1000L // las salidas casi no cambian: se piden como mucho cada 3 horas

	private val NETWORK = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()

	/** Pide las salidas del mes en curso y del siguiente (si pasaron al menos 3 horas desde la última vez). */
	fun isStale(context: Context): Boolean =
		System.currentTimeMillis() - context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("lastFetch", 0L) >= MIN_FETCH_GAP_MS

	fun fetchIfStale(context: Context) {
		if (isStale(context)) fetchNow(context)
	}

	fun fetchNow(context: Context) {
		val request = OneTimeWorkRequestBuilder<PreachingFetchWorker>()
			.setConstraints(NETWORK)
			.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
			.build()
		WorkManager.getInstance(context).enqueueUniqueWork(FETCH_WORK, ExistingWorkPolicy.KEEP, request)
	}

	fun markFetched(context: Context) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("lastFetch", System.currentTimeMillis()).apply()
	}

	/** Un único trabajo, justo cuando a esta salida le pasen las 2 horas, para que el widget muestre la siguiente. */
	fun scheduleSwitch(context: Context, session: PreachingSession) {
		val wait = untilSwitch(session, LocalDateTime.now()) ?: return
		val request = OneTimeWorkRequestBuilder<PreachingSwitchWorker>()
			.setInitialDelay(wait.toMinutes() + 1, TimeUnit.MINUTES)
			.build()
		WorkManager.getInstance(context).enqueueUniqueWork(SWITCH_WORK, ExistingWorkPolicy.REPLACE, request)
	}
}

class PreachingFetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
	override suspend fun doWork(): Result {
		val container = (applicationContext as MicongreApplication).container
		val account = container.accounts.active()
		if (account == null || account.expired) return Result.success()
		var failed = false
		for (month in monthsToRead(LocalDate.now())) {
			if (PreachingData.fetchAndStore(container, account.id, month).isFailure) failed = true
		}
		if (failed) return Result.retry()
		PreachingSchedule.markFetched(applicationContext)
		updatePreachingWidgets(applicationContext)
		return Result.success()
	}
}

class PreachingSwitchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
	override suspend fun doWork(): Result {
		updatePreachingWidgets(applicationContext)
		return Result.success()
	}
}
