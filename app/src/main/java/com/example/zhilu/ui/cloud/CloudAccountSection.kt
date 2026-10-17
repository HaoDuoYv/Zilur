package com.example.zhilu.ui.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.zhilu.domain.model.CloudUser
import com.example.zhilu.ui.component.AppButton
import com.example.zhilu.ui.component.AppButtonVariant
import com.example.zhilu.ui.component.AppChip
import com.example.zhilu.ui.component.AppDialog
import com.example.zhilu.ui.component.AppTextField
import com.example.zhilu.ui.component.QuietAction
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 账号区：未登录是"登录 / 注册 / 找回密码"三态表单，已登录是用户中心。
 *
 * 三态放在同一处而不是三个页面：它们共享**同一组输入框**（邮箱 + 密码 + 验证码），
 * 拆页只会让用户在"我是不是注册过"这种犹豫里反复跳转。
 */
@Composable
fun CloudAccountSection(
    state: CloudUiState,
    onModeChange: (CloudAuthMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit,
    onSignOut: () -> Unit,
    onUpdateUsername: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit
) {
    val user = state.user
    if (user != null && state.isSignedIn) {
        SignedInProfile(
            user = user,
            isWorking = state.isWorking,
            onUpdateUsername = onUpdateUsername,
            onPickAvatar = onPickAvatar,
            onRemoveAvatar = onRemoveAvatar,
            onSignOut = onSignOut
        )
    } else {
        AuthForm(
            state = state,
            onModeChange = onModeChange,
            onEmailChange = onEmailChange,
            onPasswordChange = onPasswordChange,
            onCodeChange = onCodeChange,
            onUsernameChange = onUsernameChange,
            onSendCode = onSendCode,
            onSubmit = onSubmit
        )
    }
}

@Composable
private fun AuthForm(
    state: CloudUiState,
    onModeChange: (CloudAuthMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
            AppChip(
                label = "登录",
                selected = state.authMode == CloudAuthMode.LOGIN,
                onClick = { onModeChange(CloudAuthMode.LOGIN) }
            )
            AppChip(
                label = "注册",
                selected = state.authMode == CloudAuthMode.REGISTER,
                enabled = state.health?.registrationEnabled != false,
                onClick = { onModeChange(CloudAuthMode.REGISTER) }
            )
            AppChip(
                label = "忘记密码",
                selected = state.authMode == CloudAuthMode.RESET,
                onClick = { onModeChange(CloudAuthMode.RESET) }
            )
        }

        AppTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "邮箱",
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (state.authMode == CloudAuthMode.REGISTER) {
            AppTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = "用户名（可选，默认取邮箱前缀）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        AppTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = if (state.authMode == CloudAuthMode.RESET) "新密码（至少 8 位）" else "密码",
            singleLine = true,
            secret = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.needsCode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextField(
                    value = state.code,
                    onValueChange = onCodeChange,
                    label = "验证码",
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (state.codeCountdown > 0) {
                    Text(
                        text = "${state.codeCountdown}s",
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    QuietAction(
                        label = "获取验证码",
                        onClick = onSendCode
                    )
                }
            }
        }

        AppButton(
            onClick = onSubmit,
            enabled = state.canSubmit,
            fillWidth = true
        ) {
            Text(
                when (state.authMode) {
                    CloudAuthMode.LOGIN -> "登录"
                    CloudAuthMode.REGISTER -> "注册并登录"
                    CloudAuthMode.RESET -> "重置密码并登录"
                }
            )
        }

        // 说清"为什么这里没有验证码"：省掉用户"我是不是该收到邮件"的等待
        if (state.health?.mailConfigured == false) {
            Text(
                text = "服务端未配置邮件服务，注册不需要验证码，找回密码暂不可用。",
                style = ZhiLuType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignedInProfile(
    user: CloudUser,
    isWorking: Boolean,
    onUpdateUsername: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onSignOut: () -> Unit
) {
    var editingName by remember { mutableStateOf(false) }
    var nameDraft by remember(user.username) { mutableStateOf(user.username) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(user = user)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                Text(
                    text = user.username,
                    style = ZhiLuType.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = user.email,
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 未验证邮箱要提示：否则用户以为"有邮箱就能找回密码"
                if (!user.emailVerified) {
                    Text(
                        text = "邮箱未验证",
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // 三个按钮在窄屏/大字号下放不下：用 FlowRow 按需换行，
        // 而不是让「移除头像」被挤成两行只剩「移除」
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
        ) {
            AppButton(
                onClick = { nameDraft = user.username; editingName = true },
                variant = AppButtonVariant.Outline
            ) { Text("修改用户名") }
            AppButton(
                onClick = onPickAvatar,
                variant = AppButtonVariant.Outline,
                enabled = !isWorking
            ) { Text("更换头像") }
            if (user.avatarUrl != null) {
                AppButton(
                    onClick = onRemoveAvatar,
                    variant = AppButtonVariant.Quiet,
                    enabled = !isWorking
                ) { Text("移除头像") }
            }
        }

        AppButton(onClick = onSignOut, variant = AppButtonVariant.Quiet) {
            Text("退出登录", color = MaterialTheme.colorScheme.error)
        }
    }

    if (editingName) {
        AppDialog(
            onDismissRequest = { editingName = false },
            title = "修改用户名",
            confirmButton = {
                AppButton(onClick = {
                    editingName = false
                    onUpdateUsername(nameDraft)
                }) { Text("保存") }
            },
            dismissButton = {
                AppButton(onClick = { editingName = false }, variant = AppButtonVariant.Quiet) {
                    Text("取消")
                }
            }
        ) {
            AppTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = "用户名",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun UserAvatar(user: CloudUser) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (user.avatarUrl != null) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(shape)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
