package com.example.zhilu.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 行内次要操作：小号文字按钮。
 *
 * 列表行的「取消 / 暂停 / 重命名」等都走它，保证次要动作在三处列表里
 * （提醒行 / 复习计划行 / 标签管理）的大小与留白一致。
 */
@Composable
fun QuietAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        style = ZhiLuType.chip,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(ShapeTokens.Pill))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
