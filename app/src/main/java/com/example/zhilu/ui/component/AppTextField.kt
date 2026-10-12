package com.example.zhilu.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.palettePaint

/**
 * 全站表单文本输入。两套外观两种材质：
 * - **纸墨**：M3 原生 [OutlinedTextField] 观感（默认配色 + 默认圆角），一点没动；
 * - **动森**：`componentBorder` 暖褐描边 + 20dp 圆角（`shape` 走 [MaterialTheme.shapes]），
 *   聚焦时描边/标签转主色 —— M3 默认那圈 `outline` 在奶油底上偏冷偏淡，
 *   跟 2.5dp 的暖褐描边不成套。
 *
 * 对标参考仓库 `AnimalInput` 的「描边 + 厚度」语言，动森这支**只取描边与圆角**：
 * 输入框在设置页这种字段密集的地方一个接一个排（一排 4 个），底部厚度层会让
 * 聚焦/失焦产生 3~4dp 的布局位移，看着在抖；聚焦换色已经给足反馈
 * （与 `HomeSearchField` 同一条取舍）。
 *
 * 与 [AppSwitch] 一样，双态判据唯一来源是 `PalettePaint.componentBorder`，
 * 不在这里比较 `ThemePalette.ANIMAL_ISLAND`。
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    secret: Boolean = false,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val paint = palettePaint(LocalThemePalette.current)
    val bordered = paint.componentBorder != Color.Unspecified
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
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, style = ZhiLuType.meta) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = isError,
        supportingText = supportingText,
        visualTransformation = if (secret) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        textStyle = ZhiLuType.bodySmall,
        shape = if (bordered) MaterialTheme.shapes.medium else OutlinedTextFieldDefaults.shape,
        colors = colors,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth()
    )
}
