package com.micongre.app.ui

import android.view.autofill.AutofillManager
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
	error: String?,
	server: String?,
	onLogin: (String, String) -> Unit,
	onChangeServer: () -> Unit,
	onForgot: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
) {
	var username by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }
	var showPassword by remember { mutableStateOf(false) }
	var forgotOpen by remember { mutableStateOf(false) }
	var submitted by remember { mutableStateOf(false) }

	// Autocompletado (Google, 1Password, Bitwarden...): los campos se declaran como usuario y contraseña; al entrar con éxito
	// (la pantalla sale) se confirma para que el gestor ofrezca guardar las credenciales, y si no, se descarta.
	val context = LocalContext.current
	DisposableEffect(Unit) {
		onDispose {
			val autofill = context.getSystemService(AutofillManager::class.java)
			if (submitted) autofill?.commit() else autofill?.cancel()
		}
	}

	Column(
		modifier = Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.Center,
	) {
		Image(painterResource(com.micongre.app.R.drawable.ic_logo), contentDescription = "Mi Congre", modifier = Modifier.size(64.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)))
		Text("Mi Congre", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
		Text("Inicia sesión con tu usuario de congregación.", style = MaterialTheme.typography.bodyMedium)

		OutlinedTextField(
			value = username,
			onValueChange = { username = it },
			label = { Text("Usuario") },
			modifier = Modifier.fillMaxWidth().padding(top = 24.dp).semantics { contentType = ContentType.Username },
			singleLine = true,
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
		)
		OutlinedTextField(
			value = password,
			onValueChange = { password = it },
			label = { Text("Contraseña") },
			visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
			// Ojo para ver lo que se escribe.
			trailingIcon = {
				androidx.compose.material3.IconButton(onClick = { showPassword = !showPassword }) {
					androidx.compose.material3.Icon(
						if (showPassword) androidx.compose.material.icons.Icons.Filled.VisibilityOff else androidx.compose.material.icons.Icons.Filled.Visibility,
						if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
					)
				}
			},
			modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics { contentType = ContentType.Password },
			singleLine = true,
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
			keyboardActions = KeyboardActions(onDone = {
				if (username.isNotBlank() && password.isNotBlank()) { submitted = true; onLogin(username, password) }
			}),
		)

		if (server != null) {
			Text(
				"Sitio: ${server.removePrefix("https://").removePrefix("http://").trimEnd('/')}",
				style = MaterialTheme.typography.bodySmall,
				modifier = Modifier.padding(top = 12.dp),
			)
			androidx.compose.material3.TextButton(onClick = onChangeServer) { Text("Cambiar sitio") }
		}

		if (error != null) {
			Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
		}

		Button(
			onClick = { submitted = true; onLogin(username, password) },
			modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
			enabled = username.isNotBlank() && password.isNotBlank(),
		) {
			Text("Ingresar")
		}
		if (server != null) {
			androidx.compose.material3.TextButton(onClick = { forgotOpen = true }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
				Text("¿Olvidaste tu contraseña?")
			}
		}
	}

	if (forgotOpen) ForgotPasswordDialog(initial = username, onSend = onForgot, onDismiss = { forgotOpen = false })
}

/** Igual que en la web: avisa a quien puede generar un enlace nuevo; la respuesta es la misma exista o no la cuenta. */
@Composable
private fun ForgotPasswordDialog(initial: String, onSend: (String, (Boolean) -> Unit) -> Unit, onDismiss: () -> Unit) {
	var identifier by remember { mutableStateOf(initial) }
	var state by remember { mutableStateOf("idle") }
	androidx.compose.material3.AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(if (state == "sent") "Listo, pedimos ayuda por ti" else "¿Olvidaste tu contraseña?") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				if (state == "sent") {
					Text("Si los datos coinciden con una persona de la congregación, le avisamos a tu superintendente de grupo y a quien administra las cuentas. Te enviarán un enlace nuevo (por WhatsApp o en persona); al abrirlo verás tu usuario y una contraseña nueva.")
				} else {
					Text("Escribe tu usuario o tu nombre completo. Avisaremos a tu superintendente de grupo y a quien administra las cuentas para que te envíen un enlace nuevo.")
					OutlinedTextField(identifier, { identifier = it.take(120) }, label = { Text("Usuario o nombre completo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
					if (state == "error") Text("No se pudo enviar. Revisa tu conexión e intenta de nuevo.", color = MaterialTheme.colorScheme.error)
				}
			}
		},
		confirmButton = {
			if (state == "sent") {
				androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Entendido") }
			} else {
				androidx.compose.material3.TextButton(
					enabled = state != "sending" && identifier.trim().length >= 2,
					onClick = {
						state = "sending"
						onSend(identifier) { ok -> state = if (ok) "sent" else "error" }
					},
				) { Text(if (state == "sending") "Enviando…" else "Pedir ayuda") }
			}
		},
		dismissButton = { if (state != "sent") androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancelar") } },
	)
}
