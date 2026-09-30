package com.example.zhilu.ui.home

import com.example.zhilu.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineGroupNotesTest {
    @Test
    fun groupsRecentNotesByBucket() {
        val now = System.currentTimeMillis()
        val notes = listOf(
            Note(id = 1, title = "today", updatedAt = now - 1_000L),
            Note(id = 2, title = "yesterday", updatedAt = now - 86_400_000L),
            Note(id = 3, title = "older", updatedAt = now - 400L * 86_400_000L)
        )
        val groups = groupNotesByTimeline(notes)
        assertEquals(listOf("今天", "昨天", "更早"), groups.map { it.label })
        assertEquals(listOf(1L), groups[0].notes.map { it.id })
        assertEquals(listOf(2L), groups[1].notes.map { it.id })
        assertEquals(listOf(3L), groups[2].notes.map { it.id })
    }

    @Test
    fun emptyNotesProduceEmptyGroups() {
        assertTrue(groupNotesByTimeline(emptyList()).isEmpty())
    }
}
