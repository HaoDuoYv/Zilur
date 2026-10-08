package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.AiMessageEntity
import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.AiRefKind
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMapperTest {

    @Test
    fun `没有引用时 refsJson 落空 读回为空列表`() {
        val entity = AiMapper.toEntity(message())
        assertNull(entity.refsJson)
        assertTrue(AiMapper.toDomain(entity).refs.isEmpty())
    }

    @Test
    fun `带快照的引用可完整往返`() {
        val refs = listOf(
            AiRef(kind = AiRefKind.NOTE, noteId = 3, title = "二次型", snapshot = "笔记正文"),
            AiRef(
                kind = AiRefKind.BLOCK,
                noteId = 3,
                cardId = 7,
                blockId = 42,
                title = "片段",
                snapshot = "块内容"
            )
        )
        val decoded = AiMapper.toDomain(AiMapper.toEntity(message(refs = refs)))
        assertEquals(refs, decoded.refs)
    }

    @Test
    fun `refsJson 为空白或坏 JSON 时按没有引用处理`() {
        // 引用是增强信息：坏一行 JSON 不该把整条消息读崩。
        assertTrue(AiMapper.toDomain(entity(refsJson = "  ")).refs.isEmpty())
        assertTrue(AiMapper.toDomain(entity(refsJson = "{oops")).refs.isEmpty())
        assertTrue(AiMapper.toDomain(entity(refsJson = null)).refs.isEmpty())
    }

    @Test
    fun `未知字段不会让旧数据读崩`() {
        val raw = """[{"kind":"CARD","noteId":1,"cardId":2,"futureField":"x"}]"""
        val decoded = AiMapper.toDomain(entity(refsJson = raw))
        assertEquals(1, decoded.refs.size)
        assertEquals(AiRefKind.CARD, decoded.refs[0].kind)
        assertEquals(2L, decoded.refs[0].cardId)
    }

    @Test
    fun `quotedMessageId 往返且默认 null`() {
        assertNull(AiMapper.toEntity(message()).quotedMessageId)
        assertNull(AiMapper.toDomain(entity()).quotedMessageId)

        val entity = AiMapper.toEntity(message(quotedMessageId = 99L))
        assertEquals(99L, AiMapper.toDomain(entity).quotedMessageId)
    }

    private fun message(
        refs: List<AiRef> = emptyList(),
        quotedMessageId: Long? = null
    ) = AiMessage(
        id = 1,
        conversationId = 2,
        role = AiRole.USER,
        content = "问题",
        refs = refs,
        quotedMessageId = quotedMessageId,
        createdAt = 10
    )

    private fun entity(
        refsJson: String? = null,
        quotedMessageId: Long? = null
    ) = AiMessageEntity(
        id = 1,
        conversationId = 2,
        role = "USER",
        content = "问题",
        createdAt = 10,
        refsJson = refsJson,
        quotedMessageId = quotedMessageId
    )
}
