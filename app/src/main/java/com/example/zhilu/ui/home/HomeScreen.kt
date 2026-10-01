package com.example.zhilu.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current
    var pendingDeleteNoteId by remember { mutableStateOf<Long?>(null) }
    var searchFocused by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
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
            HomeCreateFab(
                onClick = { navController.navigate(Destination.NoteEdit.createRoute()) }
            )
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
                        if (mode != uiState.viewMode) viewModel.toggleViewMode()
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
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    homeNoteList(
                        notes = uiState.notes,
                        viewMode = uiState.viewMode,
                        onOpenNote = { noteId ->
                            navController.navigate(Destination.NoteEdit.createRoute(noteId))
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
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