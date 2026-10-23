package com.micongre.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Qué se guardó sin internet (cada tipo se envía a su ruta del servidor al volver la conexión). */
enum class OpKind { REPORT, ATTENDANCE, TERRITORY_RESOLVE, DISMISS }

/** PENDING: se enviará solo. CONFLICT: el servidor tiene otra versión. REJECTED: el servidor no lo aceptó (permiso, datos…).
 * NEEDS_LOGIN: la sesión venció; se envía al volver a iniciar sesión. En ningún estado se borra nada por sí solo. */
enum class OpState { PENDING, CONFLICT, REJECTED, NEEDS_LOGIN }

/** Una escritura guardada en el teléfono. `payload` es el cuerpo JSON tal como se enviará (ya con `baseVersion` si aplica). */
data class OutboxOp(
	val id: String,
	val accountId: String,
	val kind: OpKind,
	/** Identifica sobre qué dato actúa (p. ej. el informe de un mes): ordena las operaciones del mismo dato y las junta si aún no se enviaron. */
	val targetKey: String,
	val payload: String,
	/** Texto para mostrar en la lista de pendientes. */
	val label: String,
	val createdAt: Long,
	val state: OpState = OpState.PENDING,
	val attempts: Int = 0,
	val lastError: String? = null,
)

/**
 * Cola de escrituras hechas sin internet, en una base SQLite del teléfono. Cada operación se guarda (y se confirma en disco) ANTES de avisar a
 * la persona que quedó guardada, el contenido va cifrado con la clave del Keystore (son datos personales), y solo se borra cuando el servidor
 * confirma que la recibió. Sobrevive a cerrar la app, a que Android la mate y a reiniciar el teléfono.
 */
class OutboxDb(context: Context) : SQLiteOpenHelper(context.applicationContext, "outbox.db", null, 1) {
	override fun onCreate(db: SQLiteDatabase) {
		db.execSQL(
			"""CREATE TABLE ops (
				id TEXT PRIMARY KEY,
				account_id TEXT NOT NULL,
				kind TEXT NOT NULL,
				target_key TEXT NOT NULL,
				payload TEXT NOT NULL,
				label TEXT NOT NULL,
				created_at INTEGER NOT NULL,
				state TEXT NOT NULL,
				attempts INTEGER NOT NULL DEFAULT 0,
				last_error TEXT
			)""",
		)
		db.execSQL("CREATE INDEX ops_account_idx ON ops (account_id, created_at)")
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

	override fun onConfigure(db: SQLiteDatabase) {
		// Escritura confirmada en disco antes de decir «guardado».
		db.execSQL("PRAGMA synchronous = FULL")
	}
}

class Outbox(private val db: OutboxDb) {
	private val _ops = MutableStateFlow<List<OutboxOp>>(emptyList())

	/** Todas las operaciones guardadas (de todas las cuentas), de la más vieja a la más nueva. */
	val ops: StateFlow<List<OutboxOp>> = _ops.asStateFlow()

	init {
		refresh()
	}

	@Synchronized
	private fun refresh() {
		_ops.value = readAll()
	}

	private fun readAll(): List<OutboxOp> {
		val out = mutableListOf<OutboxOp>()
		db.readableDatabase.rawQuery(
			"SELECT id, account_id, kind, target_key, payload, label, created_at, state, attempts, last_error FROM ops ORDER BY created_at, rowid",
			null,
		).use { c ->
			while (c.moveToNext()) {
				val payload = SecureStore.decrypt(c.getString(4)) ?: continue // clave del Keystore perdida: no hay forma de leerlo
				out += OutboxOp(
					id = c.getString(0),
					accountId = c.getString(1),
					kind = runCatching { OpKind.valueOf(c.getString(2)) }.getOrNull() ?: continue,
					targetKey = c.getString(3),
					payload = payload,
					label = c.getString(5),
					createdAt = c.getLong(6),
					state = runCatching { OpState.valueOf(c.getString(7)) }.getOrDefault(OpState.PENDING),
					attempts = c.getInt(8),
					lastError = c.getString(9),
				)
			}
		}
		return out
	}

	fun forAccount(accountId: String): List<OutboxOp> = ops.value.filter { it.accountId == accountId }

	fun count(accountId: String): Int = forAccount(accountId).size

	/**
	 * Guarda una operación nueva. Si ya hay otra sin enviar (nunca se intentó) sobre el mismo dato, se reemplaza su contenido (la última edición
	 * es la que vale); si la anterior ya se intentó enviar, se agrega a continuación — nunca se cambia una operación que pudo llegar al servidor.
	 * Devuelve la operación que quedó guardada.
	 */
	@Synchronized
	fun enqueue(accountId: String, kind: OpKind, targetKey: String, payload: String, label: String): OutboxOp {
		val database = db.writableDatabase
		val existing = readAll().firstOrNull { it.accountId == accountId && it.targetKey == targetKey && it.attempts == 0 && it.state == OpState.PENDING }
		val op: OutboxOp
		database.beginTransaction()
		try {
			if (existing != null) {
				database.update(
					"ops",
					ContentValues().apply { put("payload", SecureStore.encrypt(payload)); put("label", label); put("created_at", System.currentTimeMillis()) },
					"id = ?",
					arrayOf(existing.id),
				)
				op = existing.copy(payload = payload, label = label)
			} else {
				op = OutboxOp(UUID.randomUUID().toString().replace("-", ""), accountId, kind, targetKey, payload, label, System.currentTimeMillis())
				database.insertOrThrow(
					"ops",
					null,
					ContentValues().apply {
						put("id", op.id); put("account_id", accountId); put("kind", kind.name); put("target_key", targetKey)
						put("payload", SecureStore.encrypt(payload)); put("label", label); put("created_at", op.createdAt)
						put("state", op.state.name); put("attempts", 0)
					},
				)
			}
			database.setTransactionSuccessful()
		} finally {
			database.endTransaction()
		}
		refresh()
		return op
	}

	/** Se anota ANTES de enviar: si la app se cierra a medias, esa operación ya no se considera «nunca enviada». */
	@Synchronized
	fun markAttempt(id: String) {
		db.writableDatabase.execSQL("UPDATE ops SET attempts = attempts + 1 WHERE id = ?", arrayOf(id))
		refresh()
	}

	@Synchronized
	fun setState(id: String, state: OpState, error: String? = null) {
		db.writableDatabase.execSQL("UPDATE ops SET state = ?, last_error = ? WHERE id = ?", arrayOf(state.name, error, id))
		refresh()
	}

	/** Cambia el contenido de una operación (p. ej. la versión base tras enviar la anterior del mismo dato). */
	@Synchronized
	fun updatePayload(id: String, payload: String) {
		db.writableDatabase.execSQL("UPDATE ops SET payload = ? WHERE id = ?", arrayOf(SecureStore.encrypt(payload), id))
		refresh()
	}

	/** Solo se llama cuando el servidor confirmó la operación (o la persona decidió descartarla). */
	@Synchronized
	fun delete(id: String) {
		db.writableDatabase.execSQL("DELETE FROM ops WHERE id = ?", arrayOf(id))
		refresh()
	}

	/** Al quitar una cuenta del teléfono (con la confirmación de la persona). */
	@Synchronized
	fun deleteAccount(accountId: String) {
		db.writableDatabase.execSQL("DELETE FROM ops WHERE account_id = ?", arrayOf(accountId))
		refresh()
	}

	/** Reactiva lo que esperaba un nuevo inicio de sesión. */
	@Synchronized
	fun resumeLogin(accountId: String) {
		db.writableDatabase.execSQL("UPDATE ops SET state = ? WHERE account_id = ? AND state = ?", arrayOf(OpState.PENDING.name, accountId, OpState.NEEDS_LOGIN.name))
		refresh()
	}
}
