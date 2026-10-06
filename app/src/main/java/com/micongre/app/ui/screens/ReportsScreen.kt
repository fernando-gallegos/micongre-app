package com.micongre.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micongre.app.AppContainer
import com.micongre.app.data.Me
import com.micongre.app.data.S21SaveRequest
import com.micongre.app.data.ServiceReport
import com.micongre.app.data.api
import com.micongre.app.data.isForbidden
import com.micongre.app.ui.AppCard
import com.micongre.app.ui.EmptyText
import com.micongre.app.ui.Fmt
import com.micongre.app.ui.Loading
import com.micongre.app.ui.PageTitle
import com.micongre.app.ui.rememberLoad
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val PIONEER_CAP = 55.0
private val APPT_BOXES = listOf(
	"elder" to "Anciano",
	"ministerial_servant" to "Siervo ministerial",
	"regular_pioneer" to "Precursor regular",
	"special_pioneer" to "Precursor especial",
	"missionary" to "Misionero",
)

private fun serviceYearOf(date: LocalDate) = if (date.monthValue >= 9) date.year else date.year - 1

/** Informes: tarjeta S-21 en modo lectura, con modo edición para quien puede editar los informes de esa persona. */
@Composable
fun ReportsScreen(container: AppContainer, me: Me, userId: String?, onPickPublisher: (String) -> Unit) {
	val target = userId ?: me.id
	val scope = rememberCoroutineScope()
	val state = rememberLoad(target) { api { container.apiService.s21(target) } }
	// Quién puede ver la S-21 de otras personas en la web: recolección de informes, informes de toda la congregación o del
	// grupo, o superintendente/auxiliar de un grupo de servicio. (El servidor vuelve a validar cada consulta.)
	val canBrowse = me.has("field_service_reports", "reports_all", "reports_group") ||
		me.hasAppointment("field_service_group_overseer", "field_service_group_assistant")
	val today = LocalDate.now()
	var sy by remember(target) { mutableStateOf(serviceYearOf(today)) }
	var editing by remember(target) { mutableStateOf(false) }
	var editMonth by remember { mutableStateOf<Pair<Int, Int>?>(null) }
	var picker by remember { mutableStateOf(false) }

	val data = state.data
	if (state.loading && data == null) return Loading()
	if (data == null) {
		EmptyText(if (state.error?.isForbidden == true) "No tienes acceso a este informe." else "No se pudo cargar la tarjeta.", Modifier.padding(16.dp))
		return
	}

	val p = data.publisher
	val reports = data.reports
	val byMonth = reports.associateBy { it.periodYear * 100 + it.periodMonth }
	// Orden del año de servicio: septiembre a agosto.
	val months = (0 until 12).map { i -> val m = (8 + i) % 12 + 1; (if (m >= 9) sy else sy + 1) to m }
	val yearReports = months.mapNotNull { (y, m) -> byMonth[y * 100 + m] }
	val showProject = data.appointments.any { it.appointmentType == "regular_pioneer" } || yearReports.any { (it.projectHours ?: 0.0) > 0.0 }
	val activeTypes = data.appointments.filter { it.endDate == null }.map { it.appointmentType }.toSet()
	val oldest = maxOf(serviceYearOf(today) - 5, (Fmt.parse(p.baptismDate)?.year ?: serviceYearOf(today)) - 1)

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
		PageTitle("Tarjeta S-21", p.fullName) {
			if (data.canEdit) {
				IconButton(onClick = { editing = !editing }) {
					Icon(if (editing) Icons.Filled.Visibility else Icons.Filled.Edit, if (editing) "Modo lectura" else "Modo edición")
				}
			}
		}
		if (canBrowse) {
			AssistChip(onClick = { picker = true }, label = { Text("Ver otra persona") }, modifier = Modifier.padding(vertical = 4.dp))
		}
		if (editing) {
			Text("Modo edición: toca un mes para modificarlo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
		}

		AppCard(Modifier.padding(top = 8.dp)) {
			Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text("Registro de publicador de la congregación", fontWeight = FontWeight.SemiBold)
				InfoLine("Nombre", p.fullName)
				InfoLine("Fecha de nacimiento", Fmt.dmy(p.birthDate))
				InfoLine("Fecha de bautismo", Fmt.dmy(p.baptismDate))
				InfoLine("Sexo", if (p.gender == "male") "Hombre" else if (p.gender == "female") "Mujer" else "—")
				val appts = APPT_BOXES.filter { it.first in activeTypes }.joinToString(", ") { it.second }
				InfoLine("Nombramientos", appts.ifBlank { "—" })
			}
		}

		Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
			IconButton(enabled = sy > oldest, onClick = { sy -= 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Año anterior") }
			Text("Año de servicio: $sy–${sy + 1}", fontWeight = FontWeight.SemiBold)
			IconButton(enabled = sy < serviceYearOf(today), onClick = { sy += 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Año siguiente") }
		}

		AppCard {
			Column {
				HeaderRow(showProject)
				months.forEach { (y, m) ->
					val r = byMonth[y * 100 + m]
					val future = y * 12 + m > today.year * 12 + today.monthValue
					MonthRow(
						name = Fmt.monthNames[m - 1].take(3), report = r, showProject = showProject,
						onClick = if (editing && !future) ({ editMonth = y to m }) else null,
					)
				}
				val totalHours = yearReports.sumOf { it.hours ?: 0.0 }
				val totalProject = yearReports.sumOf { it.projectHours ?: 0.0 }
				Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
					Text("Total", Modifier.weight(3.1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
					Text(Fmt.hours(totalHours).ifBlank { "0" }, Modifier.weight(1.1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
					if (showProject) Text(Fmt.hours(totalProject).ifBlank { "0" }, Modifier.weight(1.1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
					Text("", Modifier.weight(2f))
				}
			}
		}
	}

	editMonth?.let { (y, m) ->
		EditMonthDialog(
			year = y, month = m, report = byMonth[y * 100 + m], showProject = showProject,
			onDismiss = { editMonth = null },
			onSave = { body ->
				scope.launch {
					val ok = api { container.apiService.saveS21(target, body) }.isSuccess
					if (ok) { editMonth = null; state.reload() }
				}
			},
			onDelete = {
				scope.launch {
					api { container.apiService.deleteS21(target, y, m) }
					editMonth = null
					state.reload()
				}
			},
		)
	}

	if (picker) PublisherPickerDialog(container, onDismiss = { picker = false }, onPick = { picker = false; onPickPublisher(it) })
}

@Composable
private fun InfoLine(label: String, value: String) {
	Row {
		Text("$label: ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
		Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
	}
}

@Composable
private fun HeaderRow(showProject: Boolean) {
	Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
		val s = 10.sp
		Text("Mes", Modifier.weight(1.7f), fontSize = s, fontWeight = FontWeight.Bold)
		Text("Part.", Modifier.weight(0.7f), fontSize = s, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
		Text("Cursos", Modifier.weight(0.7f), fontSize = s, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
		Text("Aux.", Modifier.weight(0.7f), fontSize = s, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
		Text("Horas", Modifier.weight(1.1f), fontSize = s, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
		if (showProject) Text("Proy.", Modifier.weight(1.1f), fontSize = s, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
		Text("Notas", Modifier.weight(2f), fontSize = s, fontWeight = FontWeight.Bold)
	}
}

@Composable
private fun MonthRow(name: String, report: ServiceReport?, showProject: Boolean, onClick: (() -> Unit)?) {
	val s = 12.sp
	val base = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
	Row(base.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
		Text(name, Modifier.weight(1.7f), fontSize = s)
		Text(if (report?.participatedInMinistry == true) "✓" else "", Modifier.weight(0.7f), fontSize = s, textAlign = TextAlign.Center)
		Text(report?.bibleStudiesCount?.takeIf { it > 0 }?.toString() ?: "", Modifier.weight(0.7f), fontSize = s, textAlign = TextAlign.Center)
		Text(if (report?.isAuxiliaryPioneerThisMonth == true) "✓" else "", Modifier.weight(0.7f), fontSize = s, textAlign = TextAlign.Center)
		Text(Fmt.hours(report?.hours), Modifier.weight(1.1f), fontSize = s, textAlign = TextAlign.Center)
		if (showProject) Text(Fmt.hours(report?.projectHours), Modifier.weight(1.1f), fontSize = s, textAlign = TextAlign.Center)
		Text(listOfNotNull(report?.creditHoursNote, report?.comments).filter { it.isNotBlank() }.joinToString(" · "), Modifier.weight(2f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}

private fun parseHours(text: String): Double? = text.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

@Composable
private fun EditMonthDialog(
	year: Int, month: Int, report: ServiceReport?, showProject: Boolean,
	onDismiss: () -> Unit, onSave: (S21SaveRequest) -> Unit, onDelete: () -> Unit,
) {
	var participated by remember { mutableStateOf(report?.participatedInMinistry ?: false) }
	var aux by remember { mutableStateOf(report?.isAuxiliaryPioneerThisMonth ?: false) }
	var studies by remember { mutableStateOf(report?.bibleStudiesCount?.takeIf { it > 0 }?.toString() ?: "") }
	var hours by remember { mutableStateOf(Fmt.hours(report?.hours)) }
	var project by remember { mutableStateOf(Fmt.hours(report?.projectHours)) }
	var notes by remember { mutableStateOf(report?.comments ?: "") }
	var error by remember { mutableStateOf(false) }

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("${Fmt.monthNames[month - 1]} $year") },
		text = {
			Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Checkbox(participated, { participated = it }); Text("Participó en el ministerio")
				}
				Row(verticalAlignment = Alignment.CenterVertically) {
					Checkbox(aux, { aux = it }); Text("Precursor auxiliar este mes")
				}
				OutlinedTextField(studies, { studies = it.filter(Char::isDigit).take(3); if (studies.isNotEmpty()) participated = true }, label = { Text("Cursos bíblicos") }, singleLine = true)
				OutlinedTextField(hours, { hours = it.take(7); if ((parseHours(hours) ?: 0.0) > 0) participated = true }, label = { Text("Horas") }, singleLine = true)
				if (showProject && !aux) {
					OutlinedTextField(project, { project = it.take(7) }, label = { Text("Horas de proyectos") }, singleLine = true)
				}
				OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("Notas") })
				if (error) Text("Revisa las horas y los cursos.", color = MaterialTheme.colorScheme.error)
			}
		},
		confirmButton = {
			TextButton(onClick = {
				val h = parseHours(hours)
				val st = studies.toIntOrNull() ?: 0
				val pr = if (showProject && !aux) parseHours(project)?.let { minOf(it, maxOf(PIONEER_CAP - (h ?: 0.0), 0.0)) } else null
				if ((hours.isNotBlank() && h == null) || (project.isNotBlank() && showProject && parseHours(project) == null)) {
					error = true
				} else {
					onSave(S21SaveRequest(year, month, participated || st > 0 || (h ?: 0.0) > 0, aux, h, pr, st, notes.trim().ifBlank { null }))
				}
			}) { Text("Guardar") }
		},
		dismissButton = {
			Row {
				if (report != null) TextButton(onClick = onDelete) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
				TextButton(onClick = onDismiss) { Text("Cancelar") }
			}
		},
	)
}
