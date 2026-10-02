package com.example.zhilu.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.component.AppIconButton
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 编辑页顶栏：保存状态细胶囊 + 复习 / 查找 / 目录 / 分享 / 编辑操作。
 * 复习入口做成胶囊常驻，到期时点上圆点，点开才是底部 sheet。
 *
 * **动作位是有预算的**：标题列 = 屏宽 − 返回键 − 动作位。这一栏在只读态最多会同时出现
 * 5 个入口（复习胶囊 + 查找 + 目录 + 分享 + 编辑），实测会把「知识详情」压成「知识详…」。
 * 所以：① 动作位统一 40dp；② 分享这类低频动作收进溢出菜单。新增入口前先算这笔账。
 */
@Composable
fun NoteTopBar(
    title: String,
    isEditing: Boolean,
    saveStatus: SaveStatus,
    showReview: Boolean,
    isReviewDue: Boolean,
    /** 卡片数 ≥ 3 时才露出目录入口 —— 少内容时不打扰（§6.1）。 */
    showOutline: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onStartEditing: () -> Unit,
    onReviewClick: () -> Unit,
    onOpenOutline: () -> Unit,
    onOpenFind: () -> Unit
) {
    var overflowOpen by remember { mutableStateOf(false) }

    AppTopBar(
        title = title,
        onBack = onBack,
        actions = {
            NoteSaveStatus(status = saveStatus)
            // 查找常驻：读的时候同样要找（§6.4）
            AppIconButton(
                icon = Icons.Default.Search,
                contentDescription = "查找",
                onClick = onOpenFind,
                modifier = Modifier.size(TopBarActionSize)
            )
            if (isEditing) {
                if (showOutline) {
                    AppIconButton(
                        icon = Icons.AutoMirrored.Filled.List,
                        contentDescription = "目录",
                        onClick = onOpenOutline,
                        modifier = Modifier.size(TopBarActionSize)
                    )
                }
                AppIconButton(
                    icon = Icons.Default.Check,
                    contentDescription = "保存",
                    onClick = onSave,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(TopBarActionSize)
                )
            } else {
                if (showReview) {
                    ReviewPill(isDue = isReviewDue, onClick = onReviewClick)
                }
                if (showOutline) {
                    AppIconButton(
                        icon = Icons.AutoMirrored.Filled.List,
                        contentDescription = "目录",
                        onClick = onOpenOutline,
                        modifier = Modifier.size(TopBarActionSize)
                    )
                }
                Box {
                    AppIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        onClick = { overflowOpen = true },
                        modifier = Modifier.size(TopBarActionSize)
                    )
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("分享") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null)
                            },
                            onClick = {
                                overflowOpen = false
                                onShare()
                            }
                        )
                    }
                }
                AppIconButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "编辑",
                    onClick = onStartEditing,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(TopBarActionSize)
                )
            }
        }
    )
}

/** 顶栏动作位尺寸。比 Material 默认的 48dp 窄一档，换取标题不被挤到折行。 */
private val TopBarActionSize = 40.dp

@Composable
private fun ReviewPill(
    isDue: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isDue) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ShapeTokens.Pill),
        color = accent.copy(alpha = if (isDue) 0.12f else 0.06f),
        contentColor = accent
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isDue) {
                Spacer(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accent, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text = "复习", style = ZhiLuType.label, color = accent)
        }
    }
}
