package com.micongre.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.micongre.app.AppContainer
import com.micongre.app.data.MidweekAssignment
import com.micongre.app.data.MidweekDetail
import com.micongre.app.data.Me
import com.micongre.app.data.OperationalItem
import com.micongre.app.data.WeekendMeeting
import com.micongre.app.data.api
import com.micongre.app.data.isForbidden
import com.micongre.app.ui.AppCard
import com.micongre.app.ui.EmptyText
import com.micongre.app.ui.Fmt
import com.micongre.app.ui.Loading
import com.micongre.app.ui.PageTitle
import com.micongre.app.ui.rememberLoad
import com.micongre.app.ui.theme.AppColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val STATUS_LABEL = mapOf(
	"scheduled" to "Programada",
	"cancelled" to "Cancelada",
	"cancelled_assembly" to "Cancelada (asamblea)",
	"cancelled_memorial" to "Cancelada (conmemoración)",
	"adjusted_co_visit" to "Ajustada (visita SC)",
)

private val SECTION_LABELS = mapOf(
	"treasures_talk" to "Tesoros de la Biblia",
	"spiritual_gems" to "Busquemos perlas escondidas",
	"bible_reading" to "Lectura de la Biblia",
	"student_assignment" to "Seamos mejores maestros",
	"christian_living" to "Nuestra vida cristiana",
	"congregation_needs" to "Necesidades de la congregación",
	"cbs" to "Estudio bíblico de la congregación",
	"prayer" to "Oración",
)

/** Reuniones en solo lectura: una pestaña por reunión (según el acceso), con la semana elegida y las tareas operativas al final. */
@Composable
fun MeetingsScreen(container: AppContainer, me: Me) {
	// Las reuniones las ve cualquier persona con sesión, sin importar privilegios ni nombramientos.
	val tabs = listOf("midweek" to "Entre semana", "weekend" to "Fin de semana")
	// Sábado o domingo abre Fin de semana (si hay acceso); el resto de la semana, Entre semana.
	val isWeekend = LocalDate.now().dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }
	var tab by remember { mutableStateOf(if (isWeekend) tabs.indexOfFirst { it.first == "weekend" }.coerceAtLeast(0) else 0) }
	var weekStart by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
	if (tabs.isEmpty()) {
		EmptyText("No tienes acceso a las reuniones.", Modifier.padding(16.dp))
		return
	}
	Column(Modifier.fillMaxSize()) {
		if (tabs.size > 1) {
			TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
				tabs.forEachIndexed { i, (_, label) -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }) }
			}
		}
		Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
			WeekNav(weekStart, onPrev = { weekStart = weekStart.minusWeeks(1) }, onNext = { weekStart = weekStart.plusWeeks(1) }, onToday = { weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) })
			when (tabs[tab.coerceIn(0, tabs.lastIndex)].first) {
				"midweek" -> MidweekView(container, weekStart)
				else -> WeekendView(container, weekStart)
			}
		}
	}
}

/** Selector de semana como el de la web: una píldora de ancho completo con las flechas, el rango y "Hoy". */
@Composable
private fun WeekNav(start: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
	Row(
		Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).padding(4.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Semana anterior") }
		Text(
			Fmt.weekRange(start),
			Modifier.weight(1f),
			textAlign = androidx.compose.ui.text.style.TextAlign.Center,
			fontWeight = FontWeight.SemiBold,
			maxLines = 1,
		)
		IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Semana siguiente") }
		androidx.compose.material3.OutlinedButton(onClick = onToday, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) { Text("Hoy") }
	}
}

// --- Piezas de diseño compartidas ---

@Composable
private fun SectionBar(title: String, color: Color) {
	Row(Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(10.dp).clip(CircleShape).background(color))
		Text(title.uppercase(), color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
	}
}

@Composable
private fun Line(label: String, value: String?, soft: Color? = null) {
	Row(
		Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.Top,
	) {
		Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.45f))
		Text(value?.takeIf { it.isNotBlank() } ?: "Sin asignar", fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.55f))
	}
}

@Composable
private fun Tinted(soft: Color, content: @Composable () -> Unit) {
	Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(soft)) { Column { content() } }
}

@Composable
private fun PartRow(title: String, minutes: Int?, description: String?, lines: List<Pair<String, String?>>) {
	Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
		Text(
			title + (minutes?.let { " ($it mins.)" } ?: ""),
			fontWeight = FontWeight.SemiBold,
		)
		description?.takeIf { it.isNotBlank() }?.let {
			Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
		}
		lines.forEach { (label, value) ->
			Row(Modifier.padding(top = 4.dp)) {
				Text("$label: ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
				Text(value?.takeIf { it.isNotBlank() } ?: "Sin asignar", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
			}
		}
	}
}

// --- Entre semana ---

@Composable
private fun MidweekView(container: AppContainer, weekStart: LocalDate) {
	val from = weekStart.toString()
	val to = weekStart.plusDays(6).toString()
	val list = rememberLoad(from) { api { container.apiService.midweekList(from, to) } }
	val meeting = list.data?.items?.firstOrNull()
	val detail = rememberLoad(meeting?.id) {
		if (meeting == null) Result.success<MidweekDetail?>(null) else api { container.apiService.midweekDetail(meeting.id) }.map { it }
	}
	val ops = rememberLoad(meeting?.id) {
		if (meeting == null) Result.success(emptyList<OperationalItem>())
		else api { container.apiService.operationalView("midweek", meeting.id) }.map { it.items }
	}

	if (list.loading || detail.loading) return Loading(Modifier.fillMaxWidth().padding(48.dp))
	if (list.error?.isForbidden == true || detail.error?.isForbidden == true) return EmptyText("No tienes acceso a esta reunión.")
	val d = detail.data
	if (meeting == null || d == null) return EmptyText("No hay reunión programada para esta semana.")

	val m = d.meeting
	val x = AppColors.extras
	Column {
		PageTitle(Fmt.longDate(m.meetingDate), m.weekLabel)
		AppCard {
			Column {
				Line("Presidente", m.chairmanName)
				if (!d.skipOpeningPrayer) Line("Oración inicial", m.openingPrayerName)
				Line("Oración final", d.assignments.firstOrNull { it.sectionType == "prayer" }?.mainAssigneeName)
				Line("Estado", STATUS_LABEL[m.status] ?: m.status)
			}
		}

		// Cada canción va en su lugar del programa: inicial antes de Tesoros, intermedia en Nuestra vida cristiana y final al cierre.
		m.openingSongNumber?.let {
			Spacer8()
			SongCard(songText(it, m.openingSongTitle))
		}

		val parts = d.assignments.filter { it.sectionType != "prayer" }.sortedBy { it.sequenceOrder }
		fun groupOf(a: MidweekAssignment) = when (a.sectionType) {
			"treasures_talk", "spiritual_gems", "bible_reading" -> 0
			"student_assignment" -> 1
			else -> 2
		}
		val groups = listOf(
			Triple("Tesoros de la Biblia", x.treasures, x.treasuresSoft),
			Triple("Seamos mejores maestros", x.ministry, x.ministrySoft),
			Triple("Nuestra vida cristiana", x.living, x.livingSoft),
		)
		groups.forEachIndexed { gi, (title, color, soft) ->
			val rows = parts.filter { groupOf(it) == gi }
			if (rows.isEmpty() && gi != 1) return@forEachIndexed
			SectionBar(title, color)
			Tinted(soft) {
				if (gi == 2) m.middleSongNumber?.let {
					PartRow(songText(it, m.middleSongTitle), null, null, emptyList())
				}
				var cbsDone = false
				rows.forEach { a ->
					if (a.sectionType == "cbs") {
						if (cbsDone) return@forEach
						cbsDone = true
						val cbs = rows.filter { it.sectionType == "cbs" }
						val conductor = cbs.firstOrNull { it.sectionTitle == "Conductor" } ?: cbs.first()
						val reader = cbs.firstOrNull { it.sectionTitle == "Lector" } ?: cbs.getOrNull(1)
						PartRow(
							(conductor.partNumber?.let { "$it. " } ?: "") + "Estudio bíblico de la congregación",
							conductor.durationMinutes, conductor.description,
							listOf("Conductor" to conductor.mainAssigneeName, "Lector" to reader?.mainAssigneeName),
						)
					} else {
						val heading = partHeading(a)
						val lines = buildList {
							add("Asignado" to a.mainAssigneeName)
							if (a.sectionType == "student_assignment" && a.assistantAssigneeName != null) add("Ayudante" to a.assistantAssigneeName)
							if (m.hasAuxRoom && (a.sectionType == "student_assignment" || a.sectionType == "bible_reading")) add("Sala" to if (a.room == "auxiliary") "Auxiliar" else "Principal")
						}
						PartRow((a.partNumber?.let { "$it. " } ?: "") + heading, a.durationMinutes, a.description, lines)
					}
				}
				if (rows.isEmpty()) Text("Sin partes todavía.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
		m.closingSongNumber?.let {
			Spacer8()
			SongCard(songText(it, m.closingSongTitle))
		}
		OperationalSection(ops.data.orEmpty())
	}
}

@Composable
private fun Spacer8() = Box(Modifier.padding(top = 8.dp))

private fun partHeading(a: MidweekAssignment): String = when {
	a.sectionType == "living_custom" -> a.sectionTitle ?: "Asignación"
	a.sectionType in listOf("treasures_talk", "christian_living", "student_assignment") && !a.sectionTitle.isNullOrBlank() -> a.sectionTitle
	a.sectionType == "treasures_talk" -> "Discurso"
	else -> SECTION_LABELS[a.sectionType] ?: a.sectionType
}

// --- Fin de semana ---

@Composable
private fun WeekendView(container: AppContainer, weekStart: LocalDate) {
	val from = weekStart.toString()
	val to = weekStart.plusDays(6).toString()
	val list = rememberLoad(from) { api { container.apiService.weekendList(from, to) } }
	val meeting = list.data?.items?.firstOrNull()
	val detail = rememberLoad(meeting?.id) {
		if (meeting == null) Result.success<WeekendMeeting?>(null) else api { container.apiService.weekendDetail(meeting.id) }.map { it.meeting }
	}
	val ops = rememberLoad(meeting?.id) {
		if (meeting == null) Result.success(emptyList<OperationalItem>())
		else api { container.apiService.operationalView("weekend", meeting.id) }.map { it.items }
	}

	if (list.loading || detail.loading) return Loading(Modifier.fillMaxWidth().padding(48.dp))
	if (list.error?.isForbidden == true || detail.error?.isForbidden == true) return EmptyText("No tienes acceso a esta reunión.")
	val m = detail.data
	if (meeting == null || m == null) return EmptyText("No hay reunión programada para esta semana.")

	val x = AppColors.extras
	Column {
		PageTitle(Fmt.longDate(m.meetingDate))
		AppCard {
			Column {
				Line("Presidente", m.chairmanName)
				Line("Oración", m.prayerName)
				Line("Estado", STATUS_LABEL[m.status] ?: m.status)
			}
		}
		// Normalmente se empieza con la canción y el discurso; a veces con La Atalaya y el discurso va después.
		// Cada canción va con su parte (sin «inicial/final»): la del discurso, y las dos de La Atalaya (al empezar y al terminar).
		val talkPart: @Composable () -> Unit = {
			SectionBar("Discurso público", x.talk)
			Tinted(x.talkSoft) {
				val outline = listOfNotNull(m.talkOutlineNumber?.let { "$it." }, m.talkOutlineTitle).joinToString(" ")
				Line("Orador", listOfNotNull(m.speakerName, m.speakerCongregationName?.let { "($it)" }).joinToString(" "))
				Line("Discurso", outline)
			}
		}
		val talkSong: @Composable () -> Unit = {
			m.talkSongNumber?.let {
				Spacer8()
				SongCard(songText(it, m.talkSongTitle))
			}
		}
		val watchtowerPart: @Composable () -> Unit = {
			m.watchtowerSongNumber?.let {
				Spacer8()
				SongCard(songText(it, m.watchtowerSongTitle))
			}
			SectionBar("La Atalaya", x.watchtower)
			Tinted(x.watchtowerSoft) {
				m.watchtowerTitle?.takeIf { it.isNotBlank() }?.let { Line("Estudio", it) }
				Line("Conductor", m.watchtowerConductorName)
				Line("Lector", m.watchtowerReaderName)
			}
			m.closingSongNumber?.let {
				Spacer8()
				SongCard(songText(it, m.closingSongTitle))
			}
		}
		if (m.watchtowerFirst) {
			watchtowerPart()
			talkPart()
			talkSong()
		} else {
			talkSong()
			talkPart()
			watchtowerPart()
		}
		m.notes?.takeIf { it.isNotBlank() }?.let {
			SectionBar("Notas", MaterialTheme.colorScheme.onSurfaceVariant)
			AppCard { Text(it, Modifier.padding(16.dp)) }
		}
		OperationalSection(ops.data.orEmpty())
	}
}

/** «Canción 65: título» (o solo «Canción 65» si el catálogo no tiene el título). */
private fun songText(number: Int, title: String?): String = if (title.isNullOrBlank()) "Canción $number" else "Canción $number: $title"

@Composable
private fun SongCard(text: String) {
	AppCard { Text(text, Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold) }
}

// --- Acomodadores: una sección más al final del programa ---

@Composable
private fun OperationalSection(items: List<OperationalItem>) {
	if (items.isEmpty()) return
	val av = items.filter { it.privilegeId == "priv_audio_console" || it.privilegeId == "priv_video_zoom" }
	val ushers = items - av.toSet()
	SectionBar("Acomodadores", MaterialTheme.colorScheme.primary)
	AppCard {
		Column {
			fun label(i: OperationalItem) = i.privilegeName + (i.position?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
			if (av.isNotEmpty()) {
				Text("Audio / Video", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
				av.forEach { Line(label(it), it.assignedUserName) }
			}
			if (ushers.isNotEmpty()) {
				Text("Acomodadores", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
				ushers.forEach { Line(label(it), it.assignedUserName) }
			}
		}
	}
}
