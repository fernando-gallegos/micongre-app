package com.micongre.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.micongre.app.AppContainer
import com.micongre.app.data.MyReportResponse
import com.micongre.app.data.S4SaveRequest
import com.micongre.app.data.api
import com.micongre.app.data.isoVersion
import com.micongre.app.ui.note
import com.micongre.app.ui.rememberPendingOps
import com.micongre.app.ui.saved
import com.micongre.app.ui.submitReport
import com.micongre.app.ui.Badge
import com.micongre.app.ui.Fmt
import com.micongre.app.ui.Loading
import com.micongre.app.ui.RowList
import com.micongre.app.ui.rememberLoad
import com.micongre.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val PIONEER_CAP = 55.0

private class Month(val year: Int, val month: Int) {
	val label get() = "${Fmt.monthNames[month - 1]} $year"
}

private fun parseHours(text: String): Double? = text.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

/** Mi informe de servicio: el mes actual y los 2 anteriores (no se limita al año de servicio), para enviarlos o editarlos desde Mi Panel. */
@Composable
fun MyReportsCard(container: AppContainer, userId: String) {
	val scope = rememberCoroutineScope()
	val months = remember {
		val now = LocalDate.now().withDayOfMonth(1)
		(0..2).map { val d = now.minusMonths(it.toLong()); Month(d.year, d.monthValue) }
	}
	val state = rememberLoad("my-reports") {
		runCatching {
			months.map { m ->
				m to (api { container.apiService.myReport(m.year, m.month) }.getOrNull() ?: MyReportResponse())
			}
		}
	}
	var editing by remember { mutableStateOf<Pair<Month, MyReportResponse>?>(null) }
	val warn = AppColors.extras
	// Lo guardado en el teléfono que todavía no está en el servidor, y el mensaje tras guardar sin internet.
	val pending = rememberPendingOps(container)
	var savedNote by remember { mutableStateOf<String?>(null) }

	val rows = state.data
	if (state.loading && rows == null) {
		Loading(Modifier.fillMaxWidth().height(80.dp))
		return
	}
	if (rows == null) return

	RowList(rows.map { (m, r) ->
		{
			Row(verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text(m.label, fontWeight = FontWeight.SemiBold)
					r.report?.let { rep ->
						Text(
							"Participó: ${if (rep.participatedInMinistry) "Sí" else "No"} · Horas: ${Fmt.hours(rep.hours).ifBlank { "—" }}" +
								(rep.projectHours?.takeIf { it > 0 }?.let { " (+${Fmt.hours(it)} proyectos)" } ?: "") +
								" · Cursos: ${rep.bibleStudiesCount ?: 0}",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
				if (pending.any { it.targetKey == "report:$userId:${m.year}:${m.month}" }) Badge("Por enviar", warn.warn, warn.warnSoft)
				else if (r.report != null) Badge("Enviado") else Badge("Sin enviar", warn.warn, warn.warnSoft)
				IconButton(onClick = { editing = m to r }) {
					Icon(if (r.report != null) Icons.Filled.Edit else Icons.Filled.Send, if (r.report != null) "Editar informe" else "Enviar informe")
				}
			}
		}
	})

	savedNote?.let {
		Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = androidx.compose.ui.Modifier.padding(top = 8.dp, start = 4.dp))
	}

	editing?.let { (m, r) ->
		val pioneer = r.pioneerType
		val showHours = pioneer != null
		val showProject = pioneer == "regular_pioneer"
		var participated by remember(m.label) { mutableStateOf(r.report?.participatedInMinistry ?: true) }
		var hours by remember(m.label) { mutableStateOf(Fmt.hours(r.report?.hours)) }
		var project by remember(m.label) { mutableStateOf(Fmt.hours(r.report?.projectHours)) }
		var studies by remember(m.label) { mutableStateOf(r.report?.bibleStudiesCount?.takeIf { it > 0 }?.toString() ?: "") }
		var comments by remember(m.label) { mutableStateOf("") }
		var error by remember(m.label) { mutableStateOf<String?>(null) }
		var saving by remember(m.label) { mutableStateOf(false) }
		val projectMax = (PIONEER_CAP - (parseHours(hours) ?: 0.0)).coerceAtLeast(0.0)

		AlertDialog(
			onDismissRequest = { editing = null },
			title = { Text("Informe de ${m.label}") },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Checkbox(participated, { participated = it })
						Text("Participó en el ministerio este mes")
					}
					if (showHours) OutlinedTextField(hours, { hours = it.take(7) }, label = { Text("Horas") }, singleLine = true)
					if (showProject && projectMax > 0) {
						OutlinedTextField(project, { project = it.take(7) }, label = { Text("Horas de proyectos (máx. ${Fmt.hours(projectMax)})") }, singleLine = true)
					}
					OutlinedTextField(studies, { studies = it.filter(Char::isDigit).take(3) }, label = { Text("Cursos bíblicos") }, singleLine = true)
					OutlinedTextField(comments, { comments = it.take(500) }, label = { Text("Comentarios (opcional)") })
					error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
				}
			},
			confirmButton = {
				TextButton(enabled = !saving, onClick = {
					val h = parseHours(hours)
					val p = parseHours(project)
					if ((showHours && hours.isNotBlank() && h == null) || (project.isNotBlank() && p == null)) {
						error = "Revisa las horas."
						return@TextButton
					}
					saving = true
					error = null
					scope.launch {
						val body = S4SaveRequest(
							userId = userId,
							periodYear = m.year,
							periodMonth = m.month,
							participatedInMinistry = participated,
							hours = if (showHours) h else null,
							projectHours = if (showProject) p?.let { minOf(it, projectMax) } else null,
							bibleStudiesCount = studies.toIntOrNull() ?: 0,
							comments = comments.trim().ifBlank { null },
						)
						// Se guarda primero en el teléfono (confirmado en disco) y se envía; sin internet queda en la cola y se manda sola.
						val result = container.submitReport(body, r.report?.submittedAt?.let { isoVersion(it) }, "Informe de ${m.label}")
						saving = false
						if (result.saved) { savedNote = result.note(); editing = null; state.reload() } else error = result.note() ?: "No se pudo guardar. Intenta de nuevo."
					}
				}) { Text(if (saving) "Guardando..." else "Guardar informe") }
			},
			dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
		)
	}
}
