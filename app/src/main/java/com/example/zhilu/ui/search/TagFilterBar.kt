package com.example.zhilu.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.TagChip
import com.example.zhilu.ui.component.TagChipSize
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/** 筛选条里的一项：标签 + 其下未删除笔记数（按笔记数降序排列）。 */
data class TagFilterItem(
    val tag: Tag,
    val noteCount: Int
)

/**
 * 标签筛选条：搜索框下方的横滑 chips。
 *
 * 标签并入搜索之后的**唯一常驻出口**：点选 = 加筛选（多选为 AND 语义，见 `NoteDao.searchWithTags`），
 * 长按 = 就地重命名 / 删除，条尾「管理」= 标签管理弹层。
 */
@Composable
fun TagFilterBar(
    items: List<TagFilterItem>,
    selectedTagIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onClearAll: () -> Unit,
    onManage: () -> Unit,
    onRename: (Tag) -> Unit,
    onDelete: (Tag) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Text(
            text = "还没有标签，在笔记里添加第一个标签",
            style = ZhiLuType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.PageGutter, vertical = Spacing.Xs)
        )
        return
    }

    // 哪个 chip 的长按菜单在展开（纯 UI 瞬时态，不进 ViewModel）。
    var menuTagId by remember { mutableStateOf<Long?>(null) }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.PageGutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedTagIds.isNotEmpty()) {
            item(key = "tag-filter-clear") {
                OutlineActionChip(label = "清除", onClick = onClearAll)
            }
        }
        items(items = items, key = { "tag-filter-${it.tag.id}" }) { item ->
            Box {
                TagChip(
                    tag = item.tag,
                    selected = item.tag.id in selectedTagIds,
                    size = TagChipSize.Sm,
                    onClick = { onToggle(item.tag.id) },
                    onLongClick = { menuTagId = item.tag.id }
                )
                DropdownMenu(
                    expanded = menuTagId == item.tag.id,
                    onDismissRequest = { menuTagId = null }
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名", style = ZhiLuType.bodySmall) },
                        onClick = {
                            menuTagId = null
                            onRename(item.tag)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", style = ZhiLuType.bodySmall) },
                        onClick = {
                            menuTagId = null
                            onDelete(item.tag)
                        }
                    )
                }
            }
        }
        item(key = "tag-filter-manage") {
            OutlineActionChip(label = "管理", onClick = onManage)
        }
    }
}

/** 条首「清除」/ 条尾「管理」用的小描边胶囊（与标签 chip 同高、无颜色点）。 */
@Composable
private fun OutlineActionChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.minimumInteractiveComponentSize(),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 0.dp
    ) {
        Text(
            text = label,
            style = ZhiLuType.chip,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
