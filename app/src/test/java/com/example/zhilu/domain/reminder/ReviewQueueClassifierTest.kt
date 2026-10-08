package com.example.zhilu.domain.reminder

import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewPlanWithNote
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「待复习」分区的口径：**以今天 0 点为界**（复习是天粒度，用 `now` 切会让同一天内的
 * 计划在逾期/今天之间跳动）。
 */
class ReviewQueueClassifierTest {
    private val classifier = ReviewQueueClassifier()
    private val startOfToday = 1_700_000_000_000L
    private val day = 86_400_000L
    private val endOfToday = startOfToday + day

    @Test
    fun `due before today start counts as overdue`() {
        val item = plan(noteId = 1, nextReviewAt = startOfToday - 1)

        val queue = classifier.classify(listOf(item), startOfToday)

        assertEquals(listOf(1L), queue.overdue.map { it.plan.noteId })
        assertEquals(1, queue.queuedCount)
    }

    @Test
    fun `exactly today start counts as today`() {
        val item = plan(noteId = 2, nextReviewAt = startOfToday)

        val queue = classifier.classify(listOf(item), startOfToday)

        assertEquals(listOf(2L), queue.today.map { it.plan.noteId })
        assertEquals(emptyList<ReviewPlanWithNote>(), queue.overdue)
    }

    @Test
    fun `tomorrow start opens the upcoming window of seven days`() {
        val tomorrow = plan(noteId = 3, nextReviewAt = endOfToday)
        val sixthDay = plan(noteId = 4, nextReviewAt = startOfToday + 6 * day)
        val seventhDay = plan(noteId = 5, nextReviewAt = startOfToday + 7 * day)

        val queue = classifier.classify(listOf(tomorrow, sixthDay, seventhDay), startOfToday)

        assertEquals(listOf(3L, 4L), queue.upcoming.map { it.plan.noteId })
        // 第 7 天已在窗口之外，落进「以后」，仍在列表里可见
        assertEquals(listOf(5L), queue.later.map { it.plan.noteId })
    }

    @Test
    fun `long interval plans stay visible in later`() {
        val item = plan(noteId = 6, nextReviewAt = startOfToday + 30 * day)

        val queue = classifier.classify(listOf(item), startOfToday)

        assertEquals(listOf(6L), queue.later.map { it.plan.noteId })
        assertEquals(1, queue.queuedCount)
    }

    @Test
    fun `disabled plans split into paused and completed by graduation marker`() {
        val paused = plan(noteId = 7, enabled = false, nextReviewAt = null, updatedAt = 10)
        val completed = plan(noteId = 8, enabled = false, nextReviewAt = null, completedAt = 99)

        val queue = classifier.classify(listOf(paused, completed), startOfToday)

        assertEquals(listOf(7L), queue.paused.map { it.plan.noteId })
        assertEquals(listOf(8L), queue.completed.map { it.plan.noteId })
        assertEquals("暂停与毕业都不算队列内", 0, queue.queuedCount)
        assertEquals(true, queue.hasAnyPlan)
    }

    @Test
    fun `archived plans are ordered by last update descending`() {
        val older = plan(noteId = 9, enabled = false, nextReviewAt = null, updatedAt = 1)
        val newer = plan(noteId = 10, enabled = false, nextReviewAt = null, updatedAt = 2)

        val queue = classifier.classify(listOf(older, newer), startOfToday)

        assertEquals(listOf(10L, 9L), queue.paused.map { it.plan.noteId })
    }

    @Test
    fun `queued plans are ordered by due time`() {
        val later = plan(noteId = 11, nextReviewAt = startOfToday - 1)
        val earlier = plan(noteId = 12, nextReviewAt = startOfToday - 5)

        val queue = classifier.classify(listOf(later, earlier), startOfToday)

        assertEquals(listOf(12L, 11L), queue.overdue.map { it.plan.noteId })
    }

    @Test
    fun `enabled plan without a due time belongs to no bucket`() {
        // 由 ReviewReminderSync 保证不会产生这种组合（启用必有下次复习时间）；
        // 真出现了也不能凭空塞进某个区，否则会误导"今天要复习"。
        val inconsistent = plan(noteId = 13, enabled = true, nextReviewAt = null)

        val queue = classifier.classify(listOf(inconsistent), startOfToday)

        assertEquals(false, queue.hasAnyPlan)
        assertEquals(0, queue.queuedCount)
    }

    @Test
    fun `empty input yields an empty queue`() {
        val queue = classifier.classify(emptyList(), startOfToday)

        assertEquals(false, queue.hasAnyPlan)
        assertEquals(0, queue.queuedCount)
    }

    private fun plan(
        noteId: Long,
        enabled: Boolean = true,
        nextReviewAt: Long? = null,
        completedAt: Long? = null,
        updatedAt: Long = 0
    ) = ReviewPlanWithNote(
        plan = ReviewPlan(
            id = noteId,
            noteId = noteId,
            enabled = enabled,
            nextReviewAt = nextReviewAt,
            updatedAt = updatedAt,
            completedAt = completedAt
        ),
        noteTitle = "笔记 $noteId"
    )
}
