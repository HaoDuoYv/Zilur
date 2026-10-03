package com.example.zhilu.ui.assistant

import com.example.zhilu.domain.model.AiConversation
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 历史对话的时间分组（参考主流对话应用的抽屉）。
 *
 * 用固定时区 + 固定"现在"来测：分组口径依赖日历天，跟着系统时区跑会变成 flaky 测试。
 */
class ConversationGroupsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 以"今天"为基准造一个时间戳：负偏移表示过去。 */
    private fun at(daysAgo: Long, hour: Int = 12): Long =
        LocalDate.now(zone).minusDays(daysAgo)
            .atTime(hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun conversation(id: Long, daysAgo: Long, title: String = "对话$id") =
        AiConversation(id = id, title = title, updatedAt = at(daysAgo))

    @Test
    fun `空列表没有分组`() {
        assertEquals(emptyList<ConversationGroup>(), groupConversations(emptyList(), zone = zone))
    }

    @Test
    fun `今天 七天内 三十天内 三档相对时间`() {
        val groups = groupConversations(
            listOf(conversation(1, 0), conversation(2, 3), conversation(3, 20)),
            zone = zone
        )
        assertEquals(listOf("今天", "7 天内", "30 天内"), groups.map { it.label })
        assertEquals(listOf(1L), groups[0].conversations.map { it.id })
        assertEquals(listOf(2L), groups[1].conversations.map { it.id })
        assertEquals(listOf(3L), groups[2].conversations.map { it.id })
    }

    @Test
    fun `超过三十天按月分组`() {
        val groups = groupConversations(
            listOf(conversation(1, 0), conversation(2, 40), conversation(3, 70)),
            zone = zone
        )
        assertEquals("今天", groups[0].label)
        // 40 天前与 70 天前大概率落在两个不同月份；这里只断言"标签不再是相对时间"
        groups.drop(1).forEach { group ->
            assertEquals(true, group.label.endsWith("月"))
            assertEquals(true, group.label.contains("年"))
        }
    }

    @Test
    fun `同一天的会话归到同一组并保持原有顺序`() {
        val groups = groupConversations(
            listOf(conversation(1, 0), conversation(2, 0), conversation(3, 0)),
            zone = zone
        )
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L, 3L), groups[0].conversations.map { it.id })
    }

    @Test
    fun `时间戳在未来也算今天`() {
        // 时钟回拨/时区错乱时不能冒出"−1 天前"这种组
        val groups = groupConversations(listOf(conversation(1, -2)), zone = zone)
        assertEquals(listOf("今天"), groups.map { it.label })
    }

    @Test
    fun `七天与三十天的边界`() {
        val groups = groupConversations(
            listOf(conversation(1, 6), conversation(2, 7), conversation(3, 29), conversation(4, 30)),
            zone = zone
        )
        assertEquals("7 天内", groups[0].label)
        assertEquals(listOf(1L), groups[0].conversations.map { it.id })
        assertEquals("30 天内", groups[1].label)
        assertEquals(listOf(2L, 3L), groups[1].conversations.map { it.id })
        assertEquals(true, groups[2].label.endsWith("月"))
        assertEquals(listOf(4L), groups[2].conversations.map { it.id })
    }
}
