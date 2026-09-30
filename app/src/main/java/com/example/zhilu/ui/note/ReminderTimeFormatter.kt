package com.example.zhilu.ui.note

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatReminderTime(timeMillis: Long?): String {
    if (timeMillis == null || timeMillis <= 0L) return "待安排"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
}
