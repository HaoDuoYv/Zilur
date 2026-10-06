package com.example.zhilu.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.component.AppIconButton
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.PalettePaint
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.palettePaint
import com.example.zhilu.ui.theme.shade
/**
 * 笔记页搜索框。
 *
 * 两套外观两种材质，各按各的规则来：
 * - **纸墨**：凹陷纸面底、无外框、聚焦时浮出主色描边（原来的做法，一点没动）；
 * - **动森**：胶囊 + 2.5dp 描边 + 底下一层偏移的"厚度"（参考仓库 `AnimalInput` 的手法）。
 *
 * 动森那支**刻意不做"聚焦时抬起来"**：参考仓库输入框聚焦是整体上浮 3dp，
 * 但布局盒高度不变、底部会空出 3dp 的位移 —— 在设置页这种字段密集的地方会看着在抖。
 * 这里只换描边色（→ 主色），既有反馈又不动布局。
 */
@Composable
fun HomeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val paint = palettePaint(LocalThemePalette.current)
    val border = paint.componentBorder
    val outer = modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.PageGutter, vertical = Spacing.Xs)
        .onFocusChanged { onFocusChanged(it.isFocused) }

    if (border == Color.Unspecified) {
        PaperInkSearchField(query, onQueryChange, onSubmit, outer)
    } else {
        AnimalSearchField(query, onQueryChange, onSubmit, paint, outer)
    }
}

/** 纸墨：凹陷纸面、无框、聚焦浮出主色描边。 */
@Composable
private fun PaperInkSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = {
            Text("搜索笔记和标签", style = ZhiLuType.bodySmall)
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                AppIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "清空搜索",
                    onClick = { onQueryChange("") }
                )
            }
        },
        singleLine = true,
        textStyle = ZhiLuType.bodySmall,
        shape = RoundedCornerShape(percent = 50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() })
    )
}

/**
 * 动森：胶囊 + 描边 + 底部厚度。
 *
 * 不用 `OutlinedTextField`：M3 的输入框把描边和容器都焊在内部，做不出"底下一层实体"
 * 这种结构（`shadow()` 是虚的，给不了厚度感）。这里用 `BasicTextField` 自己搭。
 */
@Composable
private fun AnimalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    paint: PalettePaint,
    modifier: Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(percent = 50)
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else paint.componentBorder
    val thickness = SearchFieldThickness

    Box(modifier = modifier) {
        Column {
            Spacer(Modifier.height(thickness))
            // 厚度层与面层**同高**，只是面层上移一个 thickness —— 布局盒的总高固定，
            // 所以聚焦换描边色时不会有任何位移。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SearchFieldHeight)
                    .background(shade(MaterialTheme.colorScheme.surfaceVariant), shape)
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(SearchFieldHeight)
                .onFocusChanged { focused = it.isFocused },
            singleLine = true,
            textStyle = ZhiLuType.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                        .border(2.5.dp, borderColor, shape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "搜索笔记和标签",
                                style = ZhiLuType.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                    if (query.isNotEmpty()) {
                        AppIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "清空搜索",
                            onClick = { onQueryChange("") }
                        )
                    }
                }
            }
        )
    }
}

/** 动森搜索框：面高与底部厚度（参考仓库 MIDDLE 档是 40dp + 3dp）。 */
private val SearchFieldHeight = 44.dp
private val SearchFieldThickness = 3.dp