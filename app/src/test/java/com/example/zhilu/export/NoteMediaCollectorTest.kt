package com.example.zhilu.export

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「挑出被笔记真正引用的媒体」是三条导出路径（单篇分享 / 全量备份 / 选中项备份）共用的
 * 前置步骤。收多了 → 单篇分享背上整个图库；收少了 → 分享出去的包里图片缺失。
 */
class NoteMediaCollectorTest {

    private val uriA = "file:///data/user/0/com.example.zhilu/files/images/img_a.png"
    private val uriB = "file:///data/user/0/com.example.zhilu/files/images/img_b.png"
    private val mediaA = Media(id = 7L, uri = uriA, size = 1)
    private val mediaB = Media(id = 8L, uri = uriB, size = 1)

    @Test
    fun `按 mediaId 收窄被引用的媒体`() {
        val note = Note(
            title = "带图",
            blocks = listOf(
                Block(type = BlockType.IMAGE, content = ImageBlockContent.fromMedia(7L, uriA))
            )
        )

        val collected = collectReferencedMedia(listOf(note), listOf(mediaA, mediaB))

        assertEquals(listOf(7L), collected.map { it.id })
    }

    @Test
    fun `裸 URI 的图片块也能按 uri 认领`() {
        // AI 早期写入的形态：content 里没有 mediaId 前缀
        val note = Note(
            title = "裸图",
            blocks = listOf(Block(type = BlockType.IMAGE, content = uriB))
        )

        val collected = collectReferencedMedia(listOf(note), listOf(mediaA, mediaB))

        assertEquals(listOf(8L), collected.map { it.id })
    }

    @Test
    fun `卡片里的图片块同样被算进去`() {
        val note = Note(
            title = "卡片图",
            cards = listOf(
                KnowledgeCard(
                    title = "一节",
                    blocks = listOf(
                        Block(type = BlockType.IMAGE, content = ImageBlockContent.fromMedia(8L, uriB))
                    )
                )
            )
        )

        assertEquals(listOf(8L), collectReferencedMedia(listOf(note), listOf(mediaA, mediaB)).map { it.id })
    }

    @Test
    fun `多篇引用同一张图时只出现一条`() {
        val note1 = Note(
            title = "一",
            blocks = listOf(Block(type = BlockType.IMAGE, content = ImageBlockContent.fromMedia(7L, uriA)))
        )
        val note2 = Note(
            title = "二",
            blocks = listOf(Block(type = BlockType.IMAGE, content = ImageBlockContent.fromMedia(7L, uriA)))
        )

        val collected = collectReferencedMedia(listOf(note1, note2), listOf(mediaA, mediaB))

        assertEquals("同一张图只该收一条", listOf(7L), collected.map { it.id })
    }

    @Test
    fun `没有图片块时返回空`() {
        val note = Note(title = "纯文字", blocks = listOf(Block(type = BlockType.TEXT, content = "x")))
        assertTrue(collectReferencedMedia(listOf(note), listOf(mediaA)).isEmpty())
    }

    @Test
    fun `uri 为空的媒体不会被任何笔记误收`() {
        // `content.contains("")` 恒为 true —— 不显式挡掉的话整个图库都会被收进来
        val broken = Media(id = 9L, uri = "", size = 0)
        val note = Note(
            title = "带图",
            blocks = listOf(Block(type = BlockType.IMAGE, content = ImageBlockContent.fromMedia(7L, uriA)))
        )

        val collected = collectReferencedMedia(listOf(note), listOf(mediaA, broken))

        assertEquals(listOf(7L), collected.map { it.id })
    }
}
