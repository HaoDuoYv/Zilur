package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.markup.InlineBrush
import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.LocalAccessibleEmphasis
import com.example.zhilu.ui.theme.emphasisToneColor

/**
 * 划词起色的浮动工具条（设计文档 §3.8）。
 *
 * 三种状态（同一套 UI，只改高亮与旗标）：
 * 1. **有选区**：4 个色块都不选中，点谁给谁上色；
 * 2. **光标在标记内**：对应色块 + 笔触项选中 —— 这是用户唯一"看得见格式"的入口；
 * 3. **标记中**（没选区时点了色块）：色块选中 + 「标记中」旗标，之后输入的字自动落入。
 *
 * 用户全程**不需要输入任何标记字符**：点一下就写好了。
 */
@Composable
fun InlineMarkToolbar(
    currentTone: EmphasisTone?,
    currentBrush: InlineBrush,
    isPending: Boolean,
    darkTheme: Boolean,
    onPickTone: (EmphasisTone) -> Unit,
    onPickBrush: (InlineBrush) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    /** 额外动作位（如"把这条行内公式提升为公式块"）。为空时不占位。 */
    extraAction: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ShapeTokens.Medium),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (tone in EmphasisTone.entries) {
                ToneSwatch(
                    tone = tone,
                    selected = currentTone == tone,
                    darkTheme = darkTheme,
                    onClick = { onPickTone(tone) }
                )
            }

            Separator()

            BrushButton(
                brush = InlineBrush.HIGHLIGHT,
                selected = currentBrush == InlineBrush.HIGHLIGHT && currentTone != null,
                label = "荧光笔"
            ) { onPickBrush(InlineBrush.HIGHLIGHT) }
            BrushButton(
                brush = InlineBrush.COLOR,
                selected = currentBrush == InlineBrush.COLOR && currentTone != null,
                label = "只变色"
            ) { onPickBrush(InlineBrush.COLOR) }
            BrushButton(
                brush = InlineBrush.UNDERLINE,
                selected = currentBrush == InlineBrush.UNDERLINE && currentTone != null,
                label = "下划线"
            ) { onPickBrush(InlineBrush.UNDERLINE) }

            // 加粗是第 4 个"笔触"（§10 P3）。它不带语义色，所以放在分隔线之后、
            // 与三个语义笔触并列，而不是混进左边那排角色色块里。
            BrushButton(
                brush = InlineBrush.BOLD,
                selected = currentBrush == InlineBrush.BOLD,
                label = "加粗"
            ) { onPickBrush(InlineBrush.BOLD) }

            Separator()

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .semantics { contentDescription = "清除标记" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(16.dp)
                )
            }

            // 额外动作（如"转为公式块"）：只在光标落在那类原子上时才由调用方传进来
            if (extraAction != null) {
                Separator()
                extraAction()
            }

            if (isPending) {
                Text(
                    text = "标记中",
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            emphasisToneColor(
                                currentTone ?: EmphasisTone.KEY,
                                darkTheme,
                                LocalAccessibleEmphasis.current
                            )
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ToneSwatch(
    tone: EmphasisTone,
    selected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit
) {
    val color = emphasisToneColor(tone, darkTheme, LocalAccessibleEmphasis.current)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.inverseOnSurface else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "标记为${tone.label}" },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text(
                text = tone.label.take(1),
                style = ZhiLuType.label,
                color = Color.White
            )
        }
    }
}

@Composable
private fun BrushButton(
    brush: InlineBrush,
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.22f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (brush) {
                InlineBrush.HIGHLIGHT -> Icons.Default.Highlight
                InlineBrush.COLOR -> Icons.Default.FormatColorText
                InlineBrush.UNDERLINE -> Icons.Default.FormatUnderlined
                InlineBrush.BOLD -> Icons.Default.FormatBold
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun Separator() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 16.dp)
            .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.3f))
    )
}
