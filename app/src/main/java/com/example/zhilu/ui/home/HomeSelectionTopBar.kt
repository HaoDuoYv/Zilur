package com.example.zhilu.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.zhilu.ui.component.AppButton
import com.example.zhilu.ui.component.AppIconButton
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.QuietAction

/**
 * 列表多选态的顶栏。
 *
 * 换成顶栏而不是在底部加一条批量操作栏：底部那一条会与全局底栏（含中央 ＋）
 * 上下叠在一起，既挤又容易点错；顶栏是"当前页在做什么"的位置，切状态本来就该在这里体现。
 *
 * 左「退出」、标题「已选 N 项」、右侧「全选/取消全选」+「导出 .dtk」——
 * 导出是当前唯一的批量动作，所以直接摊在顶栏上，不再套一层溢出菜单。
 */
@Composable
fun HomeSelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onExport: () -> Unit
) {
    AppTopBar(
        title = if (selectedCount == 0) "选择笔记" else "已选 $selectedCount 项",
        leading = {
            AppIconButton(
                icon = Icons.Default.Close,
                contentDescription = "退出选择",
                onClick = onClose
            )
        },
        actions = {
            QuietAction(
                label = if (allSelected) "取消全选" else "全选",
                onClick = onToggleSelectAll
            )
            // 一个都没选时导出没有意义；禁用比"点了弹一句提示"更直接
            AppButton(onClick = onExport, enabled = selectedCount > 0) {
                Text("导出 .dtk")
            }
        }
    )
}
