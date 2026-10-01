package com.example.zhilu.ui.tag

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 标签索引行：整行点击 = 按此标签筛选，⋮ = 删除。
 *
 * 筛选态下索引整体收起，所以这里不需要「当前选中」的视觉态——
 * 标签色身份由左侧书脊表达。
 */
@Composable
fun TagRow(
    tag: Tag,
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
            Text(
                text = tag.name,
                style = ZhiLuType.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
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