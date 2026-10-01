package com.example.zhilu.ui.trash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.component.AnimatedListItem
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

@Composable
fun TrashScreen(
    navController: NavHostController,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    var clearConfirmVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "回收站",
                onBack = { navController.popBackStack() },
                actions = {
                    if (state.deletedNotes.isNotEmpty()) {
                        TextButton(onClick = { clearConfirmVisible = true }) {
                            Text("清空", style = ZhiLuType.chip)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!state.isLoading && state.deletedNotes.isEmpty()) {
                AppEmptyState(
                    onAction = { navController.popBackStack() },
                    icon = Icons.Outlined.Delete,
                    title = "回收站是空的",
                    description = "已删除的笔记会在这里暂存 30 天，可随时恢复。",
                    buttonText = "返回"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        top = Spacing.Sm,
                        bottom = Spacing.Xl
                    )
                ) {
                    itemsIndexed(state.deletedNotes, key = { _, note -> note.id }) { index, note ->
                        AnimatedListItem(index = index) {
                            Column {
                                TrashRow(
                                    note = note,
                                    onRestore = { viewModel.restore(note.id) },
                                    onDeleteRequest = { pendingDelete = note }
                                )
                                if (index < state.deletedNotes.lastIndex) {
                                    ZhiLuDivider(
                                        modifier = Modifier.padding(start = Spacing.PageGutter)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { note ->
        DeleteForeverDialog(
            noteTitle = note.title,
            onConfirm = {
                viewModel.deleteForever(note)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    if (clearConfirmVisible) {
        ClearTrashDialog(
            count = state.deletedNotes.size,
            onConfirm = {
                viewModel.clearTrash()
                clearConfirmVisible = false
            },
            onDismiss = { clearConfirmVisible = false }
        )
    }
}