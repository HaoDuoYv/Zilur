package com.example.zhilu.data.repository

import com.example.zhilu.data.local.dao.NoteBlockDao
import com.example.zhilu.data.local.dao.NoteCardDao
import com.example.zhilu.data.local.dao.NoteDao
import com.example.zhilu.data.local.dao.TagDao
import com.example.zhilu.data.local.database.AppDatabase
import com.example.zhilu.data.local.entity.NoteEntity
import com.example.zhilu.data.local.entity.NoteTagEntity
import com.example.zhilu.data.local.mapper.BlockMapper
import com.example.zhilu.data.local.mapper.CardMapper
import com.example.zhilu.data.local.mapper.NoteMapper
import com.example.zhilu.data.local.mapper.TagMapper
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.KnowledgeCard
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
    private val noteCardDao: NoteCardDao,
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

    override suspend fun insertNote(note: Note): RepositoryResult<Note> = runCatching {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val noteId = noteDao.insert(NoteMapper.toEntity(note.copy(createdAt = note.createdAt, updatedAt = now)))
            val savedCards = replaceCards(noteId, note)
            val savedBlocks = replaceBlocks(noteId, note, savedCards)
            val tags = replaceTags(noteId, note)
            NoteMapper.toDomain(
                entity = noteDao.getById(noteId)!!,
                blocks = savedBlocks,
                cards = savedCards.values.sortedBy { it.id },
                tags = tags
            )
        }
    }.toRepositoryResult("Failed to save note")

    override suspend fun updateNote(note: Note): RepositoryResult<Note> = runCatching {
        database.withTransaction {
            noteDao.update(NoteMapper.toEntity(note.copy(updatedAt = System.currentTimeMillis())))
            val savedCards = replaceCards(note.id, note)
            val savedBlocks = replaceBlocks(note.id, note, savedCards)
            val tags = replaceTags(note.id, note)
            NoteMapper.toDomain(
                entity = noteDao.getById(note.id)!!,
                blocks = savedBlocks,
                cards = savedCards.values.sortedBy { it.id },
                tags = tags
            )
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
        val cards = noteCardDao.getByNoteIdOnce(entity.id)
        val blocks = noteBlockDao.getByNoteIdOnce(entity.id).map(BlockMapper::toDomain)
        val tags = tagDao.getByNoteId(entity.id).map(TagMapper::toDomain)
        val cardDomains = if (cards.isEmpty()) {
            emptyList()
        } else {
            val cardIds = cards.map { it.id }.toSet()
            val (matched, orphans) = blocks.partition { it.cardId in cardIds }
            cards.map { card ->
                val cardBlocks = matched.filter { it.cardId == card.id }.toMutableList()
                if (card == cards.first()) {
                    cardBlocks.addAll(orphans)
                }
                CardMapper.toDomain(
                    entity = card,
                    blocks = cardBlocks.sortedBy { it.sortOrder }
                )
            }
        }
        val fallbackBlocks = if (cards.isEmpty()) blocks else emptyList()
        return NoteMapper.toDomain(entity, fallbackBlocks, cardDomains, tags)
    }

    private suspend fun replaceCards(noteId: Long, note: Note): Map<Long, KnowledgeCard> {
        noteCardDao.deleteByNoteId(noteId)
        val result = mutableMapOf<Long, KnowledgeCard>()
        note.cards.forEachIndexed { index, card ->
            val entity = CardMapper.toEntity(card, noteId, index)
            val newId = noteCardDao.insert(entity)
            val savedCard = card.copy(id = newId)
            result[card.id] = savedCard
        }
        return result
    }

    private suspend fun replaceBlocks(
        noteId: Long,
        note: Note,
        cardMap: Map<Long, KnowledgeCard>
    ): List<Block> {
        noteBlockDao.deleteByNoteId(noteId)
        val sourceBlocks = note.blocks
        val entities = sourceBlocks.mapIndexed { index, block ->
            BlockMapper.toEntity(
                block.copy(
                    id = 0,
                    noteId = noteId,
                    cardId = cardMap[block.cardId]?.id,
                    sortOrder = index
                )
            )
        }
        if (entities.isEmpty()) return emptyList()

        val insertedIds = noteBlockDao.insertAll(entities)
        val idMapping = sourceBlocks.map { it.id }.zip(insertedIds).toMap()

        val updatedEntities = entities.mapIndexed { index, entity ->
            val newId = insertedIds[index]
            val newParentBranchId = entity.parentBranchId?.let { idMapping[it] }
            entity.copy(id = newId, parentBranchId = newParentBranchId)
        }

        updatedEntities.forEachIndexed { index, entity ->
            if (entity.parentBranchId != entities[index].parentBranchId) {
                noteBlockDao.update(entity)
            }
        }

        return updatedEntities.map(BlockMapper::toDomain)
    }

    private suspend fun replaceTags(noteId: Long, note: Note): List<com.example.zhilu.domain.model.Tag> {
        tagDao.deleteNoteTagsByNoteId(noteId)
        note.tags.filter { it.id > 0 }.forEach { tag ->
            tagDao.insertNoteTag(NoteTagEntity(noteId = noteId, tagId = tag.id))
        }
        return note.tags
    }
}
