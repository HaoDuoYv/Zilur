package com.example.zhilu.ui.home

import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页列表的纯函数工具：时间格式化、摘要提取、时间线分组。
 *
 * 这些函数被列表行组件与回收站复用，并已被单元测试覆盖，重构期间保持签名与语义不变。
 */
fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 7 -> "${days}天前"
        days < 365 -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

fun notePreviewText(note: Note): String? {
    note.blocks.firstOrNull { it.type == BlockType.TEXT && it.content.isNotBlank() }?.let {
        return it.content.trim()
    }
    note.blocks.firstOrNull { it.type == BlockType.BRANCH && it.content.isNotBlank() }?.let {
        return it.content.trim()
    }
    note.blocks.firstOrNull { it.type == BlockType.CODE && it.content.isNotBlank() }?.let {
        return it.content.trim().lineSequence().first().take(80)
    }
    note.blocks.firstOrNull { it.type == BlockType.LINK && it.content.isNotBlank() }?.let {
        return it.content.trim()
    }
    note.blocks.firstOrNull { it.type == BlockType.LATEX && it.content.isNotBlank() }?.let {
        return it.content.trim()
    }
    return null
}

data class TimelineGroup(
    val label: String,
    val notes: List<Note>
)

fun groupNotesByTimeline(notes: List<Note>): List<TimelineGroup> {
    if (notes.isEmpty()) return emptyList()
    val sorted = notes.sortedByDescending { it.updatedAt }
    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L
    return sorted.groupBy { note ->
        val days = (now - note.updatedAt) / dayMs
        when {
            days < 1 -> "今天"
            days < 2 -> "昨天"
            days < 7 -> "本周"
            days < 30 -> "本月"
            days < 365 -> "今年"
            else -> "更早"
        }
    }.map { (label, groupNotes) -> TimelineGroup(label, groupNotes) }
        .sortedBy { groupOrder(it.label) }
}

private fun groupOrder(label: String): Int = when (label) {
    "今天" -> 0
    "昨天" -> 1
    "本周" -> 2
    "本月" -> 3
    "今年" -> 4
    else -> 5
}