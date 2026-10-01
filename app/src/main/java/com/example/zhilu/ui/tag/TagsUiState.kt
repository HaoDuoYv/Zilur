package com.example.zhilu.ui.tag

import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag

data class TagsUiState(
    val tags: List<Tag> = emptyList(),
    val selectedTag: Tag? = null,
    val filteredNotes: List<Note> = emptyList(),
    val noteCount: Int = 0,
    /** 每个标签下的未删除笔记数，供标签索引行显示二级信息。未命中的标签按 0 处理。 */
    val noteCountByTag: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val isLoadingNotes: Boolean = false,
    val error: String? = null
)
