package com.example.zhilu.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.Spacing

/** 编辑器内知识卡与「新增卡片」按钮共用的静息规格。 */
object AppCardStyle {
    val elevation = ElevationTokens.Card
    val borderWidth = 1.dp
}

/**
 * 统一阴影层级：替代散落的 0/0.5/4/6/8dp 字面量。
 */
object ElevationTokens {
    val Flat = 0.dp          // 平铺（标签、块）
    val Card = 0.dp          // 编辑器内的知识卡：靠聚焦边框分层，不用阴影
    val Raised = 2.dp        // 纸卡（时间线、设置分组）/ 悬浮 / 聚焦
    val Overlay = 8.dp       // FAB、拖拽拾起、菜单、弹层
}

/**
 * 纸卡：白纸底 + 柔和阴影，**无描边**。
 *
 * 「描边卡」这一档已从全站退场——卡片靠阴影分层、列表行靠发丝线分层，
 * 两者都能自证身份，不需要再叠一条 1dp 边框（那正是此前「表格感」的来源）。
 * 需要发丝线分隔的高密度列表请用 [DocumentRow]。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.CardPadding),
    outerPadding: PaddingValues = PaddingValues(
        horizontal = Spacing.PageGutter,
        vertical = Spacing.CardGap / 2
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(Radius.Card)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevation = CardDefaults.cardElevation(defaultElevation = ElevationTokens.Raised)
    val baseModifier = modifier
        .fillMaxWidth()
        .padding(outerPadding)

    // 同时需要点击与长按（如首页列表）时，用 combinedClickable 承载两种手势。
    val cardModifier = if (onLongClick != null) {
        baseModifier
            .clip(shape)
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick
            )
    } else {
        baseModifier
    }

    if (onClick != null && onLongClick == null) {
        Card(
            modifier = cardModifier,
            onClick = onClick,
            shape = shape,
            colors = colors,
            elevation = elevation
        ) {
            Column(modifier = Modifier.padding(contentPadding)) { content() }
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            elevation = elevation
        ) {
            Column(modifier = Modifier.padding(contentPadding)) { content() }
        }
    }
}