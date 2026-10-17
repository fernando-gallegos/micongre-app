package com.micongre.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.json.JSONObject
import org.maplibre.android.maps.Style

/** Los emojis no salen de las fuentes de texto del mapa (solo tienen letras): se dibujan como imagen con la fuente de emojis del
 * teléfono y se registran en el estilo con el nombre "e:<emoji>". */
private fun emojiBitmap(emoji: String, size: Int = 88): Bitmap {
	val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
	val canvas = Canvas(bitmap)
	val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		textSize = size * 0.78f
		textAlign = Paint.Align.CENTER
	}
	val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
	canvas.drawText(emoji, size / 2f, y, paint)
	return bitmap
}

/** Registra en el estilo las imágenes de los emojis dados (las que ya están no se repiten). */
fun addEmojiImages(style: Style, emojis: Collection<String>) {
	for (emoji in emojis) {
		val name = "e:$emoji"
		if (style.getImage(name) == null) style.addImage(name, emojiBitmap(emoji))
	}
}

/** Todos los emojis que usa un GeoJSON de territorio (propiedad `emoji` de sus elementos). */
fun emojisIn(geoJson: String): Set<String> = try {
	val features = JSONObject(geoJson).optJSONArray("features")
	buildSet {
		if (features != null) {
			for (i in 0 until features.length()) {
				features.getJSONObject(i).optJSONObject("properties")?.optString("emoji")?.takeIf { it.isNotBlank() }?.let(::add)
			}
		}
	}
} catch (e: Exception) {
	emptySet()
}
