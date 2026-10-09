package com.example.zhilu.domain.reminder

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.repository.ReminderRepository
import javax.inject.Inject

/**
 * 待办项（`todo_items.remindAt`）→ TODO 提醒的**唯一同步点**，与 [ReviewReminderSync] 对称。
 *
 * 这段逻辑原先只长在 `NoteViewModel` 的私有方法里。AI 的 `add_todos` 现在也要能带提醒时间，
 * 若各写一遍 `notificationId` 的派生规则，两处迟早会算出不同的通知 id ——
 * 于是同一条待办在通知栏留下两条。收口到这里，谁建待办都走同一段。
 *
 * 与 REVIEW 侧的区别：复习提醒的生死由**计划**决定（提醒跟着 `nextReviewAt` 走），
 * 待办提醒的源头就是 `todo_items.remindAt` 本身 —— 所以在提醒页处理待办提醒时，
 * 回写的也是这个字段（见 `ResolveReminderUseCase`）。
 */
class TodoReminderSync @Inject constructor(
    private val reminderRepository: ReminderRepository
) {
    /** 建 / 改提醒；`upsertScheduled` 会先取消同源旧实例，所以重复调用不会堆通知。 */
    suspend fun schedule(todoId: Long, noteId: Long?, dueAt: Long): RepositoryResult<Unit> {
        val result = reminderRepository.upsertScheduled(
            ReminderInstance(
                type = ReminderType.TODO,
                sourceId = todoId,
                noteId = noteId,
                dueAt = dueAt,
                notificationId = notificationIdFor(todoId)
            )
        )
        return when (result) {
            is RepositoryResult.Success -> RepositoryResult.Success(Unit)
            is RepositoryResult.Error -> result
        }
    }

    /** 取消提醒（待办本身留着，只是不再提醒）。 */
    suspend fun cancel(todoId: Long): RepositoryResult<Unit> =
        reminderRepository.cancel(ReminderType.TODO, todoId)

    /** 待办被勾选完成 → 提醒随之结束。 */
    suspend fun markDone(todoId: Long): RepositoryResult<Unit> =
        reminderRepository.markDone(ReminderType.TODO, todoId)

    /**
     * 按待办**当前状态**配平提醒：完成 → 结束；有 `remindAt` → 重建；都没有 → 取消。
     *
     * @param fallbackNoteId `noteId` 为空的待办兜底用（笔记页场景下就是当前笔记）。
     */
    suspend fun reconcile(todo: TodoItem, fallbackNoteId: Long? = null): RepositoryResult<Unit> =
        when {
            todo.isCompleted -> markDone(todo.id)
            todo.remindAt != null -> schedule(todo.id, todo.noteId ?: fallbackNoteId, todo.remindAt)
            else -> cancel(todo.id)
        }

    companion object {
        /**
         * TODO 提醒的稳定通知 id，由待办 id 派生。
         *
         * 不用自增 id：同一条待办改了提醒时间后，通知应当**就地更新**成同一条，
         * 而不是在通知栏多留一条旧的。
         */
        fun notificationIdFor(todoId: Long): Int = "todo-$todoId".hashCode()
    }
}
