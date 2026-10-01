package com.example.zhilu.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.ui.component.AnimatedListItem
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.SegmentedToggle
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.Spacing

@Composable
fun ReminderCenterScreen(
    navController: NavHostController,
    viewModel: ReminderCenterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val pending = remember(state.today, state.future) {
        (state.today + state.future).sortedBy { it.dueAt }
    }
    var selectedFilter by remember { mutableStateOf<ReminderFilter?>(null) }
    val filter = selectedFilter
        ?: if (state.overdue.isNotEmpty()) ReminderFilter.Overdue else ReminderFilter.Pending
    val items: List<ReminderInstance> = when (filter) {
        ReminderFilter.Pending -> pending
        ReminderFilter.Overdue -> state.overdue
        ReminderFilter.Completed -> state.completed
    }
    val style = reminderStatusStyle(filter, MaterialTheme.colorScheme)
    val allEmpty = state.overdue.isEmpty() && pending.isEmpty() && state.completed.isEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "提醒",
                onBack = { navController.popBackStack() }
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
            if (!state.isLoading && allEmpty) {
                AppEmptyState(
                    onAction = { navController.popBackStack() },
                    icon = Icons.Outlined.NotificationsNone,
                    title = "没有提醒",
                    description = "为笔记设置复习或待办提醒，它们会按时出现在这里。",
                    buttonText = "返回"
                )
            } else {
                SegmentedToggle(
                    options = listOf(
                        ReminderFilter.Pending to "${ReminderFilter.Pending.label} ${pending.size}",
                        ReminderFilter.Overdue to "${ReminderFilter.Overdue.label} ${state.overdue.size}",
                        ReminderFilter.Completed to "${ReminderFilter.Completed.label} ${state.completed.size}"
                    ),
                    selected = filter,
                    onSelect = { selectedFilter = it },
                    modifier = Modifier.padding(
                        start = Spacing.PageGutter,
                        end = Spacing.PageGutter,
                        top = Spacing.Sm,
                        bottom = Spacing.Xs
                    )
                )
                if (items.isEmpty()) {
                    AppEmptyState(
                        onAction = { navController.popBackStack() },
                        icon = Icons.Outlined.NotificationsNone,
                        title = "暂无${filter.label}提醒",
                        description = "切换上方分段查看其它状态的提醒。",
                        buttonText = "返回",
                        compact = true
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = Spacing.Xs, bottom = Spacing.Xl),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(
                            items = items,
                            key = { _, reminder -> "${filter.name}-${reminder.id}" }
                        ) { index, reminder ->
                            AnimatedListItem(index = index) {
                                Column {
                                    ReminderRow(
                                        reminder = reminder,
                                        style = style,
                                        statusLabel = filter.label,
                                        actionsEnabled = filter != ReminderFilter.Completed,
                                        onClick = {
                                            reminder.noteId?.let {
                                                navController.navigate(Destination.NoteEdit.createRoute(it))
                                            }
                                        },
                                        onDone = { viewModel.markDone(reminder) },
                                        onCancel = { viewModel.cancel(reminder) }
                                    )
                                    if (index < items.lastIndex) {
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
    }
}