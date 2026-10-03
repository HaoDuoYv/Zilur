package com.example.zhilu.ui.assistant

import com.example.zhilu.domain.model.AiConversation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 历史抽屉里的一组对话（组标题 + 组内对话）。 */
data class ConversationGroup(
    val label: String,
    val conversations: List<AiConversation>
)

private val monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.getDefault())

/**
 * 把历史对话按时间分组（设计参考：DeepSeek 的历史抽屉）。
 *
 * 分组口径：**今天 / 7 天内 / 30 天内 / 更早的按月**。
 * 前面三档用相对时间（近期会话最常翻），更早的换成「2026年8月」这种绝对月份
 * —— 再往后"多少天前"就失去意义了，用户记得住的是"那个月做的"。
 *
 * 输入按 `updatedAt` 倒序（DAO 就是这么查的），所以同一组的会话必然相邻，
 * 顺序扫描即可；调用方不需要先排序。
 *
 * 纯函数（`now` 与 `zone` 可注入），可直接单测。
 */
fun groupConversations(
    conversations: List<AiConversation>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): List<ConversationGroup> {
    if (conversations.isEmpty()) return emptyList()

    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val groups = mutableListOf<ConversationGroup>()
    var currentLabel: String? = null
    var bucket = mutableListOf<AiConversation>()

    fun flush() {
        val label = currentLabel ?: return
        if (bucket.isNotEmpty()) groups += ConversationGroup(label, bucket.toList())
        bucket = mutableListOf()
    }

    conversations.forEach { conversation ->
        val day = Instant.ofEpochMilli(conversation.updatedAt).atZone(zone).toLocalDate()
        val label = labelFor(day, today)
        if (label != currentLabel) {
            flush()
            currentLabel = label
        }
        bucket += conversation
    }
    flush()
    return groups
}

private fun labelFor(day: LocalDate, today: LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(day, today)
    return when {
        // 未来时间（时钟回拨等）也算今天，避免出现"−1 天前"这种组
        days <= 0L -> "今天"
        days < 7L -> "7 天内"
        days < 30L -> "30 天内"
        else -> day.format(monthFormatter)
    }
}
