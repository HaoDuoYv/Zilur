package com.example.zhilu.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.AnimatedListItem
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.NoteRow
import com.example.zhilu.ui.component.NoteRowVariant
import com.example.zhilu.ui.component.RevealSide
import com.example.zhilu.ui.component.RowReveal
import com.example.zhilu.ui.component.SectionHeader
import com.example.zhilu.ui.theme.Spacing

/**
 * 笔记列表内容。
 *
 * 两种视图都是**圆角卡片**，差别在两处：
 * 时间线多一层吸附的日期分节标题，且卡片左侧留白里有一条贯穿的竖直轨道与节点圆点；
 * 列表视图则是一串等价的卡片，没有分组也没有轨道——快扫用。
 * 卡片之间靠 [Spacing.CardGap] 的留白分隔，不再用发丝线——卡片自己就能自证边界。
 *
 * 露出态（滑动露出的操作槽）由调用方持有（[reveal]），因此同一时刻至多一行露出，
 * 且列表滚动时可以一次性收起。
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.homeNoteList(
    notes: List<Note>,
    viewMode: ViewMode,
    reveal: RowReveal?,
    onRevealChange: (RowReveal?) -> Unit,
    onOpenNote: (Long) -> Unit,
    onToggleFavorite: (Note) -> Unit,
    onDeleteRequest: (Note) -> Unit,
    onTagClick: ((Tag) -> Unit)? = null
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
                    // 时间线比列表视图多一条贯穿的轨道：这是两个视图一眼可辨的差别所在。
                    Box(
                        modifier = Modifier.timelineRail(
                            isFirst = index == 0,
                            isLast = index == group.notes.lastIndex
                        )
                    ) {
                        HomeNoteItem(
                            note = note,
                            variant = NoteRowVariant.Card,
                            revealedSide = reveal.sideFor(note.id),
                            onRevealChange = { side -> onRevealChange(side.toReveal(note.id)) },
                            onClick = { onOpenNote(note.id) },
                            onToggleFavorite = { onToggleFavorite(note) },
                            onDeleteRequest = { onDeleteRequest(note) },
                            onTagClick = onTagClick
                        )
                    }
                }
            }
        }
    } else {
        itemsIndexed(notes, key = { _, note -> note.id }) { index, note ->
            AnimatedListItem(index = index) {
                HomeNoteItem(
                    note = note,
                    variant = NoteRowVariant.Card,
                    revealedSide = reveal.sideFor(note.id),
                    onRevealChange = { side -> onRevealChange(side.toReveal(note.id)) },
                    onClick = { onOpenNote(note.id) },
                    onToggleFavorite = { onToggleFavorite(note) },
                    onDeleteRequest = { onDeleteRequest(note) },
                    onTagClick = onTagClick
                )
            }
        }
    }
}

/** 搜索结果：与列表视图同为圆角卡片，但无滑动与长按菜单。 */
fun LazyListScope.homeSearchResults(
    results: List<Note>,
    query: String,
    isSearching: Boolean,
    onClearQuery: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onTagClick: ((Tag) -> Unit)? = null
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
            NoteRow(
                note = note,
                variant = NoteRowVariant.Card,
                highlightQuery = query,
                onClick = { onOpenNote(note.id) },
                onTagClick = onTagClick
            )
        }
    }
}

/** 该行是否就是当前露出的一行；不是则一律视为未展开。 */
private fun RowReveal?.sideFor(noteId: Long): RevealSide {
    val current = this ?: return RevealSide.None
    return if (current.id == noteId) current.side else RevealSide.None
}

/** 反向映射：`None` 表示没有任何行露出，向列表层传 `null`。 */
private fun RevealSide.toReveal(noteId: Long): RowReveal? =
    if (this == RevealSide.None) null else RowReveal(noteId, this)
