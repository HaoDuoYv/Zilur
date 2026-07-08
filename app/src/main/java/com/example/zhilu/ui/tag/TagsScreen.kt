package com.example.zhilu.ui.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.AppCard
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

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Tags") },
        bottomBar = { BottomBar(navController = navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) LinearProgressIndicator()
            AppCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${state.noteCount} notes across ${state.tags.size} tags",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("New tag") },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                viewModel.addTag(newTagName)
                                newTagName = ""
                            }
                        ) {
                            Text("Add")
                        }
                    }
                }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.tags, key = { it.id }) { tag ->
                    TagRow(
                        tag = tag,
                        selected = state.selectedTag?.id == tag.id,
                        onClick = { viewModel.selectTag(tag) },
                        onDelete = { viewModel.deleteTag(tag) }
                    )
                }
                state.selectedTag?.let { tag ->
                    item {
                        Text(
                            text = "“${tag.name}” 下的知识点",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    if (state.isLoadingNotes) {
                        item { LinearProgressIndicator() }
                    } else if (state.filteredNotes.isEmpty()) {
                        item {
                            AppCard {
                                Text(
                                    text = "这个标签下还没有知识点。",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(state.filteredNotes, key = { "note-${it.id}" }) { note ->
                            NoteListItem(
                                note = note,
                                onClick = {
                                    navController.navigate(Destination.NoteEdit.createRoute(note.id))
                                }
                            )
                        }
                    }
                }
            }
        }
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
                    .size(14.dp)
                    .background(Color(tag.color), MaterialTheme.shapes.small)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(tag.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (selected) "正在显示该标签下的知识点" else "点击查看该标签下的知识点",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onClick) {
                Text(if (selected) "收起" else "查看")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete tag")
            }
        }
    }
}
