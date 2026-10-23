package com.micongre.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.micongre.app.data.GroupMember
import com.micongre.app.data.PanelGroup
import com.micongre.app.data.contactLinks
import com.micongre.app.ui.AppCard
import com.micongre.app.ui.SectionLabel
import com.micongre.app.ui.parseColor
import com.micongre.app.ui.WhatsAppGlyph

private val APPOINTMENT_SHORT = mapOf("elder" to "AC", "ministerial_servant" to "SM", "regular_pioneer" to "PR", "auxiliary_pioneer" to "PA")

/** «Mi grupo de servicio» (igual que en la web): superintendente, auxiliar y, al abrir, los compañeros. Tocar a quien tiene número muestra llamar, SMS y WhatsApp. */
@Composable
fun MyGroupSection(group: PanelGroup?) {
	SectionLabel("Mi grupo de servicio")
	AppCard {
		if (group == null) {
			Text("Todavía no tienes un grupo asignado.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
			return@AppCard
		}
		var expanded by remember { mutableStateOf(false) }
		var openMember by remember { mutableStateOf<String?>(null) }
		Column {
			Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
					Text(group.name, fontWeight = FontWeight.SemiBold, color = group.color?.let { parseColor(it) } ?: MaterialTheme.colorScheme.primary)
					Text("Superintendente: ${group.overseerName ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
					Text("Auxiliar: ${group.assistantName ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, if (expanded) "Ocultar compañeros" else "Ver compañeros")
			}
			AnimatedVisibility(expanded) {
				Column {
					HorizontalDivider(color = MaterialTheme.colorScheme.outline)
					Text(
						"COMPAÑEROS DE GRUPO", Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
						style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					group.members.forEach { m ->
						MemberRow(m, open = openMember == m.id) { openMember = if (openMember == m.id) null else m.id }
					}
				}
			}
		}
	}
}

@Composable
private fun MemberRow(member: GroupMember, open: Boolean, onToggle: () -> Unit) {
	val links = contactLinks(member.phone)
	val context = LocalContext.current
	Column {
		// Sin número de contacto la fila no se abre.
		Row(
			Modifier.fillMaxWidth().let { if (links != null) it.clickable(onClick = onToggle) else it }.padding(horizontal = 16.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Text(member.fullName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
			val badges = member.activeAppointmentTypes.mapNotNull { APPOINTMENT_SHORT[it] }
			if (badges.isNotEmpty()) Text(badges.joinToString(" "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
			if (links != null) Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		if (links != null) AnimatedVisibility(open) {
			Row(Modifier.padding(start = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
				ContactButton(Icons.Filled.Call, "Llamar a ${member.fullName} (${links.display})", MaterialTheme.colorScheme.primary) {
					open(context, Intent(Intent.ACTION_DIAL, Uri.parse(links.tel)))
				}
				ContactButton(Icons.AutoMirrored.Filled.Message, "Enviar SMS a ${member.fullName}", Color(0xFF3D5AA3)) {
					open(context, Intent(Intent.ACTION_SENDTO, Uri.parse(links.sms)))
				}
				ContactButton(WhatsAppGlyph, "WhatsApp a ${member.fullName}", Color(0xFF25D366)) {
					open(context, Intent(Intent.ACTION_VIEW, Uri.parse(links.whatsapp)))
				}
			}
		}
	}
}

@Composable
private fun ContactButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
	Box(Modifier.size(46.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
		Icon(icon, label, Modifier.size(24.dp), tint = Color.White)
	}
}

private fun open(context: Context, intent: Intent) {
	runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
