package com.micongre.app.widget

import com.micongre.app.data.PreachingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Qué predicación muestra el widget: la siguiente a la que todavía no le pasan 2 horas desde su inicio. */
class PreachingPickTest {
	private fun s(id: String, date: String, time: String) = PreachingSession(id = id, date = date, time = time)
	private fun at(date: String, hour: Int, minute: Int = 0) = LocalDate.parse(date).atTime(hour, minute)

	private val today = listOf(s("manana", "2026-10-17", "09:00"), s("tarde", "2026-10-17", "16:00"), s("otro-dia", "2026-10-19", "09:00"))

	@Test fun antesDelInicioMuestraLaDeHoy() = assertEquals("manana", pickNextSession(today, at("2026-10-17", 7))?.id)

	@Test fun durante_las_primeras_2_horas_sigue_la_misma() = assertEquals("manana", pickNextSession(today, at("2026-10-17", 10, 59))?.id)

	@Test fun a_las_2_horas_exactas_cambia_a_la_siguiente() = assertEquals("tarde", pickNextSession(today, at("2026-10-17", 11))?.id)

	@Test fun pasadas_las_de_hoy_muestra_la_siguiente_registrada_de_otro_dia() = assertEquals("otro-dia", pickNextSession(today, at("2026-10-17", 18, 5))?.id)

	@Test fun sin_nada_registrado_no_hay_sesion() {
		assertNull(pickNextSession(emptyList(), at("2026-10-17", 8)))
		assertNull(pickNextSession(today, at("2026-10-20", 8)))
	}

	@Test fun ordena_por_fecha_y_hora_aunque_lleguen_desordenadas() =
		assertEquals("manana", pickNextSession(listOf(today[2], today[1], today[0]), at("2026-10-17", 8))?.id)

	@Test fun ignora_las_que_no_tienen_fecha_u_hora_valida() =
		assertEquals("tarde", pickNextSession(listOf(s("mala", "no-fecha", "09:00"), s("mala2", "2026-10-17", "xx"), today[1]), at("2026-10-17", 8))?.id)

	@Test fun avisa_cuanto_falta_para_cambiar() {
		assertEquals(Duration.ofMinutes(150), untilSwitch(today[0], at("2026-10-17", 8, 30)))
		assertNull(untilSwitch(today[0], at("2026-10-17", 11)))
	}

	@Test fun etiquetas_de_fecha_y_hora() {
		val hoy = LocalDate.parse("2026-10-17")
		assertEquals("Hoy", dateLabel(today[0], hoy))
		assertEquals("Mañana", dateLabel(today[2], LocalDate.parse("2026-10-18")))
		assertEquals("lun 19 oct", dateLabel(today[2], hoy))
		assertEquals("9:00 a. m.", timeLabel("09:00"))
		assertEquals("12:30 p. m.", timeLabel("12:30"))
		assertEquals("12:05 a. m.", timeLabel("00:05"))
		assertEquals("4:00 p. m.", timeLabel("16:00"))
	}
}
