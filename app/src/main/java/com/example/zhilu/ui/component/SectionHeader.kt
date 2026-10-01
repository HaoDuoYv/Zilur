package com.example.zhilu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 分节标题：衬线小标题 + 可选右侧计数/操作。
 *
 * @param containerColor 置为不透明底色后可作为 `stickyHeader` 使用，吸附时不会透出下方内容。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (containerColor != Color.Unspecified) Modifier.background(containerColor) else Modifier
            )
            .padding(horizontal = Spacing.PageGutter, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = ZhiLuType.sectionTitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}