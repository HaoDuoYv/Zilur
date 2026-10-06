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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current

    // 只有导出留在这里；导入已经搬到**底栏中央 ＋** 的「新建 / 导入」弹层
    // （见 AppShell / CreateSheet），所以这里不再持有导入用的文件选择器。
    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let(viewModel::exportJsonToUri) }
    )
    val markdownExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
        onResult = { uri -> uri?.let(viewModel::exportMarkdownToUri) }
    )

    // 一次性提示要派发到屏幕作用域，不能留在 LaunchedEffect 里（见下方注释）
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.exportMessage, state.error) {
        val message = state.exportMessage ?: state.error ?: return@LaunchedEffect
        // **先消费、再弹提示** —— 消息必须一次性消费掉，否则它留在 state 里，
        // 之后每次进入「我的」都会重弹一次（真机反馈过）。
        //
        // 但 `showSnackbar`（会一直挂起到提示消失）**不能**留在这个 effect 里：
        // `clearMessages()` 会把 key 变成 null，Compose 随即取消并重启本 effect，
        // 挂起中的 `showSnackbar` 会被一起取消 —— 提示一闪即逝甚至根本不出现。
        // 所以这里只负责消费与派发，真正的弹出交给屏幕作用域。
        viewModel.clearMessages()
        scope.launch { snackbar.showSnackbar(message) }
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

            // 顺序对齐设计原型：身份 → 提醒 → 外观 → AI → 数据 → 存储 → 关于。
            // 「导入」不在这里了 —— 它属于"往知识库里加东西"，已经搬到**底栏中央 ＋**
            // 弹出的「新建 / 导入」弹层（全局唯一入口）。设置页只留"导出"与"存储管理"。
            ProfileCard(
                noteCount = state.noteCount,
                tagCount = state.tagCount,
                recordedDays = state.recordedDays,
                streakDays = state.streakDays
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

            // 外观不再是内联的三组控件，而是一行**独立入口**：进去之后有配色缩略卡
            // 和一句话说明，选起来比在长列表里翻开关清楚得多。行尾直接写出当前外观，
            // 不进去也知道现在是什么。
            SettingsGroup(title = "外观") {
                SettingsRow(
                    title = "配色与主题",
                    description = appearanceSummary(state.themePalette, state.themeMode),
                    leadingIcon = Icons.Outlined.Palette,
                    onClick = { navController.navigate(Destination.Appearance.path) },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            // AI 配置与外观一样，走**独立入口**：多供应商之后它有服务列表 + 表单 +
            // 默认与回退三组，塞在这里既放不下也找不到。行尾直接写出当前服务名。
            SettingsGroup(title = "AI 助手") {
                SettingsRow(
                    title = "AI 配置",
                    description = aiConfigSummary(state.aiSettings),
                    leadingIcon = Icons.Outlined.SmartToy,
                    onClick = { navController.navigate(Destination.AiConfig.path) },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            DataSection(
                noteCount = state.noteCount,
                tagCount = state.tagCount,
                mediaCount = state.mediaCount,
                totalMediaSize = state.totalMediaSize,
                onExportJson = { jsonExportLauncher.launch("zhilu-backup.json") },
                onExportMarkdown = { markdownExportLauncher.launch("zhilu-notes.md") }
            )

            StorageSection(onOpenTrash = { navController.navigate(Destination.Trash.path) })

            SettingsGroup(title = "关于") {
                SettingsRow(
                    title = "版本",
                    description = appVersionLabel(context),
                    leadingIcon = Icons.Outlined.Info,
                    trailing = {}
                )
            }
        }
    }
}

private fun Context.hasNotificationPermission(): Boolean =
    !NotificationPermissionState.requiresRuntimePermission(Build.VERSION.SDK_INT) ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED