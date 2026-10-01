package com.example.zhilu.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.Spacing

/** 色书脊宽度。 */
internal val DocumentSpineWidth = 3.dp

/** 书脊到正文的间隙；与 [Spacing.PageGutter] 相加正好是正文的左边距。 */
internal val DocumentSpineGap = 17.dp

/**
 * 在节点左缘画一条通高的色书脊。
 *
 * 用 `drawBehind` 而非独立子项，因此天然贴着节点左缘、并随内容高度伸展，
 * 不需要 `IntrinsicSize` 这类会误判多行文本高度的测量技巧。
 */
internal fun Modifier.accentSpine(accent: Color): Modifier = drawBehind {
    drawRect(color = accent, size = Size(DocumentSpineWidth.toPx(), size.height))
}

/**
 * 文档行：贴边通高的色书脊 + 正文，无卡片、无描边、无阴影。
 *
 * 笔记列表、标签索引、回收站、提醒中心四处列表共用同一套行壳，
 * 避免每个页面各写一遍内边距与书脊逻辑。
 *
 * 行与行之间用 [ZhiLuDivider] 分隔，由调用方决定（最后一行不带线）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentRow(
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = when {
        onLongClick != null -> Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick
        )
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(interaction)
            .accentSpine(accent),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = DocumentSpineGap,
                    end = Spacing.PageGutter,
                    top = Spacing.Md,
                    bottom = Spacing.Md
                ),
            content = content
        )
    }
}