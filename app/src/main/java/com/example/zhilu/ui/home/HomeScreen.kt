package com.example.zhilu.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.ui.component.AnimatedListItem
import com.example.zhilu.ui.navigation.BottomBar
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.theme.MotionDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteNoteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "知录",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    HomeActionIconButton(
                        icon = Icons.Default.Notifications,
                        contentDescription = "提醒中心",
                        onClick = { navController.navigate(Destination.Reminders.path) }
                    )
                    HomeActionIconButton(
                        icon = Icons.Default.Search,
                        contentDescription = "探索",
                        onClick = { navController.navigate(Destination.Explore.path) }
                    )
                }
            )
        },
        bottomBar = { BottomBar(navController = navController) },
        floatingActionButton = {
            var fabExpanded by remember { mutableStateOf(false) }
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = { uri ->
                    uri?.let { viewModel.parseImportPreview(it) }
                }
            )
            HomeFabMenu(
                expanded = fabExpanded,
                onToggle = { fabExpanded = !fabExpanded },
                onCreateNote = { navController.navigate(Destination.NoteEdit.createRoute()) },
                onImport = {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    label = "知识",
                    value = uiState.noteCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "标签",
                    value = uiState.tagCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "图片",
                    value = uiState.mediaCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            ViewModeToggle(
                currentMode = uiState.viewMode,
                onSelectList = { if (uiState.viewMode != ViewMode.LIST) viewModel.toggleViewMode() },
                onSelectTimeline = { if (uiState.viewMode != ViewMode.TIMELINE) viewModel.toggleViewMode() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "加载中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.notes.isEmpty() -> EmptyState {
                    navController.navigate(Destination.NoteEdit.createRoute())
                }

                else -> {
                    val timelineGroups = remember(uiState.notes) {
                        if (uiState.viewMode == ViewMode.TIMELINE) {
                            groupNotesByTimeline(uiState.notes)
                        } else {
                            emptyList()
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp)
                    ) {
                        if (uiState.viewMode == ViewMode.TIMELINE) {
                            timelineGroups.forEach { group ->
                                item(key = "header-${group.label}") {
                                    Text(
                                        text = group.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                itemsIndexed(
                                    group.notes,
                                    key = { _, note -> note.id }
                                ) { index, note ->
                                    AnimatedListItem(index = index) {
                                        NoteCard(
                                            note = note,
                                            onClick = {
                                                navController.navigate(Destination.NoteEdit.createRoute(note.id))
                                            },
                                            onToggleFavorite = { viewModel.toggleFavorite(note) },
                                            onDelete = { pendingDeleteNoteId = note.id }
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(uiState.notes, key = { _, note -> note.id }) { index, note ->
                                AnimatedListItem(index = index) {
                                    NoteCard(
                                        note = note,
                                        onClick = {
                                            navController.navigate(Destination.NoteEdit.createRoute(note.id))
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(note) },
                                        onDelete = { pendingDeleteNoteId = note.id }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteNoteId?.let { noteId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteNoteId = null },
            title = { Text("移入回收站？") },
            text = { Text("删除后可在回收站恢复，30 天后自动清空。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.softDeleteNote(noteId)
                        pendingDeleteNoteId = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteNoteId = null }) {
                    Text("取消")
                }
            }
        )
    }

    val preview = uiState.importPreview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissImportPreview,
            title = { Text("导入知识点") },
            text = {
                Text(
                    "标题：${preview.title}\n" +
                    "块数：${preview.blockCount}\n" +
                    "图片：${preview.imageCount}"
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) {
                    Text("导入")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissImportPreview) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun HomeActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = androidx.compose.animation.core.tween(MotionDuration.Short),
        label = "home_action_scale"
    )

    IconButton(
        onClick = onClick,
        modifier = modifier.scale(scale),
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ViewModeToggle(
    currentMode: ViewMode,
    onSelectList: () -> Unit,
    onSelectTimeline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(percent = 50)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row {
                ToggleButton(
                    text = "列表",
                    selected = currentMode == ViewMode.LIST,
                    onClick = onSelectList
                )
                ToggleButton(
                    text = "时间线",
                    selected = currentMode == ViewMode.TIMELINE,
                    onClick = onSelectTimeline
                )
            }
        }
    }
}

@Composable
private fun ToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
