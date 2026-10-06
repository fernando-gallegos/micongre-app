package com.micongre.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.concurrent.TimeUnit

// Mapa base gratuito: teselas vectoriales de OpenFreeMap (datos de OpenStreetMap, sin llave ni cuota), el mismo estilo "bright"
// que usa la web, sin los nombres de lugares (ciudades, colonias, agua, aeropuertos, límites y escudos de carreteras): quedan los
// nombres de calles y los lugares de interés (ver micongre-admin/src/lib/territories/mapStyle.ts).
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/bright"
private val HIDDEN_SOURCE_LAYERS = setOf("place", "water_name", "aerodrome_label", "boundary", "housenumber", "mountain_peak")
private val HIDDEN_LAYER_IDS = setOf("waterway_line_label", "highway-shield-non-us", "highway-shield-us-interstate", "road_shield_us")

private var cachedStyle: String? = null

/** Descarga el estilo del mapa (una vez por sesión de la app); null si no hay conexión. */
suspend fun loadBaseStyle(): String? {
	cachedStyle?.let { return it }
	return withContext(Dispatchers.IO) {
		try {
			val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
			val text = client.newCall(Request.Builder().url(STYLE_URL).build()).execute().use { r ->
				if (!r.isSuccessful) return@use null
				r.body?.string()
			} ?: return@withContext null
			val style = JSONObject(text)
			val layers = style.getJSONArray("layers")
			val out = JSONArray()
			for (i in 0 until layers.length()) {
				val layer = layers.getJSONObject(i)
				val sourceLayer = layer.optString("source-layer")
				if (layer.getString("id") in HIDDEN_LAYER_IDS || sourceLayer in HIDDEN_SOURCE_LAYERS) continue
				// Nombres de calle: el nombre local, un poco más grandes y desde un zoom más lejano.
				if (sourceLayer == "transportation_name" && layer.optString("type") == "symbol") {
					layer.put("minzoom", minOf(layer.optInt("minzoom", 14), 14))
					val layout = layer.optJSONObject("layout") ?: JSONObject().also { layer.put("layout", it) }
					layout.put("text-field", JSONArray("""["coalesce",["get","name"],["get","name_en"]]"""))
					layout.put("text-size", JSONArray("""["interpolate",["linear"],["zoom"],13,12,16,14,19,16]"""))
					val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
					paint.put("text-color", "#374151")
					paint.put("text-halo-color", "#ffffff")
					paint.put("text-halo-width", 1.5)
				}
				out.put(layer)
			}
			style.put("layers", out)
			style.toString().also { cachedStyle = it }
		} catch (e: Exception) {
			null
		}
	}
}

/** Mapa base con el dibujo del territorio encima (polígonos, líneas, puntos y textos); ajusta la cámara para que quepa todo. */
@Composable
fun BaseMapView(styleJson: String, geoJson: String, points: List<Pair<Double, Double>>, accentHex: String) {
	val context = LocalContext.current
	val mapView = remember {
		MapLibre.getInstance(context)
		// Modo textura: se lleva bien con diálogos (la superficie normal se dibuja por encima o por debajo).
		MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
	}

	DisposableEffect(mapView) {
		mapView.onCreate(null)
		mapView.onStart()
		mapView.onResume()
		onDispose {
			mapView.onPause()
			mapView.onStop()
			mapView.onDestroy()
		}
	}

	AndroidView(
		factory = {
			mapView.apply {
				getMapAsync { map ->
					map.uiSettings.isRotateGesturesEnabled = false
					map.uiSettings.isTiltGesturesEnabled = false
					map.uiSettings.isAttributionEnabled = true
					map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
						style.addSource(GeoJsonSource("territory", geoJson))
						val isPolygon = Expression.eq(Expression.geometryType(), "Polygon")
						style.addLayer(
							FillLayer("t-fill", "territory").withFilter(isPolygon)
								.withProperties(PropertyFactory.fillColor(accentHex), PropertyFactory.fillOpacity(0.2f)),
						)
						style.addLayer(
							LineLayer("t-line", "territory")
								.withFilter(Expression.any(isPolygon, Expression.eq(Expression.geometryType(), "LineString")))
								.withProperties(PropertyFactory.lineColor(accentHex), PropertyFactory.lineWidth(3f)),
						)
						style.addLayer(
							CircleLayer("t-point", "territory")
								.withFilter(Expression.all(Expression.eq(Expression.geometryType(), "Point"), Expression.not(Expression.has("text")), Expression.not(Expression.has("emoji"))))
								.withProperties(
									PropertyFactory.circleColor("#D64545"),
									PropertyFactory.circleRadius(7f),
									PropertyFactory.circleStrokeColor("#ffffff"),
									PropertyFactory.circleStrokeWidth(2f),
								),
						)
						style.addLayer(
							SymbolLayer("t-emoji", "territory").withFilter(Expression.has("emoji")).withProperties(
								PropertyFactory.textField(Expression.get("emoji")),
								PropertyFactory.textSize(26f),
								PropertyFactory.textAllowOverlap(true),
								PropertyFactory.textIgnorePlacement(true),
							),
						)
						style.addLayer(
							SymbolLayer("t-text", "territory").withFilter(Expression.has("text")).withProperties(
								PropertyFactory.textField(Expression.get("text")),
								PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
								PropertyFactory.textSize(14f),
								PropertyFactory.textColor("#1c1b17"),
								PropertyFactory.textHaloColor("#ffffff"),
								PropertyFactory.textHaloWidth(2f),
								PropertyFactory.textAllowOverlap(true),
							),
						)
						if (points.isNotEmpty()) {
							val builder = LatLngBounds.Builder()
							points.forEach { (lng, lat) -> builder.include(LatLng(lat, lng)) }
							if (points.size == 1) {
								map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].second, points[0].first), 17.0))
							} else {
								map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 70))
							}
						}
					}
				}
			}
		},
		modifier = Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(14.dp)),
	)
}
