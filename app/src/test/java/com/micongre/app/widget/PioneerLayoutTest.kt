package com.micongre.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** Qué dibuja el widget de precursorado según el tamaño, y cómo se llena el medidor. */
class PioneerLayoutTest {
	@Test fun celdas_segun_la_regla_de_70dp() {
		assertEquals(1, cellsOf(40f))
		assertEquals(2, cellsOf(110f))
		assertEquals(3, cellsOf(180f))
		assertEquals(4, cellsOf(250f))
		assertEquals(5, cellsOf(320f))
	}

	@Test fun dos_por_dos_es_el_medidor() {
		assertEquals(PioneerLayout.GAUGE, pioneerLayout(2, 2))
		assertEquals(PioneerLayout.GAUGE, pioneerLayout(1, 1))
		assertEquals(PioneerLayout.GAUGE, pioneerLayout(2, 1))
	}

	@Test fun mas_grande_que_dos_por_dos_pero_de_menos_de_cuatro_filas_es_la_grafica() {
		assertEquals(PioneerLayout.CHART, pioneerLayout(4, 2))
		assertEquals(PioneerLayout.CHART, pioneerLayout(3, 3))
		assertEquals(PioneerLayout.CHART, pioneerLayout(2, 3))
		assertEquals(PioneerLayout.CHART, pioneerLayout(5, 1))
	}

	@Test fun de_cuatro_filas_es_grafica_medidor_y_horas() {
		assertEquals(PioneerLayout.COMBINED, pioneerLayout(4, 4))
		assertEquals(PioneerLayout.COMBINED, pioneerLayout(2, 4))
		assertEquals(PioneerLayout.COMBINED, pioneerLayout(4, 5))
	}

	@Test fun el_medidor_se_llena_de_0_a_600_sin_pasarse() {
		assertEquals(0f, gaugeFraction(0.0), 0.0001f)
		assertEquals(0.5f, gaugeFraction(300.0), 0.0001f)
		assertEquals(1f, gaugeFraction(600.0), 0.0001f)
		assertEquals(1f, gaugeFraction(742.5), 0.0001f)
		assertEquals(0f, gaugeFraction(-5.0), 0.0001f)
	}

	@Test fun iniciales_del_ano_de_servicio_de_septiembre_a_agosto() {
		val order = listOf(9, 10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8).joinToString("") { monthInitial(it) }
		assertEquals("SONDEFMAMJJA", order)
	}

	@Test fun tope_de_la_grafica_redondea_a_decenas_con_minimo_de_50() {
		assertEquals(50.0, chartMax(emptyList()), 0.0)
		assertEquals(50.0, chartMax(listOf(12.0, 30.5)), 0.0)
		assertEquals(60.0, chartMax(listOf(12.0, 55.5)), 0.0)
		assertEquals(70.0, chartMax(listOf(70.0)), 0.0)
	}
}
