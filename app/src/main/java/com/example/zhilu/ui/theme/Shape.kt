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
 * 语义圆角：字段 / 卡片 / 底部弹层 / 胶囊。
 */
object Radius {
    val Field = 10.dp
    val Card = 14.dp
    val Sheet = 20.dp
    const val Chip = 50  // percent
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(ShapeTokens.ExtraSmall),
    small = RoundedCornerShape(ShapeTokens.Small),
    medium = RoundedCornerShape(Radius.Card),
    large = RoundedCornerShape(ShapeTokens.Large),
    extraLarge = RoundedCornerShape(ShapeTokens.ExtraLarge)
)