package com.micongre.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.micongre.app.BuildConfig

/** Primer inicio: cada congregación tiene su propio sitio, así que se pide la dirección de "Mi Congre en la web". */
@Composable
fun ServerScreen(current: String?, onSave: (String, (String?) -> Unit) -> Unit, onCancel: (() -> Unit)?) {
	var text by remember { mutableStateOf(current?.trimEnd('/') ?: if (BuildConfig.DEBUG) "http://10.0.2.2:3000" else "") }
	var error by remember { mutableStateOf<String?>(null) }
	var checking by remember { mutableStateOf(false) }

	fun submit(value: String = text) {
		if (checking || value.isBlank()) return
		checking = true
		error = null
		onSave(value) { message ->
			checking = false
			error = message
		}
	}

	Column(
		modifier = Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.Center,
	) {
		Image(painterResource(com.micongre.app.R.drawable.ic_logo), contentDescription = "Mi Congre", modifier = Modifier.size(64.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)))
		Text("Mi Congre", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
		Text(
			"Escanea el código QR de tu congregación o escribe la dirección de Mi Congre en la web. Te la comparte quien administra el sitio.",
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(top = 4.dp),
		)
		OutlinedTextField(
			value = text,
			onValueChange = { text = it; error = null },
			label = { Text("Mi Congre en la web") },
			placeholder = { Text("https://micongre.ejemplo.workers.dev") },
			singleLine = true,
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
			isError = error != null,
			modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
		)
		if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
		Button(
			onClick = { submit() },
			enabled = text.isNotBlank() && !checking,
			modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
		) { Text(if (checking) "Comprobando..." else "Continuar") }
		val context = LocalContext.current
		OutlinedButton(
			onClick = {
				val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
				GmsBarcodeScanning.getClient(context, options).startScan()
					.addOnSuccessListener { barcode ->
						val value = barcode.rawValue.orEmpty()
						text = value
						submit(value)
					}
					.addOnFailureListener { error = "No se pudo abrir el escáner. Escribe la dirección a mano." }
			},
			enabled = !checking,
			modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
		) { Text("Escanear código QR") }
		if (onCancel != null) {
			TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
		}
	}
}
