package com.example.zhilu.ui.tag

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/** 当前筛选状态提示条，含一键取消。 */
@Composable
fun TagFilterBanner(
    tagName: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClear,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.PageGutter, vertical = Spacing.Xs)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "正在筛选：$tagName",
                style = ZhiLuType.chip,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "取消筛选"
            )
        }
    }
}