package com.example.zhilu.domain.usecase

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.TodoRepository
import javax.inject.Inject

/**
 * 在提醒列表上处理提醒，并把结果**回写到源头**（单一事实来源）。
 *
 * 既有缺陷：`ReminderRepository.markDone` 只翻提醒实例的状态，`todo_items.completedAt`
 * 不会被写，于是"提醒里完成了、笔记里的待办还欠着勾"；取消同理不会清 `todo_items.remindAt`。
 * 这两个动作必须在同一处收口，页面各自拼接必然再次分叉。
 *
 * REVIEW 类提醒由 `review_plans.nextReviewAt` 驱动（开启/评级/暂停时重建），
 * UI 上是只读的，不提供完成/取消/延后；对 REVIEW 类型调用本用例会被当作
 * "只翻提醒状态"处理 —— 但这不应发生，调用方需自行拦截。
 */
class ResolveReminderUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val todoRepository: TodoRepository
) {
    /** 完成待办提醒：提醒置 DONE，并回写 `todo_items.completedAt`（笔记里同步打勾）。 */
    suspend fun complete(
        reminder: ReminderInstance,
        completedAt: Long = System.currentTimeMillis()
    ): RepositoryResult<Unit> {
        val marked = reminderRepository.markDone(reminder.type, reminder.sourceId)
        if (marked is RepositoryResult.Error) return marked
        return if (reminder.type == ReminderType.TODO) {
            todoRepository.completeTodo(reminder.sourceId, completedAt)
        } else {
            RepositoryResult.Success(Unit)
        }
    }

    /** 取消待办提醒：提醒置 CANCELED，并清空 `todo_items.remindAt`（待办保留，只是不再提醒）。 */
    suspend fun cancel(
        reminder: ReminderInstance,
        updatedAt: Long = System.currentTimeMillis()
    ): RepositoryResult<Unit> {
        val canceled = reminderRepository.cancel(reminder.type, reminder.sourceId)
        if (canceled is RepositoryResult.Error) return canceled
        return if (reminder.type == ReminderType.TODO) {
            todoRepository.updateRemindAt(reminder.sourceId, remindAt = null, updatedAt = updatedAt)
        } else {
            RepositoryResult.Success(Unit)
        }
    }

    /**
     * 延后：把提醒重建到 [dueAt]（旧实例被取消，新实例 SCHEDULED）。
     * TODO 类同时回写 `todo_items.remindAt`，笔记里的提醒时间同步前移。
     */
    suspend fun snooze(
        reminder: ReminderInstance,
        dueAt: Long,
        updatedAt: Long = System.currentTimeMillis()
    ): RepositoryResult<Unit> {
        val rescheduled = reminderRepository.upsertScheduled(
            reminder.copy(id = 0, dueAt = dueAt)
        )
        if (rescheduled is RepositoryResult.Error) return rescheduled
        return if (reminder.type == ReminderType.TODO) {
            todoRepository.updateRemindAt(reminder.sourceId, remindAt = dueAt, updatedAt = updatedAt)
        } else {
            RepositoryResult.Success(Unit)
        }
    }
}
