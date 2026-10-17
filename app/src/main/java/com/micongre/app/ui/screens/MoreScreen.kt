package com.micongre.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.micongre.app.data.Me
import com.micongre.app.ui.AppViewModel
import com.micongre.app.ui.Dest
import com.micongre.app.ui.PageTitle
import com.micongre.app.ui.RowList
import com.micongre.app.ui.SectionLabel
import com.micongre.app.ui.theme.ACCENTS

/** Menú "Más": solo los módulos a los que la persona tiene acceso por privilegios y nombramientos, más su perfil. */
@Composable
fun MoreScreen(vm: AppViewModel, me: Me, onOpen: (Dest) -> Unit, onLogout: () -> Unit) {
	val modules = vm.shortcutOptions(me)
	val accountCount = vm.accounts.collectAsState().value.size
	Box(Modifier.fillMaxSize()) {
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
		PageTitle("Más")
		SectionLabel("Módulos")
		// Predicación: el calendario lo ve cualquier persona (solo lo que le toca; quien administra el módulo, todo).
		RowList(
			listOf<@Composable () -> Unit>({ MenuRow("Predicación") { onOpen(Dest.Preaching) } }) +
				(if (canManageTerritories(me)) listOf<@Composable () -> Unit>({ MenuRow("Territorios") { onOpen(Dest.Territories) } }) else emptyList()) +
				modules.map { m -> { MenuRow(m.label) { onOpen(m.dest) } } })
		SectionLabel("Cuenta")
		RowList(
			listOf(
				{ MenuRow("Cuentas", subtitle = if (accountCount > 1) "$accountCount cuentas en este teléfono" else "Agregar a alguien de tu familia") { onOpen(Dest.Accounts) } },
				{ MenuRow("Mi perfil", subtitle = me.fullName) { onOpen(Dest.Profile) } },
				{ Text("Cerrar sesión de esta cuenta", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().clickable(onClick = onLogout)) },
			),
		)
	}
	// Versión de la app, para saber cuál usa cada persona.
	Text(
		"Mi Congre · versión ${com.micongre.app.BuildConfig.VERSION_NAME}",
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
	)
	}
}

@Composable
private fun MenuRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
	Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			Text(title, fontWeight = FontWeight.Medium)
			if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}

/** Mi perfil: solo la apariencia (modo oscuro y color) y el botón personalizable de la barra inferior. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: AppViewModel, me: Me, onBack: () -> Unit) {
	val accent by vm.accent.collectAsState()
	val dark by vm.darkMode.collectAsState()
	val syncMinutes by vm.syncMinutes.collectAsState()
	val fcmActive by vm.fcmActive.collectAsState()
	val options = vm.shortcutOptions(me)
	var message by remember { mutableStateOf<String?>(null) }
	var expanded by remember { mutableStateOf(false) }
	val current = vm.activeShortcut(me)
	var deleting by remember { mutableStateOf(false) }
	var deletePassword by remember { mutableStateOf("") }
	var deleteError by remember { mutableStateOf<String?>(null) }
	var deleteBusy by remember { mutableStateOf(false) }

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
			PageTitle("Mi perfil", me.fullName)
		}

		SectionLabel("Apariencia")
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			listOf("system" to "Sistema", "light" to "Claro", "dark" to "Oscuro").forEach { (key, label) ->
				FilterChip(selected = dark == key, onClick = { vm.setDarkMode(key) }, label = { Text(label) })
			}
		}
		Text("Color", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
			ACCENTS.forEach { a ->
				Box(
					Modifier
						.size(40.dp)
						.clip(CircleShape)
						.background(Color(a.swatch))
						.border(if (accent == a.key) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
						.selectable(selected = accent == a.key, onClick = { vm.setAccent(a.key) }),
					contentAlignment = Alignment.Center,
				) { if (accent == a.key) Icon(Icons.Filled.Check, a.label, tint = Color.White) }
			}
		}
		Text(ACCENTS.firstOrNull { it.key == accent }?.label ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

		SectionLabel("Notificaciones")
		if (fcmActive) {
			Text("Las notificaciones llegan en tiempo real (push).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		} else {
		Text("Revisar avisos y asignaciones nuevas cada", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
		Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			listOf(1 to "1 min", 5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min", 60 to "1 h", 120 to "2 h", 360 to "6 h").forEach { (minutes, label) ->
				FilterChip(selected = syncMinutes == minutes, onClick = { vm.setSyncMinutes(minutes) }, label = { Text(label) })
			}
		}
		Text("Un intervalo más corto avisa antes, pero gasta más batería. Por debajo de 15 minutos, Android puede retrasar la revisión cuando el teléfono está en reposo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
		}

		SectionLabel("Acceso directo")
		ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
			OutlinedTextField(
				value = current?.label ?: "Ninguno",
				onValueChange = {},
				readOnly = true,
				label = { Text("Módulo en la barra inferior") },
				trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
				modifier = Modifier.menuAnchor().fillMaxWidth(),
			)
			ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
				DropdownMenuItem(text = { Text("Ninguno") }, onClick = {
					expanded = false
					vm.setShortcut(null) { ok -> message = if (ok) "Quitado de la barra de abajo." else "No se pudo guardar." }
				})
				options.forEach { o ->
					DropdownMenuItem(text = { Text(o.label) }, onClick = {
						expanded = false
						vm.setShortcut(o.href) { ok -> message = if (ok) "Listo: aparece en la barra de abajo." else "No se pudo guardar." }
					})
				}
			}
		}
		message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }

		SectionLabel("Eliminar cuenta")
		Text(
			"Elimina tu usuario y contraseña y cierra tus sesiones y los avisos de este teléfono. Tu ficha de publicador, tus asignaciones y tus informes siguen en la congregación; los administra quien lleva el sitio y puedes pedirle que también los borre.",
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		OutlinedButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Eliminar mi cuenta", color = MaterialTheme.colorScheme.error) }
	}

	if (deleting) {
		fun closeDialog() {
			if (deleteBusy) return
			deleting = false
			deletePassword = ""
			deleteError = null
		}
		AlertDialog(
			onDismissRequest = ::closeDialog,
			title = { Text("¿Eliminar tu cuenta?") },
			text = {
				Column {
					Text("Ya no podrás entrar con tu usuario y contraseña actuales. Escribe tu contraseña para confirmar.")
					OutlinedTextField(
						value = deletePassword,
						onValueChange = { deletePassword = it; deleteError = null },
						label = { Text("Contraseña") },
						singleLine = true,
						enabled = !deleteBusy,
						visualTransformation = PasswordVisualTransformation(),
						modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
					)
					deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
				}
			},
			confirmButton = {
				TextButton(
					enabled = deletePassword.isNotEmpty() && !deleteBusy,
					onClick = {
						deleteBusy = true
						vm.deleteMyAccount(deletePassword) { message ->
							deleteBusy = false
							deleteError = message
						}
					},
				) { Text(if (deleteBusy) "Eliminando..." else "Eliminar mi cuenta", color = MaterialTheme.colorScheme.error) }
			},
			dismissButton = { TextButton(onClick = ::closeDialog, enabled = !deleteBusy) { Text("Cancelar") } },
		)
	}
}
