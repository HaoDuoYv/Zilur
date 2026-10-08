package com.example.zhilu.ui.navigation

import com.example.zhilu.ui.review.ReviewTab
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨页**一次性意图**交接（consume-once）：告诉"下一个打开的页面"这次要做什么。
 *
 * ## 为什么不能走路由参数
 *
 * 与 `AiPromptHandoff` 同一条教训：带参数的路由每次都是**新的导航 entry**，
 * 会把目标页的 ViewModel 换成一份新状态（助手页曾经因为 `?prefill=` 造出第二个实例，
 * 后台任务、输入草稿全丢）。所以"进复习中心落在哪一档""打开笔记时自动弹复习面板"
 * "进搜索预置哪个标签"这类意图，一律走进程级单例，导航本身保持无参数。
 *
 * ## 消费语义
 *
 * 值被放到这里后**只生效一次**，消费方取走后立即清空；消费方用 collect 而不是
 * 一次性读取 —— 顶层页的 ViewModel 会被 `restoreState` 复用（不会被重建），
 * 一次性读取会漏掉"页面已存在时再次进入"的意图。
 */
@Singleton
class AppIntents @Inject constructor() {

    private val _pendingReviewTab = MutableStateFlow<ReviewTab?>(null)

    /** 下一次进入复习中心时预选的档位。 */
    val pendingReviewTab: StateFlow<ReviewTab?> = _pendingReviewTab.asStateFlow()

    private val _pendingReviewSheetNoteId = MutableStateFlow<Long?>(null)

    /** 下一次打开该笔记时自动弹出复习面板。 */
    val pendingReviewSheetNoteId: StateFlow<Long?> = _pendingReviewSheetNoteId.asStateFlow()

    private val _pendingSearchTagId = MutableStateFlow<Long?>(null)

    /** 下一次进入首页搜索时预置的标签筛选。 */
    val pendingSearchTagId: StateFlow<Long?> = _pendingSearchTagId.asStateFlow()

    /** 请求：进入复习中心后落在 [tab] 档。 */
    fun requestReviewTab(tab: ReviewTab) {
        _pendingReviewTab.value = tab
    }

    /** 复习中心取走档位意图并清空（没有意图时返回 null）。 */
    fun consumeReviewTab(): ReviewTab? {
        val current = _pendingReviewTab.value ?: return null
        _pendingReviewTab.value = null
        return current
    }

    /** 请求：打开 [noteId] 这篇笔记时自动弹出复习面板。 */
    fun requestReviewSheet(noteId: Long) {
        _pendingReviewSheetNoteId.value = noteId
    }

    /**
     * 笔记页尝试消费：只有意图指向**本篇笔记**时才取走并返回 true。
     *
     * 不匹配时保留意图 —— "开始复习 → 打开笔记"链路里用户中途退回再点别的笔记，
     * 目标笔记下次打开仍应弹出面板。
     */
    fun consumeReviewSheetNoteId(noteId: Long): Boolean {
        if (_pendingReviewSheetNoteId.value != noteId) return false
        _pendingReviewSheetNoteId.value = null
        return true
    }

    /** 请求：进入首页搜索时预置标签 [tagId] 的筛选。 */
    fun requestSearchTag(tagId: Long) {
        _pendingSearchTagId.value = tagId
    }

    /** 首页取走标签筛选意图并清空（没有意图时返回 null）。 */
    fun consumeSearchTagId(): Long? {
        val current = _pendingSearchTagId.value ?: return null
        _pendingSearchTagId.value = null
        return current
    }
}
