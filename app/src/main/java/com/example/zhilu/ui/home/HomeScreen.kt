package com.example.zhilu.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.component.RowReveal
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.MotionEasing
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.motionEnterTween
import com.example.zhilu.ui.theme.motionExitTween
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val motion = !LocalReducedMotion.current
    var pendingDeleteNoteId by remember { mutableStateOf<Long?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    // 滑动露出的操作槽：同一时刻至多一行，列表层统一持有。
    var reveal by remember { mutableStateOf<RowReveal?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 一旦开始滚动就收起露出态——这是「上下翻动时误触」最直接的兜底。
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) reveal = null
    }

    // 进入 / 退出搜索等于换了一套列表，露出态同样作废。
    LaunchedEffect(uiState.isSearchActive) { reveal = null }

    // 收藏是可逆动作，不需要二次确认，但必须给一个看得见、能退回的反馈。
    val onToggleFavorite: (Note) -> Unit = { note ->
        val target = !note.isFavorite
        viewModel.setFavorite(note, target)
        scope.launch {
            val result = snackbar.showSnackbar(
                message = if (target) "已收藏" else "已取消收藏",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                // 写回原值，而不是「再取反一次」——列表刷新过也不会漂移。
                viewModel.setFavorite(note, !target)
            }
        }
    }

    AppTabScaffold(
        topBar = {
            HomeTopBar(
                dueReminderCount = uiState.dueReminderCount,
                onOpenReminders = { navController.navigate(Destination.Reminders.path) }
            )
        },
        floatingActionButton = {
            // 有行处于露出态时收起 FAB。它常驻右下，正好压在最后一行露出的操作槽上
            // 并且会抢走点击——露出态是一次性的「行操作」，全局新建入口此时让位。
            // 用 AnimatedVisibility 而不是单纯的条件组合：退出动画结束后内容才真正移出，
            // 移出后 FAB 不再占据触摸区，点击才能落到槽位上。
            AnimatedVisibility(
                visible = reveal == null,
                enter = fadeIn(
                    motionEnterTween(MotionDuration.Short, easing = MotionEasing.Enter, enabled = motion)
                ) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = motionEnterTween(
                        MotionDuration.Short,
                        easing = MotionEasing.Enter,
                        enabled = motion
                    )
                ),
                exit = fadeOut(
                    motionExitTween(MotionDuration.Quick, easing = MotionEasing.Exit, enabled = motion)
                ) + scaleOut(
                    targetScale = 0.85f,
                    animationSpec = motionExitTween(
                        MotionDuration.Quick,
                        easing = MotionEasing.Exit,
                        enabled = motion
                    )
                )
            ) {
                HomeCreateFab(
                    onClick = { navController.navigate(Destination.NoteEdit.createRoute()) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HomeSearchField(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                onSubmit = viewModel::submitSearch,
                onFocusChanged = { searchFocused = it }
            )

            if (uiState.isSearchActive) {
                SearchResultHeader(
                    resultCount = uiState.searchResults.size,
                    isSearching = uiState.isSearching,
                    onClear = viewModel::clearQuery
                )
            } else {
                HomeListHeader(
                    noteCount = uiState.noteCount,
                    tagCount = uiState.tagCount,
                    mediaCount = uiState.mediaCount,
                    viewMode = uiState.viewMode,
                    onSelectViewMode = { mode ->
                        if (mode != uiState.viewMode) {
                            // 换视图等于换一套列表，露出态跟着作废——否则新视图里第一行
                            // 会莫名其妙带着上一次的「删除」槽出现。
                            reveal = null
                            viewModel.toggleViewMode()
                        }
                    }
                )
                if (searchFocused && uiState.recentQueries.isNotEmpty()) {
                    RecentQueriesRow(
                        queries = uiState.recentQueries,
                        onSelect = viewModel::useRecentQuery
                    )
                }
            }

            when {
                uiState.isSearchActive -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    homeSearchResults(
                        results = uiState.searchResults,
                        query = uiState.query,
                        isSearching = uiState.isSearching,
                        onClearQuery = viewModel::clearQuery,
                        onOpenNote = { noteId ->
                            navController.navigate(Destination.NoteEdit.createRoute(noteId))
                        }
                    )
                }

                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "加载中…",
                        style = ZhiLuType.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.notes.isEmpty() -> AppEmptyState(
                    onAction = { navController.navigate(Destination.NoteEdit.createRoute()) },
                    icon = Icons.Outlined.Create,
                    title = "从这里开始记录",
                    description = "写下第一条知识，之后可按标签、内容和时间找回它。",
                    buttonText = "开始记录"
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    homeNoteList(
                        notes = uiState.notes,
                        viewMode = uiState.viewMode,
                        reveal = reveal,
                        onRevealChange = { reveal = it },
                        onOpenNote = { noteId ->
                            navController.navigate(Destination.NoteEdit.createRoute(noteId))
                        },
                        onToggleFavorite = onToggleFavorite,
                        onDeleteRequest = { note -> pendingDeleteNoteId = note.id }
                    )
                }
            }
        }
    }

    pendingDeleteNoteId?.let { noteId ->
        DeleteNoteDialog(
            onConfirm = {
                viewModel.softDeleteNote(noteId)
                pendingDeleteNoteId = null
            },
            onDismiss = { pendingDeleteNoteId = null }
        )
    }
}

/** 搜索结果头：结果数 / 进行中状态 + 清空。 */
@Composable
private fun SearchResultHeader(
    resultCount: Int,
    isSearching: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.PageGutter, vertical = Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
    ) {
        MetaLine(
            parts = listOf(if (isSearching) "搜索中…" else "$resultCount 条结果"),
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClear) {
            Text("清空", style = ZhiLuType.chip)
        }
    }
}

/** 搜索框聚焦且尚未输入时的历史关键词建议。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentQueriesRow(
    queries: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.PageGutter, vertical = Spacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
    ) {
        queries.forEach { query ->
            AssistChip(
                onClick = { onSelect(query) },
                label = { Text(query, style = ZhiLuType.chip) }
            )
        }
    }
}