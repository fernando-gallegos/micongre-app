package com.micongre.app.widget

/** Meta de horas del año de servicio de un precursor regular (el aro del medidor se llena de 0 a esto). */
const val PIONEER_GOAL_HOURS = 600.0

/** Qué dibuja el widget de precursorado según su tamaño (en celdas de la pantalla de inicio). */
enum class PioneerLayout {
	/** 2×2 o menos: solo el medidor con las horas en grande al centro. */
	GAUGE,

	/** Más grande que 2×2 pero de menos de 4 filas: la gráfica de horas por mes. */
	CHART,

	/** 4 filas o más: arriba la gráfica por mes; abajo el medidor con las horas en grande al centro y «/600» en chiquito. */
	COMBINED,
}

/** Celdas que ocupa una medida en dp: una celda mide 70 dp menos 30 de margen (la regla de Android para widgets). */
fun cellsOf(dp: Float): Int = ((dp + 30f) / 70f).toInt().coerceAtLeast(1)

fun pioneerLayout(cols: Int, rows: Int): PioneerLayout = when {
	cols <= 2 && rows <= 2 -> PioneerLayout.GAUGE
	rows >= 4 -> PioneerLayout.COMBINED
	else -> PioneerLayout.CHART
}

/** Cuánto del medidor se llena (0 a 1): horas entre la meta de 600, sin pasarse. */
fun gaugeFraction(hours: Double): Float = (hours / PIONEER_GOAL_HOURS).toFloat().coerceIn(0f, 1f)

/** Inicial del mes (1 = enero) para el eje de la gráfica; el año de servicio empieza en septiembre. */
fun monthInitial(month: Int): String = "EFMAMJJASOND"[(month - 1).coerceIn(0, 11)].toString()

/** Tope de la gráfica: lo más alto del año redondeado hacia arriba a un múltiplo de 10, con un mínimo de 50 (la meta mensual de un precursor regular es ~50). */
fun chartMax(values: List<Double>): Double {
	val top = values.maxOrNull() ?: 0.0
	return (kotlin.math.ceil(top / 10.0) * 10.0).coerceAtLeast(50.0)
}
