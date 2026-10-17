package com.micongre.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

val ES: Locale = Locale("es")

object Fmt {
	private val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
	val monthNames = listOf(
		"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
	)

	fun parse(iso: String?): LocalDate? = try {
		if (iso.isNullOrBlank()) null else LocalDate.parse(iso.take(10))
	} catch (e: Exception) {
		null
	}

	/** dd/mmm/yyyy, como en la web. */
	fun dmy(iso: String?): String {
		val d = parse(iso) ?: return "—"
		return "%02d/%s/%d".format(d.dayOfMonth, months[d.monthValue - 1], d.year)
	}

	/** "24 de Septiembre de 2026": solo para títulos de pantalla, como en la web. */
	fun longDate(iso: String?): String {
		val d = parse(iso) ?: return iso ?: ""
		return "${d.dayOfMonth} de ${monthNames[d.monthValue - 1]} de ${d.year}"
	}

	private fun dayMonth(d: LocalDate) = "%02d/%s".format(d.dayOfMonth, months[d.monthValue - 1])

	/** Rango de una semana (lunes a domingo) como en el selector de la web: "06/oct - 12/oct", "28/sep - 04/oct de 2026". */
	fun weekRange(monday: LocalDate): String {
		val end = monday.plusDays(6)
		return when {
			monday.year != end.year -> "${dmy(monday.toString())} - ${dmy(end.toString())}"
			monday.monthValue == end.monthValue -> "${dayMonth(monday)} - ${dayMonth(end)}"
			else -> "${dayMonth(monday)} - ${dayMonth(end)} de ${end.year}"
		}
	}

	fun hours(v: Double?): String {
		if (v == null || v == 0.0) return ""
		return if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
	}

	/** "hace 5 min", "ayer"... para los avisos. */
	fun timeAgo(iso: String): String = try {
		val then = java.time.Instant.parse(iso)
		val mins = java.time.Duration.between(then, java.time.Instant.now()).toMinutes()
		when {
			mins < 1 -> "ahora"
			mins < 60 -> "hace $mins min"
			mins < 60 * 24 -> "hace ${mins / 60} h"
			mins < 60 * 48 -> "ayer"
			mins < 60 * 24 * 7 -> "hace ${mins / (60 * 24)} d"
			else -> dmy(iso)
		}
	} catch (e: Exception) {
		""
	}
}

/** Recarga global: al deslizar hacia abajo se sube `tick` y todas las cargas visibles vuelven a pedir lo último; `active` cuenta las
 * que siguen en curso (para mostrar el indicador de actualización). */
@Stable
class RefreshController {
	var tick by mutableIntStateOf(0)
	var active by mutableIntStateOf(0)
}

val LocalRefresh = staticCompositionLocalOf { RefreshController() }

/** Estado de una carga: datos, error y recarga manual. */
@Stable
class LoadState<T>(
	private val scope: CoroutineScope,
	private val refresh: RefreshController? = null,
	private val block: suspend () -> Result<T>,
) {
	var loading by mutableStateOf(true)
		private set
	var data by mutableStateOf<T?>(null)
		private set
	var error by mutableStateOf<Throwable?>(null)
		private set

	fun reload() {
		scope.launch {
			loading = true
			refresh?.let { it.active += 1 }
			try {
				block().onSuccess { data = it; error = null }.onFailure { error = it }
			} finally {
				refresh?.let { it.active -= 1 }
				loading = false
			}
		}
	}
}

/** Carga al entrar (y cada vez que cambie [key]). */
@Composable
fun <T> rememberLoad(key: Any? = Unit, block: suspend () -> Result<T>): LoadState<T> {
	val scope = rememberCoroutineScope()
	val refresh = LocalRefresh.current
	val state = remember(key) { LoadState(scope, refresh, block) }
	LaunchedEffect(key) { state.reload() }
	// Deslizar hacia abajo (o volver la conexión) fuerza pedir de nuevo lo último.
	var seenTick by remember { mutableIntStateOf(refresh.tick) }
	LaunchedEffect(refresh.tick) {
		if (refresh.tick != seenTick) {
			seenTick = refresh.tick
			state.reload()
		}
	}
	return state
}

@Composable
fun Loading(modifier: Modifier = Modifier.fillMaxSize()) {
	Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 3.dp) }
}

@Composable
fun EmptyText(text: String, modifier: Modifier = Modifier) {
	Text(
		text,
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = modifier.padding(vertical = 12.dp),
	)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
	Text(
		text.uppercase(),
		style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		fontWeight = FontWeight.SemiBold,
		modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
	)
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
	Surface(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(20.dp),
		color = MaterialTheme.colorScheme.surface,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
	) { content() }
}

/** Lista de filas dentro de una tarjeta, con divisores. */
@Composable
fun RowList(items: List<@Composable () -> Unit>) {
	AppCard {
		Column {
			items.forEachIndexed { i, row ->
				Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) { row() }
				if (i < items.lastIndex) {
					Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)).padding(0.5.dp))
				}
			}
		}
	}
}

@Composable
fun Badge(text: String, color: Color = MaterialTheme.colorScheme.primary, soft: Color = MaterialTheme.colorScheme.primaryContainer) {
	Box(Modifier.clip(RoundedCornerShape(50)).background(soft).padding(horizontal = 9.dp, vertical = 3.dp)) {
		Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
	}
}

@Composable
fun ColorDot(color: Color, size: Int = 10) {
	Box(Modifier.padding(end = 0.dp).clip(CircleShape).background(color).padding(size.dp / 2))
}

@Composable
fun PageTitle(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
	Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
			if (subtitle != null) {
				Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
		trailing()
	}
}

fun parseColor(hex: String?, fallback: Color = Color(0xFF888888)): Color = try {
	if (hex.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
	fallback
}

val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
val ArrangementSpaced = Arrangement.spacedBy(0.dp)

/** Campo de fecha: muestra "03/oct/2026" y abre el selector de fecha nativo de Android al tocarlo. Guarda AAAA-MM-DD. */
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
	val context = androidx.compose.ui.platform.LocalContext.current
	val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	fun open() {
		val d = Fmt.parse(value) ?: LocalDate.now()
		android.app.DatePickerDialog(
			context,
			{ _, y, m, day -> onChange(LocalDate.of(y, m + 1, day).toString()) },
			d.year, d.monthValue - 1, d.dayOfMonth,
		).show()
	}
	LaunchedEffect(pressed) { if (pressed) open() }
	androidx.compose.material3.OutlinedTextField(
		value = if (value.isBlank()) "" else Fmt.dmy(value),
		onValueChange = {},
		readOnly = true,
		label = { Text(label) },
		trailingIcon = {
			androidx.compose.material3.Icon(Icons.Filled.CalendarMonth, "Elegir fecha")
		},
		interactionSource = interaction,
		modifier = modifier.fillMaxWidth(),
	)
}

/** Lista dentro de una tarjeta donde cada fila se quita deslizándola hacia la derecha. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun <T> SwipeToHideList(items: List<T>, key: (T) -> String, onHide: (T) -> Unit, content: @Composable (T) -> Unit) {
	AppCard {
		Column {
			items.forEachIndexed { i, item ->
				androidx.compose.runtime.key(key(item)) {
					val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
						positionalThreshold = { distance -> distance * 0.25f },
						confirmValueChange = { value ->
							if (value == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) {
								onHide(item)
								true
							} else {
								false
							}
						},
					)
					androidx.compose.material3.SwipeToDismissBox(
						state = state,
						enableDismissFromEndToStart = false,
						backgroundContent = {
							Box(
								Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary).padding(horizontal = 24.dp),
								contentAlignment = Alignment.CenterStart,
							) {
								androidx.compose.material3.Icon(
									androidx.compose.material.icons.Icons.Filled.VisibilityOff,
									"Ocultar aviso",
									tint = MaterialTheme.colorScheme.onPrimary,
								)
							}
						},
					) {
						Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp)) { content(item) }
					}
					if (i < items.lastIndex) {
						Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)).padding(0.5.dp))
					}
				}
			}
		}
	}
}
