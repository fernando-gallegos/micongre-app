package com.micongre.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.micongre.app.data.ServiceYearMonth

/** Colores del dibujo (el widget no puede leer el tema de la app; se elige claro u oscuro según el teléfono). */
class ChartColors(val track: Int, val accent: Int, val faint: Int, val ink: Int, val soft: Int) {
	companion object {
		/** Los colores del acento elegido; `faint` es el acento muy suave sobre la pista. */
		fun of(night: Boolean, accent: com.micongre.app.ui.theme.AccentOption = com.micongre.app.ui.theme.ACCENTS.first()) = if (night) {
			val track = 0xFF2B2F35.toInt()
			val a = accent.dark.accent.toInt()
			ChartColors(track = track, accent = a, faint = androidx.core.graphics.ColorUtils.blendARGB(track, a, 0.3f), ink = 0xFFEEEAE1.toInt(), soft = 0xFF9A9488.toInt())
		} else {
			val track = 0xFFE3DED2.toInt()
			val a = accent.light.accent.toInt()
			ChartColors(track = track, accent = a, faint = androidx.core.graphics.ColorUtils.blendARGB(track, a, 0.3f), ink = 0xFF1C1B17.toInt(), soft = 0xFF6C6658.toInt())
		}
	}
}

/** Los widgets se dibujan con Canvas porque Glance no trae gráficas; el tamaño en píxeles se limita para no pasarse de la memoria permitida. */
private fun bitmap(widthPx: Int, heightPx: Int): Bitmap {
	val maxSide = 640
	val scale = minOf(1f, maxSide.toFloat() / maxOf(widthPx, heightPx, 1))
	return Bitmap.createBitmap((widthPx * scale).toInt().coerceAtLeast(1), (heightPx * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
}

/** Medidor de 270° (abierto por abajo): pista y arco de progreso con extremos redondeados; `fraction` 0..1. */
fun drawGauge(widthPx: Int, heightPx: Int, fraction: Float, colors: ChartColors): Bitmap {
	val bmp = bitmap(widthPx, heightPx)
	val canvas = Canvas(bmp)
	val side = minOf(bmp.width, bmp.height).toFloat()
	val stroke = side * 0.11f
	val pad = stroke / 2f + side * 0.03f
	val left = (bmp.width - side) / 2f + pad
	val top = (bmp.height - side) / 2f + pad
	val rect = RectF(left, top, left + side - 2 * pad, top + side - 2 * pad)
	val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = stroke
		strokeCap = Paint.Cap.ROUND
	}
	val start = 135f // abajo a la izquierda
	val sweep = 270f
	paint.color = colors.track
	canvas.drawArc(rect, start, sweep, false, paint)
	if (fraction > 0f) {
		paint.color = colors.accent
		canvas.drawArc(rect, start, sweep * fraction, false, paint)
	}
	return bmp
}

/** Barras de horas por mes (septiembre a agosto): el mes en curso resaltado, los demás en el color del acento, y con la cifra encima cuando caben. */
fun drawMonthBars(widthPx: Int, heightPx: Int, months: List<ServiceYearMonth>, currentMonth: Int, colors: ChartColors): Bitmap {
	val bmp = bitmap(widthPx, heightPx)
	val canvas = Canvas(bmp)
	if (months.isEmpty()) return bmp
	val w = bmp.width.toFloat()
	val h = bmp.height.toFloat()
	val labelSize = (h * 0.14f).coerceIn(10f, 26f)
	val valueSize = labelSize * 0.9f
	val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = labelSize; color = colors.soft; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
	val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = valueSize; color = colors.ink; textAlign = Paint.Align.CENTER }
	val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

	val max = chartMax(months.map { it.hours })
	val axisBottom = h - labelSize * 1.5f
	val topPad = valueSize * 1.4f
	val plotHeight = (axisBottom - topPad).coerceAtLeast(1f)
	val slot = w / months.size
	val barWidth = slot * 0.62f
	val radius = barWidth / 3f
	months.forEachIndexed { i, m ->
		val cx = slot * i + slot / 2f
		val barHeight = (m.hours / max).toFloat().coerceIn(0f, 1f) * plotHeight
		val rect = RectF(cx - barWidth / 2f, axisBottom - maxOf(barHeight, if (m.hours > 0) 2f else 0f), cx + barWidth / 2f, axisBottom)
		// Sin horas queda una marca finita de la pista, para que se vea el mes aunque esté vacío.
		if (m.hours <= 0.0) {
			barPaint.color = colors.track
			canvas.drawRoundRect(RectF(cx - barWidth / 2f, axisBottom - 3f, cx + barWidth / 2f, axisBottom), radius, radius, barPaint)
		} else {
			barPaint.color = if (m.month == currentMonth) colors.accent else colors.accent.withAlpha(170)
			canvas.drawRoundRect(rect, radius, radius, barPaint)
			// Cifra encima si el espacio alcanza (con 12 barras en un widget angosto se omite).
			if (slot >= valueSize * 1.9f) {
				val text = if (m.hours % 1.0 == 0.0) m.hours.toInt().toString() else "%.1f".format(java.util.Locale.US, m.hours)
				canvas.drawText(text, cx, rect.top - valueSize * 0.25f, valuePaint)
			}
		}
		labelPaint.color = if (m.month == currentMonth) colors.ink else colors.soft
		canvas.drawText(monthInitial(m.month), cx, h - labelSize * 0.35f, labelPaint)
	}
	return bmp
}

private fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
