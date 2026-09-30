package com.example.zhilu.ui.note

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTimeFormatterTest {
    @Test
    fun nullAndInvalidTimeShowPending() {
        assertEquals("待安排", formatReminderTime(null))
        assertEquals("待安排", formatReminderTime(0L))
        assertEquals("待安排", formatReminderTime(-1L))
    }

    @Test
    fun positiveTimeFormatsAsDateTime() {
        val formatted = formatReminderTime(1_700_000_000_000L)
        assertEquals(16, formatted.length)
    }
}
