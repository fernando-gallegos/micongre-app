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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micongre.app.AppContainer
import com.micongre.app.data.PreachingSession
import com.micongre.app.data.api
import com.micongre.app.ui.AppCard
import com.micongre.app.ui.EmptyText
import com.micongre.app.ui.Fmt
import com.micongre.app.ui.Loading
import com.micongre.app.ui.PageTitle
import com.micongre.app.ui.rememberLoad
import java.time.LocalDate

private val WEEKDAYS = listOf("L", "M", "X", "J", "V", "S", "D")

internal fun time12(value: String): String {
	val (h, m) = value.split(":").map { it.toInt() }
	return "${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(m)} ${if (h < 12) "a. m." else "p. m."}"
}

private fun timeShort(value: String): String {
	val (h, m) = value.split(":").map { it.toInt() }
	return "${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(m)}${if (h < 12) "a" else "p"}"
}

/** Casita: general a toda la congregación · grupo: general a grupos · pin: predicación pública. */
private fun iconFor(s: PreachingSession): Pair<ImageVector, String> = when {
	s.kind == "public_preaching" -> Icons.Filled.Place to "Predicación pública"
	s.targets.congregation -> Icons.Filled.Home to "General · toda la congregación"
	else -> Icons.Filled.Groups to "General · grupos"
}

/** Predicación: calendario del mes en solo lectura. Muestra las salidas que le tocan a la persona (a toda la congregación, a su
 * grupo o a ella); quien administra el módulo en la web ve todas. Quien administra la predicación puede agregar salidas tocando un día; editar y borrar es desde la web. */
@Composable
fun PreachingScreen(container: AppContainer) {
	var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
	val key = "%04d-%02d".format(month.year, month.monthValue)
	val state = rememberLoad(key) { api { container.apiService.preachingMonth(key) } }
	var dayOpen by remember { mutableStateOf<String?>(null) }
	var addDate by remember { mutableStateOf<String?>(null) }
	val canManage = state.data?.canManage == true
	val sessions = state.data?.items.orEmpty()
	val byDate = sessions.groupBy { it.date }.mapValues { (_, list) -> list.sortedBy { it.time } }
	val today = LocalDate.now()
	val offset = (month.dayOfWeek.value - 1) // lunes = 0
	val days = (0 until month.lengthOfMonth()).map { month.plusDays(it.toLong()) }
	val cells: List<LocalDate?> = (List(offset) { null } + days).let { it + List((7 - it.size % 7) % 7) { null } }

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
		PageTitle("Predicación", "Las salidas que te corresponden")
		Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
			IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior") }
			Text("${Fmt.monthNames[month.monthValue - 1]} ${month.year}", fontWeight = FontWeight.SemiBold)
			IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente") }
		}
		Row(Modifier.fillMaxWidth()) {
			WEEKDAYS.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
		}
		if (state.loading && state.data == null) {
			Loading(Modifier.fillMaxWidth().height(200.dp))
		} else {
			cells.chunked(7).forEach { week ->
				Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
					week.forEach { date ->
						if (date == null) {
							Box(Modifier.weight(1f).height(66.dp))
						} else {
							val list = byDate[date.toString()].orEmpty()
							DayCell(date, list, date == today, canManage, Modifier.weight(1f)) {
								if (list.isNotEmpty()) dayOpen = date.toString() else if (canManage) addDate = date.toString()
							}
						}
					}
				}
			}
			if (sessions.isEmpty()) EmptyText("No hay predicaciones este mes.")
		}
	}

	dayOpen?.let { date ->
		val list = byDate[date].orEmpty()
		AlertDialog(
			onDismissRequest = { dayOpen = null },
			title = { Text(Fmt.longDate(date)) },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
					list.forEach { s ->
						val (icon, label) = iconFor(s)
						Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
								Text(" " + time12(s.time), fontWeight = FontWeight.SemiBold)
								Text("  $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
							}
							Text(
								(if (s.territories.size == 1) "Territorio " else "Territorios ") +
									s.territories.joinToString(", ") { t -> t.name?.let { "${t.number} — $it" } ?: t.number },
								style = MaterialTheme.typography.bodyMedium,
							)
							val who = when {
								s.targets.congregation -> "Toda la congregación"
								s.targets.groups.isNotEmpty() -> "Grupos: " + s.targets.groups.joinToString(", ") { it.name }
								else -> "Publicadores: " + s.targets.users.joinToString(", ") { it.fullName }
							}
							Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
							s.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
						}
					}
				}
			},
			confirmButton = { TextButton(onClick = { dayOpen = null }) { Text("Cerrar") } },
			dismissButton = if (canManage) ({ TextButton(onClick = { dayOpen = null; addDate = date }) { Text("Agregar") } }) else null,
		)
	}

	addDate?.let { date ->
		AddPreachingDialog(container, date, onDismiss = { addDate = null }, onSaved = { addDate = null; state.reload() })
	}
}

@Composable
private fun DayCell(date: LocalDate, list: List<PreachingSession>, isToday: Boolean, clickable: Boolean, modifier: Modifier, onClick: () -> Unit) {
	val has = list.isNotEmpty()
	Column(
		modifier
			.height(66.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(if (has) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
			.then(
				Modifier.clickable(enabled = has || clickable, onClick = onClick),
			)
			.padding(horizontal = 3.dp, vertical = 3.dp),
		verticalArrangement = Arrangement.spacedBy(1.dp),
	) {
		Text(
			date.dayOfMonth.toString(),
			fontSize = 11.sp,
			fontWeight = FontWeight.SemiBold,
			color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
		)
		if (list.size <= 2) {
			list.forEach { s ->
				val (icon, label) = iconFor(s)
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(11.dp))
					Text(timeShort(s.time), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
				}
			}
		} else {
			Text(
				"Varios",
				fontSize = 10.sp,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.onPrimary,
				modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 5.dp, vertical = 1.dp),
			)
		}
	}
}
