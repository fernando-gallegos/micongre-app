package com.micongre.app.ui.screens

import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pentagon
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.micongre.app.AppContainer
import com.micongre.app.data.api
import com.micongre.app.ui.Loading
import com.micongre.app.ui.rememberLoad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
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
import java.util.UUID
import kotlin.math.hypot

private val POINT_EMOJIS = listOf("📍", "🏠", "🏢", "🏫", "⛪", "🛒", "🅿️", "🚏", "🚫", "⚠️", "🐕", "🔒", "🔔", "👥", "✅", "❌")
private const val DEFAULT_EMOJI = "📍"
private const val MAX_FEATURES = 500
private const val MAX_HISTORY = 50
private const val POLYGON_COLOR = "#e11d48"
private const val LINE_COLOR = "#2563eb"

private enum class Tool(val label: String, val icon: ImageVector, val hint: String) {
	SELECT("Seleccionar", Icons.Filled.TouchApp, "Toca una figura para seleccionarla. Un punto o texto seleccionado se mueve arrastrándolo."),
	POLYGON("Polígono", Icons.Filled.Pentagon, "Toca el mapa para marcar los vértices (mínimo 3) y pulsa «Terminar»."),
	LINE("Línea", Icons.Filled.Timeline, "Toca el mapa para marcar los puntos (mínimo 2) y pulsa «Terminar»."),
	POINT("Punto", Icons.Filled.Place, "Elige un ícono y toca el mapa para colocarlo."),
	TEXT("Texto", Icons.Filled.TextFields, "Toca el mapa donde quieras poner el texto."),
}

/** Un elemento del dibujo, como en la web: `type` es Point, LineString o Polygon; las coordenadas son (lng, lat). */
private data class Feat(
	val fid: String,
	val type: String,
	val coords: List<Pair<Double, Double>>,
	val kind: String? = null,
	val text: String? = null,
	val emoji: String? = null,
)

private fun parseFeatures(data: JsonElement?): List<Feat> {
	val array = try { data?.jsonObject?.get("features")?.jsonArray } catch (e: Exception) { null } ?: return emptyList()
	val out = mutableListOf<Feat>()
	for (element in array) {
		try {
			val obj = element.jsonObject
			val geometry = obj["geometry"]!!.jsonObject
			val props = obj["properties"]?.jsonObject
			val type = geometry["type"]!!.jsonPrimitive.content
			val raw = geometry["coordinates"]!!.jsonArray
			fun pt(e: JsonElement) = e.jsonArray[0].jsonPrimitive.doubleOrNull!! to e.jsonArray[1].jsonPrimitive.doubleOrNull!!
			val coords = when (type) {
				"Point" -> listOf(pt(raw))
				"LineString" -> raw.map(::pt)
				"Polygon" -> raw[0].jsonArray.map(::pt).dropLast(1) // sin el vértice de cierre repetido
				else -> continue
			}
			out += Feat(
				fid = props?.get("fid")?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString(),
				type = type,
				coords = coords,
				kind = props?.get("kind")?.jsonPrimitive?.contentOrNull,
				text = props?.get("text")?.jsonPrimitive?.contentOrNull,
				emoji = props?.get("emoji")?.jsonPrimitive?.contentOrNull,
			)
		} catch (e: Exception) {
			// Elemento que no se entiende: se omite.
		}
	}
	return out
}

/** GeoJSON para mostrar (con `sel` marcando el elemento seleccionado) o para guardar (`forSave`, solo las propiedades válidas). */
private fun toGeoJson(features: List<Feat>, selected: String?, forSave: Boolean): String {
	val array = JSONArray()
	for (f in features) {
		val coords = f.coords.map { JSONArray().put(it.first).put(it.second) }
		val geometry = JSONObject().put("type", f.type).put(
			"coordinates",
			when (f.type) {
				"Point" -> coords.first()
				"LineString" -> JSONArray(coords)
				else -> JSONArray().put(JSONArray(coords + coords.first())) // anillo cerrado
			},
		)
		val props = JSONObject().put("fid", f.fid)
		f.kind?.let { props.put("kind", it) }
		f.text?.let { props.put("text", it) }
		f.emoji?.let { props.put("emoji", it) }
		if (!forSave) props.put("sel", f.fid == selected)
		array.put(JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", props))
	}
	return JSONObject().put("type", "FeatureCollection").put("features", array).toString()
}

private fun draftGeoJson(draft: List<Pair<Double, Double>>, closed: Boolean): String {
	val array = JSONArray()
	if (draft.size >= 2) {
		val line = draft.map { JSONArray().put(it.first).put(it.second) }.toMutableList()
		if (closed && draft.size >= 3) line += line.first()
		array.put(
			JSONObject().put("type", "Feature").put("properties", JSONObject())
				.put("geometry", JSONObject().put("type", "LineString").put("coordinates", JSONArray(line))),
		)
	}
	for (p in draft) {
		array.put(
			JSONObject().put("type", "Feature").put("properties", JSONObject())
				.put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(p.first).put(p.second))),
		)
	}
	return JSONObject().put("type", "FeatureCollection").put("features", array).toString()
}

private fun boundsOf(features: List<Feat>): LatLngBounds? {
	val pts = features.flatMap { it.coords }
	if (pts.size < 2) return null
	val b = LatLngBounds.Builder()
	pts.forEach { b.include(LatLng(it.second, it.first)) }
	return b.build()
}

private val httpClient = OkHttpClient()

/** Busca un lugar en OpenStreetMap (Nominatim) y devuelve su caja [oeste, sur, este, norte]. */
private suspend fun geocode(text: String): DoubleArray? = withContext(Dispatchers.IO) {
	try {
		val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&accept-language=es&q=" + java.net.URLEncoder.encode(text, "UTF-8")
		val req = Request.Builder().url(url).header("User-Agent", "MiCongre-Android").build()
		httpClient.newCall(req).execute().use { r ->
			if (!r.isSuccessful) return@use null
			val box = JSONArray(r.body?.string() ?: "[]").optJSONObject(0)?.optJSONArray("boundingbox") ?: return@use null
			// Nominatim: [sur, norte, oeste, este]
			doubleArrayOf(box.getString(2).toDouble(), box.getString(0).toDouble(), box.getString(3).toDouble(), box.getString(1).toDouble())
		}
	} catch (e: Exception) {
		null
	}
}

/** Editor del dibujo de un territorio (pantalla completa), con la misma barra de herramientas que la web: seleccionar, polígono,
 * línea, punto con ícono y texto; deshacer; arrastrar un punto o texto para moverlo. Un territorio de predicación pública solo
 * admite puntos y textos. `hintTerritoryId`: otro territorio con mapa, para centrar el mapa cuando este todavía está vacío. */
@Composable
fun TerritoryMapEditor(container: AppContainer, territoryId: String, hintTerritoryId: String?, onClose: (saved: Boolean) -> Unit) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val loaded = rememberLoad(territoryId) { api { container.apiService.territoryMap(territoryId) } }
	var styleJson by remember { mutableStateOf<String?>(null) }
	LaunchedEffect(Unit) { styleJson = loadBaseStyle() }

	var features by remember { mutableStateOf<List<Feat>>(emptyList()) }
	var history by remember { mutableStateOf<List<List<Feat>>>(emptyList()) }
	var selected by remember { mutableStateOf<String?>(null) }
	var tool by remember { mutableStateOf(Tool.SELECT) }
	var draft by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
	var activeEmoji by remember { mutableStateOf<String?>(DEFAULT_EMOJI) }
	var customEmoji by remember { mutableStateOf("") }
	var dirty by remember { mutableStateOf(false) }
	var initialised by remember { mutableStateOf(false) }
	var saving by remember { mutableStateOf(false) }
	var message by remember { mutableStateOf<String?>(null) }
	var askDiscard by remember { mutableStateOf(false) }
	var textFor by remember { mutableStateOf<Pair<String?, Pair<Double, Double>?>?>(null) } // (fid existente | null, posición nueva)
	var search by remember { mutableStateOf("") }
	var searching by remember { mutableStateOf(false) }

	val data = loaded.data
	val publicOnly = (data?.kind ?: "general") == "public_preaching"
	LaunchedEffect(data) {
		if (data != null && !initialised) {
			features = parseFeatures(data.mapData)
			initialised = true
		}
	}

	var map by remember { mutableStateOf<MapLibreMap?>(null) }
	var styleReady by remember { mutableStateOf(false) }
	val toolState = rememberUpdatedState(tool)
	val featuresState = rememberUpdatedState(features)
	val selectedState = rememberUpdatedState(selected)
	val draftState = rememberUpdatedState(draft)
	val emojiState = rememberUpdatedState(activeEmoji)

	val mapView = remember {
		MapLibre.getInstance(context)
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

	fun requestClose() {
		if (dirty && !saving) askDiscard = true else onClose(false)
	}

	/** Todo cambio pasa por aquí: guarda el estado anterior para poder deshacer. */
	fun commit(next: List<Feat>) {
		history = (history + listOf(features)).takeLast(MAX_HISTORY)
		features = next
		dirty = true
	}

	fun addFeature(f: Feat) {
		if (features.size >= MAX_FEATURES) {
			message = "Se alcanzó el máximo de $MAX_FEATURES elementos en un mapa."
			return
		}
		commit(features + f)
		selected = f.fid
	}

	fun finishDraft() {
		val t = tool
		if (t == Tool.POLYGON && draft.size >= 3) addFeature(Feat(UUID.randomUUID().toString(), "Polygon", draft))
		else if (t == Tool.LINE && draft.size >= 2) addFeature(Feat(UUID.randomUUID().toString(), "LineString", draft))
		else return
		draft = emptyList()
		tool = Tool.SELECT
	}

	fun undo() {
		if (draft.isNotEmpty()) {
			draft = draft.dropLast(1)
		} else if (history.isNotEmpty()) {
			features = history.last()
			history = history.dropLast(1)
			selected = null
			dirty = history.isNotEmpty()
		}
	}

	fun save() {
		saving = true
		message = null
		scope.launch {
			val geo = kotlinx.serialization.json.Json.parseToJsonElement(toGeoJson(features, null, forSave = true))
			val body: JsonObject = buildJsonObject { put("mapData", geo) }
			val r = api { container.apiService.saveTerritoryMap(territoryId, body) }
			saving = false
			if (r.isSuccess) {
				dirty = false
				onClose(true)
			} else {
				message = "No se pudo guardar el mapa. Revisa tu conexión (el dibujo no puede ser demasiado grande)."
			}
		}
	}

	// Mapa → fuentes de datos
	LaunchedEffect(map, styleReady, features, selected, draft, tool) {
		val m = map ?: return@LaunchedEffect
		if (!styleReady) return@LaunchedEffect
		m.getStyle { style ->
			addEmojiImages(style, features.mapNotNull { it.emoji }.toSet())
			style.getSourceAs<GeoJsonSource>("ed")?.setGeoJson(toGeoJson(features, selected, forSave = false))
			style.getSourceAs<GeoJsonSource>("draft")?.setGeoJson(draftGeoJson(draft, closed = tool == Tool.POLYGON))
		}
	}

	// Encuadre inicial: el dibujo propio, o el de otro territorio.
	var framed by remember { mutableStateOf(false) }
	LaunchedEffect(map, styleReady, initialised) {
		val m = map ?: return@LaunchedEffect
		if (!styleReady || !initialised || framed) return@LaunchedEffect
		framed = true
		val own = boundsOf(features)
		if (own != null) {
			m.moveCamera(CameraUpdateFactory.newLatLngBounds(own, 80))
		} else if (features.size == 1) {
			val p = features.first().coords.first()
			m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.second, p.first), 17.0))
		} else if (hintTerritoryId != null) {
			val hint = api { container.apiService.territoryMap(hintTerritoryId) }.getOrNull()
			boundsOf(parseFeatures(hint?.mapData))?.let { m.moveCamera(CameraUpdateFactory.newLatLngBounds(it, 80)) }
		}
	}

	val current = features.firstOrNull { it.fid == selected }
	val selectedPoint = current?.takeIf { it.type == "Point" }
	val showEmojis = tool == Tool.POINT || (tool == Tool.SELECT && selectedPoint != null && selectedPoint.kind != "text")
	val showDraftActions = tool == Tool.POLYGON || tool == Tool.LINE
	val showSelectionActions = tool == Tool.SELECT && current != null
	val tools = Tool.entries.filter { !publicOnly || it == Tool.SELECT || it == Tool.POINT || it == Tool.TEXT }

	Dialog(onDismissRequest = { requestClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
		Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
			Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
				// Encabezado
				Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
					IconButton(onClick = { requestClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar") }
					Column(Modifier.weight(1f)) {
						Text("Dibujar mapa", fontWeight = FontWeight.SemiBold)
						Text(
							if (publicOnly) "Predicación pública: puntos y textos" else "Polígonos, líneas, puntos y textos",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					Button(onClick = { save() }, enabled = dirty && !saving, modifier = Modifier.padding(end = 8.dp)) { Text(if (saving) "Guardando…" else "Guardar") }
				}
				// Búsqueda de un lugar para ubicar el mapa
				Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
					OutlinedTextField(
						search, { search = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Buscar un lugar o dirección") },
					)
					IconButton(
						enabled = search.isNotBlank() && !searching,
						onClick = {
							searching = true
							scope.launch {
								val box = geocode(search)
								searching = false
								if (box == null) message = "No se encontró ese lugar."
								else map?.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(box[3], box[2], box[1], box[0]), 40))
							}
						},
					) { Icon(Icons.Filled.Search, "Buscar") }
				}

				Box(Modifier.weight(1f).fillMaxWidth()) {
					val style = styleJson
					if (style == null || loaded.loading && data == null) {
						Loading()
					} else {
						AndroidView(
							modifier = Modifier.fillMaxSize(),
							factory = {
								mapView.apply {
									getMapAsync { m ->
										m.uiSettings.isRotateGesturesEnabled = false
										m.uiSettings.isTiltGesturesEnabled = false
										m.setStyle(Style.Builder().fromJson(style)) { st ->
											st.addSource(GeoJsonSource("ed", """{"type":"FeatureCollection","features":[]}"""))
											st.addSource(GeoJsonSource("draft", """{"type":"FeatureCollection","features":[]}"""))
											val poly = Expression.eq(Expression.geometryType(), "Polygon")
											val lineT = Expression.eq(Expression.geometryType(), "LineString")
											val pointT = Expression.eq(Expression.geometryType(), "Point")
											val sel = Expression.eq(Expression.get("sel"), Expression.literal(true))
											st.addLayer(
												LineLayer("ed-sel", "ed").withFilter(Expression.all(sel, Expression.neq(Expression.geometryType(), "Point")))
													.withProperties(PropertyFactory.lineColor("#facc15"), PropertyFactory.lineWidth(10f), PropertyFactory.lineOpacity(0.7f)),
											)
											st.addLayer(
												FillLayer("ed-fill", "ed").withFilter(poly)
													.withProperties(PropertyFactory.fillColor(POLYGON_COLOR), PropertyFactory.fillOpacity(0.18f)),
											)
											st.addLayer(
												LineLayer("ed-outline", "ed").withFilter(poly)
													.withProperties(PropertyFactory.lineColor(POLYGON_COLOR), PropertyFactory.lineWidth(3f)),
											)
											st.addLayer(
												LineLayer("ed-line", "ed").withFilter(lineT)
													.withProperties(PropertyFactory.lineColor(LINE_COLOR), PropertyFactory.lineWidth(4f)),
											)
											st.addLayer(
												CircleLayer("ed-pt-sel", "ed").withFilter(Expression.all(pointT, sel))
													.withProperties(PropertyFactory.circleRadius(22f), PropertyFactory.circleColor("#facc15"), PropertyFactory.circleOpacity(0.6f)),
											)
											st.addLayer(
												CircleLayer("ed-pt", "ed")
													.withFilter(Expression.all(pointT, Expression.not(Expression.has("emoji")), Expression.not(Expression.has("text"))))
													.withProperties(
														PropertyFactory.circleRadius(8f), PropertyFactory.circleColor("#ffffff"),
														PropertyFactory.circleStrokeColor("#111827"), PropertyFactory.circleStrokeWidth(2.5f),
													),
											)
											st.addLayer(
												SymbolLayer("ed-emoji", "ed").withFilter(Expression.has("emoji")).withProperties(
													PropertyFactory.iconImage(Expression.concat(Expression.literal("e:"), Expression.get("emoji"))),
													PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true), PropertyFactory.iconSize(0.7f),
												),
											)
											st.addLayer(
												SymbolLayer("ed-text", "ed").withFilter(Expression.has("text")).withProperties(
													PropertyFactory.textField(Expression.get("text")), PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
													PropertyFactory.textSize(15f), PropertyFactory.textColor("#111827"),
													PropertyFactory.textHaloColor("#ffffff"), PropertyFactory.textHaloWidth(2f),
													PropertyFactory.textAllowOverlap(true), PropertyFactory.textIgnorePlacement(true),
												),
											)
											st.addLayer(
												LineLayer("dr-line", "draft").withFilter(lineT).withProperties(
													PropertyFactory.lineColor("#111827"), PropertyFactory.lineWidth(3f), PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
												),
											)
											st.addLayer(
												CircleLayer("dr-pt", "draft").withFilter(pointT).withProperties(
													PropertyFactory.circleRadius(6f), PropertyFactory.circleColor("#ffffff"),
													PropertyFactory.circleStrokeColor("#111827"), PropertyFactory.circleStrokeWidth(2f),
												),
											)
											map = m
											styleReady = true
										}

										// Toques sobre el mapa según la herramienta activa.
										m.addOnMapClickListener { ll ->
											val pos = ll.longitude to ll.latitude
											when (toolState.value) {
												Tool.POLYGON, Tool.LINE -> draft = draftState.value + pos
												Tool.POINT -> addFeature(Feat(UUID.randomUUID().toString(), "Point", listOf(pos), emoji = emojiState.value))
												Tool.TEXT -> textFor = null to pos
												Tool.SELECT -> {
													val px = m.projection.toScreenLocation(ll)
													val box = RectF(px.x - 30f, px.y - 30f, px.x + 30f, px.y + 30f)
													val hits = m.queryRenderedFeatures(box, "ed-pt-sel", "ed-emoji", "ed-text", "ed-pt", "ed-line", "ed-outline", "ed-fill")
													// Un punto o un texto se elige antes que la línea o el polígono que tenga debajo.
													fun rank(type: String?) = when (type) { "Point" -> 0; "LineString" -> 1; else -> 2 }
													selected = hits
														.filter { it.getStringProperty("fid") != null }
														.minByOrNull { rank(it.geometry()?.type()) }
														?.getStringProperty("fid")
												}
											}
											true
										}
									}

									// Arrastrar: un punto o texto ya seleccionado se mueve con el dedo (el mapa no se desplaza mientras tanto).
									var dragging = false
									setOnTouchListener { _, ev ->
										val m = map
										val target = featuresState.value.firstOrNull { it.fid == selectedState.value && it.type == "Point" }
										if (m == null || toolState.value != Tool.SELECT || target == null) return@setOnTouchListener false
										when (ev.actionMasked) {
											MotionEvent.ACTION_DOWN -> {
												val p = m.projection.toScreenLocation(LatLng(target.coords[0].second, target.coords[0].first))
												if (hypot(ev.x - p.x, ev.y - p.y) < 64f) {
													dragging = true
													m.uiSettings.setAllGesturesEnabled(false)
													history = (history + listOf(featuresState.value)).takeLast(MAX_HISTORY)
													true
												} else false
											}
											MotionEvent.ACTION_MOVE -> if (dragging) {
												val ll = m.projection.fromScreenLocation(PointF(ev.x, ev.y))
												features = featuresState.value.map { if (it.fid == target.fid) it.copy(coords = listOf(ll.longitude to ll.latitude)) else it }
												true
											} else false
											MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
												dragging = false
												m.uiSettings.setAllGesturesEnabled(true)
												m.uiSettings.isRotateGesturesEnabled = false
												m.uiSettings.isTiltGesturesEnabled = false
												dirty = true
												true
											} else false
											else -> false
										}
									}
								}
							},
						)

						// Encuadrar todo
						IconButton(
							onClick = {
								val b = boundsOf(features)
								if (b != null) map?.animateCamera(CameraUpdateFactory.newLatLngBounds(b, 80))
							},
							enabled = features.size > 1,
							modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
						) { Icon(Icons.Filled.CenterFocusStrong, "Encuadrar todo") }

						// Controles flotantes, como en la web
						Column(
							Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
							horizontalAlignment = Alignment.CenterHorizontally,
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							message?.let {
								Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
									Text(it, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
								}
							}
							Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shadowElevation = 2.dp) {
								Text(tool.hint, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
							}

							// Acciones del momento: terminar o cancelar el trazo, o editar / eliminar lo seleccionado
							if (showDraftActions || showSelectionActions) {
								Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
									Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
										if (showDraftActions) {
											val enough = draft.size >= if (tool == Tool.POLYGON) 3 else 2
											Button(onClick = { finishDraft() }, enabled = enough) {
												Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
												Text(" Terminar")
											}
											IconButton(onClick = { draft = emptyList(); tool = Tool.SELECT }) { Icon(Icons.Filled.Close, "Cancelar el trazo") }
										}
										if (showSelectionActions) {
											if (current?.kind == "text") {
												IconButton(onClick = { textFor = current.fid to null }) { Icon(Icons.Filled.Edit, "Editar texto") }
											}
											IconButton(onClick = {
												commit(features.filterNot { it.fid == current?.fid })
												selected = null
											}) { Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) }
										}
									}
								}
							}

							// Íconos para los puntos: se elige antes de colocar, o cambia el del punto seleccionado
							if (showEmojis) {
								Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 4.dp) {
									Row(
										Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(2.dp),
									) {
										val chosen = if (tool == Tool.POINT) activeEmoji else selectedPoint?.emoji
										fun pick(e: String?) {
											if (tool == Tool.POINT) activeEmoji = e
											else selectedPoint?.let { sp -> commit(features.map { if (it.fid == sp.fid) it.copy(emoji = e) else it }) }
										}
										Box(
											Modifier.size(40.dp).clip(CircleShape).background(if (chosen == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
												.clickable { pick(null) },
											contentAlignment = Alignment.Center,
										) { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF2563EB))) }
										POINT_EMOJIS.forEach { e ->
											Box(
												Modifier.size(40.dp).clip(CircleShape).background(if (chosen == e) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
													.clickable { pick(e) },
												contentAlignment = Alignment.Center,
											) { Text(e, fontSize = 22.sp) }
										}
										OutlinedTextField(
											customEmoji, { customEmoji = it.take(8) },
											modifier = Modifier.width(120.dp), singleLine = true, placeholder = { Text("Otro", fontSize = 13.sp) },
											keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
											keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
												if (customEmoji.isNotBlank()) {
													pick(customEmoji.trim())
													customEmoji = ""
												}
											}),
										)
									}
								}
							}

							// Barra de herramientas: íconos grandes y el deshacer
							Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
								Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
									tools.forEach { t ->
										val active = tool == t
										Column(
											Modifier.width(64.dp).clip(RoundedCornerShape(20.dp))
												.background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
												.clickable {
													draft = emptyList()
													tool = t
													if (t != Tool.SELECT) selected = null
												}
												.padding(vertical = 8.dp),
											horizontalAlignment = Alignment.CenterHorizontally,
										) {
											Icon(t.icon, t.label, Modifier.size(26.dp), tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
											Text(
												t.label, fontSize = 11.sp, maxLines = 1,
												color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
											)
										}
									}
									val canUndo = draft.isNotEmpty() || history.isNotEmpty()
									IconButton(onClick = { undo() }, enabled = canUndo, modifier = Modifier.size(48.dp)) {
										Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer", Modifier.size(26.dp))
									}
								}
							}
						}
					}
				}
			}
		}
	}

	if (askDiscard) {
		AlertDialog(
			onDismissRequest = { askDiscard = false },
			title = { Text("¿Salir sin guardar?") },
			text = { Text("Hiciste cambios en el mapa que no se han guardado.") },
			confirmButton = { TextButton(onClick = { askDiscard = false; onClose(false) }) { Text("Salir sin guardar") } },
			dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Seguir editando") } },
		)
	}

	textFor?.let { (fid, newPos) ->
		var value by remember(fid, newPos) { mutableStateOf(features.firstOrNull { it.fid == fid }?.text ?: "") }
		AlertDialog(
			onDismissRequest = { textFor = null },
			title = { Text(if (fid == null) "Agregar texto" else "Editar texto") },
			text = { OutlinedTextField(value, { value = it.take(200) }, label = { Text("Texto") }, singleLine = true) },
			confirmButton = {
				TextButton(enabled = value.isNotBlank(), onClick = {
					if (fid == null && newPos != null) {
						addFeature(Feat(UUID.randomUUID().toString(), "Point", listOf(newPos), kind = "text", text = value.trim()))
					} else {
						commit(features.map { if (it.fid == fid) it.copy(text = value.trim()) else it })
					}
					textFor = null
				}) { Text("Aceptar") }
			},
			dismissButton = { TextButton(onClick = { textFor = null }) { Text("Cancelar") } },
		)
	}
}
