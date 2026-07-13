package com.example.zhilu.domain.model

data class KnowledgeCard(
    val id: Long = 0,
    val title: String = "",
    val blocks: List<Block> = emptyList(),
    val isExpanded: Boolean = true,
    val isFocused: Boolean = false
)
