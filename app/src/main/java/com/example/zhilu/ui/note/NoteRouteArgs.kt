package com.example.zhilu.ui.note

import androidx.lifecycle.SavedStateHandle

object NoteRouteArgs {
    private const val NOTE_ID = "noteId"

    fun noteId(savedStateHandle: SavedStateHandle): Long {
        return when (val value = savedStateHandle.get<Any?>(NOTE_ID)) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }
}
