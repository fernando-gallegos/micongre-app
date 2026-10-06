package com.micongre.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.micongre.app.data.ActivatePreviewResponse

private sealed interface ActivationState {
	data object Loading : ActivationState
	data class Error(val message: String) : ActivationState
	data class Ready(val preview: ActivatePreviewResponse) : ActivationState
	data class Done(val username: String, val password: String) : ActivationState
}

/**
 * Enlace de activación (/activar/<token>) abierto en la app. Mismo contrato que la página web
 * (docs/mobile-activation-protocol.md): GET precarga el usuario y la contraseña sugeridos, POST canjea el enlace y deja la
 * sesión iniciada.
 */
@Composable
fun ActivationScreen(vm: AppViewModel, link: ActivationLink, onClose: () -> Unit) {
	var state by remember(link) { mutableStateOf<ActivationState>(ActivationState.Loading) }
	var username by remember(link) { mutableStateOf("") }
	var password by remember(link) { mutableStateOf("") }
	var error by remember(link) { mutableStateOf<String?>(null) }
	var busy by remember(link) { mutableStateOf(false) }

	LaunchedEffect(link) {
		vm.previewActivation(link)
			.onSuccess {
				username = it.suggestedUsername
				password = it.suggestedPassword
				state = ActivationState.Ready(it)
			}
			.onFailure {
				state = ActivationState.Error(
					when (it.message?.substringAfterLast('_')) {
						"410" -> "Este enlace ya se usó o venció. Pide uno nuevo a un administrador."
						"400" -> "Este enlace no es válido."
						else -> "No se pudo conectar. Revisa tu conexión e intenta de nuevo."
					},
				)
			}
	}

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.Center,
	) {
		Image(painterResource(com.micongre.app.R.drawable.ic_logo), contentDescription = "Mi Congre", modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
		Text("Mi Congre", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))

		when (val s = state) {
			ActivationState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

			is ActivationState.Error -> {
				Text("No se pudo activar", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
				Text(s.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
				TextButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) { Text("Cerrar") }
			}

			is ActivationState.Ready -> {
				Text("¡Hola, ${s.preview.fullName}!", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
				Text(
					if (s.preview.usernameEditable) "Te sugerimos un usuario y una contraseña: puedes cambiarlos antes de continuar."
					else "Elige una nueva contraseña para tu cuenta. Tu usuario no cambia.",
					style = MaterialTheme.typography.bodyMedium,
					modifier = Modifier.padding(top = 8.dp),
				)
				OutlinedTextField(
					username, { username = it; error = null },
					label = { Text("Usuario") }, singleLine = true, enabled = s.preview.usernameEditable && !busy,
					modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
				)
				OutlinedTextField(
					password, { password = it; error = null },
					label = { Text("Contraseña") }, singleLine = true, enabled = !busy,
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
					modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
				)
				error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
				Button(
					enabled = !busy && username.isNotBlank() && password.length >= 6,
					onClick = {
						busy = true
						error = null
						vm.confirmActivation(link, username.trim(), password) { message ->
							busy = false
							if (message == null) state = ActivationState.Done(username.trim(), password) else error = message
						}
					},
					modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
				) { Text(if (busy) "Activando..." else "Continuar") }
				TextButton(onClick = onClose, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
			}

			is ActivationState.Done -> {
				Text("¡Bienvenido!", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
				Text("Guarda estos datos: los necesitarás si vuelves a entrar desde otro dispositivo.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
				Text("Usuario", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 20.dp))
				Text(s.username, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
				Text("Contraseña", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
				Text(s.password, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
				Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Entrar") }
			}
		}
	}
}
