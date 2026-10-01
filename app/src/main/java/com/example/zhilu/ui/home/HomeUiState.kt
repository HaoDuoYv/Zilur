package com.example.zhilu.ui.home

import com.example.zhilu.domain.model.Note

enum class ViewMode {
    LIST,
    TIMELINE
}

data class HomeUiState(
    val notes: List<Note> = emptyList(),
    val noteCount: Int = 0,
    val tagCount: Int = 0,
    val mediaCount: Int = 0,
    val viewMode: ViewMode = ViewMode.LIST,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** 搜索关键词；非空时列表切换为搜索结果。 */
    val query: String = "",
    val searchResults: List<Note> = emptyList(),
    val isSearching: Boolean = false,
    /** 最近搜索词，仅存活于内存，最多 [HomeViewModel.MAX_RECENT_QUERIES] 条。 */
    val recentQueries: List<String> = emptyList(),
    /** 已逾期与今天到期的提醒数，供顶栏铃铛展示状态圆点。 */
    val dueReminderCount: Int = 0
) {
    /** 是否处于搜索结果态（而非笔记列表态）。 */
    val isSearchActive: Boolean get() = query.isNotBlank()
}