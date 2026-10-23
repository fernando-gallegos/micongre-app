package com.micongre.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.micongre.app.AppContainer
import com.micongre.app.data.Me
import com.micongre.app.ui.screens.MeetingsScreen
import com.micongre.app.ui.screens.MoreScreen
import com.micongre.app.ui.screens.NotificationsScreen
import com.micongre.app.ui.screens.PanelScreen
import com.micongre.app.ui.screens.ProfileScreen
import com.micongre.app.ui.screens.PublisherDetailScreen
import com.micongre.app.ui.screens.PublishersScreen
import com.micongre.app.ui.screens.ReportsScreen

private fun iconFor(dest: Dest): ImageVector = when (dest) {
	is Dest.Publishers -> Icons.Filled.Groups
	is Dest.Meetings -> Icons.Filled.CalendarMonth
	is Dest.Reports -> Icons.Filled.Assessment
	else -> Icons.Filled.Apps
}

/** Barra inferior de la app: Mi Panel, Reuniones, un botón configurable (opcional), Avisos y Más. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainShell(vm: AppViewModel, container: AppContainer, me: Me) {
	val refresh = remember { RefreshController() }
	androidx.compose.runtime.CompositionLocalProvider(LocalRefresh provides refresh) { MainShellContent(vm, container, me, refresh) }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MainShellContent(vm: AppViewModel, container: AppContainer, me: Me, refresh: RefreshController) {
	// Android 13+: pedir el permiso de notificaciones una vez, para poder avisar de asignaciones y avisos nuevos.
	val context = androidx.compose.ui.platform.LocalContext.current
	val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
	androidx.compose.runtime.LaunchedEffect(Unit) {
		if (android.os.Build.VERSION.SDK_INT >= 33 &&
			androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
		) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
	}

	// Al cambiar de cuenta se vuelve al Panel de esa cuenta.
	var stack by remember(me.id) { mutableStateOf<List<Dest>>(listOf(Dest.Panel)) }
	androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.refreshMe() }

	// Mismas reglas que el menú de la web: si ya no hay acceso a la pantalla actual, se vuelve al Panel.
	fun allowed(d: Dest) = when (d) {
		is Dest.Publishers, is Dest.PublisherDetail -> vm.canPublishers(me)
		else -> true
	}
	androidx.compose.runtime.LaunchedEffect(me) { if (stack.any { !allowed(it) }) stack = listOf(Dest.Panel) }
	val current = stack.last()

	fun go(dest: Dest) {
		stack = stack + dest
	}
	fun tab(dest: Dest) {
		stack = listOf(dest)
	}
	BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

	// Sin conexión: aviso fijo arriba, y un mensaje claro cuando se intenta guardar algo.
	val offline by com.micongre.app.data.NetworkStatus.offline.collectAsState()
	val connected by com.micongre.app.data.NetworkStatus.connected.collectAsState()
	val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
	androidx.compose.runtime.LaunchedEffect(Unit) {
		com.micongre.app.data.NetworkStatus.blockedWrites.collect {
			snackbar.currentSnackbarData?.dismiss()
			snackbar.showSnackbar("Sin conexión: no se puede guardar ahora. Conéctate a internet e inténtalo de nuevo.")
		}
	}
	// Al volver internet se actualiza solo lo que se está viendo.
	var wasConnected by remember { mutableStateOf(connected) }
	androidx.compose.runtime.LaunchedEffect(connected) {
		if (connected && !wasConnected) {
			refresh.tick += 1
			vm.refreshMe()
		}
		wasConnected = connected
	}
	// Deslizar hacia abajo: pide de nuevo lo último de todo lo que hay en pantalla.
	var pulling by remember { mutableStateOf(false) }
	androidx.compose.runtime.LaunchedEffect(pulling) {
		if (pulling) {
			kotlinx.coroutines.delay(500)
			while (refresh.active > 0) kotlinx.coroutines.delay(150)
			pulling = false
		}
	}

	val shortcut = vm.activeShortcut(me)
	// La pestaña activa es la raíz de la pila; los submódulos (detalle de publicador, etc.) cuentan como su pestaña.
	val root = stack.first()
	val activeTab: Dest = when {
		current is Dest.PublisherDetail -> Dest.Publishers
		shortcut != null && sameKind(root, shortcut.dest) -> shortcut.dest
		root is Dest.Preaching && stack.size == 1 -> Dest.Panel // el ciclo de Mi Panel: Panel → calendario de predicación → Panel
		root is Dest.Panel || root is Dest.Meetings || root is Dest.Notifications -> root
		else -> Dest.More
	}

	Scaffold(
		snackbarHost = {
			androidx.compose.material3.SnackbarHost(snackbar, Modifier.padding(horizontal = 12.dp)) { data ->
				Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
					Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.inverseOnSurface)
						Text(data.visuals.message, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium)
					}
				}
			}
		},
		bottomBar = {
			// Barra propia: el resalte es un cuadrado alto de esquinas redondeadas (en vez de la píldora delgada de Material).
			Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
				Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
					// Mi Panel, Reuniones, uno configurable en Mi perfil (oculto por defecto), Avisos y Más.
					// Pulsar Mi Panel estando en Mi Panel abre el calendario de predicación; pulsarlo de nuevo vuelve a Mi Panel.
					// Si se viene de otro módulo, siempre va a Mi Panel.
					TabItem(if (root is Dest.Preaching && stack.size == 1) Icons.Filled.Campaign else Icons.Filled.Home, "Mi Panel", sameKind(activeTab, Dest.Panel)) {
						tab(if (stack.size == 1 && root is Dest.Panel) Dest.Preaching else Dest.Panel)
					}
					TabItem(Icons.Filled.CalendarMonth, "Reuniones", sameKind(activeTab, Dest.Meetings)) { tab(Dest.Meetings) }
					if (shortcut != null) {
						TabItem(iconFor(shortcut.dest), shortcut.label, sameKind(activeTab, shortcut.dest)) { tab(shortcut.dest) }
					}
					TabItem(Icons.Filled.Notifications, "Avisos", sameKind(activeTab, Dest.Notifications)) { tab(Dest.Notifications) }
					TabItem(Icons.Filled.Apps, "Más", sameKind(activeTab, Dest.More)) { tab(Dest.More) }
				}
			}
		},
	) { padding ->
		Column(Modifier.fillMaxSize().padding(padding)) {
		if (offline || !connected) {
			Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
				Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Icon(Icons.Filled.CloudOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
					Text("Sin conexión · mostrando lo último guardado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
				}
			}
		}
		// Cambios guardados en el teléfono que todavía no están en el servidor (nada se pierde: se envían solos al haber internet).
		PendingBanner(container)
		val pendingLogout by vm.pendingLogout.collectAsState()
		pendingLogout?.let { (accountId, count) ->
			androidx.compose.material3.AlertDialog(
				onDismissRequest = { vm.dismissPendingLogout() },
				title = { Text("Tienes cambios sin enviar") },
				text = {
					Text(
						"Hay $count cambio${if (count == 1) "" else "s"} guardado${if (count == 1) "" else "s"} en este teléfono que todavía no llegó${if (count == 1) "" else "n"} al servidor. " +
							"Si cierras la sesión ahora se perderán. Lo mejor es esperar a tener internet para que se envíen.",
					)
				},
				confirmButton = {
					androidx.compose.material3.TextButton(onClick = {
						vm.dismissPendingLogout()
						com.micongre.app.push.OutboxScheduler.kick(container.app)
					}) { Text("Enviar primero") }
				},
				dismissButton = {
					androidx.compose.material3.TextButton(onClick = { vm.removeAccount(accountId, force = true) }) {
						Text("Cerrar sesión y perderlos", color = MaterialTheme.colorScheme.error)
					}
				},
			)
		}
		androidx.compose.material3.pulltorefresh.PullToRefreshBox(
			isRefreshing = pulling,
			onRefresh = {
				pulling = true
				refresh.tick += 1
				vm.refreshMe()
			},
			modifier = Modifier.weight(1f).fillMaxWidth(),
		) {
		Box(Modifier.fillMaxSize()) {
			when (val d = current) {
				is Dest.Panel -> PanelScreen(container, me, onOpenS21 = { go(Dest.Reports(me.id)) })
				is Dest.Notifications -> NotificationsScreen(container, me)
				is Dest.More -> MoreScreen(vm, me, onOpen = ::go, onLogout = vm::logout)
				is Dest.Preaching -> com.micongre.app.ui.screens.PreachingScreen(container, me, onOpenTerritories = { go(Dest.Territories) })
				is Dest.Territories -> com.micongre.app.ui.screens.TerritoriesScreen(container, me)
				is Dest.Accounts -> com.micongre.app.ui.screens.AccountsScreen(vm, onAdd = { go(Dest.AddAccount) })
				is Dest.AddAccount -> com.micongre.app.ui.screens.AddAccountScreen(vm, vm.serverUrl.value, onBack = { stack = stack.dropLast(1) })
				is Dest.Profile -> ProfileScreen(vm, me, onBack = { stack = stack.dropLast(1) })
				is Dest.Meetings -> MeetingsScreen(container, me)
				is Dest.Publishers -> PublishersScreen(container, onOpen = { go(Dest.PublisherDetail(it)) })
				is Dest.PublisherDetail -> PublisherDetailScreen(
					container, d.id, onBack = { stack = stack.dropLast(1) }, onOpenS21 = { go(Dest.Reports(d.id)) },
				)
				is Dest.Reports -> com.micongre.app.ui.screens.ReportsHost(container, me, d.userId, onPickPublisher = { go(Dest.Reports(it)) })
			}
		}
		}
		}
	}
}

private fun sameKind(a: Dest, b: Dest): Boolean = when {
	a is Dest.Reports && b is Dest.Reports -> true
	a is Dest.PublisherDetail && b is Dest.PublisherDetail -> true
	else -> a == b
}

@Composable
private fun RowScope.TabItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
	Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
		Box(
			Modifier
				.size(width = 64.dp, height = 52.dp)
				.clip(RoundedCornerShape(18.dp))
				.background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
				.selectable(selected = selected, role = Role.Tab, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				icon, label, Modifier.size(26.dp),
				tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}
