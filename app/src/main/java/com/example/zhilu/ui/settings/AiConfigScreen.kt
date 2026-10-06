package com.example.zhilu.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import com.example.zhilu.ui.component.AppSwitch
import com.example.zhilu.ui.component.ProviderAvatar
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.palettePaint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.AiService
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 「AI 配置」独立页。
 *
 * 与 [AppearanceScreen] 同一个理由从「我的」里搬出来：多供应商之后这里有
 * 「服务列表 + 增删改表单 + 默认与回退」三组，塞进设置长列表既放不下也找不到。
 *
 * 两种形态共用一个页面：**列表态**（管理已有服务）与**编辑态**（填一个服务的表单）。
 * 不做成"列表 → 点进去 → 又一层页面"，因为服务的字段本来就不多，
 * 多一层导航只会让"改个模型"变成三次跳转。
 */
@Composable
fun AiConfigScreen(
    navController: NavHostController,
    viewModel: AiConfigViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.autoStartCreateIfEmpty() }

    AppTabScaffold(
        topBar = {
            AppTopBar(
                title = if (state.editing) {
                    if (state.draft?.id.isNullOrBlank()) "新建 AI" else "编辑 AI"
                } else {
                    "AI 配置"
                },
                onBack = {
                    if (state.editing) viewModel.cancelEdit() else navController.popBackStack()
                }
            )
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
            val draft = state.draft
            if (draft == null) {
                AiServiceListSection(
                    state = state,
                    onAdd = viewModel::startCreate,
                    onEdit = viewModel::startEdit,
                    onDelete = viewModel::delete,
                    onSetActive = viewModel::setActive,
                    onToggleEnabled = viewModel::setEnabled,
                    onToggleFallback = viewModel::setFallbackOnFailure
                )
            } else {
                AiServiceFormSection(
                    draft = draft,
                    advancedExpanded = state.advancedExpanded,
                    testInProgress = state.testInProgress,
                    testResult = state.testResult,
                    onUpdate = viewModel::updateDraft,
                    onToggleAdvanced = viewModel::toggleAdvanced,
                    onSave = viewModel::saveDraft,
                    onTest = viewModel::testDraft,
                    onCancel = viewModel::cancelEdit
                )
            }
        }
    }
}

// ── 列表态 ────────────────────────────────────────────────────────────

@Composable
private fun AiServiceListSection(
    state: AiConfigUiState,
    onAdd: () -> Unit,
    onEdit: (AiService) -> Unit,
    onDelete: (AiService) -> Unit,
    onSetActive: (AiService) -> Unit,
    onToggleEnabled: (AiService, Boolean) -> Unit,
    onToggleFallback: (Boolean) -> Unit
) {
    val active = state.active

    if (state.settings.services.isEmpty()) {
        SettingsGroup(title = "AI 与服务") {
            Text(
                text = "还没有接入 AI。点下面的「新建 AI」填入供应商、API Key 与模型 ID。",
                style = ZhiLuType.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.Sm))
            AddAiButton(onClick = onAdd)
        }
        return
    }

    SettingsGroup(title = "AI 与服务") {
        Text(
            text = "共 ${state.settings.services.size} 个，已启用 ${state.settings.enabledServices.size} 个",
            style = ZhiLuType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // 自己排一遍间距：`SettingsGroup` 的 Column 用的是 Spacing.Md，
        // 那是给"设置行"之间留的，用在胶囊列表上会松得像散了架。
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
            state.settings.services.forEach { service ->
                AiServiceRow(
                    service = service,
                    isActive = service.id == active?.id,
                    onSetActive = { onSetActive(service) },
                    onEdit = { onEdit(service) },
                    onDelete = { onDelete(service) },
                    onToggleEnabled = { onToggleEnabled(service, it) }
                )
            }
            AddAiButton(onClick = onAdd)
        }
    }

    SettingsGroup(title = "偏好设置") {
        SettingsRow(
            title = "失败自动回退",
            description = "请求失败时自动切换到其他可用 AI（仅在一字未出时）",
            trailing = {
                com.example.zhilu.ui.component.AppSwitch(
                    checked = state.settings.fallbackOnFailure,
                    onCheckedChange = onToggleFallback
                )
            }
        )
        Text(
            text = "「当前使用」就是新会话默认用的 AI。点某一项即可切换，不用进编辑页。",
            style = ZhiLuType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 一个 AI 服务——做成**胶囊状**的一整块，而不是裸的一行。
 *
 * 为什么整块成胶囊：这一行里同时有四种可交互元素（整块点击=切换、开关、编辑、删除），
 * 裸行时它们是四个散落的图标，用户分不清"点到哪是哪"。套一层胶囊把"这是一个 AI"
 * 这层边界画出来之后，里面的图标才读得懂是"这一项的操作"。
 *
 * 当前使用的那一项额外**加主色底 + 描边 + 勾**：勾在最右，一眼扫过去时底色比
 * 一个小图标更容易定位到"我现在用的是哪个"（色觉障碍下也有勾和字重兜底）。
 */
@Composable
private fun AiServiceRow(
    service: AiService,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    val paint = palettePaint(LocalThemePalette.current)
    val bordered = paint.componentBorder != Color.Unspecified
    // 胶囊：动森用大圆角、纸墨用它的字段圆角；形状与切换层里的行保持一致。
    val shape = RoundedCornerShape(if (bordered) Radius.CardAnimalIsland else Radius.Field)
    val clickable = service.enabled && service.isConfigured

    Surface(
        onClick = onSetActive,
        enabled = clickable,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = shape,
        color = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = if (bordered) 2.dp else 1.dp,
            color = when {
                isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                bordered -> paint.componentBorder
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.Sm, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderAvatar(
                label = service.provider.ifBlank { service.displayName },
                size = 36.dp
            )
            Spacer(Modifier.width(Spacing.Sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = service.displayName,
                        style = ZhiLuType.rowTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                    if (isActive) {
                        Spacer(Modifier.width(Spacing.Xs))
                        Text(
                            text = "使用中",
                            style = ZhiLuType.label,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = buildString {
                        append(service.model.ifBlank { "未填模型" })
                        if (service.provider.isNotBlank()) append(" · ${service.provider}")
                        if (!service.isConfigured) append(" · 未填完")
                    },
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            AppSwitch(
                checked = service.enabled,
                onCheckedChange = onToggleEnabled
            )
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddAiButton(onClick: () -> Unit) {
    // contentPadding 收窄：默认的 16dp 左右内边距让图标看起来浮在卡片里，
    // 与上面那几行的左边界对不齐。
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(Spacing.Xs))
        Text(text = "新建 AI", style = ZhiLuType.chip)
    }
}

// ── 编辑态 ────────────────────────────────────────────────────────────

@Composable
private fun AiServiceFormSection(
    draft: AiService,
    advancedExpanded: Boolean,
    testInProgress: Boolean,
    testResult: String?,
    onUpdate: ((AiService) -> AiService) -> Unit,
    onToggleAdvanced: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onCancel: () -> Unit
) {
    SettingsGroup(title = "供应商") {
        Text(
            text = "选一个只是帮你带出端点，之后随时能改；自建网关或中转站直接改端点即可。",
            style = ZhiLuType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VendorPresetPicker(
            selectedProvider = draft.provider,
            onPick = { preset ->
                onUpdate {
                    it.copy(
                        provider = preset.label,
                        endpoint = preset.endpoint,
                        // 名字留空过就顺手填上供应商名，用户想改再改
                        name = it.name.ifBlank { preset.label }
                    )
                }
            }
        )
    }

    SettingsGroup(title = "基本信息") {
        AiTextField(
            label = "显示名称",
            value = draft.name,
            onValueChange = { v -> onUpdate { it.copy(name = v) } },
            placeholder = "如：我的 DeepSeek"
        )
        AiTextField(
            label = "API Key",
            value = draft.apiKey,
            onValueChange = { v -> onUpdate { it.copy(apiKey = v) } },
            secret = true
        )
        AiTextField(
            label = "模型 ID",
            value = draft.model,
            onValueChange = { v -> onUpdate { it.copy(model = v) } },
            placeholder = "如：deepseek-chat"
        )
        AiTextField(
            label = "API 端点",
            value = draft.endpoint,
            onValueChange = { v -> onUpdate { it.copy(endpoint = v) } },
            placeholder = "https://api.example.com/v1"
        )
        Text(
            text = "密钥仅保存在本机 DataStore，不会上传到任何服务器。"
                + "注意它没有额外加密，能读到本机应用数据的人就能看到它。",
            style = ZhiLuType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SettingsGroup(title = "高级设置") {
        TextButton(onClick = onToggleAdvanced) {
            Icon(
                imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
            Spacer(Modifier.width(Spacing.Xs))
            Text(
                text = if (advancedExpanded) "收起" else "展开高级设置",
                style = ZhiLuType.chip
            )
        }
        if (advancedExpanded) {
            TemperatureField(
                value = draft.temperature,
                onValueChange = { v -> onUpdate { it.copy(temperature = v) } }
            )
            AiTextField(
                label = "最大 Tokens",
                value = draft.maxTokens.toString(),
                onValueChange = { v ->
                    v.filter { it.isDigit() }.take(6).toIntOrNull()?.let { n ->
                        onUpdate { it.copy(maxTokens = n) }
                    }
                }
            )
            AiTextField(
                label = "系统提示词",
                value = draft.systemPrompt,
                onValueChange = { v -> onUpdate { it.copy(systemPrompt = v) } },
                placeholder = "留空则用应用内置的那份",
                singleLine = false
            )
        }
    }

    SettingsGroup(title = "连接") {
        testResult?.let { result ->
            Text(
                text = result,
                style = ZhiLuType.meta,
                color = if (result.startsWith("连接成功")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
            com.example.zhilu.ui.component.AppCircleButton(
                icon = Icons.Default.Check,
                contentDescription = "保存",
                onClick = onSave
            )
            TextButton(onClick = onTest, enabled = !testInProgress) {
                Text(if (testInProgress) "测试中…" else "测试连接", style = ZhiLuType.chip)
            }
            TextButton(onClick = onCancel) {
                Text("取消", style = ZhiLuType.chip)
            }
        }
    }
}

/** 温度：0~2，步长 0.1。用分段按钮而不是滑杆 —— 滑杆在手机上很难精确停在 0.7。 */
@Composable
private fun TemperatureField(value: Float, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
        Text(
            text = "温度：${"%.1f".format(value)}",
            style = ZhiLuType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
            listOf(0.0f, 0.3f, 0.7f, 1.0f, 1.5f).forEach { preset ->
                val selected = kotlin.math.abs(value - preset) < 0.001f
                TextButton(onClick = { onValueChange(preset) }) {
                    Text(
                        text = "%.1f".format(preset),
                        style = ZhiLuType.chip,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** 供应商预设：一排可选胶囊。选中只影响端点与显示名，不是白名单。 */
@Composable
private fun VendorPresetPicker(
    selectedProvider: String,
    onPick: (com.example.zhilu.domain.model.AiVendorPreset) -> Unit
) {
    com.example.zhilu.ui.settings.VendorChips(
        selected = selectedProvider,
        onPick = onPick
    )
}
