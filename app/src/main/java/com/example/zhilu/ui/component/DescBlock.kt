package com.example.zhilu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 卡内说明块（原型 `task-card-desc` 的形态）：浅底 + 左侧 3dp 色条。
 *
 * 两处使用同一形态、同一实现：
 * - 待办卡（[com.example.zhilu.ui.review.TodoTaskCard]）用它标出来源笔记；
 * - 提醒卡（[com.example.zhilu.ui.reminder.ReminderRow]）用它放「由复习计划驱动…」引导语。
 *
 * 行高取「最小固有高度」：左缘色条用 fillMaxHeight 就能与文本块同高，
 * 不必手算一个假高度（文本样式改了也会自动跟上）。
 */
@Composable
internal fun DescBlock(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant)
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(scheme.outlineVariant)
        )
        Text(
            text = text,
            style = ZhiLuType.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        )
    }
}
