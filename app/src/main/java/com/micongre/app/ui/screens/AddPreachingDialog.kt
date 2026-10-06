package com.micongre.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.micongre.app.AppContainer
import com.micongre.app.data.CreatePreachingRequest
import com.micongre.app.data.PreachingTargetsBody
import com.micongre.app.data.api
import com.micongre.app.ui.Fmt
import com.micongre.app.ui.Loading
import com.micongre.app.ui.rememberLoad
import kotlinx.coroutines.launch

/** Agregar una salida de predicación (solo agregar) para quien administra el módulo; el servidor valida igual que en la web. */
@Composable
fun AddPreachingDialog(container: AppContainer, date: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
	val options = rememberLoad(Unit) { api { container.apiService.preachingOptions() } }
	val scope = rememberCoroutineScope()
	var time by remember { mutableStateOf("") }
	var note by remember { mutableStateOf("") }
	var territoryIds by remember { mutableStateOf(setOf<String>()) }
	var congregation by remember { mutableStateOf(false) }
	var groupIds by remember { mutableStateOf(setOf<String>()) }
	var userIds by remember { mutableStateOf(setOf<String>()) }
	var saving by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }
	val data = options.data
	LaunchedEffect(data) { if (data != null && time.isEmpty()) time = data.defaultTime }

	AlertDialog(
		onDismissRequest = { if (!saving) onDismiss() },
		title = { Text("Agregar predicación · " + Fmt.longDate(date)) },
		text = {
			if (data == null) {
				if (options.loading) Loading(Modifier.fillMaxWidth().height(160.dp)) else Text("No se pudieron cargar las opciones.")
			} else {
				Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
					val context = LocalContext.current
					val (hh, mm) = time.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 9) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
					// Selector de hora nativo del sistema (reloj), con el formato de 12/24 h del teléfono.
					OutlinedButton(
						onClick = {
							android.app.TimePickerDialog(
								context,
								{ _, h, m -> time = "%02d:%02d".format(h, m) },
								hh, mm, android.text.format.DateFormat.is24HourFormat(context),
							).show()
						},
						modifier = Modifier.fillMaxWidth(),
					) { Text("Hora: " + if (time.isEmpty()) "elegir" else time12(time)) }
					Section("Territorios")
					if (data.territories.isEmpty()) Text("No hay territorios asignados.", style = MaterialTheme.typography.bodySmall)
					data.territories.forEach { t ->
						Check(
							(t.name?.let { "${t.number} — $it" } ?: t.number) + (t.assigneeName?.let { " ($it)" } ?: ""),
							t.id in territoryIds,
						) { territoryIds = if (it) territoryIds + t.id else territoryIds - t.id }
					}
					Section("Para")
					Check("Toda la congregación", congregation) { congregation = it }
					data.groups.forEach { g -> Check("Grupo: ${g.name}", g.id in groupIds) { groupIds = if (it) groupIds + g.id else groupIds - g.id } }
					Section("Publicadores (predicación pública)")
					data.people.forEach { p -> Check(p.fullName, p.id in userIds) { userIds = if (it) userIds + p.id else userIds - p.id } }
					OutlinedTextField(
						note, { note = it.take(500) }, label = { Text("Nota (opcional)") },
						modifier = Modifier.fillMaxWidth(),
					)
					error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
				}
			}
		},
		confirmButton = {
			TextButton(
				enabled = data != null && !saving && territoryIds.isNotEmpty() && Regex("([01]\\d|2[0-3]):[0-5]\\d").matches(time),
				onClick = {
					saving = true
					error = null
					scope.launch {
						val body = CreatePreachingRequest(
							date, time, note.trim().ifEmpty { null }, territoryIds.toList(),
							PreachingTargetsBody(congregation, groupIds.toList(), userIds.toList()),
						)
						val r = api { container.apiService.createPreaching(body) }
						saving = false
						if (r.isSuccess) onSaved() else error = "No se pudo guardar. Revisa que los territorios y a quién va dirigida sean compatibles."
					}
				},
			) { Text(if (saving) "Guardando…" else "Guardar") }
		},
		dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
	)
}

@Composable
private fun Section(text: String) {
	Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Check(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
	Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
		Checkbox(checked, onChange)
		Text(label, style = MaterialTheme.typography.bodyMedium)
	}
}
