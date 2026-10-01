package com.example.zhilu.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.component.AppIconButton
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 编辑页顶栏：保存状态细胶囊 + 复习 / 保存 / 分享 / 编辑操作。
 * 复习入口做成胶囊常驻，到期时点上圆点，点开才是底部 sheet。
 */
@Composable
fun NoteTopBar(
    title: String,
    isEditing: Boolean,
    saveStatus: SaveStatus,
    showReview: Boolean,
    isReviewDue: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onStartEditing: () -> Unit,
    onReviewClick: () -> Unit
) {
    AppTopBar(
        title = title,
        onBack = onBack,
        actions = {
            NoteSaveStatus(status = saveStatus)
            if (isEditing) {
                AppIconButton(
                    icon = Icons.Default.Check,
                    contentDescription = "保存",
                    onClick = onSave,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                if (showReview) {
                    ReviewPill(isDue = isReviewDue, onClick = onReviewClick)
                }
                AppIconButton(
                    icon = Icons.Default.Share,
                    contentDescription = "分享",
                    onClick = onShare
                )
                AppIconButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "编辑",
                    onClick = onStartEditing,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    )
}

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
                .padding(horizontal = 12.dp, vertical = 6.dp),
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