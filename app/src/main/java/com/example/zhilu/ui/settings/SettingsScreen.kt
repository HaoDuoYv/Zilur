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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.Spacing

@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current

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
    val dtkImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::parseImportPreview) }
    )

    LaunchedEffect(state.exportMessage, state.error) {
        val message = state.exportMessage ?: state.error
        if (message != null) {
            snackbar.showSnackbar(message)
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

    AppTabScaffold(
        topBar = { AppTopBar(title = "我的") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = Spacing.Xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
        ) {
            if (state.isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            AppearanceSection(
                themeMode = state.themeMode,
                accentColor = state.accentColor,
                onSelectThemeMode = viewModel::setThemeMode,
                onSelectAccent = viewModel::setAccentColor
            )

            DataSection(
                noteCount = state.noteCount,
                tagCount = state.tagCount,
                mediaCount = state.mediaCount,
                totalMediaSize = state.totalMediaSize,
                onExportJson = { jsonExportLauncher.launch("zhilu-backup.json") },
                onExportMarkdown = { markdownExportLauncher.launch("zhilu-notes.md") },
                onImportJson = {
                    jsonImportLauncher.launch(arrayOf("application/json", "text/*"))
                },
                onImportDtk = {
                    dtkImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }
            )

            ReminderSection(
                notificationPermissionGranted = state.notificationPermissionGranted,
                remindersEnabled = state.remindersEnabled,
                onOpenReminders = { navController.navigate(Destination.Reminders.path) },
                onOpenSystemNotificationSettings = {
                    context.startActivity(
                        Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                },
                onToggleReminders = viewModel::setRemindersEnabled
            )

            SettingsGroup(title = "AI 助手") {
                AiSettingsSection(
                    config = state.aiConfig,
                    testInProgress = state.aiTestInProgress,
                    testResult = state.aiTestResult,
                    onConfigChange = viewModel::updateAiConfig,
                    onSave = viewModel::saveAiConfig,
                    onTest = viewModel::testAiConnection
                )
            }

            StorageSection(onOpenTrash = { navController.navigate(Destination.Trash.path) })
        }
    }

    state.importPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismissImportPreview
        )
    }
}

private fun Context.hasNotificationPermission(): Boolean =
    !NotificationPermissionState.requiresRuntimePermission(Build.VERSION.SDK_INT) ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED