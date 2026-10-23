package com.micongre.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.micongre.app.push.EXTRA_ACCOUNT_ID
import com.micongre.app.ui.ActivationScreen
import com.micongre.app.ui.AppViewModel
import com.micongre.app.ui.LoginScreen
import com.micongre.app.ui.MainShell
import com.micongre.app.ui.ServerScreen
import com.micongre.app.ui.authenticateThen
import com.micongre.app.ui.theme.MiCongreTheme

// FragmentActivity (en vez de ComponentActivity) para poder pedir la huella o el PIN del teléfono al cambiar de cuenta.
class MainActivity : FragmentActivity() {
	/** Cuenta a la que pertenece la notificación que abrió la app: al tocarla se cambia a esa cuenta. */
	private var pendingAccount by mutableStateOf<String?>(null)

	/** Enlace https://<sitio>/activar/<token> con el que Android abrió la app (App Link). */
	private var pendingLink by mutableStateOf<Uri?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		pendingAccount = intent?.getStringExtra(EXTRA_ACCOUNT_ID)
		// Solo en el primer arranque: al girar la pantalla el mismo enlace (ya canjeado) no debe abrirse otra vez.
		if (savedInstanceState == null) pendingLink = intent?.data

		val container = (application as MicongreApplication).container

		setContent {
			val viewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container))
			val accent by viewModel.accent.collectAsState()
			val darkMode by viewModel.darkMode.collectAsState()
			MiCongreTheme(accentKey = accent, darkMode = darkMode) {
				val loggedIn by viewModel.loggedIn.collectAsState()
				val loginError by viewModel.loginError.collectAsState()
				val me by viewModel.me.collectAsState()
				val serverUrl by viewModel.serverUrl.collectAsState()
				val serverChecked by viewModel.serverChecked.collectAsState()
				val editingServer by viewModel.editingServer.collectAsState()
				val activeId by viewModel.activeId.collectAsState()
				val lock by viewModel.lockSwitch.collectAsState()
				val activation by viewModel.activation.collectAsState()

				LaunchedEffect(pendingLink) {
					pendingLink?.let { viewModel.openActivationLink(it) }
					pendingLink = null
				}

				// Notificación de otra cuenta del teléfono: se cambia a ella (con huella o PIN si está activado).
				LaunchedEffect(pendingAccount, loggedIn, activeId) {
					val target = pendingAccount
					if (target != null && loggedIn != null && serverChecked) {
						pendingAccount = null
						if (target != activeId) {
							if (lock) authenticateThen(this@MainActivity, "Cambiar de cuenta") { viewModel.switchTo(target) } else viewModel.switchTo(target)
						}
					}
				}

				Surface(Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
					val user = me
					when {
						activation != null -> ActivationScreen(viewModel, activation!!, onClose = viewModel::closeActivation)
						!serverChecked -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
						serverUrl == null || editingServer -> ServerScreen(
							current = serverUrl,
							onSave = viewModel::saveServer,
							onCancel = if (serverUrl != null) viewModel::cancelChangeServer else null,
						)
						loggedIn == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
						loggedIn == false || user == null -> LoginScreen(
							error = loginError,
							server = serverUrl,
							onLogin = viewModel::login,
							onChangeServer = viewModel::changeServer,
							onForgot = viewModel::requestPasswordHelp,
						)
						else -> MainShell(viewModel, container, user)
					}
				}
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		pendingAccount = intent.getStringExtra(EXTRA_ACCOUNT_ID)
		pendingLink = intent.data
	}
}
