package com.example.zhilu.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.ui.navigation.BottomBar
import com.example.zhilu.ui.navigation.Destination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知录") },
                actions = {
                    TextButton(onClick = { navController.navigate(Destination.Reminders.path) }) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Text("\u63d0\u9192")
                    }
                    TextButton(onClick = { navController.navigate(Destination.Explore.path) }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Text("搜索")
                    }
                }
            )
        },
        bottomBar = { BottomBar(navController = navController) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Destination.NoteEdit.createRoute()) }) {
                Icon(Icons.Default.Add, contentDescription = "新建知识")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                StatCard(label = "知识", value = uiState.noteCount.toString(), modifier = Modifier.weight(1f))
                StatCard(label = "标签", value = uiState.tagCount.toString(), modifier = Modifier.weight(1f))
                StatCard(label = "图片", value = uiState.mediaCount.toString(), modifier = Modifier.weight(1f))
            }

            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                TextButton(
                    onClick = { if (uiState.viewMode != ViewMode.LIST) viewModel.toggleViewMode() },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (uiState.viewMode == ViewMode.LIST) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("列表")
                }
                TextButton(
                    onClick = { if (uiState.viewMode != ViewMode.TIMELINE) viewModel.toggleViewMode() },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (uiState.viewMode == ViewMode.TIMELINE) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("时间线")
                }
            }

            when {
                uiState.isLoading -> Text("加载中...", modifier = Modifier.padding(16.dp))
                uiState.notes.isEmpty() -> EmptyState { navController.navigate(Destination.NoteEdit.createRoute()) }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(uiState.notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { navController.navigate(Destination.NoteEdit.createRoute(note.id)) },
                            onToggleFavorite = { viewModel.toggleFavorite(note) },
                            onDelete = { viewModel.softDeleteNote(note.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
