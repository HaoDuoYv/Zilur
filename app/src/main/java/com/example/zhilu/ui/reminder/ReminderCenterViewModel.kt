package com.example.zhilu.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus
import com.example.zhilu.domain.reminder.ReminderClassifier
import com.example.zhilu.domain.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ReminderCenterViewModel(
    private val reminderRepository: ReminderRepository,
    private val classifier: ReminderClassifier = ReminderClassifier(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val startOfTodayProvider: () -> Long = { defaultStartOfToday() }
) : ViewModel() {
    @Inject
    constructor(reminderRepository: ReminderRepository) : this(
        reminderRepository = reminderRepository,
        classifier = ReminderClassifier()
    )

    private val _uiState = MutableStateFlow(ReminderCenterUiState())
    val uiState: StateFlow<ReminderCenterUiState> = _uiState.asStateFlow()

    init {
        observeReminders()
    }

    fun markDone(reminder: ReminderInstance) {
        viewModelScope.launch {
            when (val result = reminderRepository.markDone(reminder.type, reminder.sourceId)) {
                is RepositoryResult.Success -> moveReminderToCompleted(reminder)
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun cancel(reminder: ReminderInstance) {
        viewModelScope.launch {
            when (val result = reminderRepository.cancel(reminder.type, reminder.sourceId)) {
                is RepositoryResult.Success -> removeReminder(reminder)
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun observeReminders() {
        viewModelScope.launch {
            reminderRepository.observeAll().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> {
                        val bucket = classifier.classify(
                            reminders = result.data,
                            now = nowProvider(),
                            startOfToday = startOfTodayProvider()
                        )
                        _uiState.update {
                            it.copy(
                                today = bucket.today,
                                overdue = bucket.overdue,
                                future = bucket.future,
                                completed = bucket.completed,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                    is RepositoryResult.Error -> _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    private fun moveReminderToCompleted(reminder: ReminderInstance) {
        val completed = reminder.copy(status = ReminderStatus.DONE, updatedAt = nowProvider())
        _uiState.update { state ->
            state.without(reminder).copy(
                completed = (listOf(completed) + state.completed.filterNot { it.matches(reminder) })
                    .sortedByDescending { it.updatedAt },
                error = null
            )
        }
    }

    private fun removeReminder(reminder: ReminderInstance) {
        _uiState.update { state -> state.without(reminder).copy(error = null) }
    }

    private fun ReminderCenterUiState.without(reminder: ReminderInstance): ReminderCenterUiState =
        copy(
            today = today.filterNot { it.matches(reminder) },
            overdue = overdue.filterNot { it.matches(reminder) },
            future = future.filterNot { it.matches(reminder) },
            completed = completed.filterNot { it.matches(reminder) }
        )

    private fun ReminderInstance.matches(other: ReminderInstance): Boolean =
        type == other.type && sourceId == other.sourceId

    private companion object {
        fun defaultStartOfToday(): Long =
            LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
    }
}
