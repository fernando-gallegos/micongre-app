package com.micongre.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.micongre.app.data.AccountInfo
import com.micongre.app.ui.AppViewModel
import com.micongre.app.ui.Badge
import com.micongre.app.ui.PageTitle
import com.micongre.app.ui.RowList
import com.micongre.app.ui.SectionLabel
import com.micongre.app.ui.authenticateThen
import com.micongre.app.ui.findFragmentActivity
import com.micongre.app.ui.theme.AppColors

private fun host(server: String) = server.removePrefix("https://").removePrefix("http://").trimEnd('/')
private fun initials(name: String) = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }

/** Cuentas guardadas en este teléfono: cambiar de una a otra, agregar más o cerrar una. Todas reciben sus notificaciones. */
@Composable
fun AccountsScreen(vm: AppViewModel, onAdd: () -> Unit) {
	val accounts by vm.accounts.collectAsState()
	val activeId by vm.activeId.collectAsState()
	val lock by vm.lockSwitch.collectAsState()
	val activity = LocalContext.current.findFragmentActivity()
	var closing by remember { mutableStateOf<AccountInfo?>(null) }

	// Para cambiar de cuenta se pide la huella o el PIN del teléfono (si está activado): el celular puede ser de toda la familia.
	fun requestSwitch(account: AccountInfo) {
		if (account.id == activeId) return
		if (lock && activity != null) authenticateThen(activity, "Cambiar de cuenta", account.name) { vm.switchTo(account.id) }
		else vm.switchTo(account.id)
	}

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
		PageTitle("Cuentas", "Todas las cuentas guardadas reciben sus notificaciones en este teléfono.")

		SectionLabel("En este teléfono")
		RowList(accounts.map { a ->
			{
				Row(Modifier.fillMaxWidth().clickable { requestSwitch(a) }, verticalAlignment = Alignment.CenterVertically) {
					Box(
						Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
						contentAlignment = Alignment.Center,
					) { Text(initials(a.name), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold) }
					Column(Modifier.weight(1f).padding(start = 12.dp)) {
						Text(a.name, fontWeight = FontWeight.SemiBold)
						Text(host(a.server), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
					when {
						a.expired -> Badge("Sesión vencida", AppColors.extras.warn, AppColors.extras.warnSoft)
						a.id == activeId -> Row(verticalAlignment = Alignment.CenterVertically) {
							Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
							Text(" Activa", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
						}
					}
					IconButton(onClick = { closing = a }) { Icon(Icons.Filled.Close, "Cerrar la sesión de ${a.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
				}
			}
		})

		Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp), shape = CircleShape) { Text("Agregar cuenta") }

		SectionLabel("Seguridad")
		RowList(
			listOf({
				Row(verticalAlignment = Alignment.CenterVertically) {
					Column(Modifier.weight(1f)) {
						Text("Pedir huella o PIN al cambiar de cuenta", fontWeight = FontWeight.Medium)
						Text(
							"Recomendado si el teléfono lo usa toda la familia: nadie cambia a otra cuenta sin desbloquear el teléfono.",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					Switch(checked = lock, onCheckedChange = { enabled ->
						// Para apagarlo también se pide desbloquear el teléfono.
						if (!enabled && activity != null) authenticateThen(activity, "Desactivar el bloqueo") { vm.setLockSwitch(false) } else vm.setLockSwitch(enabled)
					})
				}
			}),
		)
		Text(
			"Las notificaciones de todas las cuentas aparecen también en la pantalla de bloqueo, con el nombre de la persona a la que van.",
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = 10.dp, start = 4.dp),
		)
	}

	closing?.let { a ->
		AlertDialog(
			onDismissRequest = { closing = null },
			title = { Text("¿Cerrar la sesión de ${a.name}?") },
			text = { Text("Este teléfono dejará de recibir los avisos de esa cuenta. Las demás cuentas siguen igual.") },
			confirmButton = { TextButton(onClick = { closing = null; vm.removeAccount(a.id) }) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) } },
			dismissButton = { TextButton(onClick = { closing = null }) { Text("Cancelar") } },
		)
	}
}

/** Agregar otra cuenta (de la misma congregación o de otra). */
@Composable
fun AddAccountScreen(vm: AppViewModel, defaultServer: String?, onBack: () -> Unit) {
	var server by remember { mutableStateOf(defaultServer?.trimEnd('/') ?: "") }
	var user by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }
	var error by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
		PageTitle("Agregar cuenta", "Inicia sesión con otra persona de tu familia. La cuenta actual no se cierra.")
		OutlinedTextField(
			server, { server = it; error = null },
			label = { Text("Mi Congre en la web") }, singleLine = true,
			keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
			modifier = Modifier.fillMaxWidth(),
		)
		// Escanear el QR de la congregación (el mismo que se usa en el primer inicio) en vez de escribir la dirección.
		val context = LocalContext.current
		OutlinedButton(
			onClick = {
				val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
				GmsBarcodeScanning.getClient(context, options).startScan()
					.addOnSuccessListener { server = it.rawValue.orEmpty().trim(); error = null }
					.addOnFailureListener { error = "No se pudo abrir el escáner. Escribe la dirección a mano." }
			},
			modifier = Modifier.fillMaxWidth().height(48.dp),
			shape = CircleShape,
		) { Text("Escanear código QR") }
		OutlinedTextField(user, { user = it; error = null }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
		OutlinedTextField(
			password, { password = it; error = null },
			label = { Text("Contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
			modifier = Modifier.fillMaxWidth(),
		)
		error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
		Button(
			enabled = !busy && server.isNotBlank() && user.isNotBlank() && password.isNotBlank(),
			onClick = {
				busy = true
				vm.addAccount(server, user, password) { message ->
					busy = false
					error = message
				}
			},
			modifier = Modifier.fillMaxWidth().height(52.dp),
			shape = CircleShape,
		) { Text(if (busy) "Comprobando..." else "Agregar") }
		OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text("Cancelar") }
	}
}
