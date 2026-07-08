package com.example.zhilu.ui.reminder

import com.example.zhilu.domain.model.ReminderInstance

data class ReminderCenterUiState(
    val today: List<ReminderInstance> = emptyList(),
    val overdue: List<ReminderInstance> = emptyList(),
    val future: List<ReminderInstance> = emptyList(),
    val completed: List<ReminderInstance> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
