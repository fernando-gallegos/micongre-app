package com.micongre.app.ui

import android.view.autofill.AutofillManager
import androidx.compose.foundation.Image
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
fun LoginScreen(error: String?, server: String?, onLogin: (String, String) -> Unit, onChangeServer: () -> Unit) {
	var username by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }
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
			visualTransformation = PasswordVisualTransformation(),
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
	}
}
