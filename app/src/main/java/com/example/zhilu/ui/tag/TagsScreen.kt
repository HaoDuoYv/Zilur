package com.example.zhilu.ui.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.AnimatedListItem
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.NoteListItem
import com.example.zhilu.ui.navigation.BottomBar
import com.example.zhilu.ui.navigation.Destination

@Composable
fun TagsScreen(
    navController: NavHostController,
    viewModel: TagsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var newTagName by remember { mutableStateOf("") }
    var pendingDeleteTag by remember { mutableStateOf<Tag?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "标签") },
        bottomBar = { BottomBar(navController = navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            AppCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${state.noteCount} 条笔记，${state.tags.size} 个标签",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("新标签") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                        Button(
                            onClick = {
                                viewModel.addTag(newTagName)
                                newTagName = ""
                            },
                            enabled = newTagName.isNotBlank()
                        ) {
                            Text("添加")
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = 8.dp,
                    bottom = 88.dp
                )
            ) {
                state.selectedTag?.let { tag ->
                    item(key = "filtered-header") {
                        Text(
                            text = "“${tag.name}” 下的知识点",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    if (state.isLoadingNotes) {
                        item(key = "filtered-loading") { LinearProgressIndicator() }
                    } else if (state.filteredNotes.isEmpty()) {
                        item(key = "filtered-empty") {
                            AppEmptyState(
                                onAction = {
                                    navController.navigate(Destination.NoteEdit.createRoute())
                                },
                                icon = "标",
                                title = "这个标签下还没有知识点",
                                description = "给笔记添加「${tag.name}」标签后，会出现在这里。",
                                buttonText = "去记录",
                                secondaryActionLabel = "取消筛选",
                                onSecondaryAction = { viewModel.selectTag(tag) }
                            )
                        }
                    } else {
                        itemsIndexed(
                            state.filteredNotes,
                            key = { _, note -> "note-${note.id}" }
                        ) { index, note ->
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
                    item(key = "tags-header") {
                        Text(
                            text = "全部标签",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
                if (!state.isLoading && state.tags.isEmpty()) {
                    item {
                        AppEmptyState(
                            onAction = { navController.navigate(Destination.NoteEdit.createRoute()) },
                            icon = "标",
                            title = "还没有标签",
                            description = "创建第一条笔记并添加标签，知识将更容易被找到。",
                            buttonText = "去记录"
                        )
                    }
                } else {
                    itemsIndexed(state.tags, key = { _, tag -> tag.id }) { index, tag ->
                        AnimatedListItem(index = index) {
                            TagRow(
                                tag = tag,
                                selected = state.selectedTag?.id == tag.id,
                                onClick = { viewModel.selectTag(tag) },
                                onDelete = { pendingDeleteTag = tag }
                            )
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
                TextButton(onClick = { pendingDeleteTag = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun TagRow(
    tag: Tag,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    AppCard {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(tag.color))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (selected) "正在筛选该标签下的知识点" else "点击查看该标签下的知识点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onClick) {
                Text(if (selected) "取消筛选" else "筛选")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除标签",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
