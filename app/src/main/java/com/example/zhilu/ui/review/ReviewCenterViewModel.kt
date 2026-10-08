package com.example.zhilu.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.reminder.ReminderClassifier
import com.example.zhilu.domain.reminder.ReviewQueueClassifier
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.usecase.ManageReviewPlanUseCase
import com.example.zhilu.domain.usecase.ResolveReminderUseCase
import com.example.zhilu.ui.navigation.AppIntents
import com.example.zhilu.ui.reminder.ReminderFilter
import com.example.zhilu.ui.reminder.SnoozeOption
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 复习中心：一档管"计划与队列"（待复习），一档管"提醒实例"（提醒）。
 *
 * 数据来源是两条独立 Flow（`review_plans JOIN notes` / `reminder_instances JOIN …`），
 * 分别分类后进同一个 UiState；提醒行的展示上下文按提醒 id 建映射，供 UI 取用。
 * 列表动作全部**不做本地乐观更新**：写库成功即由 Room Flow 回流，
 * 手写一份"移动后的列表"只会与回流数据打架。
 */
@HiltViewModel
class ReviewCenterViewModel(
    private val reviewRepository: ReviewRepository,
    private val reminderRepository: ReminderRepository,
    private val manageReviewPlan: ManageReviewPlanUseCase,
    private val resolveReminder: ResolveReminderUseCase,
    private val appIntents: AppIntents,
    private val queueClassifier: ReviewQueueClassifier = ReviewQueueClassifier(),
    private val reminderClassifier: ReminderClassifier = ReminderClassifier(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val startOfTodayProvider: () -> Long = { defaultStartOfToday() }
) : ViewModel() {

    @Inject
    constructor(
        reviewRepository: ReviewRepository,
        reminderRepository: ReminderRepository,
        manageReviewPlan: ManageReviewPlanUseCase,
        resolveReminder: ResolveReminderUseCase,
        appIntents: AppIntents
    ) : this(
        reviewRepository = reviewRepository,
        reminderRepository = reminderRepository,
        manageReviewPlan = manageReviewPlan,
        resolveReminder = resolveReminder,
        appIntents = appIntents,
        // 必须显式给出第 6 个参数：本辅助构造与主构造前 5 参签名相同，
        // 少传一参时 `this(...)` 会解析回自己，触发 "cycle in the delegation calls chain"。
        queueClassifier = ReviewQueueClassifier()
    )

    private val _uiState = MutableStateFlow(ReviewCenterUiState())
    val uiState: StateFlow<ReviewCenterUiState> = _uiState.asStateFlow()

    init {
        observePlans()
        observeReviewedToday()
        observeReminders()
        observeIntents()
    }

    fun selectTab(tab: ReviewTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectReminderFilter(filter: ReminderFilter) {
        _uiState.update { it.copy(reminderFilter = filter) }
    }

    // ---- 「待复习」档动作（计划与提醒成对配平，见 ManageReviewPlanUseCase）----

    /** 暂停：保留档位，之后可「继续」。 */
    fun pausePlan(plan: ReviewPlan) {
        launchAction { manageReviewPlan.pause(plan) }
    }

    /** 继续：保留档位、立即回到待复习队列。 */
    fun resumePlan(plan: ReviewPlan) {
        launchAction { manageReviewPlan.resume(plan, nowProvider()) }
    }

    /** 重新开始：档位归零（「已完成」的计划拾起）。 */
    fun restartPlan(noteId: Long) {
        launchAction { manageReviewPlan.restart(noteId, nowProvider()) }
    }

    // ---- 「提醒」档动作（TODO 类回写源头，REVIEW 类 UI 层已禁掉）----

    fun completeReminder(reminder: ReminderInstance) {
        launchAction { resolveReminder.complete(reminder, nowProvider()) }
    }

    fun cancelReminder(reminder: ReminderInstance) {
        launchAction { resolveReminder.cancel(reminder, nowProvider()) }
    }

    fun snoozeReminder(reminder: ReminderInstance, option: SnoozeOption) {
        val dueAt = when (option) {
            SnoozeOption.OneHourLater -> nowProvider() + HOUR
            SnoozeOption.TomorrowMorning -> startOfTodayProvider() + DAY + MORNING_HOUR
        }
        launchAction { resolveReminder.snooze(reminder, dueAt, nowProvider()) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 统一的"执行 + 错误上浮"：动作成功不需要拼 UI（Flow 会回流），只处理失败。
     */
    private fun launchAction(block: suspend () -> RepositoryResult<*>) {
        viewModelScope.launch {
            val result = block()
            if (result is RepositoryResult.Error) {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    private fun observePlans() {
        viewModelScope.launch {
            reviewRepository.observePlans().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update {
                        it.copy(
                            queue = queueClassifier.classify(result.data, startOfTodayProvider()),
                            startOfToday = startOfTodayProvider(),
                            isLoading = false,
                            error = null
                        )
                    }
                    is RepositoryResult.Error -> _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    private fun observeReviewedToday() {
        viewModelScope.launch {
            reviewRepository.observeEventCountSince(startOfTodayProvider()).collect { count ->
                _uiState.update { it.copy(reviewedTodayCount = count) }
            }
        }
    }

    private fun observeReminders() {
        viewModelScope.launch {
            reminderRepository.observeAllWithContext().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> {
                        val contexts = result.data
                        val bucket = reminderClassifier.classify(
                            reminders = contexts.map { it.reminder },
                            now = nowProvider(),
                            startOfToday = startOfTodayProvider()
                        )
                        _uiState.update {
                            it.copy(
                                reminderBucket = bucket,
                                reminderContextById = contexts.associateBy { ctx -> ctx.reminder.id },
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

    /**
     * 消费「这次进来要看哪一档」的一次性意图（铃铛 / 通知 / 设置页入口）。
     *
     * 用 collect 而不是 init 里读一次：复习中心是顶层页，`restoreState` 会复用
     * 同一个 ViewModel —— 读一次只能覆盖"VM 首次创建"那一回，之后从铃铛进来就失灵了。
     */
    private fun observeIntents() {
        viewModelScope.launch {
            appIntents.pendingReviewTab.collect { pending ->
                if (pending != null) {
                    appIntents.consumeReviewTab()
                    _uiState.update { it.copy(selectedTab = pending) }
                }
            }
        }
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L

        /** 「明天上午」的落点：上午 9 点整。 */
        const val MORNING_HOUR = 9 * HOUR

        fun defaultStartOfToday(): Long =
            LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
    }
}
