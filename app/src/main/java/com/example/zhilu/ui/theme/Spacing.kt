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

    /**
     * 列表行节奏。
     *
     * 行内四个层级（标题 / 摘要 / 标签 / 元信息）靠留白梯度区分，而不是靠字号硬拉：
     * 组内最紧（[RowGapTight]）→ 跨组最松（[RowGapGroup]）→ 组内从属居中（[RowGapMeta]）。
     * 行上下用 [RowVertical] 兜住，与卡片内边距 [CardPadding] 取同一数值，
     * 这样列表视图与时间线视图切换时行距不会跳动。
     */
    val RowVertical = 16.dp   // 列表行上下内边距
    val RowGapTight = 6.dp    // 行内同组：标题 ↔ 摘要
    val RowGapGroup = 10.dp   // 行内跨组：摘要 ↔ 标签
    val RowGapMeta = 8.dp     // 行内从属：标签 ↔ 元信息
    val ListTopGap = 4.dp     // 头部与列表之间的呼吸
    val ListBottomGap = 24.dp // 列表底部留白
}