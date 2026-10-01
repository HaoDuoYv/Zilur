package com.example.zhilu.ui.tag

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.AppIconButton
import com.example.zhilu.ui.component.DocumentRow
import com.example.zhilu.ui.component.rememberTagAccent
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 标签索引行：整行点击 = 按此标签筛选，⋮ = 删除。
 *
 * 行由「标签名 + 笔记数」两级信息构成——单行纯文本会让整个索引退化成等距表格，
 * 而笔记数既能撑起行高，也是标签索引真正有用的信息。
 *
 * 破坏性操作（删除）**只走 ⋮ 显式菜单**，不提供滑动手势：标签数量少、滑动收益低，
 * 但一旦误触就是「标签关联被摘掉」，代价不对等。
 *
 * 筛选态下索引整体收起，所以这里不需要「当前选中」的视觉态——
 * 标签色身份由左侧书脊表达。
 */
@Composable
fun TagRow(
    tag: Tag,
    noteCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    DocumentRow(
        accent = rememberTagAccent(tagColor = tag.color),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tag.name,
                    style = ZhiLuType.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (noteCount > 0) "$noteCount 条笔记" else "暂无笔记",
                    style = ZhiLuType.meta,
                    color = LocalExtendedColors.current.inkFaint,
                    modifier = Modifier.padding(top = Spacing.RowGapTight)
                )
            }
            Box {
                AppIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "更多操作",
                    onClick = { menuOpen = true }
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "删除标签",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
