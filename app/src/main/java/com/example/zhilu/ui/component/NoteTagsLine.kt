package com.example.zhilu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 列表行里的标签：圆角胶囊，至多 [max] 个，多出的收成一个「+N」。
 *
 * 胶囊底用标签色的一层浅 tint（而不是不透明的标签色），文字仍走 `onSurfaceVariant`——
 * 这样既有颜色线索，又不用为每个标签色单独验一遍对比度，深浅主题都稳。
 *
 * 宽度处理：每个胶囊 `weight(1f, fill = false)`，短标签只占自己需要的宽度，
 * 长标签最多也只能吃掉一半，于是两个标签怎么都不会把行撑爆。
 */
@Composable
fun NoteTagsLine(
    tags: List<Tag>,
    modifier: Modifier = Modifier,
    max: Int = 2
) {
    if (tags.isEmpty()) return
    val visible = tags.take(max)
    val overflowCount = tags.size - max

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visible.forEach { tag ->
            TagPill(tag = tag, modifier = Modifier.weight(1f, fill = false))
        }
        if (overflowCount > 0) {
            Text(
                text = "+$overflowCount",
                style = ZhiLuType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TagPill(
    tag: Tag,
    modifier: Modifier = Modifier
) {
    val accent = rememberTagAccent(tagColor = tag.color)
    NoteTagPillSurface(
        containerColor = accent.copy(alpha = AlphaTokens.Hover),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = tag.name,
            style = ZhiLuType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 胶囊壳：非交互元素，**不要**加 `minimumInteractiveComponentSize`——那会把列表行撑到 48dp 高。 */
@Composable
private fun NoteTagPillSurface(
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = Radius.Chip),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
