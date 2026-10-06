package com.example.zhilu.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.AiVendorPreset
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.palettePaint

/**
 * 供应商预设胶囊。
 *
 * 选中态只表示"这组端点是从它带出来的"，之后改过端点也不会自动取消选中 ——
 * 那会让标签闪来闪去。它是**填表辅助**，不是绑定关系。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VendorChips(
    selected: String,
    onPick: (AiVendorPreset) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AiVendorPreset.presets.forEach { preset ->
            FilterChip(
                selected = selected == preset.label,
                onClick = { onPick(preset) },
                label = { Text(preset.label, style = ZhiLuType.chip) }
            )
        }
    }
}

/**
 * 表单文本输入。
 *
 * 抽出来只为两件事：① 圆角/字号统一；② 动森外观下把描边换成与卡片/按钮同一支
 * `componentBorder` —— M3 默认那圈 `outline` 在奶油底上偏冷偏淡，跟 2.5dp 的暖褐描边不成套。
 */
@Composable
fun AiTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    secret: Boolean = false,
    singleLine: Boolean = true
) {
    val paint = palettePaint(LocalThemePalette.current)
    val bordered = paint.componentBorder != Color.Unspecified
    val shape = RoundedCornerShape(if (bordered) Radius.CardAnimalIsland else Radius.Field)
    val colors = if (bordered) {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = paint.componentBorder,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, style = ZhiLuType.meta) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        visualTransformation = if (secret) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        textStyle = ZhiLuType.bodySmall,
        shape = shape,
        colors = colors,
        modifier = modifier.fillMaxWidth()
    )
}
