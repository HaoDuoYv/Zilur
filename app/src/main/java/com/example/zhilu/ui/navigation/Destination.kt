package com.example.zhilu.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

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
 * 还剩 [NoteEdit] 一个带参数的页面（noteId 是它真正的身份，绕不开）。
 */
sealed class Destination(val path: String) {
    open val route: String get() = path

    data object Home : Destination("home")
    data object Tags : Destination("tags")

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
    data object Reminders : Destination("reminders")

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

    data object NoteEdit : Destination("note/{noteId}") {
        const val ARG_NOTE_ID = "noteId"

        override val route: String get() = "note"

        fun createRoute(noteId: Long = 0L): String = "note/$noteId"
    }
}

/**
 * 底部导航承载的四个平级页面：笔记 / 标签 / 助手 / 我的。
 *
 * 存的是 [Destination.route]（不带参数的那一份）。若放路由模板，`isTopLevel` 拿
 * `route.substringBefore('?')` 去比就永远不匹配 —— 底栏会在助手页整条消失（真机踩过）。
 */
val TopLevelRoutes = setOf(
    Destination.Home.route,
    Destination.Tags.route,
    Destination.Assistant.route,
    Destination.Settings.route
)

/** 该目的地是否为底部导航平级页（决定是否显示底栏）。 */
val Destination.isTopLevel: Boolean
    get() = route.substringBefore('?') in TopLevelRoutes

/** 以与底栏一致的选项切到「助手」平级页（避免返回栈膨胀，并复用该页的 ViewModel）。 */
fun NavHostController.navigateToAssistant() {
    navigate(Destination.Assistant.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}