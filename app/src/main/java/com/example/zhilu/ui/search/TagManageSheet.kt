package com.example.zhilu.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.QuietAction
import com.example.zhilu.ui.component.rememberTagAccent
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

private val PillShape = RoundedCornerShape(ShapeTokens.Pill)

/**
 * 标签管理弹层：新建 + 逐个重命名 / 删除 + **多选合并**（含笔记数）。
 *
 * 标签页撤销后，这里补上它剩下的"管理"职责；"用标签筛选"则由搜索页的
 * 筛选条承担（见 [TagFilterBar]）。
 *
 * 「合并」进入选择模式：勾两个以上 → 选一个保留 → 其余删除、关联改挂（见 [TagMergeDialog]）。
 * 重命名只能一次纠一个名字，合并才是收拾"同义标签"（"论文 / 论文资料 / 论文素材"）的正路。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManageSheet(
    items: List<TagFilterItem>,
    onRename: (Tag) -> Unit,
    onDelete: (Tag) -> Unit,
    onCreate: (String) -> Unit,
    onMerge: (sourceIds: List<Long>, targetId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptyList<Long>()) }
    var mergePicking by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.PageGutter)
                .padding(bottom = Spacing.Xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "标签管理",
                    style = ZhiLuType.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // 少于两个标签没有可合并的对象，入口直接不出现（点了也只会得到禁用按钮）
                if (items.size >= 2) {
                    QuietAction(
                        label = if (selecting) "取消" else "合并",
                        onClick = {
                            selecting = !selecting
                            if (!selecting) selectedIds = emptyList()
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("新标签名", style = ZhiLuType.bodySmall) },
                    singleLine = true,
                    // 走 MaterialTheme.shapes（跟外观走）：纸墨 14dp、动森 20dp。
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onCreate(newName)
                        newName = ""
                    },
                    enabled = newName.isNotBlank(),
                    shape = PillShape
                ) {
                    Text("新建", style = ZhiLuType.chip)
                }
            }
            if (items.isEmpty()) {
                Text(
                    text = "还没有标签。在笔记里添加标签，或在上方输入名称新建。",
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (selecting) {
                    Text(
                        text = "勾选两个以上标签，选一个保留，其余会被合并掉。",
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(items = items, key = { it.tag.id }) { item ->
                        TagManageRow(
                            item = item,
                            selecting = selecting,
                            checked = item.tag.id in selectedIds,
                            onToggleSelect = {
                                selectedIds = if (item.tag.id in selectedIds) {
                                    selectedIds - item.tag.id
                                } else {
                                    selectedIds + item.tag.id
                                }
                            },
                            onRename = { onRename(item.tag) },
                            onDelete = { onDelete(item.tag) }
                        )
                    }
                }
                if (selecting) {
                    Button(
                        onClick = { mergePicking = true },
                        enabled = selectedIds.size >= 2,
                        shape = PillShape
                    ) {
                        Text(
                            text = "合并所选（${selectedIds.size}）",
                            style = ZhiLuType.chip
                        )
                    }
                }
            }
        }
    }

    if (mergePicking) {
        TagMergeDialog(
            items = items.filter { it.tag.id in selectedIds },
            onConfirm = { targetId ->
                onMerge(selectedIds, targetId)
                mergePicking = false
                selecting = false
                selectedIds = emptyList()
            },
            onDismiss = { mergePicking = false }
        )
    }
}

@Composable
private fun TagManageRow(
    item: TagFilterItem,
    selecting: Boolean,
    checked: Boolean,
    onToggleSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selecting) {
                    Modifier.clickable(onClick = onToggleSelect)
                } else {
                    Modifier
                }
            )
            .padding(vertical = Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
    ) {
        if (selecting) {
            // 选择模式下勾选框接管行首，色点让位（少一个视觉噪声，勾选状态就是全部信息）
            Checkbox(checked = checked, onCheckedChange = null)
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(rememberTagAccent(item.tag.color))
            )
        }
        Text(
            text = item.tag.name,
            style = ZhiLuType.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${item.noteCount} 条笔记",
            style = ZhiLuType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!selecting) {
            QuietAction(label = "重命名", onClick = onRename)
            QuietAction(label = "删除", onClick = onDelete)
        }
    }
}
