package com.micongre.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.micongre.app.AppContainer
import com.micongre.app.data.AttendanceRequest
import com.micongre.app.data.OpKind
import com.micongre.app.data.OpState
import com.micongre.app.data.OutboxOp
import com.micongre.app.data.ResolveTerritoryRequest
import com.micongre.app.data.S4SaveRequest
import com.micongre.app.data.SubmitResult
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

private val outboxJson = Json { ignoreUnknownKeys = true }

/** Cuerpo JSON a enviar. `baseVersion` solo va si se conoce el dato sobre el que se editó (null explícito = «no había registro»). */
private fun body(base: JsonObject, hasBase: Boolean, baseVersion: Long?): String =
	JsonObject(if (hasBase) base + ("baseVersion" to (baseVersion?.let { JsonPrimitive(it) } ?: JsonNull)) else base).toString()

private suspend fun AppContainer.activeAccount(): String? = accounts.activeIdNow()

/** Informe de servicio propio: se guarda en el teléfono y se envía (de inmediato si hay internet, o al volver). */
suspend fun AppContainer.submitReport(request: S4SaveRequest, baseVersion: Long?, label: String): SubmitResult {
	val account = activeAccount() ?: return SubmitResult.NeedsLogin
	val base = outboxJson.encodeToJsonElement(S4SaveRequest.serializer(), request).jsonObject
	return outboxSender.submit(account, OpKind.REPORT, "report:${request.userId}:${request.periodYear}:${request.periodMonth}", body(base, true, baseVersion), label)
}

suspend fun AppContainer.submitAttendance(request: AttendanceRequest, baseVersion: Long?, label: String): SubmitResult {
	val account = activeAccount() ?: return SubmitResult.NeedsLogin
	val base = outboxJson.encodeToJsonElement(AttendanceRequest.serializer(), request).jsonObject
	return outboxSender.submit(account, OpKind.ATTENDANCE, "att:${request.meetingType}:${request.meetingId}", body(base, true, baseVersion), label)
}

suspend fun AppContainer.submitResolveTerritory(assignmentId: String, request: ResolveTerritoryRequest, label: String): SubmitResult {
	val account = activeAccount() ?: return SubmitResult.NeedsLogin
	val base = outboxJson.encodeToJsonElement(ResolveTerritoryRequest.serializer(), request).jsonObject
	return outboxSender.submit(account, OpKind.TERRITORY_RESOLVE, "terr:$assignmentId", body(base, false, null), label)
}

suspend fun AppContainer.submitDismiss(notificationId: String): SubmitResult {
	val account = activeAccount() ?: return SubmitResult.NeedsLogin
	return outboxSender.submit(account, OpKind.DISMISS, "dismiss:$notificationId", "{}", "Ocultar un aviso")
}

/** Lo pendiente de la cuenta activa (se actualiza solo). */
@Composable
fun rememberPendingOps(container: AppContainer): List<OutboxOp> {
	val all by container.outbox.ops.collectAsState()
	val active by container.accounts.activeId.collectAsState(initial = null)
	return remember(all, active) { all.filter { it.accountId == active } }
}

/** Mensaje corto para una pantalla tras guardar; null si se envió y no hace falta decir nada especial. */
fun SubmitResult.note(): String? = when (this) {
	SubmitResult.Sent -> null
	SubmitResult.Queued -> "Guardado en el teléfono. Se enviará solo cuando haya internet."
	SubmitResult.Conflict -> "Alguien cambió ese dato mientras tanto. Tu versión quedó guardada en pendientes para que decidas."
	SubmitResult.NeedsLogin -> "Tu sesión venció. Quedó guardado y se enviará cuando inicies sesión de nuevo."
	is SubmitResult.Rejected -> message
}

/** ¿Quedó guardado (aunque aún no esté en el servidor)? Rechazado es lo único que no se guardó. */
val SubmitResult.saved: Boolean get() = this !is SubmitResult.Rejected

private fun stateText(op: OutboxOp): String = when (op.state) {
	OpState.PENDING -> if (op.attempts > 0) "Pendiente de enviar · se reintentará solo" else "Pendiente de enviar"
	OpState.CONFLICT -> "Ese dato cambió en el servidor. Elige qué hacer con el tuyo."
	OpState.REJECTED -> op.lastError ?: "El servidor no lo aceptó."
	OpState.NEEDS_LOGIN -> "Falta iniciar sesión para enviarlo."
}

/** Franja bajo el aviso de «sin conexión»: hay cambios guardados en el teléfono que todavía no están en el servidor. */
@Composable
fun PendingBanner(container: AppContainer) {
	val ops = rememberPendingOps(container)
	if (ops.isEmpty()) return
	var open by remember { mutableStateOf(false) }
	val problems = ops.count { it.state != OpState.PENDING }
	Surface(
		color = if (problems > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
		modifier = Modifier.fillMaxWidth().clickable { open = true },
	) {
		Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			Icon(if (problems > 0) Icons.Filled.ErrorOutline else Icons.Filled.CloudUpload, null, Modifier.size(18.dp))
			Text(
				if (problems > 0) "$problems cambio${if (problems == 1) "" else "s"} necesita${if (problems == 1) "" else "n"} revisión · toca para ver"
				else "${ops.size} cambio${if (ops.size == 1) "" else "s"} guardado${if (ops.size == 1) "" else "s"} en el teléfono, por enviar · toca para ver",
				style = MaterialTheme.typography.labelMedium,
			)
		}
	}
	if (open) PendingChangesDialog(container, onDismiss = { open = false })
}

/** Lista de lo guardado sin enviar, con lo que se puede hacer con cada cosa. Nada se borra sin que la persona lo confirme. */
@Composable
fun PendingChangesDialog(container: AppContainer, onDismiss: () -> Unit) {
	val ops = rememberPendingOps(container)
	val scope = rememberCoroutineScope()
	var discard by remember { mutableStateOf<OutboxOp?>(null) }
	var busy by remember { mutableStateOf(false) }

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Cambios por enviar") },
		text = {
			Column(Modifier.verticalScroll(rememberScrollState())) {
				if (ops.isEmpty()) Text("No queda nada pendiente. ¡Todo está enviado!")
				ops.forEachIndexed { i, op ->
					Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
						Text(op.label, style = MaterialTheme.typography.titleSmall)
						Text(stateText(op), style = MaterialTheme.typography.bodySmall, color = if (op.state == OpState.PENDING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
						Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
							when (op.state) {
								OpState.CONFLICT -> {
									// «Enviar el mío»: se manda sin comparar con la versión del servidor (la persona lo decidió).
									TextButton(onClick = {
										val obj = runCatching { outboxJson.parseToJsonElement(op.payload).jsonObject }.getOrNull()
										if (obj != null) container.outbox.updatePayload(op.id, JsonObject(obj - "baseVersion").toString())
										container.outbox.setState(op.id, OpState.PENDING, null)
										com.micongre.app.push.OutboxScheduler.kick(container.app)
									}) { Text("Enviar el mío") }
									TextButton(onClick = { discard = op }) { Text("Descartar") }
								}
								OpState.REJECTED -> {
									TextButton(onClick = {
										container.outbox.setState(op.id, OpState.PENDING, null)
										com.micongre.app.push.OutboxScheduler.kick(container.app)
									}) { Text("Reintentar") }
									TextButton(onClick = { discard = op }) { Text("Descartar") }
								}
								else -> Unit
							}
						}
					}
					if (i < ops.lastIndex) HorizontalDivider()
				}
			}
		},
		confirmButton = {
			TextButton(enabled = !busy && ops.any { it.state == OpState.PENDING }, onClick = {
				busy = true
				scope.launch {
					container.accounts.activeIdNow()?.let { container.outboxSender.drain(it) }
					busy = false
				}
			}) { Text(if (busy) "Enviando…" else "Enviar ahora") }
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
	)

	discard?.let { op ->
		AlertDialog(
			onDismissRequest = { discard = null },
			title = { Text("¿Descartar este cambio?") },
			text = { Text("«${op.label}» se borrará del teléfono y no se enviará. Esto no se puede deshacer.") },
			confirmButton = { TextButton(onClick = { container.outbox.delete(op.id); discard = null }) { Text("Descartar") } },
			dismissButton = { TextButton(onClick = { discard = null }) { Text("Cancelar") } },
		)
	}
}

/** Ids de lo que ya se ocultó o resolvió sin internet y espera enviarse (para quitarlo de la pantalla de inmediato). */
fun List<OutboxOp>.pendingTargets(kind: OpKind, prefix: String): Set<String> = filter { it.kind == kind }.map { it.targetKey.removePrefix(prefix) }.toSet()

/** Cifras de asistencia guardadas sin enviar: `tipo-id` → (presentes, Zoom). */
fun List<OutboxOp>.pendingAttendance(): Map<String, Pair<Int, Int>> = filter { it.kind == OpKind.ATTENDANCE }.mapNotNull { op ->
	val o = runCatching { outboxJson.parseToJsonElement(op.payload).jsonObject }.getOrNull() ?: return@mapNotNull null
	val key = "${o["meetingType"]?.jsonPrimitive?.content}-${o["meetingId"]?.jsonPrimitive?.content}"
	key to ((o["inPersonCount"]?.jsonPrimitive?.intOrNull ?: 0) to (o["zoomCount"]?.jsonPrimitive?.intOrNull ?: 0))
}.toMap()
