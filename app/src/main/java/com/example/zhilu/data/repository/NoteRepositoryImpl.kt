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
import com.example.zhilu.domain.search.NoteSearchTokens
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class NoteRepositoryImpl(
    private val database: AppDatabase,
    private val noteDao: NoteDao,
    private val noteBlockDao: NoteBlockDao,
    private val noteCardDao: NoteCardDao,
    private val tagDao: TagDao,
    private val searchIndexer: NoteSearchIndexer
) : NoteRepository {
    override fun getAllNotes(): Flow<RepositoryResult<List<Note>>> = noteDao.getAll().asNoteResultFlow("Failed to load notes")

    override fun getFavoriteNotes(): Flow<RepositoryResult<List<Note>>> =
        noteDao.getFavorites().asNoteResultFlow("Failed to load favorite notes")

    override fun getDeletedNotes(): Flow<RepositoryResult<List<Note>>> =
        noteDao.getDeleted().asNoteResultFlow("Failed to load deleted notes")

    override suspend fun getNoteById(id: Long): RepositoryResult<Note?> = runCatching {
        noteDao.getById(id)?.let { hydrate(it) }
    }.toRepositoryResult("Failed to load note")

    override suspend fun searchNotes(
        keyword: String,
        tagIds: List<Long>,
        limit: Int
    ): RepositoryResult<List<Note>> = runCatching {
        val normalized = keyword.trim()
        val ftsQuery = NoteSearchTokens.buildMatchQuery(normalized)
        when {
            // 无关键词也无标签：保持旧行为返回空，不意外全量返回。
            normalized.isEmpty() && tagIds.isEmpty() -> emptyList()
            // 纯关键词：FTS 主路径，异常（如索引未就绪/查询语法）降级回 LIKE 扫描。
            tagIds.isEmpty() -> {
                if (ftsQuery == null) {
                    // 关键词里没有可检索的 token（纯标点等）：LIKE 语义兜底，
                    // 保证与改造前行为一致（例如正文里真写了「！！」）。
                    noteDao.search(normalized)
                } else {
                    runCatching { noteDao.searchFts(ftsQuery, limit) }
                        .getOrElse { noteDao.search(normalized) }
                }.map { hydrate(it) }
            }
            // 标签筛选（关键词可为空串 = 纯标签浏览；SQL 内 `:keyword = ''` 分支兜底）。
            ftsQuery == null -> noteDao.searchWithTags(normalized, tagIds, tagIds.size).map { hydrate(it) }
            else -> runCatching { noteDao.searchWithTagsFts(ftsQuery, tagIds, tagIds.size, limit) }
                .getOrElse { noteDao.searchWithTags(normalized, tagIds, tagIds.size) }
                .map { hydrate(it) }
        }
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
            // 检索索引与数据同一事务：写入失败整体回滚，不会留下"内容变了索引没变"。
            searchIndexer.reindex(noteId)
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
            searchIndexer.reindex(note.id)
            NoteMapper.toDomain(
                entity = noteDao.getById(note.id)!!,
                blocks = savedBlocks,
                cards = savedCards.values.sortedBy { it.id },
                tags = tags
            )
        }
    }.toRepositoryResult("Failed to update note")

    override suspend fun deleteNote(note: Note): RepositoryResult<Unit> = runCatching {
        database.withTransaction {
            noteDao.delete(NoteMapper.toEntity(note))
            // 硬删后笔记已不存在，reindex 会清掉它的索引行。
            searchIndexer.reindex(note.id)
        }
    }.toRepositoryResult("Failed to delete note")

    override suspend fun softDeleteNote(id: Long): RepositoryResult<Unit> = runCatching {
        // 索引不动：软删只是改 deletedAt，查询侧 JOIN notes 过滤即可。
        noteDao.softDelete(id, System.currentTimeMillis())
    }.toRepositoryResult("Failed to move note to trash")

    override suspend fun restoreNote(id: Long): RepositoryResult<Unit> = runCatching {
        noteDao.restore(id, System.currentTimeMillis())
    }.toRepositoryResult("Failed to restore note")

    override suspend fun clearDeletedNotes(): RepositoryResult<Unit> = runCatching {
        database.withTransaction {
            noteDao.clearDeleted()
            // 清掉已硬删笔记留下的索引行。
            searchIndexer.pruneOrphans()
        }
    }.toRepositoryResult("Failed to clear trash")

    override suspend fun getNoteCount(): RepositoryResult<Int> = runCatching {
        noteDao.count()
    }.toRepositoryResult("Failed to count notes")

    /**
     * 列表类 Flow 的统一组装。
     *
     * `combine(observeTagChanges)` 补一层「标签体系变化」的感知：`hydrate` 里的标签
     * 是一次性查询（`tagDao.getByNoteId`），`notes` 表自身的变更推不出
     * `note_tags` / `tags` 的写入 —— 不 combine 的话，合并 / 删除 / 重命名标签后
     * 卡片上的标签胶囊会陈旧到冷启动（真机复现过）。
     */
    private fun Flow<List<NoteEntity>>.asNoteResultFlow(message: String): Flow<RepositoryResult<List<Note>>> =
        combine(tagDao.observeTagChanges()) { entities, _ -> entities }
            .map<List<NoteEntity>, RepositoryResult<List<Note>>> { entities ->
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
            val matchedByCardId = matched.groupBy { it.cardId }
            cards.mapIndexed { index, card ->
                val cardBlocks = matchedByCardId[card.id].orEmpty().toMutableList()
                if (index == 0 && orphans.isNotEmpty()) {
                    val nextSortOrder = (cardBlocks.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    val sortedOrphans = orphans.sortedBy { it.sortOrder }
                    val reassignedOrphans = sortedOrphans.mapIndexed { orphanIndex, block ->
                        block.copy(sortOrder = nextSortOrder + orphanIndex)
                    }
                    cardBlocks.addAll(reassignedOrphans)
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
            // 卡片 id 在内存里只是临时值（新建的卡片为负数），必须归零交给数据库分配主键。
            // 否则临时 id 会被当成真实主键落库，与后续会话重新分配的临时 id 相撞，
            // 表现为 LazyColumn 的 key 重复崩溃——与 replaceBlocks 的处理保持一致。
            val entity = CardMapper.toNewEntity(card, noteId, index)
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
        // 取块必须走 Note.contentBlocks：hydrate 读出来的笔记只会填 blocks / cards 中的一处，
        // 有卡片的笔记 blocks 是空的。早先这里直接读 note.blocks，于是「只改标题」「只改标签」
        // 「切换收藏」这类 copy() 往返都会把正文整段删掉（读回来是空 → 写回去也是空）。
        val sourceBlocks = note.contentBlocks
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
