package com.example.zhilu.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.palettePaint

/**
 * 弹层底部的**整宽居中**动作按钮（如「管理 AI 配置」）。
 *
 * 为什么不做成右下角的文字按钮：底部弹层里右下角的位置既是拇指最难够到的角，
 * 又容易被误当成"确定/完成"这类关闭语义。做成整宽居中的一条，语义清楚、也好点。
 *
 * 两套外观各走各的材质：
 * - **纸墨**：浅凹底、无描边（它一贯靠底色分层）；
 * - **动森**：描边 + 更大圆角 + 主色文字（它靠描边立形状）。
 * 所以这里读的是 `componentBorder`，与卡片/输入框同一套判据。
 */
@Composable
fun AppSheetAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val paint = palettePaint(LocalThemePalette.current)
    val bordered = paint.componentBorder != Color.Unspecified
    val shape = RoundedCornerShape(if (bordered) Radius.CardAnimalIsland else Radius.Field)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = if (bordered) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (bordered) BorderStroke(2.dp, paint.componentBorder) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(Spacing.Sm))
            }
            Text(
                text = text,
                style = ZhiLuType.chip,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
