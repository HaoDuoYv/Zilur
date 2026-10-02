package com.example.zhilu.domain.model

data class KnowledgeCard(
    val id: Long = 0,
    val title: String = "",
    val blocks: List<Block> = emptyList(),
    val isExpanded: Boolean = true,
    val isFocused: Boolean = false,
    /**
     * 卡片身份色（`TagColors` 批次的一个色值，见 `CardAccentRotation`）。
     *
     * `null` = 用户没改过 → 渲染时按卡片序号回退到轮转色（**不写库**）。
     * 用可空而不是哨兵值：`0` 是合法的 `Color(0x00000000)`，
     * 拿它当"未设置"会在将来某天被误当成一个真色值。
     */
    val accent: Int? = null
)
