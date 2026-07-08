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
import androidx.compose.foundation.lazy.items
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
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.ui.component.AppCard
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

    Scaffold(
        topBar = {
            AppTopBar(
                title = "\u63d0\u9192\u4e2d\u5fc3",
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reminderSection(
                    title = "\u903e\u671f",
                    reminders = state.overdue,
                    emptyText = "\u6682\u65e0\u903e\u671f\u63d0\u9192",
                    actionsEnabled = true,
                    onReminderClick = { reminder ->
                        reminder.noteId?.let { navController.navigate(Destination.NoteEdit.createRoute(it)) }
                    },
                    onDone = viewModel::markDone,
                    onCancel = viewModel::cancel
                )
                reminderSection(
                    title = "\u4eca\u65e5",
                    reminders = state.today,
                    emptyText = "\u4eca\u5929\u6ca1\u6709\u63d0\u9192",
                    actionsEnabled = true,
                    onReminderClick = { reminder ->
                        reminder.noteId?.let { navController.navigate(Destination.NoteEdit.createRoute(it)) }
                    },
                    onDone = viewModel::markDone,
                    onCancel = viewModel::cancel
                )
                reminderSection(
                    title = "\u672a\u6765",
                    reminders = state.future,
                    emptyText = "\u6682\u65e0\u672a\u6765\u63d0\u9192",
                    actionsEnabled = true,
                    onReminderClick = { reminder ->
                        reminder.noteId?.let { navController.navigate(Destination.NoteEdit.createRoute(it)) }
                    },
                    onDone = viewModel::markDone,
                    onCancel = viewModel::cancel
                )
                reminderSection(
                    title = "\u5df2\u5b8c\u6210",
                    reminders = state.completed,
                    emptyText = "\u6682\u65e0\u5df2\u5b8c\u6210\u63d0\u9192",
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

private fun LazyListScope.reminderSection(
    title: String,
    reminders: List<ReminderInstance>,
    emptyText: String,
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
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        items(reminders, key = { "${title}-${it.id}-${it.status.value}" }) { reminder ->
            ReminderRow(
                reminder = reminder,
                actionsEnabled = actionsEnabled,
                onClick = { onReminderClick(reminder) },
                onDone = { onDone(reminder) },
                onCancel = { onCancel(reminder) }
            )
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderInstance,
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TypeLabel(text = typeLabel(reminder.type))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatDueTime(reminder.dueAt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (reminder.noteId != null) "\u5173\u8054\u7b14\u8bb0 #${reminder.noteId}" else "\u672a\u5173\u8054\u7b14\u8bb0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (actionsEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = onDone) {
                        Text("\u5b8c\u6210")
                    }
                    OutlinedButton(onClick = onCancel) {
                        Text("\u53d6\u6d88")
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeLabel(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun typeLabel(type: ReminderType): String = when (type) {
    ReminderType.REVIEW -> "\u590d\u4e60"
    ReminderType.TODO -> "TODO"
}

private fun formatDueTime(dueAt: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(dueAt))
