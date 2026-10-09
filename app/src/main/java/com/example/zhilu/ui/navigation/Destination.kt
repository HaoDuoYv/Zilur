package com.example.zhilu.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.example.zhilu.ui.review.ReviewTab

/**
 * 一个导航目的地。
 *
 * 之所以有 [path] 与 [route] 两套：
 * - [path]：**在 NavHost 里注册的路由模板**（可带 `{arg}` 占位）；
 * - [route]：**真正拿去 navigate 的路由**，带占位参数的页面在这里去掉 `?…`。
 *
 * 带参数的路由绝不能出现在「跨页交接」的场景里（助手页曾经用 `?prefill=` 收开场白）：
 * 每次拼参数都是一次**新的导航 entry**，会造出第二个 ViewModel、把页面状态全部重置。
 * 助手页的开场白因此改走进程级的 `AiPromptHandoff`，[Assistant] 现在与 [route] 相同；
 * 复习中心的"落在哪一档"同理走 [AppIntents]；
 * 还剩 [NoteEdit] 一个带参数的页面（noteId 是它真正的身份，绕不开）。
 */
sealed class Destination(val path: String) {
    open val route: String get() = path

    data object Home : Destination("home")

    /**
     * 复习中心（底栏第二格）。
     *
     * 取代原先的「标签」页：标签的职责（索引 / 筛选 / 管理）整体并入搜索链路，
     * 这一格让给"有数据层却没有独立界面"的复习计划与提醒（两档，见 `ReviewTab`）。
     * 档位意图不写进路由 —— 带参数的路由会新建 entry 并重置页面状态，
     * 走 [navigateToReview] 的一次性交接（[AppIntents.pendingReviewTab]）。
     */
    data object Review : Destination("review")

    /**
     * 助手页。
     *
     * 刻意保持无参数：任务状态 / 当前会话 / 输入草稿全靠**同一个 entry 上的 ViewModel**
     * 活着，任何带参数的路由都会把它换成一份新状态。开场白见 `AiPromptHandoff`。
     */
    data object Assistant : Destination("assistant")

    data object Settings : Destination("settings")
    data object Camera : Destination("camera")
    data object Trash : Destination("trash")

    /**
     * 外观设置。
     *
     * 从「我的」里独立出来：外观项已经长到三组（配色方案 / 明暗 / 强调色 + 无障碍色板），
     * 还要放配色缩略卡，混在设置长列表里既难找也没空间。
     */
    data object Appearance : Destination("appearance")

    /**
     * AI 配置。
     *
     * 与 [Appearance] 同一个理由从「我的」里独立出来：多供应商之后这里有服务列表、
     * 增删改表单、默认模型与失败回退三组，塞进设置长列表既放不下也找不到。
     */
    data object AiConfig : Destination("ai_config")

    /**
     * 复习间隔（自定义复习阶梯）。
     *
     * 独立成页：除了输入，还要放预设、逐档预览与"改了之后已有计划怎么走"的说明，
     * 塞成设置里的一行放不下。入口在「我的 → 复习」。
     */
    data object ReviewInterval : Destination("review_interval")

    data object NoteEdit : Destination("note/{noteId}") {
        const val ARG_NOTE_ID = "noteId"

        override val route: String get() = "note"

        fun createRoute(noteId: Long = 0L): String = "note/$noteId"
    }
}

/**
 * 底部导航承载的四个平级页面：笔记 / 复习 / 助手 / 我的。
 *
 * 存的是 [Destination.route]（不带参数的那一份）。若放路由模板，`isTopLevel` 拿
 * `route.substringBefore('?')` 去比就永远不匹配 —— 底栏会在助手页整条消失（真机踩过）。
 */
val TopLevelRoutes = setOf(
    Destination.Home.route,
    Destination.Review.route,
    Destination.Assistant.route,
    Destination.Settings.route
)

/** 该目的地是否为底部导航平级页（决定是否显示底栏）。 */
val Destination.isTopLevel: Boolean
    get() = route.substringBefore('?') in TopLevelRoutes

/**
 * 以与底栏一致的选项切到某个平级页（避免返回栈膨胀，并复用该页的 ViewModel）。
 *
 * 必须用 route（去掉模板的那一份）：用 path 会把 `{noteId}` 这类占位符当字面值传进去。
 * 底栏原本有一份私有拷贝，复习中心 / 助手 / 深链三处又各写一遍 flags —— flags 一旦
 * 有人改错就是"进页面必重置"的隐蔽 bug，统一收敛到这里。
 */
fun NavHostController.navigateTopLevel(destination: Destination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 以与底栏一致的选项切到「助手」平级页。 */
fun NavHostController.navigateToAssistant() = navigateTopLevel(Destination.Assistant)

/**
 * 切到「复习中心」，并可带上"这次要落在哪一档"的一次性意图（如铃铛 / 通知 → 提醒档）。
 *
 * [tab] 为 null 时保持上一次的档位（顶层页被 restoreState 复用时 VM 状态还在）。
 */
fun NavHostController.navigateToReview(intents: AppIntents, tab: ReviewTab? = null) {
    tab?.let(intents::requestReviewTab)
    navigateTopLevel(Destination.Review)
}

/**
 * 切到「首页搜索」并预置标签 [tagId] 的筛选。
 *
 * 标签页撤掉后，标签只剩搜索一个出口，所以这条链路是标签的主要入口
 * （笔记卡片上的标签胶囊、将来的任意标签位都走它）。
 *
 * 已经在首页时导航部分是空操作 —— 意图靠 `HomeViewModel` 对 [AppIntents.pendingSearchTagId]
 * 的 collect 送达，不依赖导航。
 */
fun NavHostController.navigateToSearchTag(intents: AppIntents, tagId: Long) {
    intents.requestSearchTag(tagId)
    navigateTopLevel(Destination.Home)
}
