package com.example.zhilu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 共享圆角 token。数值阶梯用于通用场景，[Radius] 为语义别名，优先使用。
 */
object ShapeTokens {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val ExtraLarge = 24.dp
    // Pill: passed as a percent to RoundedCornerShape(ShapeTokens.Pill) for full capsules.
    val Pill = 50
}

/**
 * 语义圆角：字段 / 卡片 / 底部弹层 / 胶囊 / 输入区。
 */
object Radius {
    val Field = 10.dp
    val Card = 14.dp
    val Sheet = 20.dp

    /**
     * 对话输入区容器。比 [Sheet] 再大一档：它内部同时容纳附件、多行正文与工具行，
     * 圆角要大到让「一个整体的面」读得出来，小了就会跟正文的段落块混在一起。
     */
    val Composer = 22.dp
    const val Chip = 50  // percent
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(ShapeTokens.ExtraSmall),
    small = RoundedCornerShape(ShapeTokens.Small),
    medium = RoundedCornerShape(Radius.Card),
    large = RoundedCornerShape(ShapeTokens.Large),
    extraLarge = RoundedCornerShape(ShapeTokens.ExtraLarge)
)