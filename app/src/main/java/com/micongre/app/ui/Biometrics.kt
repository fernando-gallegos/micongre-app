package com.micongre.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun Context.findFragmentActivity(): FragmentActivity? {
	var current: Context? = this
	while (current is ContextWrapper) {
		if (current is FragmentActivity) return current
		current = current.baseContext
	}
	return null
}

/**
 * Pide la huella, el rostro o el PIN/patrón del teléfono antes de seguir (por ejemplo, para cambiar de cuenta en un celular
 * que comparte toda la familia). Si el teléfono no tiene un bloqueo de pantalla configurado (o es muy antiguo para pedirlo),
 * sigue sin pedir nada.
 */
fun authenticateThen(activity: FragmentActivity, title: String, subtitle: String? = null, onSuccess: () -> Unit) {
	val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
	if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
		onSuccess()
		return
	}
	val prompt = BiometricPrompt(
		activity,
		ContextCompat.getMainExecutor(activity),
		object : BiometricPrompt.AuthenticationCallback() {
			override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
		},
	)
	prompt.authenticate(
		BiometricPrompt.PromptInfo.Builder()
			.setTitle(title)
			.apply { if (subtitle != null) setSubtitle(subtitle) }
			.setAllowedAuthenticators(authenticators)
			.build(),
	)
}
