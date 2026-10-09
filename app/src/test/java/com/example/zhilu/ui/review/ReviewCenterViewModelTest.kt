package com.example.zhilu.ui.review

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReminderWithContext
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewPlanWithNote
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.ReviewStats
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.reminder.ReviewReminderSync
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.repository.TodoRepository
import com.example.zhilu.domain.usecase.ManageReviewPlanUseCase
import com.example.zhilu.domain.usecase.ResolveReminderUseCase
import com.example.zhilu.ui.navigation.AppIntents
import com.example.zhilu.ui.reminder.ReminderFilter
import com.example.zhilu.ui.reminder.SnoozeOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 复习中心的两档：待复习队列（计划）与提醒列表（提醒实例）。
 *
 * 断言口径遵循"写库不乐观更新"：动作只看**下发给仓库的调用**（参数对不对），
 * 界面状态只断言由 Flow 回流的那部分。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewCenterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val startOfToday = 1_000_000L
    private val now = startOfToday + 10_000L
    private val day = 86_400_000L
    private val endOfToday = startOfToday + day

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- 待复习档 ----

    @Test
    fun plansAreClassifiedIntoQueue() = runTest(dispatcher) {
        val overdue = plan(noteId = 1, nextReviewAt = startOfToday - 1)
        val today = plan(noteId = 2, nextReviewAt = startOfToday + 1)
        val upcoming = plan(noteId = 3, nextReviewAt = endOfToday + 1)
        val later = plan(noteId = 4, nextReviewAt = startOfToday + 30 * day)
        val paused = plan(noteId = 5, enabled = false, nextReviewAt = null)
        val completed = plan(noteId = 6, enabled = false, nextReviewAt = null, completedAt = now)
        val repository = FakeReviewRepository(
            plans = listOf(later, completed, overdue, today, upcoming, paused)
        )

        val viewModel = viewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals(startOfToday, state.startOfToday)
        assertEquals(listOf(1L), state.queue.overdue.map { it.plan.noteId })
        assertEquals(listOf(2L), state.queue.today.map { it.plan.noteId })
        assertEquals(listOf(3L), state.queue.upcoming.map { it.plan.noteId })
        assertEquals(listOf(4L), state.queue.later.map { it.plan.noteId })
        assertEquals(listOf(5L), state.queue.paused.map { it.plan.noteId })
        assertEquals(listOf(6L), state.queue.completed.map { it.plan.noteId })
        assertEquals(4, state.queue.queuedCount)
        assertTrue(state.queue.hasAnyPlan)
    }

    @Test
    fun statsWindowStartsAtTheFirstCellOfTheWeek() = runTest(dispatcher) {
        val stats = ReviewStats(dailyCounts = listOf(0, 2, 0, 1, 0, 0, 3))
        val repository = FakeReviewRepository(stats = stats)

        val viewModel = viewModel(repository)
        advanceUntilIdle()

        // 窗口是"今天 0 点往前推满一周"，起点即第一格 —— 与队列分区同一个基准
        assertEquals(listOf(ReviewStats.windowStart(startOfToday)), repository.statsWindowCalls)
        assertEquals(stats, viewModel.uiState.value.stats)
    }

    @Test
    fun todayCountComesFromTheLastBarNotASecondQuery() = runTest(dispatcher) {
        val repository = FakeReviewRepository(
            stats = ReviewStats(dailyCounts = listOf(0, 2, 0, 1, 0, 0, 3))
        )

        val viewModel = viewModel(repository)
        advanceUntilIdle()

        // 曲线的最后一格就是今天 —— 不再有一条单独的"今日计数"查询
        assertEquals(3, viewModel.uiState.value.stats.todayCount)
        assertEquals(6, viewModel.uiState.value.stats.windowTotal)
    }

    @Test
    fun pauseDisablesPlanAndCancelsItsReminder() = runTest(dispatcher) {
        val target = plan(noteId = 7, id = 70, nextReviewAt = startOfToday - 1)
        val reviewRepository = FakeReviewRepository(plans = listOf(target))
        val reminderRepository = FakeReminderRepository()
        val viewModel = viewModel(reviewRepository, reminderRepository)
        advanceUntilIdle()

        viewModel.pausePlan(target.plan)
        advanceUntilIdle()

        assertEquals(listOf(7L), reviewRepository.disableCalls)
        // 计划停了，REVIEW 提醒必须跟着取消（sourceId 是**计划 id**，不是笔记 id）
        assertEquals(listOf(ReminderType.REVIEW to 70L), reminderRepository.cancelCalls)
        assertEquals(emptyList<ReminderInstance>(), reminderRepository.scheduledCalls)
    }

    @Test
    fun resumeKeepsStepAndRebuildsReminderAtNow() = runTest(dispatcher) {
        val target = plan(noteId = 8, id = 80, currentStep = 2, enabled = false, nextReviewAt = null)
        val reviewRepository = FakeReviewRepository(plans = listOf(target))
        val reminderRepository = FakeReminderRepository()
        val viewModel = viewModel(reviewRepository, reminderRepository)
        advanceUntilIdle()

        viewModel.resumePlan(target.plan)
        advanceUntilIdle()

        assertEquals(listOf(8L to now), reviewRepository.enableCalls)
        val scheduled = reminderRepository.scheduledCalls.single()
        assertEquals(ReminderType.REVIEW, scheduled.type)
        assertEquals(80L, scheduled.sourceId)
        assertEquals(8L, scheduled.noteId)
        assertEquals(now, scheduled.dueAt)
    }

    @Test
    fun restartZeroesStepAndRebuildsReminder() = runTest(dispatcher) {
        val target = plan(noteId = 9, id = 90, currentStep = 4, enabled = false, completedAt = now)
        val reviewRepository = FakeReviewRepository(plans = listOf(target))
        val reminderRepository = FakeReminderRepository()
        val viewModel = viewModel(reviewRepository, reminderRepository)
        advanceUntilIdle()

        viewModel.restartPlan(noteId = 9L)
        advanceUntilIdle()

        assertEquals(listOf(9L to now), reviewRepository.startCalls)
        assertEquals(now, reminderRepository.scheduledCalls.single().dueAt)
    }

    // ---- 提醒档 ----

    @Test
    fun remindersAreBucketedAndContextIsMappedById() = runTest(dispatcher) {
        val overdue = reminder(id = 1, dueAt = now - 1)
        val today = reminder(id = 2, dueAt = now + 1)
        val future = reminder(id = 3, dueAt = endOfToday)
        val completed = reminder(id = 4, dueAt = now, status = ReminderStatus.DONE)
        val reminderRepository = FakeReminderRepository(
            contexts = listOf(
                ReminderWithContext(overdue, noteTitle = "红黑树"),
                ReminderWithContext(today, noteTitle = "线性代数"),
                ReminderWithContext(future, noteTitle = "英语"),
                ReminderWithContext(completed, noteTitle = "算法")
            )
        )

        val viewModel = viewModel(FakeReviewRepository(), reminderRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(overdue), state.reminderBucket.overdue)
        assertEquals(listOf(today), state.reminderBucket.today)
        assertEquals(listOf(future), state.reminderBucket.future)
        assertEquals(listOf(completed), state.reminderBucket.completed)
        assertEquals("红黑树", state.reminderContextById.getValue(1L).noteTitle)
        assertEquals(3, state.pendingReminderCount)
        assertTrue(state.hasAnyReminder)
    }

    @Test
    fun reminderFilterSwitchesTheVisibleList() = runTest(dispatcher) {
        val overdue = reminder(id = 1, dueAt = now - 1)
        val today = reminder(id = 2, dueAt = now + 1)
        val reminderRepository = FakeReminderRepository(
            contexts = listOf(ReminderWithContext(overdue), ReminderWithContext(today))
        )
        val viewModel = viewModel(FakeReviewRepository(), reminderRepository)
        advanceUntilIdle()

        // 默认「待处理」= 今天 + 未来（老提醒中心同口径）
        assertEquals(ReminderFilter.Pending, viewModel.uiState.value.reminderFilter)
        assertEquals(listOf(today), viewModel.uiState.value.filteredReminders)

        viewModel.selectReminderFilter(ReminderFilter.Overdue)
        advanceUntilIdle()
        assertEquals(listOf(overdue), viewModel.uiState.value.filteredReminders)
    }

    @Test
    fun completeTodoReminderWritesBackToTheTodoItself() = runTest(dispatcher) {
        val todo = reminder(id = 5, type = ReminderType.TODO, sourceId = 55L, dueAt = now + 1)
        val reminderRepository = FakeReminderRepository(
            contexts = listOf(ReminderWithContext(todo, todoContent = "交作业"))
        )
        val todoRepository = FakeTodoRepository()
        val viewModel = viewModel(FakeReviewRepository(), reminderRepository, todoRepository)
        advanceUntilIdle()

        viewModel.completeReminder(todo)
        advanceUntilIdle()

        assertEquals(listOf(ReminderType.TODO to 55L), reminderRepository.doneCalls)
        // 既有缺陷的护栏：提醒完成必须把 todo_items.completedAt 一起写掉
        assertEquals(listOf(55L to now), todoRepository.completedCalls)
    }

    @Test
    fun cancelTodoReminderClearsTheTodoRemindAt() = runTest(dispatcher) {
        val todo = reminder(id = 6, type = ReminderType.TODO, sourceId = 56L, dueAt = now + 1)
        val reminderRepository = FakeReminderRepository()
        val todoRepository = FakeTodoRepository()
        val viewModel = viewModel(FakeReviewRepository(), reminderRepository, todoRepository)
        advanceUntilIdle()

        viewModel.cancelReminder(todo)
        advanceUntilIdle()

        assertEquals(listOf(ReminderType.TODO to 56L), reminderRepository.cancelCalls)
        assertEquals(listOf(Triple(56L, null, now)), todoRepository.remindAtCalls)
    }

    @Test
    fun snoozeRebuildsReminderAndMovesTheTodoReminderToo() = runTest(dispatcher) {
        val todo = reminder(id = 7, type = ReminderType.TODO, sourceId = 57L, dueAt = now + 1)
        val reminderRepository = FakeReminderRepository()
        val todoRepository = FakeTodoRepository()
        val viewModel = viewModel(FakeReviewRepository(), reminderRepository, todoRepository)
        advanceUntilIdle()

        viewModel.snoozeReminder(todo, SnoozeOption.OneHourLater)
        advanceUntilIdle()

        val oneHourLater = now + 3_600_000L
        // 重建用 id = 0（upsert 走新增，旧实例由仓库取消），dueAt 落在新时刻
        val scheduled = reminderRepository.scheduledCalls.single()
        assertEquals(0L, scheduled.id)
        assertEquals(oneHourLater, scheduled.dueAt)
        assertEquals(listOf(Triple(57L, oneHourLater, now)), todoRepository.remindAtCalls)
    }

    @Test
    fun snoozeTomorrowMorningLandsAtNineAm() = runTest(dispatcher) {
        val todo = reminder(id = 8, type = ReminderType.TODO, sourceId = 58L, dueAt = now + 1)
        val reminderRepository = FakeReminderRepository()
        val viewModel = viewModel(FakeReviewRepository(), reminderRepository, FakeTodoRepository())
        advanceUntilIdle()

        viewModel.snoozeReminder(todo, SnoozeOption.TomorrowMorning)
        advanceUntilIdle()

        assertEquals(startOfToday + day + 9 * 3_600_000L, reminderRepository.scheduledCalls.single().dueAt)
    }

    // ---- 意图与错误 ----

    @Test
    fun tabIntentIsConsumedOnceOnEntry() = runTest(dispatcher) {
        val intents = AppIntents()
        intents.requestReviewTab(ReviewTab.Reminders)

        val viewModel = viewModel(FakeReviewRepository(), appIntents = intents)
        advanceUntilIdle()

        assertEquals(ReviewTab.Reminders, viewModel.uiState.value.selectedTab)
        // 消费即清空：同一次意图不会在页面下次被复用时再抢一次档位
        assertNull(intents.pendingReviewTab.value)
    }

    @Test
    fun defaultTabIsPendingAndCanBeSwitched() = runTest(dispatcher) {
        val viewModel = viewModel(FakeReviewRepository())
        advanceUntilIdle()

        assertEquals(ReviewTab.Pending, viewModel.uiState.value.selectedTab)
        viewModel.selectTab(ReviewTab.Reminders)
        assertEquals(ReviewTab.Reminders, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun actionErrorsAreExposedAndClearable() = runTest(dispatcher) {
        val todo = reminder(id = 9, type = ReminderType.TODO, sourceId = 59L, dueAt = now + 1)
        val reminderRepository = FakeReminderRepository(
            markDoneResult = RepositoryResult.Error("完成失败")
        )
        val viewModel = viewModel(FakeReviewRepository(), reminderRepository)
        advanceUntilIdle()

        viewModel.completeReminder(todo)
        advanceUntilIdle()
        assertEquals("完成失败", viewModel.uiState.value.error)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    private fun viewModel(
        reviewRepository: ReviewRepository,
        reminderRepository: ReminderRepository = FakeReminderRepository(),
        todoRepository: TodoRepository = FakeTodoRepository(),
        appIntents: AppIntents = AppIntents()
    ) = ReviewCenterViewModel(
        reviewRepository = reviewRepository,
        reminderRepository = reminderRepository,
        manageReviewPlan = ManageReviewPlanUseCase(reviewRepository, ReviewReminderSync(reminderRepository)),
        resolveReminder = ResolveReminderUseCase(reminderRepository, todoRepository),
        appIntents = appIntents,
        nowProvider = { now },
        startOfTodayProvider = { startOfToday }
    )

    private fun plan(
        noteId: Long,
        id: Long = noteId,
        currentStep: Int = 0,
        enabled: Boolean = true,
        nextReviewAt: Long? = null,
        completedAt: Long? = null
    ) = ReviewPlanWithNote(
        plan = ReviewPlan(
            id = id,
            noteId = noteId,
            enabled = enabled,
            currentStep = currentStep,
            nextReviewAt = nextReviewAt,
            updatedAt = noteId,
            completedAt = completedAt
        ),
        noteTitle = "笔记 $noteId"
    )

    private fun reminder(
        id: Long,
        type: ReminderType = ReminderType.REVIEW,
        sourceId: Long = id,
        dueAt: Long = now,
        status: ReminderStatus = ReminderStatus.SCHEDULED
    ) = ReminderInstance(
        id = id,
        type = type,
        sourceId = sourceId,
        noteId = id,
        dueAt = dueAt,
        status = status,
        notificationId = id.toInt(),
        updatedAt = id
    )
}

private class FakeReviewRepository(
    private val plans: List<ReviewPlanWithNote> = emptyList(),
    private val stats: ReviewStats = ReviewStats()
) : ReviewRepository {
    private val plansFlow =
        MutableStateFlow<RepositoryResult<List<ReviewPlanWithNote>>>(RepositoryResult.Success(plans))

    val enableCalls = mutableListOf<Pair<Long, Long>>()
    val startCalls = mutableListOf<Pair<Long, Long>>()
    val disableCalls = mutableListOf<Long>()
    val statsWindowCalls = mutableListOf<Long>()

    override fun observePlans(): Flow<RepositoryResult<List<ReviewPlanWithNote>>> = plansFlow

    override fun observeStats(windowStart: Long): Flow<ReviewStats> {
        statsWindowCalls += windowStart
        return flowOf(stats)
    }

    override suspend fun getPlanByNoteId(noteId: Long): RepositoryResult<ReviewPlan?> =
        RepositoryResult.Success(plans.firstOrNull { it.plan.noteId == noteId }?.plan)

    override suspend fun startPlan(noteId: Long, now: Long): RepositoryResult<ReviewPlan> {
        startCalls += noteId to now
        // 忠实于真实仓库：返回的是**库里那条计划**（id / 档位都不是新造的），
        // 否则 ReviewReminderSync 拿 `plan.id` 当 sourceId 时会与真机对不上。
        val existing = plans.firstOrNull { it.plan.noteId == noteId }?.plan
        return RepositoryResult.Success(
            existing?.copy(enabled = true, currentStep = 0, nextReviewAt = now, completedAt = null)
                ?: ReviewPlan(id = noteId, noteId = noteId, nextReviewAt = now)
        )
    }

    override suspend fun enablePlan(noteId: Long, now: Long): RepositoryResult<ReviewPlan> {
        enableCalls += noteId to now
        // 「继续」保留档位：只把 enabled 置回、到期时间挪到 now
        val existing = plans.firstOrNull { it.plan.noteId == noteId }?.plan
        return RepositoryResult.Success(
            existing?.copy(enabled = true, nextReviewAt = now)
                ?: ReviewPlan(id = noteId, noteId = noteId, nextReviewAt = now)
        )
    }

    override suspend fun recordReview(
        plan: ReviewPlan,
        rating: ReviewRating,
        reviewedAt: Long
    ): RepositoryResult<ReviewPlan> = RepositoryResult.Success(plan)

    override suspend fun disablePlan(noteId: Long): RepositoryResult<Unit> {
        disableCalls += noteId
        return RepositoryResult.Success(Unit)
    }
}

private class FakeReminderRepository(
    private val contexts: List<ReminderWithContext> = emptyList(),
    private val markDoneResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
) : ReminderRepository {
    private val contextFlow =
        MutableStateFlow<RepositoryResult<List<ReminderWithContext>>>(RepositoryResult.Success(contexts))

    val doneCalls = mutableListOf<Pair<ReminderType, Long>>()
    val cancelCalls = mutableListOf<Pair<ReminderType, Long>>()
    val scheduledCalls = mutableListOf<ReminderInstance>()

    override fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>> =
        flowOf(RepositoryResult.Success(contexts.map { it.reminder }))

    override fun observeAllWithContext(): Flow<RepositoryResult<List<ReminderWithContext>>> = contextFlow

    override suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getActiveReminder(
        type: ReminderType,
        sourceId: Long
    ): RepositoryResult<ReminderInstance?> = RepositoryResult.Success(null)

    override suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long> {
        scheduledCalls += reminder
        return RepositoryResult.Success(reminder.id)
    }

    override suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit> {
        doneCalls += type to sourceId
        return markDoneResult
    }

    override suspend fun markFired(
        reminder: ReminderInstance,
        firedAt: Long
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit> {
        cancelCalls += type to sourceId
        return RepositoryResult.Success(Unit)
    }
}

private class FakeTodoRepository : TodoRepository {
    val completedCalls = mutableListOf<Pair<Long, Long>>()
    val remindAtCalls = mutableListOf<Triple<Long, Long?, Long>>()

    override fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun addTodo(todo: TodoItem): RepositoryResult<Long> =
        RepositoryResult.Success(1L)

    override suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit> {
        completedCalls += id to completedAt
        return RepositoryResult.Success(Unit)
    }

    override suspend fun updateRemindAt(
        id: Long,
        remindAt: Long?,
        updatedAt: Long
    ): RepositoryResult<Unit> {
        remindAtCalls += Triple(id, remindAt, updatedAt)
        return RepositoryResult.Success(Unit)
    }
}
