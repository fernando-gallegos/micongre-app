package com.micongre.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micongre.app.AppContainer
import com.micongre.app.data.AttendanceRequest
import com.micongre.app.data.Me
import com.micongre.app.data.api
import com.micongre.app.ui.AppCard
import com.micongre.app.ui.EmptyText
import com.micongre.app.ui.Fmt
import com.micongre.app.ui.Loading
import com.micongre.app.ui.SectionLabel
import com.micongre.app.ui.rememberLoad
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Mismo criterio que en la web: admin, o el privilegio "Registro de asistencia" (todo anciano lo tiene por defecto). */
fun canRecordAttendance(me: Me) = me.has("meeting_attendance_recorder")

/** Informes: sin la persona objetivo, si puede registrar asistencia se ofrecen las pestañas Tarjeta S-21 / Asistencia. */
@Composable
fun ReportsHost(container: AppContainer, me: Me, userId: String?, onPickPublisher: (String) -> Unit) {
	if (userId != null || !canRecordAttendance(me)) {
		ReportsScreen(container, me, userId, onPickPublisher)
		return
	}
	var tab by remember { mutableStateOf("s21") }
	Column(Modifier.fillMaxSize()) {
		Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			FilterChip(selected = tab == "s21", onClick = { tab = "s21" }, label = { Text("Tarjeta S-21") })
			FilterChip(selected = tab == "attendance", onClick = { tab = "attendance" }, label = { Text("Asistencia") })
		}
		Box(Modifier.weight(1f)) {
			if (tab == "s21") ReportsScreen(container, me, null, onPickPublisher) else AttendanceScreen(container)
		}
	}
}

private fun num(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(java.util.Locale("es"), v)

private fun monthLabel(ym: String): String {
	val (y, m) = ym.split("-").map { it.toInt() }
	return "${Fmt.monthNames[m - 1]} de $y"
}

/** Un tipo de reunión dentro de un mes: promedio (presencial + Zoom) y de cuántas reuniones sale. */
@Composable
private fun StatCell(label: String, stat: com.micongre.app.data.AttendanceMonthStat?, modifier: Modifier = Modifier) {
	Column(modifier) {
		Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
		if (stat == null) {
			Text("—", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
		} else {
			Text(num(stat.average), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
			Text(
				"${stat.meetings} ${if (stat.meetings == 1) "reunión" else "reuniones"}" +
					if (stat.averageZoom > 0) " · ${num(stat.averageInPerson)} en salón + ${num(stat.averageZoom)} Zoom" else "",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

private data class MeetingRow(val type: String, val id: String, val date: String, val inPerson: Int?, val zoom: Int?) {
	val key get() = "$type-$id"
	val label get() = if (type == "midweek") "Entre semana" else "Fin de semana"
}

/** Contador grande como el de la web: − / número (también se escribe) / +. */
@Composable
private fun Counter(label: String, value: String, onChange: (String) -> Unit, big: Boolean) {
	val step = { delta: Int -> onChange(((value.toIntOrNull() ?: 0) + delta).coerceIn(0, 9999).toString()) }
	val button: Dp = if (big) 56.dp else 46.dp
	Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (big) 16.dp else 12.dp)) {
			Surface(
				shape = CircleShape,
				color = MaterialTheme.colorScheme.surface,
				border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
				modifier = Modifier.size(button).clickable { step(-1) },
			) {
				Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Remove, "Quitar uno ($label)", Modifier.size(if (big) 26.dp else 22.dp)) }
			}
			Box(Modifier.width(if (big) 120.dp else 88.dp), contentAlignment = Alignment.Center) {
				BasicTextField(
					value = value,
					onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
					singleLine = true,
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
					textStyle = TextStyle(
						fontSize = if (big) 60.sp else 40.sp,
						fontWeight = FontWeight.Medium,
						textAlign = TextAlign.Center,
						color = MaterialTheme.colorScheme.onSurface,
					),
					cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
					decorationBox = { inner ->
						Box(contentAlignment = Alignment.Center) {
							if (value.isEmpty()) {
								Text("0", fontSize = if (big) 60.sp else 40.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), fontWeight = FontWeight.Medium)
							}
							inner()
						}
					},
				)
			}
			Surface(
				shape = CircleShape,
				color = MaterialTheme.colorScheme.surface,
				border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
				modifier = Modifier.size(button).clickable { step(1) },
			) {
				Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "Agregar uno ($label)", Modifier.size(if (big) 26.dp else 22.dp)) }
			}
		}
		Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}

/** Asistencia a las reuniones del año de servicio en curso, hasta la semana actual (las futuras todavía no han pasado). Arriba, el
 * formulario de la reunión elegida (sumar, restar o escribir, presencial y Zoom); debajo, la lista para elegir otra. */
@Composable
fun AttendanceScreen(container: AppContainer) {
	val today = LocalDate.now()
	val from = LocalDate.of(if (today.monthValue >= 9) today.year else today.year - 1, 9, 1).toString()
	// Domingo de esta semana: la reunión de esta semana ya se puede registrar.
	val to = today.plusDays((7 - today.dayOfWeek.value).toLong()).toString()
	val scope = rememberCoroutineScope()
	val state = rememberLoad(from) {
		api {
			// Tres consultas en una: reuniones de entre semana, de fin de semana y las cifras ya registradas.
			val midweek = container.apiService.midweekList(from, to)
			val weekend = container.apiService.weekendList(from, to)
			val counts = container.apiService.attendanceFrom(from)
			retrofit2.Response.success(
				Triple(
					midweek.body()?.items.orEmpty().map { Triple("midweek", it.id, it.meetingDate) },
					weekend.body()?.items.orEmpty().map { Triple("weekend", it.id, it.meetingDate) },
					counts.body()?.items.orEmpty(),
				),
			)
		}
	}
	val summary = rememberLoad(from) { api { container.apiService.attendanceSummary(from) } }
	var selectedKey by remember { mutableStateOf<String?>(null) }
	var saved by remember { mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap()) }

	val data = state.data
	if (state.loading && data == null) return Loading()
	if (data == null) {
		EmptyText("No se pudo cargar la asistencia.", Modifier.padding(16.dp))
		return
	}
	val counts = data.third.associate { "${it.meetingType}-${it.meetingId}" to (it.inPersonCount to it.zoomCount) } + saved
	val rows = (data.first + data.second)
		.filter { it.third <= to }
		.map { (type, id, date) ->
			val c = counts["$type-$id"]
			MeetingRow(type, id, date, c?.first, c?.second)
		}
		.sortedByDescending { it.date }
	// La reunión a registrar por defecto: la más reciente ya realizada que todavía no tiene cifras; si todas tienen, la más reciente.
	val past = rows.filter { it.date <= today.toString() }
	val selected = rows.firstOrNull { it.key == selectedKey } ?: past.firstOrNull { it.inPerson == null } ?: past.firstOrNull() ?: rows.firstOrNull()

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
		if (rows.isEmpty() || selected == null) {
			EmptyText("Todavía no hay reuniones realizadas en este año de servicio.")
			return@Column
		}
		AppCard(Modifier.padding(top = 4.dp)) {
			// Se reinicia al cambiar de reunión o al recargar las cifras guardadas.
			var inPerson by remember(selected.key) { mutableStateOf(selected.inPerson?.toString() ?: "") }
			var zoom by remember(selected.key) { mutableStateOf(selected.zoom?.takeIf { it > 0 }?.toString() ?: "") }
			var saving by remember(selected.key) { mutableStateOf(false) }
			var done by remember(selected.key) { mutableStateOf(false) }
			var error by remember(selected.key) { mutableStateOf<String?>(null) }
			Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
				Text(
					"${selected.label} · ${Fmt.longDate(selected.date)}",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Counter("Presentes en el salón", inPerson, { inPerson = it; done = false }, big = true)
				Counter("Conectados por Zoom", zoom, { zoom = it; done = false }, big = false)
				error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
				Button(
					enabled = !saving,
					modifier = Modifier.fillMaxWidth().height(52.dp),
					onClick = {
						saving = true
						error = null
						done = false
						scope.launch {
							val body = AttendanceRequest(selected.type, selected.id, selected.date, inPerson.toIntOrNull() ?: 0, zoom.toIntOrNull() ?: 0)
							val ok = api { container.apiService.saveAttendance(body) }.isSuccess
							saving = false
							if (ok) {
								saved = saved + (selected.key to (body.inPersonCount to body.zoomCount))
								done = true
								summary.reload()
								state.reload()
							} else {
								error = "No se pudo guardar. Revisa tu conexión e inténtalo de nuevo."
							}
						}
					},
				) {
					if (done) Icon(Icons.Filled.Check, null, Modifier.size(18.dp).padding(end = 0.dp))
					Text(if (saving) "Guardando…" else if (done) " Guardado" else "Guardar asistencia")
				}
			}
		}

		val months = summary.data?.months.orEmpty().reversed()
		if (months.isNotEmpty()) {
			SectionLabel("Promedio mensual")
			AppCard {
				Column {
					months.forEachIndexed { i, m ->
						Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
							Text(monthLabel(m.month), fontWeight = FontWeight.SemiBold)
							Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
								StatCell("Entre semana", m.midweek, Modifier.weight(1f))
								StatCell("Fin de semana", m.weekend, Modifier.weight(1f))
							}
						}
						if (i < months.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
					}
				}
			}
			Text(
				"Presentes en el salón más conectados por Zoom, por reunión, entre las reuniones con asistencia registrada de cada mes (una reunión cuenta en el mes en que se celebra).",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 6.dp, start = 4.dp),
			)
		}

		SectionLabel("Año de servicio ${from.take(4)}–${from.take(4).toInt() + 1}")
		AppCard {
			Column {
				rows.forEachIndexed { i, r ->
					val active = r.key == selected.key
					Row(
						Modifier
							.fillMaxWidth()
							.background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
							.clickable { selectedKey = r.key }
							.padding(horizontal = 16.dp, vertical = 12.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(Modifier.weight(1f)) {
							Text("${r.label} · ${Fmt.dmy(r.date)}", fontWeight = FontWeight.Medium)
							if ((r.zoom ?: 0) > 0) {
								Text("${r.zoom} por Zoom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
							}
						}
						Text(
							r.inPerson?.toString() ?: "—",
							fontSize = 22.sp,
							fontWeight = FontWeight.SemiBold,
							color = if (r.inPerson == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
						)
					}
					if (i < rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
				}
			}
		}
	}
}
