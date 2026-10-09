package com.example.zhilu.ui.home

import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.search.TagFilterItem
import com.example.zhilu.ui.search.TagQueryParser

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
    val dueReminderCount: Int = 0,
    /** 标签筛选条的候选（按笔记数降序）——标签页撤销后，这里是标签的唯一常驻出口。 */
    val tagFilters: List<TagFilterItem> = emptyList(),
    /** 当前选中的筛选标签（多选为 AND 语义）。 */
    val selectedTagIds: Set<Long> = emptySet()
) {
    /**
     * 是否处于搜索结果态（而非笔记列表态）。
     *
     * 选中有标签也算 —— 空关键词 + 标签就是老标签页「点标签看笔记」的等价物。
     */
    val isSearchActive: Boolean get() = query.isNotBlank() || selectedTagIds.isNotEmpty()

    /**
     * 输入框末尾 `#token` 命中的标签候选（点选即落成筛选 chip）。
     *
     * 现算而不是存成独立字段：候选完全由 [query] 与 [tagFilters] 决定，
     * 多存一份就多一个会忘记同步的状态。
     *
     * 只打一个 `#`（token 为空）时列出全部未选中的标签 —— 那是"我要挑一个"的手势。
     */
    val tagSuggestions: List<TagFilterItem>
        get() {
            val token = TagQueryParser.trailingToken(query) ?: return emptyList()
            return tagFilters
                .filter { it.tag.id !in selectedTagIds }
                .filter { it.tag.name.contains(token.name, ignoreCase = true) }
                .take(MAX_TAG_SUGGESTIONS)
        }

    /** 候选条是否该顶掉筛选条（两者同位置，见 `TagSuggestionRow` 的说明）。 */
    val showTagSuggestions: Boolean get() = tagSuggestions.isNotEmpty()

    companion object {
        /** 候选上限：条是横滑的，再多也没人滑到底，还会把结果列表挤下去。 */
        const val MAX_TAG_SUGGESTIONS = 8
    }
}