package com.example.zhilu.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.component.SegmentedToggle
import com.example.zhilu.ui.theme.Spacing

/** 首页列表头：统计与「列表 / 时间线」分段合并成一行。 */
@Composable
fun HomeListHeader(
    noteCount: Int,
    tagCount: Int,
    mediaCount: Int,
    viewMode: ViewMode,
    onSelectViewMode: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.PageGutter, vertical = Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
    ) {
        MetaLine(
            parts = listOf("$noteCount 知识", "$tagCount 标签", "$mediaCount 图片"),
            modifier = Modifier.weight(1f)
        )
        SegmentedToggle(
            options = listOf(
                ViewMode.LIST to "列表",
                ViewMode.TIMELINE to "时间线"
            ),
            selected = viewMode,
            onSelect = onSelectViewMode
        )
    }
}