package com.example.zhilu.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.navigation.BottomBar
import com.example.zhilu.ui.navigation.Destination

@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var followSystem by remember { mutableStateOf(true) }
    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let(viewModel::exportJsonToUri) }
    )
    val markdownExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
        onResult = { uri -> uri?.let(viewModel::exportMarkdownToUri) }
    )
    val jsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::importJsonFromUri) }
    )

    LaunchedEffect(state.exportMessage, state.error) {
        val message = state.exportMessage ?: state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(context) {
        viewModel.updateNotificationPermissionGranted(context.hasNotificationPermission())
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateNotificationPermissionGranted(context.hasNotificationPermission())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Settings") },
        bottomBar = { BottomBar(navController = navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.isWorking) LinearProgressIndicator()
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Follow system theme")
                            Text(
                                "Theme wiring belongs to the app shell.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = followSystem, onCheckedChange = { followSystem = it })
                    }
                }
            }
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Data", style = MaterialTheme.typography.titleMedium)
                    Text("${state.noteCount} notes")
                    Text("${state.tagCount} tags")
                    Text("${state.mediaCount} media items, ${formatBytes(state.totalMediaSize)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            jsonExportLauncher.launch("zhilu-backup.json")
                        }) {
                            Text("Export JSON")
                        }
                        OutlinedButton(onClick = {
                            markdownExportLauncher.launch("zhilu-notes.md")
                        }) {
                            Text("Export MD")
                        }
                    }
                    OutlinedButton(onClick = {
                        jsonImportLauncher.launch(arrayOf("application/json", "text/*"))
                    }) {
                        Text("Import JSON")
                    }
                }
            }
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Reminders", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Review pending, overdue, and completed reminders.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(onClick = { navController.navigate(Destination.Reminders.path) }) {
                            Text("Open")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Notification permission")
                            Text(
                                if (state.notificationPermissionGranted) "Enabled" else "Not enabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }
                        ) {
                            Text("System settings")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Reminder master switch")
                            Text(
                                "Placeholder only; reminder delivery is controlled by saved reminders and system permission.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = true, onCheckedChange = {}, enabled = false)
                    }
                }
            }
            AppCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Trash", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Restore or permanently delete removed notes.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(onClick = { navController.navigate(Destination.Trash.path) }) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    return String.format("%.1f MB", kb / 1024.0)
}

private fun Context.hasNotificationPermission(): Boolean =
    !NotificationPermissionState.requiresRuntimePermission(Build.VERSION.SDK_INT) ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
