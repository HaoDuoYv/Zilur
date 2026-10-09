package com.example.zhilu.data.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.dao.NoteBlockDao
import com.example.zhilu.data.local.dao.NoteCardDao
import com.example.zhilu.data.local.dao.NoteDao
import com.example.zhilu.data.local.dao.TagDao
import com.example.zhilu.data.local.database.AppDatabase
import com.example.zhilu.data.local.entity.NoteBlockEntity
import com.example.zhilu.data.local.entity.NoteCardEntity
import com.example.zhilu.data.local.entity.NoteEntity
import com.example.zhilu.data.local.entity.TagEntity
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRepositoryImplTest {
    private val database: AppDatabase = mockk(relaxed = true)
    private val noteDao: NoteDao = mockk()
    private val noteBlockDao: NoteBlockDao = mockk()
    private val noteCardDao: NoteCardDao = mockk()
    private val tagDao: TagDao = mockk()

    private val repository = NoteRepositoryImpl(
        database = database,
        noteDao = noteDao,
        noteBlockDao = noteBlockDao,
        noteCardDao = noteCardDao,
        tagDao = tagDao
    )

    @Test
    fun `hydrate appends orphan blocks after matched blocks with reassigned sortOrder`() = runTest {
        val noteId = 1L
        val noteEntity = noteEntity(noteId)
        val cardEntity = NoteCardEntity(id = 10L, noteId = noteId, title = "卡片", sortOrder = 0)
        val matchedBlock = textBlock(id = 101L, cardId = 10L, content = "matched", sortOrder = 1)
        val orphanBlock = textBlock(id = 100L, cardId = null, content = "orphan", sortOrder = 0)

        mockHydrate(
            noteId = noteId,
            noteEntity = noteEntity,
            cards = listOf(cardEntity),
            blocks = listOf(matchedBlock, orphanBlock),
            tags = emptyList()
        )

        val result = repository.getNoteById(noteId)
        val note = assertSuccess(result)

        assertEquals(1, note.cards.size)
        assertEquals(2, note.cards.first().blocks.size)
        assertEquals(listOf("matched", "orphan"), note.cards.first().blocks.map { it.content })
        assertEquals(listOf(1, 2), note.cards.first().blocks.map { it.sortOrder })
    }

    @Test
    fun `hydrate treats stale cardId reference as orphan and appends to first card`() = runTest {
        val noteId = 5L
        val noteEntity = noteEntity(noteId)
        val cardEntity = NoteCardEntity(id = 10L, noteId = noteId, title = "卡片", sortOrder = 0)
        val matchedBlock = textBlock(id = 101L, cardId = 10L, content = "matched", sortOrder = 1)
        val staleReferenceBlock = textBlock(id = 100L, cardId = 999L, content = "stale reference", sortOrder = 0)

        mockHydrate(
            noteId = noteId,
            noteEntity = noteEntity,
            cards = listOf(cardEntity),
            blocks = listOf(matchedBlock, staleReferenceBlock),
            tags = emptyList()
        )

        val result = repository.getNoteById(noteId)
        val note = assertSuccess(result)

        assertEquals(1, note.cards.size)
        assertEquals(2, note.cards.first().blocks.size)
        assertEquals(listOf("matched", "stale reference"), note.cards.first().blocks.map { it.content })
        assertEquals(listOf(1, 2), note.cards.first().blocks.map { it.sortOrder })
    }

    @Test
    fun `hydrate merges all orphan blocks into first card only for multi-card note`() = runTest {
        val noteId = 2L
        val firstCard = NoteCardEntity(id = 10L, noteId = noteId, title = "卡片 1", sortOrder = 0)
        val secondCard = NoteCardEntity(id = 11L, noteId = noteId, title = "卡片 2", sortOrder = 1)
        val orphanA = textBlock(id = 100L, cardId = null, content = "orphan A", sortOrder = 0)
        val orphanB = textBlock(id = 101L, cardId = null, content = "orphan B", sortOrder = 1)
        val matchedToSecond = textBlock(id = 102L, cardId = 11L, content = "matched to second", sortOrder = 2)

        mockHydrate(
            noteId = noteId,
            noteEntity = noteEntity(noteId),
            cards = listOf(firstCard, secondCard),
            blocks = listOf(orphanA, orphanB, matchedToSecond),
            tags = emptyList()
        )

        val result = repository.getNoteById(noteId)
        val note = assertSuccess(result)

        assertEquals(2, note.cards.size)

        val firstCardBlocks = note.cards[0].blocks
        assertEquals(2, firstCardBlocks.size)
        assertEquals(listOf("orphan A", "orphan B"), firstCardBlocks.map { it.content })

        val secondCardBlocks = note.cards[1].blocks
        assertEquals(1, secondCardBlocks.size)
        assertEquals("matched to second", secondCardBlocks.single().content)
    }

    @Test
    fun `hydrate keeps matched blocks unchanged when no orphan blocks exist`() = runTest {
        val noteId = 3L
        val cardEntity = NoteCardEntity(id = 10L, noteId = noteId, title = "卡片", sortOrder = 0)
        val firstMatched = textBlock(id = 100L, cardId = 10L, content = "first", sortOrder = 1)
        val secondMatched = textBlock(id = 101L, cardId = 10L, content = "second", sortOrder = 0)

        mockHydrate(
            noteId = noteId,
            noteEntity = noteEntity(noteId),
            cards = listOf(cardEntity),
            blocks = listOf(firstMatched, secondMatched),
            tags = emptyList()
        )

        val result = repository.getNoteById(noteId)
        val note = assertSuccess(result)

        assertEquals(1, note.cards.size)
        assertEquals(2, note.cards.first().blocks.size)
        assertEquals(listOf("second", "first"), note.cards.first().blocks.map { it.content })
    }

    @Test
    fun `hydrate merges all blocks into first card when all blocks are orphan`() = runTest {
        val noteId = 4L
        val firstCard = NoteCardEntity(id = 10L, noteId = noteId, title = "卡片 1", sortOrder = 0)
        val secondCard = NoteCardEntity(id = 11L, noteId = noteId, title = "卡片 2", sortOrder = 1)
        val orphanA = textBlock(id = 100L, cardId = null, content = "A", sortOrder = 1)
        val orphanB = textBlock(id = 101L, cardId = null, content = "B", sortOrder = 0)

        mockHydrate(
            noteId = noteId,
            noteEntity = noteEntity(noteId),
            cards = listOf(firstCard, secondCard),
            blocks = listOf(orphanA, orphanB),
            tags = emptyList()
        )

        val result = repository.getNoteById(noteId)
        val note = assertSuccess(result)

        assertEquals(2, note.cards.size)
        assertEquals(0, note.cards[1].blocks.size)
        assertEquals(2, note.cards[0].blocks.size)
        assertEquals(listOf("B", "A"), note.cards[0].blocks.map { it.content })
    }

    @Test
    fun `getAllNotes re-emits when the tag hierarchy changes`() = runTest {
        val noteId = 1L
        val tagChanges = MutableStateFlow(0)
        val cardEntity = NoteCardEntity(id = 10L, noteId = noteId, title = "卡片", sortOrder = 0)
        val block = textBlock(id = 100L, cardId = 10L, content = "内容", sortOrder = 0)

        every { noteDao.getAll() } returns flowOf(listOf(noteEntity(noteId)))
        every { tagDao.observeTagChanges() } returns tagChanges
        coEvery { noteCardDao.getByNoteIdOnce(noteId) } returns listOf(cardEntity)
        coEvery { noteBlockDao.getByNoteIdOnce(noteId) } returns listOf(block)
        // 第一次 hydrate 时还没有标签；心跳变化后重查拿到合并/重命名后的新标签。
        coEvery { tagDao.getByNoteId(noteId) } returnsMany listOf(
            emptyList(),
            listOf(TagEntity(id = 1L, name = "线性代数", color = 1))
        )

        val emissions = mutableListOf<RepositoryResult<List<Note>>>()
        val job = launch { repository.getAllNotes().collect { emissions += it } }
        runCurrent()
        assertEquals(1, emissions.size)

        tagChanges.value = 1
        runCurrent()
        job.cancel()

        assertEquals(2, emissions.size)
        val notes = assertSuccess(emissions.last()).orEmpty()
        assertEquals(listOf("线性代数"), notes.single().tags.map { it.name })
    }

    private fun noteEntity(id: Long): NoteEntity = NoteEntity(
        id = id,
        title = "旧笔记",
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun textBlock(
        id: Long,
        cardId: Long?,
        content: String,
        sortOrder: Int
    ): NoteBlockEntity = NoteBlockEntity(
        id = id,
        noteId = 0L,
        cardId = cardId,
        type = BlockType.TEXT.value,
        content = content,
        sortOrder = sortOrder
    )

    private fun mockHydrate(
        noteId: Long,
        noteEntity: NoteEntity,
        cards: List<NoteCardEntity>,
        blocks: List<NoteBlockEntity>,
        tags: List<TagEntity>
    ) {
        coEvery { noteDao.getById(noteId) } returns noteEntity
        coEvery { noteCardDao.getByNoteIdOnce(noteId) } returns cards
        coEvery { noteBlockDao.getByNoteIdOnce(noteId) } returns blocks
        coEvery { tagDao.getByNoteId(noteId) } returns tags
    }

    private fun <T : Any> assertSuccess(result: RepositoryResult<T?>): T {
        assertTrue(result is RepositoryResult.Success)
        return (result as RepositoryResult.Success).data!!
    }
}
