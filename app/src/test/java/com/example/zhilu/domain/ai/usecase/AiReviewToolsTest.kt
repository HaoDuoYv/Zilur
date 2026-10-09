package com.example.zhilu.domain.ai.usecase

import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewPlanWithNote
import com.example.zhilu.domain.model.ReviewQueue
import com.example.zhilu.domain.reminder.ReviewQueueClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 两个复习类 AI 工具的判定与文案。
 *
 * 重点是 [reviewScheduleAction]：它是「AI 调 schedule_review 不会把用户复习了半个月的
 * 计划抹回第 1 档」的唯一保障 —— 一律 `startPlan` 是这份代码最容易犯的错。
 */
class AiReviewToolsTest {

    private val startOfToday = 1_700_000_000_000L
    private val day = 86_400_000L

    // ---- schedule_review 的动作判定 ----

    @Test
    fun `no plan yet means create or restart`() {
        assertEquals(ReviewScheduleAction.CreateOrRestart, reviewScheduleAction(null))
    }

    @Test
    fun `a running plan is left alone`() {
        val running = ReviewPlan(id = 1, noteId = 2, enabled = true, currentStep = 3)

        // 什么都不做：进度已经到第 4 档了，重启会把它抹回第 1 档
        assertEquals(ReviewScheduleAction.AlreadyScheduled, reviewScheduleAction(running))
    }

    @Test
    fun `a paused plan resumes with its step kept`() {
        val paused = ReviewPlan(id = 1, noteId = 2, enabled = false, currentStep = 3)

        assertEquals(ReviewScheduleAction.Resume, reviewScheduleAction(paused))
    }

    @Test
    fun `a graduated plan starts over rather than resuming`() {
        // 毕业 = enabled false + completedAt 有值；判定必须先看 enabled，否则会被当成"暂停"
        val graduated = ReviewPlan(
            id = 1,
            noteId = 2,
            enabled = false,
            currentStep = 4,
            completedAt = startOfToday
        )

        assertEquals(ReviewScheduleAction.CreateOrRestart, reviewScheduleAction(graduated))
    }

    // ---- list_due_reviews 的文案 ----

    @Test
    fun `an empty knowledge base gets an empty state not a row of zeros`() {
        val text = formatDueReviews(emptyList(), ReviewQueue())

        assertEquals("还没有任何复习计划。", text)
    }

    @Test
    fun `the summary and the sections agree with the classifier`() {
        val overdue = planItem(noteId = 10, dueAt = startOfToday - day)
        val today = planItem(noteId = 11, dueAt = startOfToday + 3600_000L)
        val upcoming = planItem(noteId = 12, dueAt = startOfToday + 3 * day)
        val plans = listOf(overdue, today, upcoming)
        val queue = ReviewQueueClassifier().classify(plans, startOfToday)

        val text = formatDueReviews(plans, queue)

        // 计数与用户界面上看到的是同一套分区（同一个分类器），不该各算各的
        assertTrue(text.contains("逾期 1 个、今天 1 个"))
        assertTrue(text.contains("接下来 ${ReviewQueueClassifier.UPCOMING_DAYS} 天 1 个"))
        assertTrue(text.contains("已逾期："))
        assertTrue(text.contains("今天："))
        assertTrue(text.contains("接下来："))
        // 每行带笔记 id（模型回头要拿它调其它工具）与档位
        assertTrue(text.contains("[10] 笔记 10"))
        assertTrue(text.contains("第 1 档"))
    }

    @Test
    fun `sections with nothing in them are omitted`() {
        val upcoming = planItem(noteId = 12, dueAt = startOfToday + 3 * day)
        val plans = listOf(upcoming)
        val queue = ReviewQueueClassifier().classify(plans, startOfToday)

        val text = formatDueReviews(plans, queue)

        assertTrue(text.contains("今天：").not())
        assertTrue(text.contains("已逾期：").not())
        assertTrue(text.contains("接下来："))
    }

    @Test
    fun `far future plans are listed by name not just counted`() {
        // 只报「更远 1 个」的话，用户问"那是哪篇"时模型手里没有名字（真机就这样答不上来）
        val later = planItem(noteId = 13, dueAt = startOfToday + 10 * day)
        val plans = listOf(later)
        val queue = ReviewQueueClassifier().classify(plans, startOfToday)

        val text = formatDueReviews(plans, queue)

        assertTrue(text.contains("更远 1 个"))
        assertTrue(text.contains("[13] 笔记 13"))
    }

    @Test
    fun `paused and completed are counted so the model can mention them`() {
        val paused = ReviewPlanWithNote(
            plan = ReviewPlan(id = 20, noteId = 20, enabled = false, currentStep = 2),
            noteTitle = "笔记 20"
        )
        val completed = ReviewPlanWithNote(
            plan = ReviewPlan(id = 21, noteId = 21, enabled = false, completedAt = startOfToday),
            noteTitle = "笔记 21"
        )
        val plans = listOf(paused, completed)
        val queue = ReviewQueueClassifier().classify(plans, startOfToday)

        val text = formatDueReviews(plans, queue)

        assertTrue(text.contains("已暂停 1 个"))
        assertTrue(text.contains("已完成 1 个"))
    }

    private fun planItem(noteId: Long, dueAt: Long) = ReviewPlanWithNote(
        plan = ReviewPlan(id = noteId, noteId = noteId, nextReviewAt = dueAt),
        noteTitle = "笔记 $noteId"
    )
}
