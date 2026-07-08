package com.example.zhilu.ui.note

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRouteArgsTest {
    @Test
    fun noteIdAcceptsLongNavigationArgument() {
        val handle = SavedStateHandle(mapOf("noteId" to 0L))

        assertEquals(0L, NoteRouteArgs.noteId(handle))
    }

    @Test
    fun noteIdAcceptsStringNavigationArgument() {
        val handle = SavedStateHandle(mapOf("noteId" to "42"))

        assertEquals(42L, NoteRouteArgs.noteId(handle))
    }
}
