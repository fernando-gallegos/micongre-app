package com.micongre.app.widget

import androidx.compose.runtime.CompositionLocalProvider
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.micongre.app.MainActivity
import com.micongre.app.MicongreApplication
import com.micongre.app.data.PanelSummary
import com.micongre.app.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Lo que muestran los widgets: las próximas asignaciones de la cuenta activa, leídas de lo guardado en el teléfono (sin pedir nada a internet). */
data class WidgetState(
	val signedIn: Boolean,
	val upcoming: List<Item>,
	/** Cambios guardados sin internet que todavía no se enviaron. */
	val pending: Int,
) {
	data class Item(val date: String, val label: String)
}

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

object WidgetData {
	suspend fun load(context: Context): WidgetState {
		val container = (context.applicationContext as MicongreApplication).container
		val account = container.accounts.active()
		if (account == null || account.expired) return WidgetState(signedIn = false, upcoming = emptyList(), pending = 0)
		// La misma dirección con la que se guardó la respuesta de Mi Panel (ver OfflineInterceptor).
		val server = account.server.toHttpUrlOrNull() ?: return WidgetState(signedIn = true, upcoming = emptyList(), pending = container.outbox.count(account.id))
		val url = okhttp3.HttpUrl.Builder().scheme(server.scheme).host(server.host).port(server.port).encodedPath("/api/panel/summary").build().toString()
		val body = container.offlineCache.get(account.id, url)
		val summary = body?.let { runCatching { json.decodeFromString(PanelSummary.serializer(), it) }.getOrNull() }
		val today = LocalDate.now().toString()
		val items = summary?.upcoming.orEmpty()
			.filter { (it.dateEnd ?: it.date) >= today && it.territory?.status != "completed" && it.territory?.status != "returned" }
			.sortedBy { it.date }
			.map { WidgetState.Item(it.date, it.label) }
		return WidgetState(signedIn = true, upcoming = items, pending = container.outbox.count(account.id))
	}
}

// Colores fijos (claro/oscuro del teléfono): el widget no puede leer el tema de la app.
private val BG = ColorProvider(day = Color(0xFFF7F5F0), night = Color(0xFF1C1F23))
private val INK = ColorProvider(day = Color(0xFF1C1B17), night = Color(0xFFEEEAE1))
private val SOFT = ColorProvider(day = Color(0xFF6C6658), night = Color(0xFF9A9488))
private val WARN = ColorProvider(day = Color(0xFFB5563C), night = Color(0xFFE2896C))

private fun dayLabel(iso: String): String {
	val today = LocalDate.now()
	val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return Fmt.dmy(iso)
	return when (date) {
		today -> "Hoy"
		today.plusDays(1) -> "Mañana"
		else -> Fmt.dmy(iso)
	}
}

/** Al tocar el widget se abre la app. */
@androidx.compose.runtime.Composable
private fun openApp(): android.content.Intent =
	android.content.Intent(androidx.glance.LocalContext.current, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)

@androidx.compose.runtime.Composable
private fun Header(title: String) {
	Text(title.uppercase(), style = TextStyle(color = LocalWidgetAccent.current, fontSize = 11.sp, fontWeight = FontWeight.Bold))
}

@androidx.compose.runtime.Composable
private fun SmallContent(state: WidgetState) {
	Column(
		modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(20.dp).padding(14.dp).clickable(actionStartActivity(openApp())),
		verticalAlignment = Alignment.Vertical.CenterVertically,
	) {
		Header("Mi Congre")
		Spacer(GlanceModifier.height(4.dp))
		val next = state.upcoming.firstOrNull()
		when {
			!state.signedIn -> Text("Abre la app para ver tus asignaciones", style = TextStyle(color = INK, fontSize = 13.sp))
			next == null -> Text("Sin asignaciones próximas", style = TextStyle(color = INK, fontSize = 14.sp, fontWeight = FontWeight.Medium))
			else -> {
				Text(dayLabel(next.date), style = TextStyle(color = LocalWidgetAccent.current, fontSize = 13.sp, fontWeight = FontWeight.Bold))
				Text(next.label, style = TextStyle(color = INK, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 3)
			}
		}
		if (state.pending > 0) {
			Spacer(GlanceModifier.height(4.dp))
			Text("${state.pending} por enviar", style = TextStyle(color = WARN, fontSize = 11.sp, fontWeight = FontWeight.Medium))
		}
	}
}

@androidx.compose.runtime.Composable
private fun LargeContent(state: WidgetState) {
	// Redimensionable: se muestran tantas asignaciones como caben en el alto actual.
	val rows = (((androidx.glance.LocalSize.current.height.value - 70f) / 40f).toInt()).coerceIn(1, 12)
	Column(
		modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(20.dp).padding(16.dp).clickable(actionStartActivity(openApp())),
	) {
		Header("Mis próximas asignaciones")
		Spacer(GlanceModifier.height(8.dp))
		when {
			!state.signedIn -> Text("Abre la app para ver tus asignaciones", style = TextStyle(color = INK, fontSize = 14.sp))
			state.upcoming.isEmpty() -> Text("No tienes asignaciones próximas.", style = TextStyle(color = INK, fontSize = 14.sp))
			else -> state.upcoming.take(rows).forEach { item ->
				Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
					Text(dayLabel(item.date), modifier = GlanceModifier.width(72.dp), style = TextStyle(color = LocalWidgetAccent.current, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
					Text(item.label, style = TextStyle(color = INK, fontSize = 14.sp), maxLines = 2)
				}
			}
		}
		if (state.pending > 0) {
			Spacer(GlanceModifier.height(6.dp))
			Text("${state.pending} cambio${if (state.pending == 1) "" else "s"} guardado${if (state.pending == 1) "" else "s"} en el teléfono, por enviar", style = TextStyle(color = WARN, fontSize = 11.sp, fontWeight = FontWeight.Medium))
		}
	}
}

/** Widget pequeño: la próxima asignación. */
class SmallWidget : GlanceAppWidget() {
	override val sizeMode = SizeMode.Single
	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val state = WidgetData.load(context)
		val accent = widgetAccent(context).provider()
		provideContent { CompositionLocalProvider(LocalWidgetAccent provides accent) { GlanceTheme { SmallContent(state) } } }
	}
}

/** Widget grande (4×4, redimensionable): la lista de las próximas asignaciones, tantas como caben. */
class LargeWidget : GlanceAppWidget() {
	override val sizeMode = SizeMode.Exact
	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val state = WidgetData.load(context)
		val accent = widgetAccent(context).provider()
		provideContent { CompositionLocalProvider(LocalWidgetAccent provides accent) { GlanceTheme { LargeContent(state) } } }
	}
}

class SmallWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = SmallWidget()
}

class LargeWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = LargeWidget()
}

/**
 * Los widgets NO tienen temporizador propio (updatePeriodMillis = 0): solo se redibujan cuando llega un dato nuevo (push, abrir la app, la revisión
 * de fondo, lo guardado sin internet) o cambia la cuenta. Así no despiertan al teléfono por su cuenta ni gastan batería. Se juntan los avisos
 * seguidos (15 s) para no redibujar de más.
 */
object WidgetUpdater {
	@Volatile private var last = 0L

	/** `refreshPreaching`: además se piden las salidas de predicación del mes (como mucho cada 3 horas y solo si hay un widget de predicación puesto). */
	fun request(context: Context, refreshPreaching: Boolean = false, force: Boolean = false) {
		val now = System.currentTimeMillis()
		if (!force && now - last < 15_000) return
		last = now
		val app = context.applicationContext
		CoroutineScope(Dispatchers.Default).launch {
			runCatching { SmallWidget().updateAll(app) }
			runCatching { LargeWidget().updateAll(app) }
			updatePreachingWidgets(app)
			updatePioneerWidgets(app)
			if (refreshPreaching && hasPioneerWidget(app)) PioneerSchedule.fetchIfStale(app)
			if (refreshPreaching && runCatching { androidx.glance.appwidget.GlanceAppWidgetManager(app).getGlanceIds(PreachingWidget::class.java).isNotEmpty() }.getOrDefault(false)) {
				PreachingSchedule.fetchIfStale(app)
			}
		}
	}
}
