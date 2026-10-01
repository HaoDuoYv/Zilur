package com.example.zhilu.ui.navigation

sealed class Destination(val path: String) {
    data object Home : Destination("home")
    data object Tags : Destination("tags")
    data object Assistant : Destination("assistant")
    data object Settings : Destination("settings")
    data object Camera : Destination("camera")
    data object Trash : Destination("trash")
    data object Reminders : Destination("reminders")

    data object NoteEdit : Destination("note/{noteId}") {
        const val ARG_NOTE_ID = "noteId"

        fun createRoute(noteId: Long = 0L): String = "note/$noteId"
    }
}

/** 底部导航承载的四个平级页面：笔记 / 标签 / 助手 / 我的。 */
val TopLevelRoutes = setOf(
    Destination.Home.path,
    Destination.Tags.path,
    Destination.Assistant.path,
    Destination.Settings.path
)

/** 该目的地是否为底部导航平级页（决定是否显示底栏）。 */
val Destination.isTopLevel: Boolean
    get() = path in TopLevelRoutes