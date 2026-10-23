package com.micongre.app.ui.screens

import com.micongre.app.ui.pendingTargets
import com.micongre.app.ui.rememberPendingOps
import com.micongre.app.ui.saved
import com.micongre.app.ui.submitDismiss
import com.micongre.app.ui.submitResolveTerritory
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.micongre.app.AppContainer
import com.micongre.app.data.AssignedVisit
import com.micongre.app.data.CreateVisitRequest
import com.micongre.app.data.Me
import com.micongre.app.data.PanelAssignment
import com.micongre.app.data.PanelLink
import com.micongre.app.data.ResolveTerritoryRequest
import com.micongre.app.data.VisitRequest
import com.micongre.app.data.VisitStatusRequest
import com.micongre.app.data.api
import com.micongre.app.ui.AppCard
import com.micongre.app.ui.DateField
import com.micongre.app.ui.Badge
import com.micongre.app.ui.EmptyText
import com.micongre.app.ui.Fmt
import com.micongre.app.ui.Loading
import com.micongre.app.ui.PageTitle
import com.micongre.app.ui.RowList
import com.micongre.app.ui.SectionLabel
import com.micongre.app.ui.SwipeToHideList
import com.micongre.app.ui.WhatsAppGlyph
import com.micongre.app.ui.ZoomGlyph
import com.micongre.app.ui.parseColor
import com.micongre.app.ui.rememberLoad
import com.micongre.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.time.LocalDate

private val TYPE_LABEL = mapOf(
	"midweek" to "Entre semana",
	"weekend" to "Fin de semana",
	"operational" to "Tarea operativa",
	"outgoing_talk" to "Discurso enviado",
	"pastoral_visit" to "Pastoreo",
	"pastoral_visit_request" to "Pastoreo",
	"cleaning" to "Salón del Reino",
	"preaching" to "Predicación",
	"territory" to "Territorio",
)

/** Mi Panel: asignaciones futuras, limpieza, avisos, pastoreo, arreglos de discursos y botones de enlace. */
@Composable
fun PanelScreen(container: AppContainer, me: Me, onOpenS21: () -> Unit) {
	val scope = rememberCoroutineScope()
	val summary = rememberLoad("summary") { api { container.apiService.panelSummary() } }
	val notifications = rememberLoad("notifications") { api { container.apiService.notifications() } }
	val myVisits = rememberLoad("visits") { api { container.apiService.myVisitRequests() } }
	val assigned = rememberLoad("assigned") { api { container.apiService.assignedVisits() } }

	var requestDialog by remember { mutableStateOf(false) }
	var mapFor by remember { mutableStateOf<String?>(null) }
	var resolve by remember { mutableStateOf<Pair<PanelAssignment, String>?>(null) }
	var visitAction by remember { mutableStateOf<Pair<AssignedVisit, String>?>(null) }
	var cancelVisit by remember { mutableStateOf<VisitRequest?>(null) }

	fun reloadPastoral() {
		myVisits.reload()
		assigned.reload()
		summary.reload()
	}

	val data = summary.data
	if (summary.loading && data == null) {
		Loading()
		return
	}

	val upcoming = data?.upcoming.orEmpty()
	val cleaning = upcoming.filter { it.meetingType == "cleaning" }
	// Lo que ya se completó o devolvió sin internet y espera enviarse sale de la lista de inmediato; igual con los avisos ocultados.
	val pendingOps = rememberPendingOps(container)
	val resolvedOffline = pendingOps.pendingTargets(com.micongre.app.data.OpKind.TERRITORY_RESOLVE, "terr:")
	val dismissedOffline = pendingOps.pendingTargets(com.micongre.app.data.OpKind.DISMISS, "dismiss:")
	val mine = upcoming.filter { it.meetingType != "cleaning" && it.territory?.assignmentId !in resolvedOffline }
	val first = me.fullName.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
	val today = LocalDate.now()
	val serverUrl by produceState<String?>(null) { value = container.prefs.currentServerUrl() }

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
		PageTitle(
			"Hola, $first",
			Fmt.longDate(today.toString()),
		) {
			Image(painterResource(com.micongre.app.R.drawable.ic_logo), contentDescription = "Mi Congre", modifier = Modifier.size(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)))
		}
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
			PillButton("Pastoreo", Icons.Filled.VolunteerActivism, Modifier.weight(1f)) { requestDialog = true }
			PillButton("Mi S-21", Icons.Filled.Description, Modifier.weight(1f), onClick = onOpenS21)
		}

		SectionLabel("Mis asignaciones")
		if (mine.isEmpty()) EmptyText("No tienes asignaciones próximas.")
		else RowList(mine.map { a ->
			{ AssignmentRow(a, onMap = { mapFor = a.territory?.territoryId }, onResolve = { status -> resolve = a to status }) }
		})

		if (cleaning.isNotEmpty()) {
			SectionLabel("Limpieza del Salón del Reino")
			RowList(cleaning.map { a -> { AssignmentRow(a, {}, {}) } })
		}

		val notes = notifications.data?.items.orEmpty().filter { it.id !in dismissedOffline }
		if (notes.isNotEmpty()) {
			SectionLabel("Avisos")
			SwipeToHideList(notes.take(3), key = { it.id }, onHide = { n ->
				scope.launch {
					container.submitDismiss(n.id)
					notifications.reload()
				}
			}) { n ->
				run {
					Row(verticalAlignment = Alignment.Top) {
						Column(Modifier.weight(1f)) {
							Text(plainText(n.message), style = MaterialTheme.typography.bodyMedium)
							Text(
								"${n.senderName} · ${Fmt.timeAgo(n.createdAt)}",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						IconButton(onClick = {
							scope.launch {
								container.submitDismiss(n.id)
								notifications.reload()
							}
						}, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Ocultar este aviso", Modifier.size(18.dp)) }
					}
				}
			}
		}

		SectionLabel("Mi informe de servicio")
		MyReportsCard(container, me.id)

		val visits = myVisits.data?.items.orEmpty()
		if (visits.isNotEmpty()) {
			SectionLabel("Visitas de pastoreo")
			RowList(visits.map { v ->
				{
					Row(verticalAlignment = Alignment.CenterVertically) {
						Column(Modifier.weight(1f)) {
							Text(Fmt.dmy(v.requestedDate), fontWeight = FontWeight.Medium)
							Text(
								v.assignedElders?.takeIf { it.isNotEmpty() }?.let { "Asignado a ${it.joinToString(" y ")}" } ?: "Pendiente de asignar",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						IconButton(onClick = { cancelVisit = v }) { Icon(Icons.Filled.Close, "Cancelar solicitud", tint = MaterialTheme.colorScheme.error) }
					}
				}
			})
		}

		val toDo = assigned.data?.items.orEmpty()
		if (toDo.isNotEmpty()) {
			SectionLabel("Pastoreos asignados a mí")
			RowList(toDo.map { v ->
				{
					Row(verticalAlignment = Alignment.CenterVertically) {
						Column(Modifier.weight(1f)) {
							Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
								Text(v.publisherName, fontWeight = FontWeight.Medium)
								Badge("Pendiente", AppColors.extras.warn, AppColors.extras.warnSoft)
							}
							Text(
								Fmt.dmy(v.requestedDate) + (v.coElderName?.let { " · Junto con $it" } ?: ""),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
							v.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
						}
						IconButton(onClick = { visitAction = v to "completed" }) { Icon(Icons.Filled.Check, "Completado") }
						IconButton(onClick = { visitAction = v to "postponed" }) { Icon(Icons.Filled.Schedule, "Postergar") }
						IconButton(onClick = { visitAction = v to "cancelled" }) { Icon(Icons.Filled.Close, "Cancelar") }
					}
				}
			})
		}

		MyGroupSection(data?.group)

		// Defensa en profundidad: el servidor ya filtra, pero el cliente también respeta los nombramientos.
		val visibleLinks = data?.links.orEmpty().filter {
			when (it.kind) {
				"hub", "elders" -> me.hasAppointment("elder")
				"appointed" -> me.hasAppointment("elder", "ministerial_servant")
				else -> true
			}
		}
		LinkButtons(visibleLinks, serverUrl)
	}

	if (requestDialog) {
		var date by remember { mutableStateOf(today.plusDays(7).toString()) }
		var notesText by remember { mutableStateOf("") }
		AlertDialog(
			onDismissRequest = { requestDialog = false },
			title = { Text("Solicitar visita de pastoreo") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					DateField("Fecha deseada", date, { date = it })
					OutlinedTextField(notesText, { notesText = it }, label = { Text("Nota (opcional)") })
				}
			},
			confirmButton = {
				TextButton(enabled = Fmt.parse(date) != null, onClick = {
					requestDialog = false
					scope.launch {
						api { container.apiService.createVisitRequest(CreateVisitRequest(date, notesText.ifBlank { null })) }
						reloadPastoral()
					}
				}) { Text("Enviar solicitud") }
			},
			dismissButton = { TextButton(onClick = { requestDialog = false }) { Text("Cancelar") } },
		)
	}

	cancelVisit?.let { v ->
		AlertDialog(
			onDismissRequest = { cancelVisit = null },
			title = { Text("¿Cancelar la solicitud del ${Fmt.dmy(v.requestedDate)}?") },
			confirmButton = {
				TextButton(onClick = {
					cancelVisit = null
					scope.launch { api { container.apiService.cancelVisitRequest(v.id) }; reloadPastoral() }
				}) { Text("Sí, cancelar") }
			},
			dismissButton = { TextButton(onClick = { cancelVisit = null }) { Text("No") } },
		)
	}

	visitAction?.let { (v, status) ->
		val word = when (status) { "completed" -> "completado"; "postponed" -> "postergado"; else -> "cancelado" }
		AlertDialog(
			onDismissRequest = { visitAction = null },
			title = { Text("¿Confirmas marcar el pastoreo de ${v.publisherName} (${Fmt.dmy(v.requestedDate)}) como $word?") },
			confirmButton = {
				TextButton(onClick = {
					visitAction = null
					scope.launch { api { container.apiService.setVisitStatus(v.id, VisitStatusRequest(status)) }; reloadPastoral() }
				}) { Text("Confirmar") }
			},
			dismissButton = { TextButton(onClick = { visitAction = null }) { Text("Cancelar") } },
		)
	}

	resolve?.let { (a, status) ->
		var comment by remember { mutableStateOf("") }
		var failed by remember { mutableStateOf(false) }
		val ref = a.territory!!
		AlertDialog(
			onDismissRequest = { resolve = null },
			title = { Text(if (status == "completed") "¿Marcar como completado el ${a.label}?" else "¿Devolver el ${a.label}?") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					OutlinedTextField(comment, { comment = it.take(500) }, label = { Text("Comentario (opcional)") })
					if (failed) Text("No se pudo guardar. Intenta de nuevo.", color = MaterialTheme.colorScheme.error)
				}
			},
			confirmButton = {
				TextButton(onClick = {
					scope.launch {
						// Se guarda en el teléfono y se envía; sin internet queda en la cola y se manda sola (el territorio se quita de la lista de inmediato).
						val r = container.submitResolveTerritory(ref.assignmentId, ResolveTerritoryRequest(status, comment.trim().ifBlank { null }), (if (status == "completed") "Completar el " else "Devolver el ") + a.label)
						if (r.saved) { resolve = null; summary.reload() } else failed = true
					}
				}) { Text(if (status == "completed") "Completar" else "Devolver") }
			},
			dismissButton = { TextButton(onClick = { resolve = null }) { Text("Cancelar") } },
		)
	}

	mapFor?.let { TerritoryMapDialog(container, it, onDismiss = { mapFor = null }) }
}

@Composable
private fun AssignmentRow(a: PanelAssignment, onMap: () -> Unit, onResolve: (String) -> Unit) {
	val pending = a.territory?.status == "assigned"
	Row(verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			Text(a.label, fontWeight = FontWeight.Medium)
			if (pending) {
				Text("Pendiente", color = AppColors.extras.warn, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
				Text("Asignado el ${Fmt.dmy(a.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			} else {
				val range = Fmt.dmy(a.date) + (a.dateEnd?.takeIf { it != a.date }?.let { " al ${Fmt.dmy(it)}" } ?: "")
				Text(
					"$range · ${TYPE_LABEL[a.meetingType] ?: ""}",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			a.territory?.comment?.takeIf { it.isNotBlank() && !pending }?.let {
				Text("«$it»", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
		if (pending) {
			IconButton(onClick = onMap) { Icon(Icons.Filled.Map, "Ver mapa") }
			IconButton(onClick = { onResolve("completed") }) { Icon(Icons.Filled.Check, "Completar: ya terminé de trabajar este territorio") }
			IconButton(onClick = { onResolve("returned") }) { Icon(Icons.Filled.Undo, "Devolver: no lo trabajé o lo dejo") }
		}
	}
}

/** Botón de acción del panel: píldora redondeada con el color del tema elegido y un ícono. */
@Composable
private fun PillButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
	androidx.compose.material3.Button(
		onClick = onClick,
		modifier = modifier.height(52.dp),
		shape = CircleShape,
		colors = androidx.compose.material3.ButtonDefaults.buttonColors(
			containerColor = MaterialTheme.colorScheme.primary,
			contentColor = MaterialTheme.colorScheme.onPrimary,
		),
		elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
	) {
		Icon(icon, null, Modifier.size(20.dp))
		Text(label, Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold, maxLines = 1)
	}
}

/** Botones redondos de enlace al final de Mi Panel: solo los configurados (y permitidos) y centrados según cuántos haya. */
@Composable
private fun LinkButtons(links: List<PanelLink>, serverUrl: String?) {
	val context = LocalContext.current
	if (links.isNotEmpty()) Row(
		Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp),
		horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
	) {
		links.forEach { l ->
			val icon: ImageVector = when (l.kind) {
				"zoom" -> ZoomGlyph
				"hub" -> Icons.Filled.Public
				else -> WhatsAppGlyph
			}
			Box(
				Modifier
					.size(52.dp)
					.clip(CircleShape)
					.background(parseColor(l.color))
					.clickable {
						openWebLink(context, l.url)
					},
				contentAlignment = Alignment.Center,
			) {
				Icon(icon, l.label, Modifier.size(28.dp), tint = androidx.compose.ui.graphics.Color.White)
			}
		}
	}
	// Abre Mi Congre en la web (el sitio que se configuró al iniciar la app).
	if (serverUrl != null) {
		PillButton(
			"Abrir Mi Congre", Icons.Filled.Public,
			Modifier.fillMaxWidth().padding(top = if (links.isEmpty()) 28.dp else 0.dp, bottom = 16.dp),
		) { openWebLink(context, serverUrl) }
	}
}

/** Solo se abren direcciones http(s): un enlace configurado en el servidor no puede lanzar otros esquemas (intent:, file:, etc.). */
private fun openWebLink(context: android.content.Context, url: String) {
	val uri = Uri.parse(url.trim())
	if (uri.scheme != "https" && uri.scheme != "http") return
	runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Los avisos vienen en markdown simple: aquí se muestran como texto plano. */
internal fun plainText(markdown: String): String =
	markdown
		.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
		.replace(Regex("[*_`#]+"), "")
		.trim()
