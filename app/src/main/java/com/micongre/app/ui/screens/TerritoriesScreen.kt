package com.micongre.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.micongre.app.data.AssignTerritoryRequest
import com.micongre.app.data.Me
import com.micongre.app.data.ResolveTerritoryRequest
import com.micongre.app.data.SaveTerritoryRequest
import com.micongre.app.data.TerritoryAssignmentRow
import com.micongre.app.data.TerritoryItem
import com.micongre.app.data.api
import com.micongre.app.ui.AppCard
import com.micongre.app.ui.EmptyText
import com.micongre.app.ui.Fmt
import com.micongre.app.ui.Loading
import com.micongre.app.ui.PageTitle
import com.micongre.app.ui.SectionLabel
import com.micongre.app.ui.rememberLoad
import kotlinx.coroutines.launch

/** Mismo criterio que en la web: administrador o el privilegio "Siervo de territorios" (territory_management). */
fun canManageTerritories(me: Me) = me.has("territory_management")

private fun kindLabel(kind: String) = if (kind == "public_preaching") "Predicación pública" else "General"
private fun title(number: String, name: String?) = if (name.isNullOrBlank()) "Territorio $number" else "Territorio $number · $name"

/** Qué diálogo está abierto. */
private sealed interface Action {
	data object New : Action
	data class Edit(val t: TerritoryItem) : Action
	data class Delete(val t: TerritoryItem) : Action
	data class Assign(val t: TerritoryItem) : Action
	data class Resolve(val a: TerritoryAssignmentRow, val status: String) : Action
	data class CancelAssignment(val a: TerritoryAssignmentRow) : Action
	data class DeleteHistory(val a: TerritoryAssignmentRow) : Action
}

/** Territorios (dentro de Predicación): agregar, editar y eliminar territorios, dibujar su mapa (TerritoryMapEditor) y encargarlos a
 * alguien; igual que la web. */
@Composable
fun TerritoriesScreen(container: AppContainer, me: Me) {
	if (!canManageTerritories(me)) {
		EmptyText("No tienes acceso a los territorios.", Modifier.padding(16.dp))
		return
	}
	val scope = rememberCoroutineScope()
	val territories = rememberLoad("territories") { api { container.apiService.territories() } }
	val assignments = rememberLoad("assignments") { api { container.apiService.territoryAssignments() } }
	var tab by remember { mutableStateOf("assignments") }
	var action by remember { mutableStateOf<Action?>(null) }
	var mapFor by remember { mutableStateOf<String?>(null) }
	var drawFor by remember { mutableStateOf<String?>(null) }
	var notice by remember { mutableStateOf<String?>(null) }

	fun refresh() {
		territories.reload()
		assignments.reload()
	}

	/** Ejecuta una llamada, avisa si falla y recarga. */
	fun run(failure: String = "No se pudo completar. Inténtalo de nuevo.", block: suspend () -> Result<*>, onDone: () -> Unit = {}) {
		scope.launch {
			val r = block()
			if (r.isSuccess) {
				notice = null
				onDone()
				refresh()
			} else {
				notice = failure
			}
		}
	}

	val items = territories.data?.items.orEmpty()
	val rows = assignments.data?.items.orEmpty()

	Column(Modifier.fillMaxSize()) {
		Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
			PageTitle("Territorios", "Agregar, encargar y dar seguimiento")
			Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				FilterChip(selected = tab == "assignments", onClick = { tab = "assignments" }, label = { Text("Asignaciones") })
				FilterChip(selected = tab == "territories", onClick = { tab = "territories" }, label = { Text("Territorios") })
			}
			notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
		}

		if ((territories.loading && territories.data == null) || (assignments.loading && assignments.data == null)) {
			Loading()
		} else if (tab == "territories") {
			LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
				item {
					Button(onClick = { action = Action.New }, modifier = Modifier.fillMaxWidth()) { Text("Agregar territorio") }
				}
				if (items.isEmpty()) item { EmptyText("Todavía no hay territorios.") }
				items(items, key = { it.id }) { t ->
					AppCard {
						Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
							// Datos: nombre, tipo y quién lo tiene.
							Text(title(t.number, t.name), fontWeight = FontWeight.SemiBold)
							Text(kindLabel(t.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
							val cur = t.current
							Text(
								if (cur != null) "Encargado: ${cur.assigneeName}" else "Sin encargado",
								style = MaterialTheme.typography.bodyMedium,
								color = if (cur != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
							)
							// Acciones con íconos, como en la web
							Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
								ActionIcon(Icons.Filled.PersonAdd, if (cur != null) "Cambiar" else "Asignar") { action = Action.Assign(t) }
								if (t.hasMap) ActionIcon(Icons.Filled.Map, "Ver mapa") { mapFor = t.id }
								ActionIcon(Icons.Filled.Draw, if (t.hasMap) "Editar mapa" else "Dibujar") { drawFor = t.id }
								ActionIcon(Icons.Filled.Edit, "Editar") { action = Action.Edit(t) }
							}
						}
					}
				}
			}
		} else {
			val current = rows.filter { it.status == "assigned" }
			val history = rows.filter { it.status != "assigned" }
			Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
				SectionLabel("Vigentes")
				if (current.isEmpty()) EmptyText("No hay territorios asignados.")
				current.forEach { a ->
					var menu by remember(a.id) { mutableStateOf(false) }
					AppCard(Modifier.padding(bottom = 10.dp)) {
						Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Column(Modifier.weight(1f)) {
									Text(title(a.territoryNumber, a.territoryName), fontWeight = FontWeight.SemiBold)
									Text(a.assigneeName, style = MaterialTheme.typography.bodyMedium)
								}
								Box {
									IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Acciones") }
									DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
										DropdownMenuItem(text = { Text(if (a.notifiedAt != null) "Volver a enviar el aviso" else "Enviar aviso") }, onClick = {
											menu = false
											run("No se pudo enviar el aviso.", { api { container.apiService.notifyTerritory(a.territoryId) } })
										})
										DropdownMenuItem(text = { Text("Marcar como completado") }, onClick = { menu = false; action = Action.Resolve(a, "completed") })
										DropdownMenuItem(text = { Text("Marcar como devuelto") }, onClick = { menu = false; action = Action.Resolve(a, "returned") })
										DropdownMenuItem(text = { Text("Cancelar asignación") }, onClick = { menu = false; action = Action.CancelAssignment(a) })
									}
								}
							}
							Text(
								"Desde ${Fmt.dmy(a.assignedAt)}" + if (a.notifiedAt != null) " · aviso enviado" else " · sin avisar",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}
				}
				SectionLabel("Historial")
				if (history.isEmpty()) EmptyText("Todavía no hay historial.")
				history.forEach { a ->
					AppCard(Modifier.padding(bottom = 10.dp).clickable { action = Action.DeleteHistory(a) }) {
						Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
							Text(title(a.territoryNumber, a.territoryName), fontWeight = FontWeight.SemiBold)
							Text(
								"${a.assigneeName} · ${if (a.status == "completed") "completado" else "devuelto"} ${Fmt.dmy(a.resolvedAt)}",
								style = MaterialTheme.typography.bodyMedium,
							)
							a.resolutionComment?.takeIf { it.isNotBlank() }?.let {
								Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
							}
						}
					}
				}
				Text(
					"Toca un registro del historial para eliminarlo.",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(bottom = 16.dp),
				)
			}
		}
	}

	mapFor?.let { TerritoryMapDialog(container, it, onDismiss = { mapFor = null }) }
	drawFor?.let { id ->
		TerritoryMapEditor(
			container, id, hintTerritoryId = items.firstOrNull { it.hasMap && it.id != id }?.id,
			onClose = { saved -> drawFor = null; if (saved) refresh() },
		)
	}

	when (val a = action) {
		null -> Unit
		is Action.New, is Action.Edit -> {
			val editing = (a as? Action.Edit)?.t
			TerritoryFormDialog(
				initial = editing,
				onDismiss = { action = null },
				onDelete = editing?.let { e -> { action = Action.Delete(e) } },
					onSave = { req, onError ->
					scope.launch {
						val ok = if (editing == null) api { container.apiService.createTerritory(req) }.also { r ->
							r.exceptionOrNull()?.let { e -> onError(if ((e as? com.micongre.app.data.ApiException)?.code == 409) "Ese número ya está en uso." else "No se pudo guardar.") }
						}.isSuccess else api { container.apiService.updateTerritory(editing.id, req) }.also { r ->
							r.exceptionOrNull()?.let { e -> onError(if ((e as? com.micongre.app.data.ApiException)?.code == 409) "Ese número ya está en uso." else "No se pudo guardar.") }
						}.isSuccess
						if (ok) {
							action = null
							refresh()
						}
					}
				},
			)
		}
		is Action.Delete -> ConfirmDialog(
			"Eliminar territorio",
			"¿Eliminar ${title(a.t.number, a.t.name)}? También se borra su historial de asignaciones. No se puede deshacer.",
			"Eliminar",
			onDismiss = { action = null },
		) { run("No se pudo eliminar.", { api { container.apiService.deleteTerritory(a.t.id) } }, onDone = { action = null }) }
		is Action.CancelAssignment -> ConfirmDialog(
			"Cancelar asignación",
			"¿Cancelar la asignación de ${title(a.a.territoryNumber, a.a.territoryName)} a ${a.a.assigneeName}?",
			"Cancelar asignación",
			onDismiss = { action = null },
		) { run("No se pudo cancelar.", { api { container.apiService.deleteTerritoryAssignment(a.a.id) } }, onDone = { action = null }) }
		is Action.DeleteHistory -> ConfirmDialog(
			"Eliminar del historial",
			"¿Eliminar este registro de ${title(a.a.territoryNumber, a.a.territoryName)} (${a.a.assigneeName})?",
			"Eliminar",
			onDismiss = { action = null },
		) { run("No se pudo eliminar.", { api { container.apiService.deleteTerritoryAssignment(a.a.id) } }, onDone = { action = null }) }
		is Action.Resolve -> ResolveDialog(a.status, title(a.a.territoryNumber, a.a.territoryName), onDismiss = { action = null }) { comment ->
			run("No se pudo actualizar.", { api { container.apiService.resolveTerritoryAssignment(a.a.id, ResolveTerritoryRequest(a.status, comment)) } }, onDone = { action = null })
		}
		is Action.Assign -> AssignDialog(container, a.t, onDismiss = { action = null }) { userId ->
			run(
				"No se pudo asignar: esa persona no puede encargarse de este tipo de territorio.",
				{ if (userId == null) api { container.apiService.unassignTerritory(a.t.id) } else api { container.apiService.assignTerritory(a.t.id, AssignTerritoryRequest(userId)) } },
				onDone = { action = null },
			)
		}
	}
}

/** Botón de acción con ícono y una etiqueta corta debajo. */
@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
	val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
	Column(
		Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Icon(icon, label, tint = tint)
		Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
	}
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = { Text(text) },
		confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
		dismissButton = { TextButton(onClick = onDismiss) { Text("Volver") } },
	)
}

@Composable
private fun ResolveDialog(status: String, territory: String, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
	var comment by remember { mutableStateOf("") }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(if (status == "completed") "Marcar como completado" else "Marcar como devuelto") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(territory)
				OutlinedTextField(comment, { comment = it.take(500) }, label = { Text("Comentario (opcional)") })
			}
		},
		confirmButton = { TextButton(onClick = { onConfirm(comment.trim().ifEmpty { null }) }) { Text("Confirmar") } },
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
	)
}

@Composable
private fun TerritoryFormDialog(initial: TerritoryItem?, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (SaveTerritoryRequest, (String) -> Unit) -> Unit) {
	var number by remember { mutableStateOf(initial?.number ?: "") }
	var name by remember { mutableStateOf(initial?.name ?: "") }
	var notes by remember { mutableStateOf(initial?.notes ?: "") }
	var kind by remember { mutableStateOf(initial?.kind ?: "general") }
	var error by remember { mutableStateOf<String?>(null) }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(if (initial == null) "Agregar territorio" else "Editar territorio") },
		text = {
			Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(number, { number = it.take(20) }, label = { Text("Número") }, singleLine = true)
				OutlinedTextField(name, { name = it.take(120) }, label = { Text("Nombre (opcional)") }, singleLine = true)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					FilterChip(selected = kind == "general", onClick = { kind = "general" }, label = { Text("General") })
					FilterChip(selected = kind == "public_preaching", onClick = { kind = "public_preaching" }, label = { Text("Predicación pública") })
				}
				OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text("Notas (opcional)") }, modifier = Modifier.heightIn(min = 80.dp))
				if (initial != null && initial.hasMap && kind == "public_preaching" && initial.kind != "public_preaching") {
					Text(
						"Si el mapa tiene polígonos o líneas, el servidor no permitirá este tipo (solo admite puntos y textos).",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
					// Eliminar queda aparte y discreto, para que no se toque sin querer.
					if (onDelete != null) {
						TextButton(onClick = onDelete, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
							Text("Eliminar este territorio…", style = MaterialTheme.typography.bodySmall)
						}
					}
			}
		},
		confirmButton = {
			TextButton(enabled = number.isNotBlank(), onClick = {
				error = null
				onSave(SaveTerritoryRequest(number.trim(), kind, name.trim(), notes.trim())) { error = it }
			}) { Text("Guardar") }
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
	)
}

/** Personas a las que se puede encargar este territorio (según el privilegio de su tipo); `null` = quitar al encargado. */
@Composable
private fun AssignDialog(container: AppContainer, t: TerritoryItem, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
	val state = rememberLoad(t.id) { api { container.apiService.territoryAssignees(t.kind) } }
	var query by remember { mutableStateOf("") }
	val all = state.data?.items.orEmpty()
	val q = query.trim().lowercase().let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "") }
	val shown = all.filter {
		q.isEmpty() || java.text.Normalizer.normalize(it.fullName.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").contains(q)
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Encargar ${title(t.number, t.name)}") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(
					if (t.kind == "public_preaching") "Pueden encargarse quienes tengan «Asignable para predicación pública»." else "Pueden encargarse quienes tengan «Asignable para territorio».",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				if (all.size > 6) OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true)
				when {
					state.loading && state.data == null -> Text("Cargando…")
					all.isEmpty() -> Text("Nadie tiene todavía ese privilegio. Otórgalo desde la ficha de la persona (Publicadores → Privilegios).")
					else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
						items(shown, key = { it.id }) { p ->
							Text(
								p.fullName + if (t.current?.assigneeUserId == p.id) "  ✓" else "",
								modifier = Modifier.fillMaxWidth().clickable { onPick(p.id) }.padding(vertical = 12.dp),
								fontWeight = if (t.current?.assigneeUserId == p.id) FontWeight.SemiBold else FontWeight.Normal,
							)
						}
					}
				}
			}
		},
		confirmButton = {
			if (t.current != null) TextButton(onClick = { onPick(null) }) { Text("Quitar encargado") }
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
	)
}
