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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.zhilu.ui.component.AppProgressBar
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.domain.model.CloudSettings
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppIntents
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.navigation.navigateToReview
import com.example.zhilu.ui.review.ReviewTab
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    val intents = LocalAppIntents.current

    // 只有导出留在这里；导入已经搬到**底栏中央 ＋** 的「新建 / 导入」弹层
    // （见 AppShell / CreateSheet），所以这里不再持有导入用的文件选择器。
    // 备份格式只有 `.dtk`（zip）—— 图片作为文件进包，不再走 base64 内嵌的 JSON。
    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri -> uri?.let(viewModel::exportDtkToUri) }
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
                AppProgressBar()
            }

            // 顺序：身份（Hero）→ 学习与提醒 → 个性化 → 更多 → 页脚。
            // 「导入」不在这里了 —— 它属于"往知识库里加东西"，已经搬到**底栏中央 ＋**
            // 弹出的「新建 / 导入」弹层（全局唯一入口）。设置页只留"导出"与"存储管理"。
            ProfileCard(
                noteCount = state.noteCount,
                tagCount = state.tagCount,
                mediaCount = state.mediaCount,
                recordedDays = state.recordedDays,
                streakDays = state.streakDays,
                // 登录了就显示账号（点卡片进云端页），未登录保持"我的知识库"且不可点
                account = state.cloudSettings.user?.takeIf { state.cloudSettings.isSignedIn },
                onAccountClick = { navController.navigate(Destination.Cloud.path) }
            )

            // 复习中心、复习间隔、提醒总开关同组 —— 它们本来就是一件事；
            // 通知权限仅在未授权时多占一行提示。
            ReminderSection(
                notificationPermissionGranted = state.notificationPermissionGranted,
                remindersEnabled = state.remindersEnabled,
                reviewIntervals = state.reviewIntervals,
                reviewPendingCount = state.reviewPendingCount,
                onOpenReminders = { navController.navigateToReview(intents, ReviewTab.Reminders) },
                onOpenReviewInterval = { navController.navigate(Destination.ReviewInterval.path) },
                onOpenSystemNotificationSettings = {
                    context.startActivity(
                        Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                },
                onToggleReminders = viewModel::setRemindersEnabled
            )

            // 外观与 AI 都是"让这个应用更像我的"，合并成一组；行尾直接写出当前值，
            // 不进去也知道现在是什么（所以不再需要第二行描述）。
            SettingsGroup(title = "个性化") {
                SettingsRow(
                    title = "配色与主题",
                    leadingIcon = Icons.Outlined.Palette,
                    onClick = { navController.navigate(Destination.Appearance.path) },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
                        ) {
                            SettingsRowValue(appearanceSummary(state.themePalette, state.themeMode))
                            SettingsChevron()
                        }
                    }
                )
                SettingsRow(
                    title = "AI 配置",
                    leadingIcon = Icons.Outlined.SmartToy,
                    onClick = { navController.navigate(Destination.AiConfig.path) },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
                        ) {
                            SettingsRowValue(aiConfigSummary(state.aiSettings))
                            SettingsChevron()
                        }
                    }
                )
            }

            // 低频项收进「更多」：导出最常用，排在最前两行。
            SettingsGroup(title = "更多") {
                SettingsRow(
                    title = "导出备份",
                    description = "导出全部笔记（含图片）为 .dtk 备份文件",
                    leadingIcon = Icons.Outlined.Download,
                    onClick = { backupExportLauncher.launch(backupFileName()) },
                    trailing = { SettingsChevron() }
                )
                SettingsRow(
                    title = "导出 Markdown",
                    description = "用于在其他编辑器继续写作",
                    leadingIcon = Icons.Outlined.Description,
                    onClick = { markdownExportLauncher.launch("zhilu-notes.md") },
                    trailing = { SettingsChevron() }
                )
                // 云端是**可选**的：不填地址的用户扫一眼就知道这东西跟自己无关。
                SettingsRow(
                    title = "云端服务",
                    description = cloudSummary(state.cloudSettings),
                    leadingIcon = Icons.Outlined.CloudQueue,
                    onClick = { navController.navigate(Destination.Cloud.path) },
                    trailing = { SettingsChevron() }
                )
                SettingsRow(
                    title = "回收站",
                    description = "恢复或永久删除已移除的笔记",
                    leadingIcon = Icons.Outlined.Delete,
                    onClick = { navController.navigate(Destination.Trash.path) },
                    trailing = { SettingsChevron() }
                )
            }

            // 「关于」不再占一张卡：版本号降级成页脚一行小字。
            Text(
                text = "知录 · ${appVersionLabel(context)}",
                style = ZhiLuType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.Xl)
            )
        }
    }
}

private fun Context.hasNotificationPermission(): Boolean =
    !NotificationPermissionState.requiresRuntimePermission(Build.VERSION.SDK_INT) ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

/**
 * 全量备份的建议文件名，带日期便于在文件管理器里区分几次备份。
 * 用户仍可在系统选择器里改名。
 */
private fun backupFileName(): String {
    val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    return "zhilu-backup-$date.dtk"
}

/**
 * 云端那一行的摘要。
 *
 * 「谁登录了」由顶部身份卡负责，这一行只说**连接本身**，且要够短 ——
 * 它是行内第二行描述，不该把「更多」组里的行撑高。
 */
private fun cloudSummary(settings: CloudSettings): String = when {
    !settings.isConfigured -> "未连接"
    settings.isSignedIn -> settings.serverUrl
    else -> "未登录"
}