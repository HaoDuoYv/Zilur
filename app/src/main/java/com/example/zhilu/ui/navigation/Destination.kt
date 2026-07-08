package com.example.zhilu.ui.navigation

sealed class Destination(val path: String) {
    data object Home : Destination("home")
    data object Tags : Destination("tags")
    data object Explore : Destination("explore")
    data object Settings : Destination("settings")
    data object Camera : Destination("camera")
    data object Trash : Destination("trash")
    data object Reminders : Destination("reminders")

    data object NoteEdit : Destination("note/{noteId}") {
        const val ARG_NOTE_ID = "noteId"

        fun createRoute(noteId: Long = 0L): String = "note/$noteId"
    }
}
