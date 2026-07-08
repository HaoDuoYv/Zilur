package com.example.zhilu.data.repository

import com.example.zhilu.data.local.dao.NoteBlockDao
import com.example.zhilu.data.local.dao.NoteDao
import com.example.zhilu.data.local.dao.TagDao
import com.example.zhilu.data.local.database.AppDatabase
import com.example.zhilu.data.local.entity.NoteEntity
import com.example.zhilu.data.local.entity.NoteTagEntity
import com.example.zhilu.data.local.mapper.BlockMapper
import com.example.zhilu.data.local.mapper.NoteMapper
import com.example.zhilu.data.local.mapper.TagMapper
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.repository.NoteRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class NoteRepositoryImpl(
    private val database: AppDatabase,
    private val noteDao: NoteDao,
    private val noteBlockDao: NoteBlockDao,
    private val tagDao: TagDao
) : NoteRepository {
    override fun getAllNotes(): Flow<RepositoryResult<List<Note>>> = noteDao.getAll().asNoteResultFlow("Failed to load notes")

    override fun getFavoriteNotes(): Flow<RepositoryResult<List<Note>>> =
        noteDao.getFavorites().asNoteResultFlow("Failed to load favorite notes")

    override fun getDeletedNotes(): Flow<RepositoryResult<List<Note>>> =
        noteDao.getDeleted().asNoteResultFlow("Failed to load deleted notes")

    override suspend fun getNoteById(id: Long): RepositoryResult<Note?> = runCatching {
        noteDao.getById(id)?.let { hydrate(it) }
    }.toRepositoryResult("Failed to load note")

    override suspend fun searchNotes(keyword: String): RepositoryResult<List<Note>> = runCatching {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) emptyList() else noteDao.search(normalized).map { hydrate(it) }
    }.toRepositoryResult("Failed to search notes")

    override suspend fun getNotesByTagId(tagId: Long): RepositoryResult<List<Note>> = runCatching {
        noteDao.getByTagId(tagId).map { hydrate(it) }
    }.toRepositoryResult("Failed to load notes for tag")

    override suspend fun insertNote(note: Note): RepositoryResult<Long> = runCatching {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val noteId = noteDao.insert(NoteMapper.toEntity(note.copy(createdAt = note.createdAt, updatedAt = now)))
            replaceBlocks(noteId, note)
            replaceTags(noteId, note)
            noteId
        }
    }.toRepositoryResult("Failed to save note")

    override suspend fun updateNote(note: Note): RepositoryResult<Unit> = runCatching {
        database.withTransaction {
            noteDao.update(NoteMapper.toEntity(note.copy(updatedAt = System.currentTimeMillis())))
            replaceBlocks(note.id, note)
            replaceTags(note.id, note)
        }
    }.toRepositoryResult("Failed to update note")

    override suspend fun deleteNote(note: Note): RepositoryResult<Unit> = runCatching {
        noteDao.delete(NoteMapper.toEntity(note))
    }.toRepositoryResult("Failed to delete note")

    override suspend fun softDeleteNote(id: Long): RepositoryResult<Unit> = runCatching {
        noteDao.softDelete(id, System.currentTimeMillis())
    }.toRepositoryResult("Failed to move note to trash")

    override suspend fun restoreNote(id: Long): RepositoryResult<Unit> = runCatching {
        noteDao.restore(id, System.currentTimeMillis())
    }.toRepositoryResult("Failed to restore note")

    override suspend fun clearDeletedNotes(): RepositoryResult<Unit> = runCatching {
        noteDao.clearDeleted()
    }.toRepositoryResult("Failed to clear trash")

    override suspend fun getNoteCount(): RepositoryResult<Int> = runCatching {
        noteDao.count()
    }.toRepositoryResult("Failed to count notes")

    private fun Flow<List<NoteEntity>>.asNoteResultFlow(message: String): Flow<RepositoryResult<List<Note>>> =
        map<List<NoteEntity>, RepositoryResult<List<Note>>> { entities ->
            RepositoryResult.Success(entities.map { hydrate(it) })
        }.catch { e ->
            emit(RepositoryResult.Error(message, e))
        }

    private suspend fun hydrate(entity: NoteEntity): Note {
        val blocks = noteBlockDao.getByNoteIdOnce(entity.id).map(BlockMapper::toDomain)
        val tags = tagDao.getByNoteId(entity.id).map(TagMapper::toDomain)
        return NoteMapper.toDomain(entity, blocks, tags)
    }

    private suspend fun replaceBlocks(noteId: Long, note: Note) {
        noteBlockDao.deleteByNoteId(noteId)
        val blocks = note.blocks.mapIndexed { index, block ->
            BlockMapper.toEntity(block.copy(id = 0, noteId = noteId, sortOrder = index))
        }
        if (blocks.isNotEmpty()) noteBlockDao.insertAll(blocks)
    }

    private suspend fun replaceTags(noteId: Long, note: Note) {
        tagDao.deleteNoteTagsByNoteId(noteId)
        note.tags.filter { it.id > 0 }.forEach { tag ->
            tagDao.insertNoteTag(NoteTagEntity(noteId = noteId, tagId = tag.id))
        }
    }
}
