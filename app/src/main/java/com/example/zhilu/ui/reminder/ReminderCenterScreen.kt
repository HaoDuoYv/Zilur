package com.example.zhilu.ui.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.compose.ui.graphics.Color
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.ui.component.AnimatedListItem
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.navigation.Destination
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReminderCenterScreen(
    navController: NavHostController,
    viewModel: ReminderCenterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val pending = remember(state.today, state.future) {
        (state.today + state.future).sortedBy { it.dueAt }
    }
    val allEmpty = state.overdue.isEmpty() && pending.isEmpty() && state.completed.isEmpty()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "提醒",
                onBack = { navController.popBackStack() }
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
            if (!state.isLoading && allEmpty) {
                AppEmptyState(
                    onAction = { navController.popBackStack() },
                    icon = "提",
                    title = "没有提醒",
                    description = "为笔记设置复习或待办提醒，它们会按时出现在这里。",
                    buttonText = "返回"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReminderSection(
                        title = "待处理",
                        reminders = pending,
                        emptyText = "暂无待处理提醒",
                        statusLabel = "待处理",
                        actionsEnabled = true,
                        onReminderClick = { reminder ->
                            reminder.noteId?.let { navController.navigate(Destination.NoteEdit.createRoute(it)) }
                        },
                        onDone = viewModel::markDone,
                        onCancel = viewModel::cancel
                    )
                    ReminderSection(
                        title = "已逾期",
                        reminders = state.overdue,
                        emptyText = "暂无逾期提醒",
                        statusLabel = "已逾期",
                        actionsEnabled = true,
                        onReminderClick = { reminder ->
                            reminder.noteId?.let { navController.navigate(Destination.NoteEdit.createRoute(it)) }
                        },
                        onDone = viewModel::markDone,
                        onCancel = viewModel::cancel
                    )
                    ReminderSection(
                        title = "已完成",
                        reminders = state.completed,
                        emptyText = "暂无已完成提醒",
                        statusLabel = "已完成",
                        actionsEnabled = false,
                        onReminderClick = { reminder ->
                            reminder.noteId?.let { navController.navigate(Destination.NoteEdit.createRoute(it)) }
                        },
                        onDone = viewModel::markDone,
                        onCancel = viewModel::cancel
                    )
                }
            }
        }
    }
}

private fun LazyListScope.ReminderSection(
    title: String,
    reminders: List<ReminderInstance>,
    emptyText: String,
    statusLabel: String,
    actionsEnabled: Boolean,
    onReminderClick: (ReminderInstance) -> Unit,
    onDone: (ReminderInstance) -> Unit,
    onCancel: (ReminderInstance) -> Unit
) {
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = reminders.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (reminders.isEmpty()) {
        item {
            AppCard {
                Text(
                    text = emptyText,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        itemsIndexed(
            items = reminders,
            key = { _, reminder -> "${title}-${reminder.id}-${reminder.status.value}" }
        ) { index, reminder ->
            val statusColors = rememberStatusColors(title)
            AnimatedListItem(index = index) {
                ReminderRow(
                    reminder = reminder,
                    statusLabel = statusLabel,
                    statusColor = statusColors.first,
                    statusContentColor = statusColors.second,
                    actionsEnabled = actionsEnabled,
                    onClick = { onReminderClick(reminder) },
                    onDone = { onDone(reminder) },
                    onCancel = { onCancel(reminder) }
                )
            }
        }
    }
}

@Composable
private fun rememberStatusColors(title: String): Pair<Color, Color> {
    return when (title) {
        "待处理" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "已逾期" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "已完成" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderInstance,
    statusLabel: String,
    statusColor: Color,
    statusContentColor: Color,
    actionsEnabled: Boolean,
    onClick: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = reminder.noteId != null, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCapsule(
                text = statusLabel,
                color = statusColor,
                contentColor = statusContentColor
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatDueTime(reminder.dueAt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (reminder.noteId != null) "关联笔记 #${reminder.noteId}" else "未关联笔记",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (actionsEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDone) {
                        Text("完成")
                    }
                    OutlinedButton(onClick = onCancel) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCapsule(
    text: String,
    color: Color,
    contentColor: Color
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = color,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDueTime(dueAt: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(dueAt))
