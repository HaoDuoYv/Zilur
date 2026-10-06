package com.example.zhilu.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalDensity
import com.example.zhilu.ui.theme.animalHandDrawnShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.palettePaint

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
 *
 * **动森外观下这条规则反过来**：那里是靠描边 + 暖褐投影 + 大圆角立形状的。
 * 两种做法不是谁对谁错，而是"纸"与"塑料"两种材质 —— 由 [PalettePaint.componentBorder]
 * 是否为 [Color.Unspecified] 决定，组件不各自判断。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /**
     * 用「手作圆角」形状（四角半径刻意不等 + 边缘微凸），只对动森生效。
     *
     * 默认关：整屏卡片都用会显得乱。它适合**孤立的面** —— 设置分组、空状态卡这类
     * 一屏只有一两张的地方。参考仓库也正是这么用的（只有标题卡和弹层用它）。
     */
    irregular: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(Spacing.CardPadding),
    outerPadding: PaddingValues = PaddingValues(
        horizontal = Spacing.PageGutter,
        vertical = Spacing.CardGap / 2
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    val paint = palettePaint(LocalThemePalette.current)
    val darkTheme = LocalExtendedColors.current.isDark
    val border = paint.componentBorder
    val isAnimal = border != Color.Unspecified
    val density = LocalDensity.current
    val shape = when {
        // 动森 + 手作：四角不等半径、边缘微凸（参考仓库 TITLE 卡与弹层的做法）
        isAnimal && irregular -> animalHandDrawnShape(density)
        // 动森的卡片圆角更大（参考仓库是 20dp）
        isAnimal -> RoundedCornerShape(Radius.CardAnimalIsland)
        else -> RoundedCornerShape(Radius.Card)
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    // 动森走 `Modifier.shadow` 而不是 `CardDefaults.cardElevation`：只有前者能指定
    // ambientColor/spotColor。**暖褐投影**是参考仓库的关键一笔，中性黑压在米白底上会发脏。
    val elevation = CardDefaults.cardElevation(
        defaultElevation = if (isAnimal) 0.dp else ElevationTokens.Raised
    )
    val baseModifier = modifier
        .fillMaxWidth()
        .padding(outerPadding)
        .then(
            if (isAnimal) {
                Modifier
                    .shadow(
                        elevation = 6.dp,
                        shape = shape,
                        ambientColor = paint.cardShadow,
                        spotColor = paint.cardShadow
                    )
                    .border(2.dp, border.copy(alpha = if (darkTheme) 0.7f else 1f), shape)
            } else {
                Modifier
            }
        )

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