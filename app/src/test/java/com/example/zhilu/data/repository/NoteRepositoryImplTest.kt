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
import com.example.zhilu.domain.model.BlockType
import io.mockk.coEvery
import io.mockk.mockk
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
    fun `hydrate merges orphan blocks into first card when cardId mismatch`() = runTest {
        val noteId = 1L
        val noteEntity = NoteEntity(
            id = noteId,
            title = "旧笔记",
            createdAt = 0L,
            updatedAt = 0L
        )
        val cardEntity = NoteCardEntity(id = 10L, noteId = noteId, title = "卡片", sortOrder = 0)
        val orphanBlock = NoteBlockEntity(
            id = 100L, noteId = noteId, cardId = null,
            type = BlockType.TEXT.value, content = "orphan", sortOrder = 0
        )
        val matchedBlock = NoteBlockEntity(
            id = 101L, noteId = noteId, cardId = 10L,
            type = BlockType.TEXT.value, content = "matched", sortOrder = 1
        )

        coEvery { noteDao.getById(noteId) } returns noteEntity
        coEvery { noteCardDao.getByNoteIdOnce(noteId) } returns listOf(cardEntity)
        coEvery { noteBlockDao.getByNoteIdOnce(noteId) } returns listOf(orphanBlock, matchedBlock)
        coEvery { tagDao.getByNoteId(noteId) } returns emptyList()

        val result = repository.getNoteById(noteId)

        assertTrue(result is RepositoryResult.Success)
        val note = (result as RepositoryResult.Success).data!!
        assertEquals(1, note.cards.size)
        assertEquals(2, note.cards.first().blocks.size)
        assertEquals("orphan", note.cards.first().blocks.first().content)
    }
}
