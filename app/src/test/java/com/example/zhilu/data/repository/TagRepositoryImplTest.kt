package com.example.zhilu.data.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.dao.TagDao
import com.example.zhilu.data.local.dao.TagNoteCount
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只覆盖标签笔记数的聚合映射：真正的 `GROUP BY` 语义由 Room 生成的 SQL 保证，
 * 这里验证的是「投影行 → 以 tagId 为键的 Map」这一步以及失败兜底。
 */
class TagRepositoryImplTest {
    private val tagDao: TagDao = mockk()
    private val repository = TagRepositoryImpl(tagDao = tagDao)

    @Test
    fun `getNoteCountsByTag maps aggregate rows into a tagId keyed map`() = runTest {
        every { tagDao.countNotesPerTag() } returns flowOf(
            listOf(
                TagNoteCount(tagId = 1L, noteCount = 3),
                TagNoteCount(tagId = 7L, noteCount = 1)
            )
        )

        val emissions = repository.getNoteCountsByTag().toList()

        assertEquals(1, emissions.size)
        assertEquals(mapOf(1L to 3, 7L to 1), assertSuccess(emissions.first()))
    }

    @Test
    fun `getNoteCountsByTag yields empty map when no note carries a tag`() = runTest {
        every { tagDao.countNotesPerTag() } returns flowOf(emptyList())

        val counts = assertSuccess(repository.getNoteCountsByTag().toList().first())

        assertTrue(counts.isEmpty())
    }

    @Test
    fun `getNoteCountsByTag turns dao failure into Error without crashing the stream`() = runTest {
        every { tagDao.countNotesPerTag() } returns flow { throw IllegalStateException("boom") }

        val emissions = repository.getNoteCountsByTag().toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions.first() is RepositoryResult.Error)
    }

    private fun <T : Any> assertSuccess(result: RepositoryResult<T?>): T {
        assertTrue(result is RepositoryResult.Success)
        return (result as RepositoryResult.Success).data!!
    }
}
