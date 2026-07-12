package com.example.zhilu.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.component.AnimatedListItem
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.home.formatTime

@Composable
fun TrashScreen(
    navController: NavHostController,
    viewModel: TrashViewModel = hiltViewModel()
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
        topBar = {
            AppTopBar(
                title = "回收站",
                onBack = { navController.popBackStack() },
                actions = {
                    if (state.deletedNotes.isNotEmpty()) {
                        OutlinedButton(onClick = viewModel::clearTrash) {
                            Text("清空")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (!state.isLoading && state.deletedNotes.isEmpty()) {
                AppEmptyState(
                    onAction = { navController.popBackStack() },
                    icon = "回",
                    title = "回收站是空的",
                    description = "已删除的笔记会在这里暂存，可随时恢复或永久清除。",
                    buttonText = "返回"
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(state.deletedNotes, key = { _, note -> note.id }) { index, note ->
                    AnimatedListItem(index = index) {
                        DeletedNoteCard(
                            note = note,
                            onRestore = { viewModel.restore(note.id) },
                            onDeleteForever = { viewModel.deleteForever(note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletedNoteCard(
    note: Note,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    AppCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.title.ifBlank { "未命名知识" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "删除于 ${formatTime(note.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = onDeleteForever) {
                    Text("永久删除")
                }
                Button(onClick = onRestore) {
                    Text("恢复")
                }
            }
        }
    }
}
