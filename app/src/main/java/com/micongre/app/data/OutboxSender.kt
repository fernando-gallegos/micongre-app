package com.micongre.app.data

import com.micongre.app.AppContainer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant

/** Resultado de intentar enviar una operación de la cola. */
sealed interface SendOutcome {
	/** El servidor la recibió (o ya la tenía): se borra de la cola. `version` = nueva versión del dato, si el servidor la dio. */
	data class Sent(val version: Long?) : SendOutcome

	/** Sin internet o fallo del servidor: se queda en la cola y se reintenta. */
	data object Retry : SendOutcome

	/** El servidor no la aceptó: se queda guardada, marcada, y se avisa a la persona. */
	data class Problem(val state: OpState, val message: String) : SendOutcome
}

/** Qué pasó al guardar y enviar algo desde una pantalla. */
sealed interface SubmitResult {
	/** El servidor ya lo recibió. */
	data object Sent : SubmitResult

	/** Quedó guardado en el teléfono y se enviará solo cuando haya internet. */
	data object Queued : SubmitResult

	/** El servidor tiene otra versión: lo de la persona quedó guardado en pendientes para que decida. */
	data object Conflict : SubmitResult

	/** La sesión venció: quedó guardado y se envía al iniciar sesión de nuevo. */
	data object NeedsLogin : SubmitResult

	/** El servidor no lo aceptó (permiso, datos inválidos): no se guarda en la cola, la persona lo corrige en la pantalla. */
	data class Rejected(val message: String) : SubmitResult
}

data class DrainResult(
	val sent: Int = 0,
	/** Quedó algo por reintentar (sin internet, error del servidor…). */
	val retry: Boolean = false,
	/** Operaciones que pasaron a un estado de problema en esta ronda (para avisar a la persona). */
	val newProblems: List<OutboxOp> = emptyList(),
)

/** Intentos tras los que se suelta un «ocultar aviso» que el servidor no acepta (el aviso pudo haberse borrado). */
const val MAX_DISMISS_ATTEMPTS = 6

/** Qué hacer con una respuesta de error del servidor. 408, 429 y 5xx son transitorios (se reintenta); lo demás es una decisión del servidor
 * (se conserva lo guardado, marcado, y se avisa a la persona). */
fun classifyFailure(code: Int, errorBody: String): SendOutcome = when {
	code == 409 -> SendOutcome.Problem(OpState.CONFLICT, "El dato cambió en el servidor mientras tanto.")
	code == 401 -> SendOutcome.Problem(OpState.NEEDS_LOGIN, "La sesión venció: inicia sesión para enviarlo.")
	code == 403 -> SendOutcome.Problem(OpState.REJECTED, "Ya no tienes permiso para esto.")
	code == 408 || code == 429 || code >= 500 -> SendOutcome.Retry
	else -> SendOutcome.Problem(OpState.REJECTED, "El servidor no lo aceptó (${errorBody.take(80).ifBlank { "código $code" }}).")
}

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
private val json = Json { ignoreUnknownKeys = true }

/** Versión (segundos desde 1970) a partir de una fecha ISO del servidor; null si no hay. */
fun isoVersion(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }

/**
 * Envía la cola al servidor. Reglas: nada se borra sin la confirmación del servidor; las operaciones de un mismo dato se envían en orden y, si una
 * falla, las siguientes de ese dato esperan; cada operación va con su clave de idempotencia (su id), así un reintento nunca repite el efecto.
 */
class OutboxSender(private val container: AppContainer) {
	private val outbox get() = container.outbox

	suspend fun send(op: OutboxOp): SendOutcome {
		outbox.markAttempt(op.id)
		val service = container.apiFor(op.accountId)
		val response = try {
			val body = op.payload.toRequestBody(JSON_TYPE)
			when (op.kind) {
				OpKind.REPORT -> service.sendQueuedReport(op.id, body)
				OpKind.ATTENDANCE -> service.sendQueuedAttendance(op.id, body)
				OpKind.TERRITORY_RESOLVE -> service.sendQueuedResolve(op.targetKey.removePrefix("terr:"), op.id, body)
				OpKind.DISMISS -> service.sendQueuedDismiss(op.targetKey.removePrefix("dismiss:"), op.id)
			}
		} catch (e: IOException) {
			return SendOutcome.Retry // sin internet, tiempo agotado o corte a medias: se reintenta
		} catch (e: Exception) {
			return SendOutcome.Retry
		}

		val code = response.code()
		if (response.isSuccessful) {
			// Un informe enviado cambia las horas del año de servicio: el widget de precursorado las vuelve a pedir.
			if (op.kind == OpKind.REPORT) com.micongre.app.widget.PioneerSchedule.fetchNow(container.app)
			val version = runCatching { json.parseToJsonElement(response.body()?.string().orEmpty()).jsonObject["version"]?.jsonPrimitive?.longOrNull }.getOrNull()
			return SendOutcome.Sent(version)
		}
		return classifyFailure(code, response.errorBody()?.string().orEmpty())
	}

	/**
	 * Guarda la operación en el teléfono (confirmada en disco) y, si hay internet, intenta enviarla en el momento para dar respuesta inmediata;
	 * sin internet o ante cualquier fallo transitorio queda en la cola y se envía sola después. Nunca se pierde: lo único que se descarta es
	 * un rechazo definitivo del servidor durante este intento en vivo (el contenido sigue en la pantalla para corregirlo).
	 */
	suspend fun submit(accountId: String, kind: OpKind, targetKey: String, payload: String, label: String): SubmitResult {
		val app = container.app
		val op = outbox.enqueue(accountId, kind, targetKey, payload, label)
		// Si ya hay algo anterior esperando sobre el mismo dato, esta va detrás (se respeta el orden).
		val ahead = outbox.forAccount(accountId).any { it.targetKey == targetKey && it.id != op.id && it.createdAt <= op.createdAt }
		if (!container.isConnected() || ahead) {
			com.micongre.app.push.OutboxScheduler.kick(app)
			return SubmitResult.Queued
		}
		return when (val outcome = send(op)) {
			is SendOutcome.Sent -> {
				outbox.delete(op.id)
				rebaseSuccessors(op, outcome.version)
				SubmitResult.Sent
			}
			SendOutcome.Retry -> {
				com.micongre.app.push.OutboxScheduler.kick(app)
				SubmitResult.Queued
			}
			is SendOutcome.Problem -> when (outcome.state) {
				OpState.CONFLICT -> {
					outbox.setState(op.id, OpState.CONFLICT, outcome.message)
					SubmitResult.Conflict
				}
				OpState.NEEDS_LOGIN -> {
					outbox.setState(op.id, OpState.NEEDS_LOGIN, outcome.message)
					SubmitResult.NeedsLogin
				}
				else -> {
					outbox.delete(op.id) // la persona sigue viendo lo que escribió y lo corrige
					SubmitResult.Rejected(outcome.message)
				}
			}
		}
	}

	/** Envía lo pendiente de una cuenta, de la más vieja a la más nueva. */
	suspend fun drain(accountId: String): DrainResult {
		val account = container.accounts.get(accountId) ?: return DrainResult()
		if (account.expired) {
			// Sin sesión no se puede enviar nada: se deja como «necesita iniciar sesión» (se reactiva al volver a entrar).
			val waiting = outbox.forAccount(accountId).filter { it.state == OpState.PENDING }
			waiting.forEach { outbox.setState(it.id, OpState.NEEDS_LOGIN, "La sesión venció: inicia sesión para enviarlo.") }
			return DrainResult(newProblems = waiting)
		}

		var sent = 0
		var retry = false
		val problems = mutableListOf<OutboxOp>()
		val blocked = mutableSetOf<String>() // datos cuya operación anterior no se pudo enviar: las siguientes esperan
		for (op in outbox.forAccount(accountId)) {
			if (op.state != OpState.PENDING) {
				blocked += op.targetKey // un choque o rechazo detiene lo posterior sobre ese mismo dato
				continue
			}
			if (op.targetKey in blocked) continue
			when (val outcome = send(op)) {
				is SendOutcome.Sent -> {
					outbox.delete(op.id)
					sent++
					if (!rebaseSuccessors(op, outcome.version)) blocked += op.targetKey
				}
				SendOutcome.Retry -> {
					// Ocultar un aviso es de poco valor: si el servidor no lo acepta tras varios intentos se suelta para no reintentar sin fin.
					// Todo lo demás (informes, asistencia, territorios) se reintenta hasta que el servidor responda: nada se pierde.
					if (op.kind == OpKind.DISMISS && op.attempts + 1 >= MAX_DISMISS_ATTEMPTS) {
						outbox.delete(op.id)
					} else {
						retry = true
						blocked += op.targetKey
					}
				}
				is SendOutcome.Problem -> {
					outbox.setState(op.id, outcome.state, outcome.message)
					blocked += op.targetKey
					outbox.ops.value.firstOrNull { it.id == op.id }?.let { problems += it }
				}
			}
		}
		return DrainResult(sent, retry, problems)
	}

	/**
	 * Tras enviar una edición, las siguientes del mismo dato (informe o asistencia) se basaban en la versión anterior: se actualizan a la nueva para que
	 * no choquen con la propia edición. Si el servidor no dio la versión (la operación ya estaba procesada), se consulta. false = no se pudo; esperan.
	 */
	private suspend fun rebaseSuccessors(op: OutboxOp, version: Long?): Boolean {
		if (op.kind != OpKind.REPORT && op.kind != OpKind.ATTENDANCE) return true
		val successors = outbox.forAccount(op.accountId).filter { it.targetKey == op.targetKey && it.createdAt >= op.createdAt && it.id != op.id }
		if (successors.isEmpty()) return true
		val current = version ?: fetchVersion(op) ?: return false
		for (next in successors) {
			val obj = runCatching { json.parseToJsonElement(next.payload).jsonObject }.getOrNull() ?: continue
			if (!obj.containsKey("baseVersion")) continue
			outbox.updatePayload(next.id, JsonObject(obj + ("baseVersion" to JsonPrimitive(current))).toString())
		}
		return true
	}

	private suspend fun fetchVersion(op: OutboxOp): Long? {
		val service = container.apiFor(op.accountId)
		val obj = runCatching { json.parseToJsonElement(op.payload).jsonObject }.getOrNull() ?: return null
		return when (op.kind) {
			OpKind.REPORT -> {
				val year = obj["periodYear"]?.jsonPrimitive?.longOrNull?.toInt() ?: return null
				val month = obj["periodMonth"]?.jsonPrimitive?.longOrNull?.toInt() ?: return null
				api { service.myReport(year, month) }.getOrNull()?.report?.submittedAt?.let { isoVersion(it) }
			}
			OpKind.ATTENDANCE -> {
				val type = obj["meetingType"]?.jsonPrimitive?.contentOrNull ?: return null
				val id = obj["meetingId"]?.jsonPrimitive?.contentOrNull ?: return null
				api { service.attendanceOne(type, id) }.getOrNull()?.item?.recordedAt?.let { isoVersion(it) }
			}
			else -> null
		}
	}
}
