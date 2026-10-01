package com.example.zhilu.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.component.AnimatedListItem
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.NoteRow
import com.example.zhilu.ui.component.NoteRowVariant
import com.example.zhilu.ui.component.SectionHeader
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.theme.Spacing

/**
 * 笔记列表内容。
 *
 * 列表视图 = 紧凑文档行（无卡片外观，发丝线按正文缩进分隔）；
 * 时间线视图 = 白纸卡片 + 吸附分节标题。两种模式结构不同，切换才看得出差别。
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.homeNoteList(
    notes: List<Note>,
    viewMode: ViewMode,
    onOpenNote: (Long) -> Unit,
    onToggleFavorite: (Note) -> Unit,
    onDeleteRequest: (Note) -> Unit
) {
    if (viewMode == ViewMode.TIMELINE) {
        groupNotesByTimeline(notes).forEach { group ->
            stickyHeader(key = "header-${group.label}") {
                SectionHeader(
                    title = group.label,
                    containerColor = MaterialTheme.colorScheme.background
                )
            }
            itemsIndexed(group.notes, key = { _, note -> note.id }) { index, note ->
                AnimatedListItem(index = index) {
                    HomeNoteItem(
                        note = note,
                        variant = NoteRowVariant.Card,
                        onClick = { onOpenNote(note.id) },
                        onToggleFavorite = { onToggleFavorite(note) },
                        onDeleteRequest = { onDeleteRequest(note) }
                    )
                }
            }
        }
    } else {
        itemsIndexed(notes, key = { _, note -> note.id }) { index, note ->
            AnimatedListItem(index = index) {
                Column {
                    HomeNoteItem(
                        note = note,
                        variant = NoteRowVariant.Document,
                        onClick = { onOpenNote(note.id) },
                        onToggleFavorite = { onToggleFavorite(note) },
                        onDeleteRequest = { onDeleteRequest(note) }
                    )
                    if (index < notes.lastIndex) {
                        ZhiLuDivider(modifier = Modifier.padding(start = Spacing.PageGutter))
                    }
                }
            }
        }
    }
}

/** 搜索结果：与列表视图同为文档行，但无滑动与长按菜单。 */
fun LazyListScope.homeSearchResults(
    results: List<Note>,
    query: String,
    isSearching: Boolean,
    onClearQuery: () -> Unit,
    onOpenNote: (Long) -> Unit
) {
    if (results.isEmpty() && !isSearching) {
        item(key = "search-empty") {
            AppEmptyState(
                onAction = onClearQuery,
                icon = Icons.Outlined.SearchOff,
                title = "未找到相关笔记",
                description = "换个关键词试试，或用 #标签名 精确筛选。",
                buttonText = "清空搜索"
            )
        }
        return
    }
    itemsIndexed(results, key = { _, note -> note.id }) { index, note ->
        AnimatedListItem(index = index) {
            Column {
                NoteRow(
                    note = note,
                    highlightQuery = query,
                    onClick = { onOpenNote(note.id) }
                )
                if (index < results.lastIndex) {
                    ZhiLuDivider(modifier = Modifier.padding(start = Spacing.PageGutter))
                }
            }
        }
    }
}