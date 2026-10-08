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
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.component.RowReveal
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppIntents
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.navigation.navigateToReview
import com.example.zhilu.ui.navigation.navigateToSearchTag
import com.example.zhilu.ui.review.ReviewTab
import com.example.zhilu.ui.search.TagDeleteDialog
import com.example.zhilu.ui.search.TagFilterBar
import com.example.zhilu.ui.search.TagManageSheet
import com.example.zhilu.ui.search.TagRenameDialog
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
    val intents = LocalAppIntents.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val motion = !LocalReducedMotion.current
    var pendingDeleteNoteId by remember { mutableStateOf<Long?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    // 滑动露出的操作槽：同一时刻至多一行，列表层统一持有。
    var reveal by remember { mutableStateOf<RowReveal?>(null) }
    // 标签管理：重命名 / 删除对话 + 管理弹层（标签页撤销后接管其"管理"职责）。
    var renamingTag by remember { mutableStateOf<Tag?>(null) }
    var deletingTag by remember { mutableStateOf<Tag?>(null) }
    var showTagManage by remember { mutableStateOf(false) }

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
                // 铃铛背着"到期红点"，语义就是提醒 —— 落点在复习中心的「提醒」档。
                onOpenReminders = { navController.navigateToReview(intents, ReviewTab.Reminders) }
            )
        },
        // 新建入口已经移到**底栏中央的 ＋**（见 BottomBar / CreateSheet），
        // 首页不再有右下角的 FAB —— 也就没有"FAB 压住滑出的删除键"这个问题了，
        // 因此行露出态不再需要联动收起任何悬浮件。
        floatingActionButton = {}
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

            // 聚焦或已在搜索态时露出标签筛选条：点选 = 加筛选（空关键词时就是"标签浏览"）。
            if (searchFocused || uiState.isSearchActive) {
                TagFilterBar(
                    items = uiState.tagFilters,
                    selectedTagIds = uiState.selectedTagIds,
                    onToggle = viewModel::toggleTagFilter,
                    onClearAll = viewModel::clearTagFilters,
                    onManage = { showTagManage = true },
                    onRename = { renamingTag = it },
                    onDelete = { deletingTag = it },
                    modifier = Modifier.padding(vertical = Spacing.Xs)
                )
            }

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
                        },
                        onTagClick = { tag -> navController.navigateToSearchTag(intents, tag.id) }
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
                        onDeleteRequest = { note -> pendingDeleteNoteId = note.id },
                        onTagClick = { tag -> navController.navigateToSearchTag(intents, tag.id) }
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

    renamingTag?.let { tag ->
        TagRenameDialog(
            tag = tag,
            onConfirm = { newName ->
                viewModel.renameTag(tag, newName)
                renamingTag = null
            },
            onDismiss = { renamingTag = null }
        )
    }

    deletingTag?.let { tag ->
        TagDeleteDialog(
            tag = tag,
            onConfirm = {
                viewModel.deleteTag(tag)
                deletingTag = null
            },
            onDismiss = { deletingTag = null }
        )
    }

    if (showTagManage) {
        TagManageSheet(
            items = uiState.tagFilters,
            onRename = { renamingTag = it },
            onDelete = { deletingTag = it },
            onCreate = viewModel::createTag,
            onDismiss = { showTagManage = false }
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