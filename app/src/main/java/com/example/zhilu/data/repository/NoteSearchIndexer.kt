package com.example.zhilu.data.repository

import com.example.zhilu.data.local.dao.NoteBlockDao
import com.example.zhilu.data.local.dao.NoteDao
import com.example.zhilu.data.local.dao.NoteFtsDao
import com.example.zhilu.data.local.dao.TagDao
import com.example.zhilu.data.local.entity.NoteFtsEntity
import com.example.zhilu.domain.search.NoteSearchTokens
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * 笔记检索索引（FTS4）的**唯一维护入口**。
 *
 * ## 何时调用（写路径清单）
 *
 * - `NoteRepositoryImpl.insertNote / updateNote` —— 事务内、数据落库之后；
 * - `NoteRepositoryImpl.deleteNote` —— 硬删后（`reindex` 读不到笔记会自清索引行）；
 * - `NoteRepositoryImpl.clearDeletedNotes` —— 清孤儿索引行；
 * - `TagRepositoryImpl.updateTag / deleteTag / mergeTags` —— 标签名是索引正文的
 *   一部分，改标签要连带重建关联笔记（**必须在改关联之前取 affected 列表**）。
 *
 * ## 一致性兜底
 *
 * [reconcile] 在应用启动时异步跑一次：`notes` 全表行数与索引行数不一致时全量重建。
 * 冷迁移（v7→v8 建空表）与"某次写路径中途失败"都靠它自愈；[reindexAll] 先清空再重建，
 * 保证中途失败时行数必然对不上 —— 若只用覆盖式重建，行数不变会让兜底失灵。
 *
 * ## 索引正文的组成
 *
 * 标题 + 全部块内容（含分支子块，`note_blocks` 表本身是平铺的）+ 全部标签名，
 * 分段 tokenize 后拼接（**不能整体拼接后再分词**，会在字段边界产生伪 bigram）。
 * 卡片标题（`note_cards.title`）**不在**索引内 —— 与改造前 `LIKE` 查询的
 * 覆盖范围（title / block.content / tag.name）保持一致。
 */
@Singleton
class NoteSearchIndexer @Inject constructor(
    private val noteDao: NoteDao,
    private val noteBlockDao: NoteBlockDao,
    private val tagDao: TagDao,
    private val noteFtsDao: NoteFtsDao
) {

    /** 重建单篇笔记的索引行；笔记不存在（已硬删）时清掉其索引行。 */
    suspend fun reindex(noteId: Long) {
        // 用 includingDeleted：软删的笔记仍留在索引里（查询侧 JOIN notes 过滤 deletedAt），
        // 不这么写会在"软删后再因别的原因触发重建"时把索引行误删，
        // 恢复笔记后搜索就少一篇。
        val note = noteDao.getByIdIncludingDeleted(noteId)
        if (note == null) {
            noteFtsDao.delete(noteId)
            return
        }
        val body = NoteSearchTokens.buildIndexBody(
            buildList {
                add(note.title)
                noteBlockDao.getByNoteIdOnce(noteId).forEach { add(it.content) }
                tagDao.getByNoteId(noteId).forEach { add(it.name) }
            }
        )
        // FTS3/4 的 REPLACE 语义不可靠（旧 token 残留成幽灵命中），必须显式先删后插。
        noteFtsDao.delete(noteId)
        // **无条件写入**（正文为空也写空行）—— 保持「一篇笔记 = 一行索引」的严格不变量，
        // 否则空笔记会让 [reconcile] 的行数比对永远对不上，每次启动都白跑一次全量重建。
        noteFtsDao.insert(NoteFtsEntity(rowId = noteId, body = body))
    }

    /** 批量重建（标签改动后的关联笔记重建、全量重建的循环体）。 */
    suspend fun reindex(noteIds: Collection<Long>) {
        noteIds.forEach { reindex(it) }
    }

    /** 清掉已不存在的笔记留下的索引行（`clearDeletedNotes` 之后调用）。 */
    suspend fun pruneOrphans() = noteFtsDao.pruneOrphans()

    /** 启动兜底：索引行数与笔记全表行数不一致就全量重建。绝不抛出（启动路径不许被它拖垮）。 */
    suspend fun reconcile() {
        runCatching {
            val total = noteDao.countAll()
            val indexed = noteFtsDao.count()
            if (total != indexed) {
                Timber.i("检索索引不一致（notes=%d, fts=%d），全量重建", total, indexed)
                reindexAll()
            }
        }.onFailure { Timber.w(it, "检索索引兜底重建失败") }
    }

    /** 全量重建。先清空再逐条重建 —— 清空让中途失败在下次 [reconcile] 时可见。 */
    suspend fun reindexAll() {
        noteFtsDao.clear()
        noteDao.getAllIds().forEach { reindex(it) }
    }
}
