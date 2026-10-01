package com.example.zhilu.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 列表行里的标签：纯文本、以「·」分隔，至多 [max] 个，多出的收成「+N」。
 *
 * 列表是高密度阅读场景，标签只作为分类线索；胶囊与彩色圆点留给标签页、搜索结果等可交互场景。
 */
@Composable
fun NoteTagsLine(
    tags: List<Tag>,
    modifier: Modifier = Modifier,
    max: Int = 2
) {
    if (tags.isEmpty()) return
    val visible = tags.take(max).joinToString(" · ") { it.name }
    val overflowCount = tags.size - max
    val text = if (overflowCount > 0) "$visible  +$overflowCount" else visible

    Text(
        text = text,
        style = ZhiLuType.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}