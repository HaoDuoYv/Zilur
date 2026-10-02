package com.example.zhilu.ui.note.knowledge

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 知识卡片头部（设计文档 §5.1）。
 *
 * ```
 * ┌────┐
 * │ 01 │  惯性定理与规范形            ▾  (5)
 * └────┘  用正交变换把二次型化为标准形…
 * ```
 *
 * 序号码徽标取代了原来的 📌 图钉：图钉只说明"这是一张卡"，而序号同时是
 * **区分度**（卡片之间的身份）、**目录锚点**与**索引轨标签**——一物三用。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardHeader(
    title: String,
    onTitleChange: (String) -> Unit,
    cardIndex: Int,
    accent: Color,
    blockCount: Int,
    summary: String?,
    isCollapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onToggleAllCollapsed: () -> Unit = {},
    readOnly: Boolean,
    showDelete: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
    showCite: Boolean = false,
    onCiteToAi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 卡片标题 18sp 衬线：与小点正文 body 16sp 拉开，扫视时能快速切分章节（§5.3）
    val textStyle = ZhiLuType.cardTitle.copy(
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onSurface
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        CardIdentityBadge(index = cardIndex, accent = accent)
        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (readOnly || isCollapsed) {
                    Text(
                        text = title.ifBlank { "未命名小节" },
                        style = textStyle,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    BasicTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        modifier = Modifier.weight(1f),
                        textStyle = textStyle,
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (title.isEmpty()) {
                                Text(
                                    text = "输入小节名称(如:情况一)...",
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = AlphaTokens.Hint)
                                )
                            }
                            innerTextField()
                        }
                    )
                }
                if (blockCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$blockCount 点",
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // 摘要行：折叠态要它（扫描视图的骨架），浏览态要它；
            // 编辑态且已聚焦时隐藏 —— 不占位，避免光标进入时整块跳动（§5.3）。
            if (summary != null && (isCollapsed || readOnly)) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        if (showCite) {
            IconButton(onClick = onCiteToAi, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "引用到 AI",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showDelete) {
            IconButton(
                onClick = onDelete,
                enabled = canDelete,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = if (canDelete) "删除小节" else "至少保留一个小节",
                    tint = if (canDelete) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Disabled)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(ShapeTokens.ExtraSmall))
                // 长按折叠箭头 = 全部折叠 / 全部展开：省掉常态多一个按钮（§5.4）
                .combinedClickable(
                    onClick = onToggleCollapsed,
                    onLongClick = onToggleAllCollapsed
                )
                .semantics {
                    contentDescription = if (isCollapsed) "展开小节" else "折叠小节"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (isCollapsed) -90f else 0f)
            )
        }
    }
}

/**
 * 序号码徽标：衬线两位补零，底色为卡片身份色 12%。
 *
 * 只作用于徽标底色 / 心线 / 折叠竖条三处，**不做整卡染色**，纸面保持干净（§5.2）。
 */
@Composable
fun CardIdentityBadge(
    index: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(ShapeTokens.Small))
            .background(accent.copy(alpha = 0.12f))
            .semantics { contentDescription = "第 ${index + 1} 个小节" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            style = ZhiLuType.cardTitle,
            color = accent
        )
    }
}
