package com.micongre.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.micongre.app.AppContainer
import com.micongre.app.data.CreateNotificationRequest
import com.micongre.app.data.Me
import com.micongre.app.data.PublisherItem
import com.micongre.app.data.api
import com.micongre.app.ui.AppCard
import com.micongre.app.ui.Badge
import com.micongre.app.ui.DateField
import com.micongre.app.ui.EmptyText
import com.micongre.app.ui.Fmt
import com.micongre.app.ui.Loading
import com.micongre.app.ui.PageTitle
import com.micongre.app.ui.RowList
import com.micongre.app.ui.SwipeToHideList
import com.micongre.app.ui.rememberLoad
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private val SCOPE_LABEL = mapOf("publisher" to "Para mí", "group" to "Mi grupo", "congregation" to "Congregación")
private val SENT_LABEL = mapOf("publisher" to "A personas", "group" to "A mi grupo", "congregation" to "A la congregación")

/** Qué ámbitos de aviso puede enviar esta persona: los mismos privilegios que exige la web (el servidor los vuelve a validar). */
fun sendableScopes(me: Me): List<String> = buildList {
	if (me.has("notify_publishers")) add("publisher")
	if (me.has("notify_group")) add("group")
	if (me.has("notify_congregation")) add("congregation")
}

/** Avisos recibidos (ocultar deslizando a la derecha) y, para quien tiene privilegio de notificar, los enviados y un aviso nuevo. */
@Composable
fun NotificationsScreen(container: AppContainer, me: Me) {
	val canSend = sendableScopes(me).isNotEmpty()
	var tab by remember { mutableStateOf(0) }
	var composing by remember { mutableStateOf(false) }

	Column(Modifier.fillMaxSize()) {
		Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) { PageTitle("Avisos") }
			if (canSend) {
				IconButton(onClick = { composing = true }) { Icon(Icons.Filled.Add, "Nuevo aviso") }
			}
		}
		if (canSend) {
			TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
				Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Recibidos") })
				Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Enviados") })
			}
		}
		if (tab == 0 || !canSend) ReceivedList(container) else SentList(container)
	}

	if (composing) {
		ComposeNotificationDialog(container, me, onDismiss = { composing = false }, onSent = { composing = false; tab = 1 })
	}
}

@Composable
private fun ReceivedList(container: AppContainer) {
	val scope = rememberCoroutineScope()
	val state = rememberLoad("notifications") { api { container.apiService.notifications() } }
	val items = state.data?.items.orEmpty()

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
		if (state.loading && state.data == null) {
			Loading()
		} else if (items.isEmpty()) {
			EmptyText("No tienes avisos.")
		} else {
			SwipeToHideList(items, key = { it.id }, onHide = { n ->
				scope.launch {
					api { container.apiService.dismissNotification(n.id) }
					state.reload()
				}
			}) { n ->
				Row(verticalAlignment = Alignment.Top) {
					Column(Modifier.weight(1f)) {
						Text(plainText(n.message), style = MaterialTheme.typography.bodyMedium)
						Text(
							"${n.senderName} · ${Fmt.timeAgo(n.createdAt)} · ${SCOPE_LABEL[n.scope] ?: ""}",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(top = 4.dp),
						)
					}
					IconButton(onClick = {
						scope.launch {
							api { container.apiService.dismissNotification(n.id) }
							state.reload()
						}
					}, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Ocultar este aviso", Modifier.size(18.dp)) }
				}
			}
		}
	}
}

@Composable
private fun SentList(container: AppContainer) {
	val state = rememberLoad("sent") { api { container.apiService.sentNotifications() } }
	val items = state.data?.items.orEmpty()
	val today = LocalDate.now().toString()

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
		if (state.loading && state.data == null) {
			Loading()
		} else if (items.isEmpty()) {
			EmptyText("Todavía no has enviado avisos.")
		} else {
			RowList(items.map { n ->
				{
					Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
						Text(plainText(n.message), style = MaterialTheme.typography.bodyMedium)
						val to = when (n.scope) {
							"publisher" -> n.recipientNames.joinToString(", ").ifBlank { "personas" }
							"group" -> n.groupName ?: "mi grupo"
							else -> "toda la congregación"
						}
						Text(
							"${SENT_LABEL[n.scope] ?: ""}: $to · ${n.senderName} · hasta ${Fmt.dmy(n.expiresAt)}",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
						if (n.expiresAt < today) Badge("Vencido", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
					}
				}
			})
		}
	}
}

@Composable
private fun ComposeNotificationDialog(container: AppContainer, me: Me, onDismiss: () -> Unit, onSent: () -> Unit) {
	val scope = rememberCoroutineScope()
	val scopes = remember { sendableScopes(me) }
	var target by remember { mutableStateOf(scopes.first()) }
	var message by remember { mutableStateOf("") }
	var expires by remember { mutableStateOf(LocalDate.now().toString()) }
	val chosen = remember { mutableStateListOf<PublisherItem>() }
	var search by remember { mutableStateOf("") }
	var results by remember { mutableStateOf<List<PublisherItem>>(emptyList()) }
	var error by remember { mutableStateOf<String?>(null) }
	var sending by remember { mutableStateOf(false) }

	// Búsqueda de personas (mismo endpoint que la web; el servidor limita a lo que esta persona puede ver).
	LaunchedEffect(search, target) {
		if (target != "publisher" || search.trim().length < 2) {
			results = emptyList()
			return@LaunchedEffect
		}
		delay(300)
		results = api { container.apiService.publishers(0, search.trim()) }.getOrNull()?.items.orEmpty()
			.filter { !it.isHiddenAdmin && !it.disfellowshipped }
	}

	val valid = message.isNotBlank() && expires >= LocalDate.now().toString() && (target != "publisher" || chosen.isNotEmpty())

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Nuevo aviso") },
		text = {
			Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				if (scopes.size > 1) {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
						scopes.forEach { s ->
							FilterChip(selected = target == s, onClick = { target = s }, label = { Text(SENT_LABEL[s] ?: s) })
						}
					}
				} else {
					Text(SENT_LABEL[target] ?: "", fontWeight = FontWeight.SemiBold)
				}
				if (target == "publisher") {
					OutlinedTextField(search, { search = it }, label = { Text("Buscar personas") }, singleLine = true, modifier = Modifier.fillMaxWidth())
					if (chosen.isNotEmpty()) {
						Text("Para: " + chosen.joinToString(", ") { it.fullName }, style = MaterialTheme.typography.bodySmall)
					}
					results.take(8).forEach { p ->
						val on = chosen.any { it.id == p.id }
						Row(
							Modifier.fillMaxWidth().clickable { if (on) chosen.removeAll { it.id == p.id } else chosen.add(p) },
							verticalAlignment = Alignment.CenterVertically,
						) {
							Checkbox(on, { if (it) chosen.add(p) else chosen.removeAll { x -> x.id == p.id } })
							Text(p.fullName)
						}
					}
				}
				OutlinedTextField(
					message, { message = it },
					label = { Text("Mensaje") },
					modifier = Modifier.fillMaxWidth().height(120.dp),
				)
				DateField("Vence el", expires, { expires = it })
				error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
			}
		},
		confirmButton = {
			TextButton(enabled = valid && !sending, onClick = {
				sending = true
				error = null
				scope.launch {
					val body = CreateNotificationRequest(
						scope = target,
						message = message.trim(),
						expiresAt = expires,
						recipientUserIds = if (target == "publisher") chosen.map { it.id } else null,
					)
					val r = api { container.apiService.sendNotification(body) }
					sending = false
					if (r.isSuccess) onSent() else {
						val code = (r.exceptionOrNull() as? com.micongre.app.data.ApiException)?.code
						error = when (code) {
							403 -> "No tienes permiso para enviar este tipo de aviso."
							400 -> "Revisa el mensaje, la fecha y a quién va dirigido (para tu grupo necesitas tener uno)."
							else -> "No se pudo enviar. Intenta de nuevo."
						}
					}
				}
			}) { Text(if (sending) "Enviando..." else "Enviar") }
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
	)
}

