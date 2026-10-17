package com.example.zhilu.ui.cloud

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.CloudBackupInfo
import com.example.zhilu.ui.component.AppButton
import com.example.zhilu.ui.component.AppButtonVariant
import com.example.zhilu.ui.component.AppDialog
import com.example.zhilu.ui.component.AppTextField
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.QuietAction
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.settings.SettingsGroup
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * 云端服务页。
 *
 * 这一页是**可选**功能的面孔：默认什么地址都不填，进来只看到一段说明。
 * 一旦填了地址，三组依次亮起来 —— 地址、账号、云端备份。
 *
 * 「从云端恢复」不在这里：恢复本质上是**导入**，入口留在「＋ → 云端恢复」，
 * 与 `.dtk` 导入共用同一个预览确认框（见 `CreateSheetViewModel.restoreFromCloud`）。
 */
@Composable
fun CloudScreen(
    navController: NavHostController,
    viewModel: CloudViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error ?: return@LaunchedEffect
        // 先消费再弹 + 派发到屏幕作用域（否则 effect 重启会把挂起中的提示取消）
        viewModel.consumeMessages()
        scope.launch { snackbar.showSnackbar(text) }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("读不到这张图片")
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val extension = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            Triple(bytes, mime, "avatar.$extension")
        }
        picked.fold(
            onSuccess = { (bytes, mime, name) ->
                if (bytes.size > MAX_AVATAR_BYTES) {
                    // 本地就拦住：传上去再被 413 拒绝，白费流量也白等
                    viewModel.reportLocalError("图片太大，请选择 2MB 以内的")
                } else {
                    viewModel.uploadAvatar(bytes, name, mime)
                }
            },
            onFailure = { viewModel.reportLocalError(it.message ?: "读取图片失败") }
        )
    }

    AppTabScaffold(
        topBar = {
            AppTopBar(title = "云端服务", onBack = { navController.popBackStack() })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = Spacing.Xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
        ) {
            IntroCard()

            SettingsGroup(title = "服务地址") {
                if (state.isEditingServer) {
                    AppTextField(
                        value = state.serverUrlDraft,
                        onValueChange = viewModel::onServerUrlChange,
                        label = "服务地址",
                        placeholder = "http://192.168.1.10:8787",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                        AppButton(
                            onClick = viewModel::testConnection,
                            variant = AppButtonVariant.Outline,
                            enabled = !state.isWorking
                        ) { Text("测试连接") }
                        AppButton(
                            onClick = viewModel::saveServerUrl,
                            enabled = !state.isWorking
                        ) { Text("保存") }
                        QuietAction("取消", onClick = viewModel::cancelEditingServer)
                    }
                    Text(
                        text = "留空保存 = 关闭云端服务，笔记仍完全在本地。",
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (state.isConfigured) state.settings.serverUrl else "未连接（仅本地）",
                                style = ZhiLuType.rowTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val health = state.health
                            if (health != null) {
                                Text(
                                    text = "服务端 v${health.version}" +
                                        if (health.mailConfigured) " · 邮件已配置" else " · 邮件未配置",
                                    style = ZhiLuType.meta,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        QuietAction("修改", onClick = viewModel::startEditingServer)
                    }
                }
            }

            SettingsGroup(title = "账号") {
                if (!state.isConfigured) {
                    Text(
                        text = "先填一个服务地址，才能登录。",
                        style = ZhiLuType.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    CloudAccountSection(
                        state = state,
                        onModeChange = viewModel::switchAuthMode,
                        onEmailChange = viewModel::onEmailChange,
                        onPasswordChange = viewModel::onPasswordChange,
                        onCodeChange = viewModel::onCodeChange,
                        onUsernameChange = viewModel::onUsernameChange,
                        onSendCode = viewModel::sendCode,
                        onSubmit = viewModel::submit,
                        onSignOut = viewModel::signOut,
                        onUpdateUsername = viewModel::updateUsername,
                        onPickAvatar = {
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveAvatar = viewModel::removeAvatar
                    )
                }
            }

            if (state.isSignedIn) {
                SettingsGroup(title = "云端备份") {
                    BackupRow(
                        info = state.backupInfo,
                        isLoading = state.isLoadingBackup,
                        isWorking = state.isWorking,
                        onUpload = viewModel::uploadBackup,
                        onDelete = viewModel::deleteBackup
                    )
                    Text(
                        text = "恢复入口在首页「＋ → 云端恢复」，导入前会先给你看预览。",
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Text(
        text = "云端服务是可选的。不填地址时，笔记、AI、复习都在本机，App 不会联网到任何自有服务器。",
        style = ZhiLuType.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.PageGutter, vertical = Spacing.Xs)
    )
}

@Composable
private fun BackupRow(
    info: CloudBackupInfo?,
    isLoading: Boolean,
    isWorking: Boolean,
    onUpload: () -> Unit,
    onDelete: () -> Unit
) {
    // 删除是**不可撤销**的（云端那份没了就真没了），必须二次确认 ——
    // 与删笔记、清空回收站保持同一套规矩。之前直接删，是明显的疏漏。
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
        Text(
            text = when {
                isLoading -> "读取中…"
                info == null -> "云端还没有备份"
                else -> "${formatSize(info.size)} · ${formatTimestamp(info.updatedAt)}"
            },
            style = ZhiLuType.rowTitle,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
            AppButton(onClick = onUpload, enabled = !isWorking) {
                Text(if (info == null) "立即备份到云端" else "重新上传")
            }
            if (info != null) {
                AppButton(
                    onClick = { confirmingDelete = true },
                    variant = AppButtonVariant.Quiet,
                    enabled = !isWorking
                ) { Text("删除云端备份", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmingDelete) {
        AppDialog(
            onDismissRequest = { confirmingDelete = false },
            title = "删除云端备份？",
            confirmButton = {
                AppButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = {
                AppButton(
                    onClick = { confirmingDelete = false },
                    variant = AppButtonVariant.Quiet
                ) { Text("取消") }
            }
        ) {
            Text(
                text = "云端那份 .dtk 会被永久删除，本机笔记不受影响。" +
                    "之后仍可重新备份，但这次上传的历史就没了。",
                style = ZhiLuType.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

/** 与服务端 `MAX_AVATAR_MB` 对齐（默认 2MB），本地先挡一道。 */
private const val MAX_AVATAR_BYTES = 2 * 1024 * 1024
