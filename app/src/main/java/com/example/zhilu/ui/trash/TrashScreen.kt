package com.example.zhilu.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.NoteListItem

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
                title = "Trash",
                onBack = { navController.popBackStack() },
                actions = {
                    if (state.deletedNotes.isNotEmpty()) {
                        OutlinedButton(onClick = viewModel::clearTrash) {
                            Text("Clear")
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
            if (state.isLoading) LinearProgressIndicator()
            if (!state.isLoading && state.deletedNotes.isEmpty()) {
                AppCard {
                    Text(
                        text = "Trash is empty.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.deletedNotes, key = { it.id }) { note ->
                    NoteListItem(
                        note = note,
                        trailing = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { viewModel.restore(note.id) }) {
                                    Text("Restore")
                                }
                                OutlinedButton(onClick = { viewModel.deleteForever(note) }) {
                                    Text("Delete")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
