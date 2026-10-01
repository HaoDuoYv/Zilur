package com.example.zhilu.ui.tag

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.AnimatedListItem
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.AppIconButton
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.component.NoteRow
import com.example.zhilu.ui.component.SectionHeader
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.Spacing

@Composable
fun TagsScreen(
    navController: NavHostController,
    viewModel: TagsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current
    var newTagName by remember { mutableStateOf("") }
    var newTagVisible by remember { mutableStateOf(false) }
    var pendingDeleteTag by remember { mutableStateOf<Tag?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    AppTabScaffold(
        topBar = {
            AppTopBar(
                title = "标签",
                actions = {
                    AppIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = if (newTagVisible) "收起新建标签" else "新建标签",
                        onClick = { newTagVisible = !newTagVisible }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            AnimatedVisibility(
                visible = newTagVisible,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                NewTagInput(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    onSubmit = {
                        viewModel.addTag(newTagName)
                        newTagName = ""
                        newTagVisible = false
                    }
                )
            }

            MetaLine(
                parts = listOf("${state.noteCount} 条笔记", "${state.tags.size} 个标签"),
                modifier = Modifier.padding(
                    horizontal = Spacing.PageGutter,
                    vertical = Spacing.Sm
                )
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Spacing.Xxl)
            ) {
                val selectedTag = state.selectedTag
                // 筛选态与索引态互斥：同一屏里只出现一种列表，避免「正在看的」与「已经选完的」叠在一起。
                if (selectedTag != null) {
                    item(key = "filter-banner") {
                        TagFilterBanner(
                            tagName = selectedTag.name,
                            onClear = { viewModel.selectTag(selectedTag) }
                        )
                    }
                    item(key = "filtered-header") {
                        SectionHeader(title = "「${selectedTag.name}」下的知识点")
                    }
                    if (state.isLoadingNotes) {
                        item(key = "filtered-loading") { LinearProgressIndicator() }
                    } else if (state.filteredNotes.isEmpty()) {
                        item(key = "filtered-empty") {
                            AppEmptyState(
                                onAction = { navController.navigate(Destination.NoteEdit.createRoute()) },
                                icon = Icons.Outlined.Sell,
                                title = "这个标签下还没有知识点",
                                description = "给笔记添加「${selectedTag.name}」标签后，会出现在这里。",
                                buttonText = "去记录",
                                compact = true
                            )
                        }
                    } else {
                        itemsIndexed(
                            state.filteredNotes,
                            key = { _, note -> "note-${note.id}" }
                        ) { index, note ->
                            AnimatedListItem(index = index) {
                                Column {
                                    NoteRow(
                                        note = note,
                                        onClick = {
                                            navController.navigate(Destination.NoteEdit.createRoute(note.id))
                                        }
                                    )
                                    if (index < state.filteredNotes.lastIndex) {
                                        ZhiLuDivider(
                                            modifier = Modifier.padding(horizontal = Spacing.PageGutter)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item(key = "tags-header") {
                        SectionHeader(title = "全部标签")
                    }

                    if (!state.isLoading && state.tags.isEmpty()) {
                        item(key = "tags-empty") {
                            AppEmptyState(
                                onAction = { newTagVisible = true },
                                icon = Icons.Outlined.Sell,
                                title = "还没有标签",
                                description = "创建第一条笔记并添加标签，知识将更容易被找到。",
                                buttonText = "新建标签",
                                compact = true
                            )
                        }
                    } else {
                        itemsIndexed(state.tags, key = { _, tag -> tag.id }) { index, tag ->
                            AnimatedListItem(index = index) {
                                Column {
                                    TagRow(
                                        tag = tag,
                                        noteCount = state.noteCountByTag[tag.id] ?: 0,
                                        onClick = { viewModel.selectTag(tag) },
                                        onDelete = { pendingDeleteTag = tag }
                                    )
                                    if (index < state.tags.lastIndex) {
                                        ZhiLuDivider(
                                            modifier = Modifier.padding(horizontal = Spacing.PageGutter)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            title = { Text("删除标签？") },
            text = { Text("仅移除标签关联，不会删除笔记内容。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTag(tag)
                        pendingDeleteTag = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTag = null }) { Text("取消") }
            }
        )
    }
}