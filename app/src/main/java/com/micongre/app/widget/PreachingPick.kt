package com.micongre.app.widget

import com.micongre.app.data.PreachingSession
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Horas que se sigue mostrando una predicación después de su inicio; pasado ese tiempo, el widget muestra la siguiente registrada. */
const val PREACHING_VISIBLE_HOURS = 2L

/** Inicio de la salida (fecha + hora «HH:mm»); null si la fecha u hora no se pueden leer. */
fun startOf(session: PreachingSession): LocalDateTime? = runCatching {
	LocalDateTime.of(LocalDate.parse(session.date), LocalTime.parse(session.time))
}.getOrNull()

/**
 * La predicación que debe mostrar el widget: la primera, por fecha y hora, a la que todavía no le han pasado 2 horas desde su inicio. Si ya pasaron
 * 2 horas de la de hoy, es la siguiente registrada (de hoy o de otro día). Null si no hay ninguna: el widget invita a predicar por cartas.
 */
fun pickNextSession(sessions: List<PreachingSession>, now: LocalDateTime): PreachingSession? =
	sessions
		.mapNotNull { s -> startOf(s)?.let { s to it } }
		.sortedBy { it.second }
		.firstOrNull { (_, start) -> now < start.plusHours(PREACHING_VISIBLE_HOURS) }
		?.first

/** Cuánto falta para que a la predicación mostrada le pasen las 2 horas (cuando el widget debe cambiar a la siguiente); null si ya pasó o no se sabe. */
fun untilSwitch(session: PreachingSession, now: LocalDateTime): Duration? =
	startOf(session)?.plusHours(PREACHING_VISIBLE_HOURS)?.let { Duration.between(now, it) }?.takeIf { !it.isNegative && !it.isZero }

private val WEEKDAYS = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** «Hoy», «Mañana» o «sáb 17 oct». */
fun dateLabel(session: PreachingSession, today: LocalDate): String {
	val date = runCatching { LocalDate.parse(session.date) }.getOrNull() ?: return session.date
	return when (date) {
		today -> "Hoy"
		today.plusDays(1) -> "Mañana"
		else -> "${WEEKDAYS[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
	}
}

/** «09:00» → «9:00 a. m.»; si no se puede leer, el mismo texto. */
fun timeLabel(time: String): String {
	val t = runCatching { LocalTime.parse(time) }.getOrNull() ?: return time
	val hour = (t.hour % 12).let { if (it == 0) 12 else it }
	return "$hour:${"%02d".format(t.minute)} ${if (t.hour < 12) "a. m." else "p. m."}"
}
