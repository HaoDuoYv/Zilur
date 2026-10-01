package com.example.zhilu.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距节奏 token。
 *
 * 数值阶梯以 4 为基数；语义别名把全站此前混用的 16/20、6/8 收敛为唯一定义，
 * 布局代码应优先使用语义别名，仅在确无对应语义时才用数值阶梯。
 */
object Spacing {
    // 数值阶梯
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val Xxxl = 48.dp

    // 语义别名
    val PageGutter = 20.dp    // 页面左右统一留白
    val CardPadding = 16.dp   // 卡片内边距
    val CardGap = 10.dp       // 卡片之间
    val SectionGap = 28.dp    // 分节之间
    val ListRowGap = 8.dp     // 列表行之间
    val RowMinHeight = 56.dp  // 列表行最小高度（触控）
    val GutterTap = 24.dp     // 块插入条触控高度
}