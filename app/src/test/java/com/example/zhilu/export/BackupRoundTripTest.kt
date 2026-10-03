package com.example.zhilu.export

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份往返：**小节结构、分支层级、卡片身份色、图片字节**四样都不能丢。
 *
 * 真机反馈了两条："导入 .dtk 后小节全没了"、"JSON 备份导入时图片进不来"，
 * 根因都是这里 —— 导出时只写了扁平的块、图片只留了一个指向本机存储的 uri。
 */
class BackupRoundTripTest {

    private fun noteWithCards() = Note(
        id = 7,
        title = "计算机网络",
        cards = listOf(
            KnowledgeCard(
                id = 1,
                title = "GBN 和 SR",
                accent = 0xFFB23B32.toInt(),
                blocks = listOf(
                    Block(id = 11, type = BlockType.TEXT, content = "①", sortOrder = 0),
                    Block(id = 12, type = BlockType.BRANCH, content = "推导", sortOrder = 1),
                    Block(id = 13, type = BlockType.TEXT, content = "子块", sortOrder = 2, parentBranchId = 12)
                )
            ),
            KnowledgeCard(id = 2, title = "滑动窗口", blocks = listOf(Block(id = 21, type = BlockType.TEXT, content = "②", sortOrder = 0)))
        )
    )

    @Test
    fun `导出再导入保住小节结构`() {
        val json = JsonExporter.exportNotes(listOf(noteWithCards()))
        val restored = JsonExporter.importNotes(json).single()
        assertEquals(2, restored.cards.size)
        assertEquals("GBN 和 SR", restored.cards[0].title)
        assertEquals("滑动窗口", restored.cards[1].title)
        // blocks 必须留空，否则 contentBlocks 会只看 blocks、把卡片结构忽略掉
        assertTrue(restored.blocks.isEmpty())
        // 卡片 1 有 3 块（含 1 个分支子块）+ 卡片 2 有 1 块
        assertEquals(4, restored.contentBlocks.size)
    }

    @Test
    fun `卡片身份色往返不丢`() {
        val json = JsonExporter.exportNotes(listOf(noteWithCards()))
        val restored = JsonExporter.importNotes(json).single()
        assertEquals(0xFFB23B32.toInt(), restored.cards[0].accent)
        // 没设过的仍然是 null（不是被写成 0 这种哨兵值）
        assertNull(restored.cards[1].accent)
    }

    @Test
    fun `分支父子关系往返不丢`() {
        val json = JsonExporter.exportNotes(listOf(noteWithCards()))
        val restored = JsonExporter.importNotes(json).single()
        val parent = restored.contentBlocks.first { it.type == BlockType.BRANCH }
        val child = restored.contentBlocks.first { it.content == "子块" }
        assertEquals(parent.id, child.parentBranchId)
    }

    @Test
    fun `扁平笔记仍然走 blocks 分支`() {
        val flat = Note(id = 3, title = "扁平", blocks = listOf(Block(id = 1, type = BlockType.TEXT, content = "x", sortOrder = 0)))
        val restored = JsonExporter.importNotes(JsonExporter.exportNotes(listOf(flat))).single()
        assertTrue(restored.cards.isEmpty())
        assertEquals(1, restored.blocks.size)
    }

    @Test
    fun `媒体字节随备份内嵌并能在导入时取回`() {
        val media = listOf(Media(id = 5, uri = "file:///data/old/img.png", size = 3))
        val json = JsonExporter.exportNotes(emptyList(), media, mapOf(5L to "data:image/png;base64,QUJD"))
        val backup = JsonExporter.importBackup(json)
        assertEquals(1, backup.media.size)
        assertEquals("data:image/png;base64,QUJD", backup.mediaData[5L])
    }

    @Test
    fun `不传字节时 backup 里没有 data 字段（dtk 走 zip 文件）`() {
        val media = listOf(Media(id = 5, uri = "media/a.png", size = 3))
        val json = JsonExporter.exportNotes(emptyList(), media)
        assertTrue(JsonExporter.importBackup(json).mediaData.isEmpty())
    }

    @Test
    fun `dtk 的 note_json 带 dtkVersion，整体备份不带`() {
        // 导入端会读 dtkVersion 并对高版本报错；缺字段就一直被当成 1
        val dtk = JsonExporter.exportNote(Note(id = 1, title = "x"), dtkVersion = 1)
        assertTrue(dtk.contains("\"dtkVersion\":1"))

        val backup = JsonExporter.exportNotes(listOf(Note(id = 1, title = "x")))
        assertTrue(!backup.contains("dtkVersion"))
    }

    @Test
    fun `重映射把 id 换成负临时值并保住父子与卡片归属`() {
        val remapped = remapForInsert(noteWithCards())

        // 所有块拿到互不相同的负临时 id
        val ids = remapped.contentBlocks.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it < 0 })

        // 卡片也换成负临时 id，且块指向所属卡片
        assertEquals(listOf(-1L, -2L), remapped.cards.map { it.id })
        assertTrue(remapped.cards[0].blocks.all { it.cardId == -1L })
        assertTrue(remapped.cards[1].blocks.all { it.cardId == -2L })

        // 子块指向父分支的**临时** id，而不是导出时那台设备的旧 id
        val parent = remapped.cards[0].blocks.first { it.type == BlockType.BRANCH }
        val child = remapped.cards[0].blocks.first { it.content == "子块" }
        assertEquals(parent.id, child.parentBranchId)
    }

    @Test
    fun `重映射按映射表改写图片的媒体 id`() {
        val note = Note(
            id = 1,
            title = "图",
            blocks = listOf(
                Block(
                    id = 9,
                    type = BlockType.IMAGE,
                    content = ImageBlockContent.fromMedia(5L, "media/a.png"),
                    sortOrder = 0
                )
            )
        )
        val remapped = remapForInsert(note, mediaIdMap = mapOf(5L to 88L))
        assertEquals(88L, ImageBlockContent.mediaId(remapped.blocks.single().content))
        assertEquals("media/a.png", ImageBlockContent.displayUri(remapped.blocks.single().content))
    }
}
