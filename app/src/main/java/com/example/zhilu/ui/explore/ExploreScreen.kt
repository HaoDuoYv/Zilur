package com.example.zhilu.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.ui.component.AnimatedListItem
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.NoteListItem
import com.example.zhilu.ui.component.TagChip
import com.example.zhilu.ui.navigation.BottomBar
import com.example.zhilu.ui.navigation.Destination

@Composable
fun ExploreScreen(
    navController: NavHostController,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "探索") },
        bottomBar = { BottomBar(navController = navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
        ) {
            item {
                AppCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索笔记和标签") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                errorIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearch() })
                        )
                        if (state.isSearching) CircularProgressIndicator()
                    }
                }
            }
            if (state.recentQueries.isNotEmpty()) {
                item {
                    ChipSection(title = "最近搜索") {
                        state.recentQueries.forEach { query ->
                            AssistChip(
                                onClick = { viewModel.useRecentQuery(query) },
                                label = { Text(query) }
                            )
                        }
                    }
                }
            }
            if (state.tags.isNotEmpty()) {
                item {
                    ChipSection(title = "标签") {
                        state.tags.take(12).forEach { tag ->
                            TagChip(tag = tag, onClick = { viewModel.useTagQuery(tag.name) })
                        }
                    }
                }
            }
            if (state.query.isBlank()) {
                item {
                    Text(
                        text = "最近编辑",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                if (state.recentNotes.isEmpty()) {
                    item {
                        AppEmptyState(
                            onAction = { navController.navigate(Destination.NoteEdit.createRoute()) },
                            icon = "录",
                            title = "还没有知识点",
                            description = "创建第一条笔记，探索页会展示最近编辑的内容。",
                            buttonText = "去记录"
                        )
                    }
                } else {
                    itemsIndexed(state.recentNotes, key = { _, note -> "recent-${note.id}" }) { index, note ->
                        AnimatedListItem(index = index) {
                            NoteListItem(
                                note = note,
                                onClick = {
                                    navController.navigate(Destination.NoteEdit.createRoute(note.id))
                                }
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "${state.results.size} 条结果",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                if (state.results.isEmpty() && !state.isSearching) {
                    item {
                        AppEmptyState(
                            onAction = { viewModel.onQueryChange("") },
                            icon = "搜",
                            title = "未找到相关笔记",
                            description = "换个关键词试试，用 #标签名 精确筛选，或点击上方标签缩小范围。",
                            buttonText = "清空搜索"
                        )
                    }
                }
                itemsIndexed(state.results, key = { _, note -> note.id }) { index, note ->
                    AnimatedListItem(index = index) {
                        NoteListItem(
                            note = note,
                            highlightQuery = state.query,
                            onClick = { navController.navigate(Destination.NoteEdit.createRoute(note.id)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(
    title: String,
    content: @Composable () -> Unit
) {
    AppCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = { content() }
            )
        }
    }
}
