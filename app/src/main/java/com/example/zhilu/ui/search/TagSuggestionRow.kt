package com.example.zhilu.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.zhilu.ui.component.TagChip
import com.example.zhilu.ui.component.TagChipSize
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 输入 `#` 时的标签候选条。
 *
 * 与 [TagFilterBar] **同高、同起止**，因为两者在界面上是互斥的：正在挑标签时
 * 只该看到候选（点一下就落成 chip），挑完候选消失、筛选条带着新 chip 回来 ——
 * 两排 chip 同时挂着会让人分不清"哪个才是已经选上的"。
 *
 * 候选由 `HomeUiState.tagSuggestions` 从输入框末尾的 `#token` 现算，本组件不做匹配。
 */
@Composable
fun TagSuggestionRow(
    suggestions: List<TagFilterItem>,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.PageGutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "tag-suggestion-label") {
            Text(
                text = "按 # 选标签",
                style = ZhiLuType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(items = suggestions, key = { "tag-suggestion-${it.tag.id}" }) { item ->
            TagChip(
                tag = item.tag,
                selected = false,
                size = TagChipSize.Sm,
                onClick = { onPick(item.tag.id) }
            )
        }
    }
}
