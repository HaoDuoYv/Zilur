package com.example.zhilu.ui.navigation

import android.net.Uri
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * 一个导航目的地。
 *
 * 头两个属性刻意分开，别再合并：
 * - [path]：**在 NavHost 里注册的路由模板**（可带 `{arg}` 占位），也是选中态比较的来源；
 * - [route]：**真正拿去 navigate 的路由**。带可选参数的页面（助手页）在这里去掉 `?…` 模板，
 *   否则 `navigate(path)` 会把 `{prefill}` 当成字面值传进去 —— 输入框里就会出现 `{prefill}`
 *   这行字（真机踩过）。其余页面两者相同。
 */
sealed class Destination(val path: String) {
    open val route: String get() = path

    data object Home : Destination("home")
    data object Tags : Destination("tags")

    /**
     * 助手页。
     *
     * [createRoute] 是"带着一句预填去问 AI"的入口，底栏 ＋ 的「AI 创建」用它。
     */
    data object Assistant : Destination("assistant?prefill={prefill}") {
        const val ARG_PREFILL = "prefill"

        override val route: String = "assistant"

        fun createRoute(prefill: String): String =
            "$route?$ARG_PREFILL=${Uri.encode(prefill)}"
    }

    data object Settings : Destination("settings")
    data object Camera : Destination("camera")
    data object Trash : Destination("trash")
    data object Reminders : Destination("reminders")

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

/** 以与底栏一致的选项切到「助手」平级页（避免返回栈膨胀）。 */
fun NavHostController.navigateToAssistant() {
    navigate(Destination.Assistant.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 切到助手页并**预填**输入框（底栏 ＋ 的「AI 创建」）。
 *
 * 不走 `restoreState`：要的是"带着新的一句去问"，恢复上次的会话状态反而会让预填落空。
 */
fun NavHostController.navigateToAssistant(prefill: String) {
    navigate(Destination.Assistant.createRoute(prefill)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = false
    }
}