package com.example.zhilu.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.AiConfig
import com.example.zhilu.domain.model.AiProvider
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.ZhiLuType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsSection(
    config: AiConfig,
    testInProgress: Boolean,
    testResult: String?,
    onConfigChange: (AiConfig) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    val provider = AiProvider.byId(config.provider)
    // 已配置完成时默认收起端点与模型，「能藏则藏」。
    var advancedExpanded by remember { mutableStateOf(!config.isConfigured) }

    fun selectProvider(p: AiProvider) {
        if (p.id == config.provider) return
        onConfigChange(
            if (p.id == AiProvider.CUSTOM_ID) {
                config.copy(provider = p.id)
            } else {
                config.copy(
                    provider = p.id,
                    endpoint = p.endpoint,
                    model = p.textModels.firstOrNull() ?: "",
                    visionModel = ""
                )
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "供应商",
            style = ZhiLuType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AiProvider.presets.forEach { p ->
                FilterChip(
                    selected = config.provider == p.id,
                    onClick = { selectProvider(p) },
                    label = { Text(p.label, style = ZhiLuType.chip) }
                )
            }
        }

        OutlinedTextField(
            value = config.apiKey,
            onValueChange = { onConfigChange(config.copy(apiKey = it)) },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = ZhiLuType.bodySmall,
            shape = RoundedCornerShape(Radius.Field),
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
            Icon(
                imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
            Text(
                text = if (advancedExpanded) "收起高级设置" else "高级设置",
                style = ZhiLuType.chip
            )
        }

        AnimatedVisibility(
            visible = advancedExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = config.endpoint,
                    onValueChange = { onConfigChange(config.copy(endpoint = it)) },
                    label = {
                        Text(
                            if (config.provider == AiProvider.CUSTOM_ID) {
                                "API 端点"
                            } else {
                                "API 端点（随供应商）"
                            }
                        )
                    },
                    singleLine = true,
                    readOnly = config.provider != AiProvider.CUSTOM_ID,
                    textStyle = ZhiLuType.bodySmall,
                    shape = RoundedCornerShape(Radius.Field),
                    modifier = Modifier.fillMaxWidth()
                )
                ModelField(
                    label = "文本模型",
                    value = config.model,
                    suggestions = provider.textModels,
                    onValueChange = { onConfigChange(config.copy(model = it)) },
                    modifier = Modifier.fillMaxWidth()
                )
                ModelField(
                    label = "视觉模型（识图 / OCR，可选）",
                    value = config.visionModel,
                    suggestions = provider.visionModels,
                    onValueChange = { onConfigChange(config.copy(visionModel = it)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (provider.visionModels.isEmpty() && config.provider != AiProvider.CUSTOM_ID) {
                        "${provider.label} 暂无视觉模型，发图识图可切换其他供应商或选自定义填写。"
                    } else {
                        "视觉模型留空时，识图将使用文本模型。API Key 仅保存在本机。"
                    },
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        testResult?.let {
            Text(
                text = it,
                style = ZhiLuType.meta,
                color = if (it.startsWith("连接成功")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                shape = RoundedCornerShape(Radius.Field)
            ) {
                Text("保存", style = ZhiLuType.chip)
            }
            OutlinedButton(
                onClick = onTest,
                enabled = !testInProgress,
                shape = RoundedCornerShape(Radius.Field)
            ) {
                Text(if (testInProgress) "测试中…" else "测试连接", style = ZhiLuType.chip)
            }
        }
    }
}

/** 可编辑下拉模型输入：有预设建议时提供下拉快捷选择，也可手动输入。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelField(
    label: String,
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            textStyle = ZhiLuType.bodySmall,
            shape = RoundedCornerShape(Radius.Field),
            modifier = modifier
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            textStyle = ZhiLuType.bodySmall,
            shape = RoundedCornerShape(Radius.Field),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            suggestions.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onValueChange(model)
                        expanded = false
                    }
                )
            }
        }
    }
}