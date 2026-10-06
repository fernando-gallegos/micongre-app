package com.micongre.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.micongre.app.AppContainer
import com.micongre.app.data.PublisherItem
import com.micongre.app.data.api
import com.micongre.app.data.isForbidden
import com.micongre.app.ui.AppCard
import com.micongre.app.ui.Badge
import com.micongre.app.ui.EmptyText
import com.micongre.app.ui.Fmt
import com.micongre.app.ui.Loading
import com.micongre.app.ui.PageTitle
import com.micongre.app.ui.RowList
import com.micongre.app.ui.rememberLoad
import com.micongre.app.ui.theme.AppColors
import java.text.Normalizer

private val STATUS_LABEL = mapOf("active" to "Activo", "inactive" to "Inactivo", "irregular" to "Irregular", "disfellowshipped" to "Sacado")
private val ACCOUNT_LABEL = mapOf("no_account" to "Sin cuenta", "invited" to "Invitado", "active" to "Con acceso", "disabled" to "Deshabilitado")
private val THEOCRATIC_LABEL = mapOf(
	"student" to "Estudiante",
	"unbaptized_publisher" to "Publicador no bautizado",
	"baptized_publisher" to "Publicador bautizado",
)

/** Todas las páginas de la lista (50 por página, el servidor ya limita al alcance de la persona). */
suspend fun loadAllPublishers(container: AppContainer): Result<List<PublisherItem>> {
	val all = mutableListOf<PublisherItem>()
	var cursor: Int? = 0
	while (cursor != null) {
		val page = api { container.apiService.publishers(cursor!!) }.getOrElse { return Result.failure(it) }
		all += page.items
		cursor = page.nextCursor
	}
	return Result.success(all)
}

/** Insignias de nombramiento como en la web: AC/SM y PR/PA, o P si no tiene. */
private fun badges(p: PublisherItem): List<String> {
	val active = p.appointments.filter { it.endDate == null }.map { it.appointmentType }
	val lead = if ("elder" in active) "AC" else if ("ministerial_servant" in active) "SM" else null
	val pioneer = if ("regular_pioneer" in active) "PR" else if ("auxiliary_pioneer" in active) "PA" else null
	return listOfNotNull(lead, pioneer).ifEmpty { listOf("P") }
}

private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

@Composable
fun PublishersScreen(container: AppContainer, onOpen: (String) -> Unit) {
	val state = rememberLoad("publishers") { loadAllPublishers(container) }
	var search by remember { mutableStateOf("") }
	var filter by remember { mutableStateOf("all") }
	val all = state.data.orEmpty().filter { !it.isHiddenAdmin }

	Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
		PageTitle("Personas", if (state.data != null) "${all.count { !it.disfellowshipped }} personas en la congregación" else null)
		OutlinedTextField(
			search, { search = it }, label = { Text("Buscar por nombre") }, singleLine = true,
			modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
		)
		Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			listOf("all" to "Todos", "active" to "Activos", "irregular" to "Irregulares", "inactive" to "Inactivos", "disfellowshipped" to "Sacados").forEach { (key, label) ->
				val count = when (key) {
					"all" -> all.count { !it.disfellowshipped }
					"disfellowshipped" -> all.count { it.disfellowshipped }
					else -> all.count { !it.disfellowshipped && it.congregationStatus == key }
				}
				FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text("$label $count") })
			}
		}
		if (state.loading && state.data == null) {
			Loading()
		} else if (state.error?.isForbidden == true) {
			EmptyText("No tienes acceso a la lista de personas.")
		} else {
			val q = norm(search.trim())
			val shown = all.filter { p ->
				val statusOk = when (filter) {
					"all" -> !p.disfellowshipped
					"disfellowshipped" -> p.disfellowshipped
					else -> !p.disfellowshipped && p.congregationStatus == filter
				}
				statusOk && (q.isEmpty() || norm(p.fullName).contains(q))
			}
			Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
				if (shown.isEmpty()) EmptyText("Nadie coincide con la búsqueda.")
				else RowList(shown.map { p -> { PublisherRow(p, Modifier.clickable { onOpen(p.id) }) } })
			}
		}
	}
}

@Composable
private fun PublisherRow(p: PublisherItem, modifier: Modifier = Modifier) {
	Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			Text(p.fullName, fontWeight = FontWeight.Medium)
			val parts = buildList {
				if (p.congregationStatus != "active") add(STATUS_LABEL[p.congregationStatus] ?: p.congregationStatus)
				if (p.accountStatus == "no_account") add("Sin acceso a la app")
				p.serviceYearHours?.let { add("${Fmt.hours(it).ifBlank { "0" }}/600 h") }
			}
			if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { badges(p).forEach { Badge(it) } }
	}
}

/** Datos de la persona, en solo lectura (la edición sigue siendo de la web). */
@Composable
fun PublisherDetailScreen(container: AppContainer, id: String, onBack: () -> Unit, onOpenS21: () -> Unit) {
	val state = rememberLoad(id) { api { container.apiService.publisher(id) } }
	val context = LocalContext.current
	val p = state.data

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
			PageTitle(p?.fullName ?: "Persona")
		}
		if (state.loading && p == null) return@Column Loading(Modifier.fillMaxWidth().height(200.dp))
		if (p == null) return@Column EmptyText("No se pudo cargar a esta persona.")

		Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
			AssistChip(onClick = onOpenS21, label = { Text("Tarjeta S-21") })
			if (!p.phone.isNullOrBlank()) {
				AssistChip(onClick = {
					runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phone}"))) }
				}, label = { Text("Llamar") })
			}
		}
		Text(
			listOfNotNull(
				if (p.disfellowshipped) "Sacado" else STATUS_LABEL[p.congregationStatus],
				ACCOUNT_LABEL[p.accountStatus],
			).joinToString(" · "),
			style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(bottom = 8.dp),
		)
		AppCard {
			Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
				Data("Condición", THEOCRATIC_LABEL[p.theocraticStatus])
				Data("Sexo", if (p.gender == "male") "Varón" else if (p.gender == "female") "Mujer" else null)
				Data("Nacimiento", p.birthDate?.let { Fmt.dmy(it) })
				Data("Bautismo", p.baptismDate?.let { Fmt.dmy(it) })
				Data("Teléfono", p.phone)
				Data("Correo", p.email)
				Data(
					"Contacto de emergencia",
					listOfNotNull(p.emergencyContactName, p.emergencyContactPhone, p.emergencyContactRelation?.let { "($it)" }).joinToString(" ").ifBlank { null },
				)
				Data("Tarjeta DPA", if (p.hasDpaCard) "Sí" else "No")
				Data("Informe de 15 min", if (p.isEligibleForReducedReport) "Sí" else "No")
				Data("Rol del sistema", if (p.systemRole == "admin") "Administrador" else "Regular")
				Data("Estado de cuenta", ACCOUNT_LABEL[p.accountStatus])
			}
		}
	}
}

@Composable
private fun Data(label: String, value: String?) {
	Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
		Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
		Text(value?.takeIf { it.isNotBlank() } ?: "—", fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.55f))
	}
}

/** Personas cuya S-21 puede abrir quien tiene alcance: primero la lista de informes del servidor (ya limitada a sus grupos o a
 * toda la congregación según su privilegio) y, si no hay, la lista de publicadores. El servidor decide siempre qué se devuelve. */
suspend fun loadReportPeople(container: AppContainer): Result<List<Pair<String, String>>> {
	val s4 = api { container.apiService.s4People() }
	if (s4.isSuccess) return Result.success(s4.getOrThrow().items.map { it.userId to it.fullName })
	return loadAllPublishers(container).map { list -> list.filter { !it.isHiddenAdmin && !it.disfellowshipped }.map { it.id to it.fullName } }
}

/** Selector de persona para ver su S-21 (solo quien tiene alcance sobre otras personas). */
@Composable
fun PublisherPickerDialog(container: AppContainer, onDismiss: () -> Unit, onPick: (String) -> Unit) {
	val state = rememberLoad("picker") { loadReportPeople(container) }
	var search by remember { mutableStateOf("") }
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
		title = { Text("Ver la S-21 de...") },
		text = {
			Column {
				OutlinedTextField(search, { search = it }, label = { Text("Buscar por nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
				if (state.loading) {
					Loading(Modifier.fillMaxWidth().height(120.dp))
				} else if (state.data == null) {
					EmptyText("No tienes acceso a la lista de personas.")
				} else {
					val q = norm(search.trim())
					Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
						state.data.orEmpty().filter { q.isEmpty() || norm(it.second).contains(q) }.forEach {
							Text(it.second, Modifier.fillMaxWidth().clickable { onPick(it.first) }.padding(vertical = 12.dp))
						}
					}
				}
			}
		},
	)
}
