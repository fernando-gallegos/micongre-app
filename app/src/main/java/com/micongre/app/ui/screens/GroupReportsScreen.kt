package com.micongre.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.micongre.app.AppContainer
import com.micongre.app.data.GroupReportItem
import com.micongre.app.data.api
import com.micongre.app.data.isForbidden
import com.micongre.app.ui.AppCard
import com.micongre.app.ui.EmptyText
import com.micongre.app.ui.Fmt
import com.micongre.app.ui.Loading
import com.micongre.app.ui.SectionLabel
import com.micongre.app.ui.rememberLoad
import java.time.LocalDate

private fun hoursText(v: Double?): String = when {
	v == null || v == 0.0 -> ""
	v % 1.0 == 0.0 -> v.toInt().toString()
	else -> "%.1f".format(java.util.Locale("es"), v)
}

/** Pestaña «Grupos»: los datos de la S-21 de un mes, una tabla por grupo (alfabético). Quien solo ve su grupo, solo recibe ese. */
@Composable
fun GroupReportsScreen(container: AppContainer) {
	val today = remember { LocalDate.now() }
	var year by remember { mutableStateOf(today.year) }
	var month by remember { mutableStateOf(today.monthValue) }
	val state = rememberLoad(year * 100 + month) { api { container.apiService.groupReports(year, month) } }

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
			IconButton(onClick = { val d = LocalDate.of(year, month, 1).minusMonths(1); year = d.year; month = d.monthValue }) {
				Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior")
			}
			Text("${Fmt.monthNames[month - 1]} $year", fontWeight = FontWeight.SemiBold)
			IconButton(onClick = { val d = LocalDate.of(year, month, 1).plusMonths(1); year = d.year; month = d.monthValue }) {
				Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente")
			}
		}
		val data = state.data
		when {
			state.loading && data == null -> Loading()
			data == null -> EmptyText(if (state.error?.isForbidden == true) "No supervisas ningún grupo de servicio, así que no tienes informes que revisar aquí." else "No se pudieron cargar los informes.")
			data.groups.isEmpty() -> EmptyText("No hay publicadores en tu alcance.")
			else -> data.groups.forEach { group ->
				val delivered = group.items.count { it.reportId != null }
				// Horas de proyectos: la columna sale si el grupo tiene algún precursor regular (o ya hay horas guardadas).
				val showProject = group.items.any { it.pioneerType == "regular_pioneer" || (it.projectHours ?: 0.0) > 0.0 }
				Column {
					Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
						SectionLabel(group.name)
						Text("$delivered de ${group.items.size} entregados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
					AppCard {
						Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
							HeadCells(showProject)
							group.items.forEach { RowCells(it, showProject) }
							HorizontalDivider(color = MaterialTheme.colorScheme.outline)
							Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
								Text("Total", Modifier.width(NAME_W), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
								Cell(group.items.count { it.reportId != null && it.participatedInMinistry == true }.toString(), bold = true)
								Cell(group.items.sumOf { it.bibleStudiesCount ?: 0 }.takeIf { it > 0 }?.toString() ?: "", bold = true)
								Cell(group.items.count { it.reportId != null && it.isAuxiliaryPioneerThisMonth == true }.takeIf { it > 0 }?.toString() ?: "", bold = true)
								Cell(hoursText(group.items.sumOf { it.hours ?: 0.0 }), bold = true)
								if (showProject) Cell(hoursText(group.items.sumOf { it.projectHours ?: 0.0 }), bold = true)
							}
						}
					}
				}
			}
		}
	}
}

// Anchos fijos de la tabla (se desliza de lado si no cabe).
private val NAME_W = 150.dp
private val NUM_W = 76.dp
private val NOTES_W = 200.dp

@Composable
private fun Cell(text: String, bold: Boolean = false, width: Dp = NUM_W) {
	Text(
		text, Modifier.width(width), textAlign = TextAlign.Center,
		style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
	)
}

@Composable
private fun HeadCells(showProject: Boolean) {
	val style = MaterialTheme.typography.labelMedium
	val color = MaterialTheme.colorScheme.onSurfaceVariant
	Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
		Text("Nombre", Modifier.width(NAME_W), style = style, color = color)
		Text("Participó", Modifier.width(NUM_W), style = style, color = color, textAlign = TextAlign.Center)
		Text("Cursos bíblicos", Modifier.width(NUM_W), style = style, color = color, textAlign = TextAlign.Center)
		Text("Precursor auxiliar", Modifier.width(NUM_W), style = style, color = color, textAlign = TextAlign.Center)
		Text("Horas", Modifier.width(NUM_W), style = style, color = color, textAlign = TextAlign.Center)
		if (showProject) Text("Horas de proyectos", Modifier.width(NUM_W), style = style, color = color, textAlign = TextAlign.Center)
		Text("Notas", Modifier.width(NOTES_W), style = style, color = color)
	}
}

@Composable
private fun RowCells(item: GroupReportItem, showProject: Boolean) {
	val delivered = item.reportId != null
	Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.width(NAME_W)) {
			Text(item.fullName, style = MaterialTheme.typography.bodyMedium, color = if (delivered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
			if (!delivered) Text("Falta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
		}
		Cell(if (delivered && item.participatedInMinistry == true) "✓" else "")
		Cell(item.bibleStudiesCount?.takeIf { it > 0 }?.toString() ?: "")
		Cell(if (delivered && item.isAuxiliaryPioneerThisMonth == true) "✓" else "")
		Cell(hoursText(item.hours))
		if (showProject) Cell(hoursText(item.projectHours))
		Text(item.comments.orEmpty(), Modifier.width(NOTES_W), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}
