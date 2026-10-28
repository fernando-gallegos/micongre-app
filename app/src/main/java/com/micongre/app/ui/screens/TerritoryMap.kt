package com.micongre.app.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.micongre.app.AppContainer
import com.micongre.app.data.TerritoryMapResponse
import com.micongre.app.data.api
import com.micongre.app.ui.Loading
import com.micongre.app.ui.rememberLoad
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

private class Shape(val kind: String, val points: List<Pair<Double, Double>>, val text: String?, val emoji: String?)

// Web Mercator, para que el dibujo conserve las proporciones del mapa.
private fun mx(lng: Double) = (lng + 180.0) / 360.0
private fun my(lat: Double) = 0.5 - ln(tan(PI / 4 + Math.toRadians(lat) / 2)) / (2 * PI)

private fun parseShapes(data: kotlinx.serialization.json.JsonElement?): List<Shape> {
	val features = (data as? JsonObject)?.get("features") as? JsonArray ?: return emptyList()
	val out = mutableListOf<Shape>()
	for (f in features) {
		val obj = f.jsonObject
		val geo = obj["geometry"] as? JsonObject ?: continue
		val props = obj["properties"] as? JsonObject
		val text = props?.get("text")?.jsonPrimitive?.content.takeIf { props?.get("kind")?.jsonPrimitive?.content == "text" }
		val emoji = props?.get("emoji")?.jsonPrimitive?.content
		val type = geo["type"]?.jsonPrimitive?.content ?: continue
		val coords = geo["coordinates"] ?: continue
		fun pt(e: kotlinx.serialization.json.JsonElement): Pair<Double, Double>? {
			val a = runCatching { e.jsonArray }.getOrNull() ?: return null
			if (a.size < 2) return null
			val lng = (a[0] as? JsonPrimitive)?.doubleOrNull ?: return null
			val lat = (a[1] as? JsonPrimitive)?.doubleOrNull ?: return null
			return lng to lat
		}
		when (type) {
			"Point" -> pt(coords)?.let { out += Shape(if (text != null) "text" else "point", listOf(it), text, emoji) }
			"LineString" -> (coords as? kotlinx.serialization.json.JsonArray)?.mapNotNull { pt(it) }?.takeIf { it.isNotEmpty() }?.let { out += Shape("line", it, null, null) }
			"Polygon" -> ((coords as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? kotlinx.serialization.json.JsonArray)?.mapNotNull { pt(it) }?.takeIf { it.isNotEmpty() }?.let { out += Shape("polygon", it, null, null) }
		}
	}
	return out
}

/** Dibujo del territorio (polígonos, líneas, puntos y textos) sobre un fondo liso; el mapa con calles es solo de la web. */
@Composable
fun TerritoryMapDialog(container: AppContainer, territoryId: String, onDismiss: () -> Unit) {
	val state = rememberLoad(territoryId) { api { container.apiService.territoryMap(territoryId) } }
	val accent = MaterialTheme.colorScheme.primary
	var baseStyle by remember { mutableStateOf<String?>(null) }
	var styleTried by remember { mutableStateOf(false) }
	LaunchedEffect(Unit) { baseStyle = loadBaseStyle(); styleTried = true }
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
		title = {
			val m: TerritoryMapResponse? = state.data
			Column {
				Text(if (m != null) "Territorio ${m.number}${m.name?.let { " · $it" } ?: ""}" else "Territorio")
				if (m != null) {
					Text(
						(if (m.kind == "public_preaching") "Predicación pública" else "General") + (m.notes?.let { " · $it" } ?: ""),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		},
		text = {
			val m = state.data
			if (state.loading) {
				Loading(Modifier.fillMaxWidth().height(260.dp))
			} else if (m == null) {
				Text("No se pudo cargar el mapa.")
			} else {
				val shapes = parseShapes(m.mapData)
				if (shapes.isEmpty()) {
					Text("Este territorio todavía no tiene dibujo.")
				} else if (!styleTried) {
					Loading(Modifier.fillMaxWidth().height(260.dp))
				} else if (baseStyle != null) {
					BaseMapView(
						styleJson = baseStyle!!,
						geoJson = m.mapData.toString(),
						points = shapes.flatMap { it.points },
						accentHex = String.format("#%06X", accent.toArgb() and 0xFFFFFF),
					)
				} else {
					Canvas(
						Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF4F1EA)).padding(4.dp),
					) {
						val all = shapes.flatMap { it.points }
						val xs = all.map { mx(it.first) }
						val ys = all.map { my(it.second) }
						val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
						val pad = 28f
						val spanX = max(maxX - minX, 1e-7)
						val spanY = max(maxY - minY, 1e-7)
						val scale = min((size.width - pad * 2) / spanX, (size.height - pad * 2) / spanY).toFloat()
						val offX = (size.width - spanX.toFloat() * scale) / 2f
						val offY = (size.height - spanY.toFloat() * scale) / 2f
						fun map(p: Pair<Double, Double>) = Offset(
							offX + ((mx(p.first) - minX) * scale).toFloat(),
							offY + ((my(p.second) - minY) * scale).toFloat(),
						)
						for (s in shapes) when (s.kind) {
							"polygon" -> if (s.points.size >= 3) {
								val path = Path().apply {
									val first = map(s.points.first())
									moveTo(first.x, first.y)
									s.points.drop(1).forEach { val o = map(it); lineTo(o.x, o.y) }
									close()
								}
								drawPath(path, accent.copy(alpha = 0.2f))
								drawPath(path, accent, style = Stroke(width = 4f))
							}
							"line" -> if (s.points.size >= 2) {
								val path = Path().apply {
									val first = map(s.points.first())
									moveTo(first.x, first.y)
									s.points.drop(1).forEach { val o = map(it); lineTo(o.x, o.y) }
								}
								drawPath(path, accent, style = Stroke(width = 5f))
							}
							"point" -> {
								val o = map(s.points.first())
								if (s.emoji != null) {
									drawContext.canvas.nativeCanvas.drawText(
										s.emoji, o.x - 18f, o.y + 12f,
										android.graphics.Paint().apply { textSize = 44f },
									)
								} else {
									drawCircle(Color(0xFFD64545), radius = 9f, center = o)
								}
							}
							"text" -> {
								val o = map(s.points.first())
								drawContext.canvas.nativeCanvas.drawText(
									s.text ?: "", o.x, o.y,
									android.graphics.Paint().apply {
										textSize = 34f
										isFakeBoldText = true
										color = android.graphics.Color.parseColor("#1c1b17")
										textAlign = android.graphics.Paint.Align.CENTER
										setShadowLayer(4f, 0f, 0f, android.graphics.Color.WHITE)
									},
								)
							}
						}
					}
				}
			}
		},
	)
}
