package com.example.zhilu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * 笔记全文检索索引（FTS4 虚拟表，`rowid` = 笔记 id）。
 *
 * ## 为什么是独立表而不是 external content
 *
 * 索引正文是**聚合**的多字段文本（标题 + 全部块内容 + 全部标签名）经
 * [com.example.zhilu.domain.search.NoteSearchTokens] bigram 化后的 token 流 ——
 * 它不对应任何单张表的列，external content 模式（content=/contentEntity=）无从引用。
 * 自存内容还避开 FTS3/4 触发器同步的一整套模板 SQL。
 *
 * ## 为什么是 FTS4 而不是 FTS5
 *
 * Room 2.6.1 没有 `@Fts5` 注解，走 FTS5 要么换 BundledSQLiteDriver（+2–3 MB APK）、
 * 要么手写 SQL 绕开 Room 校验。应用层 bigram 之后两种分词器行为一致，
 * FTS4 + `unicode61` 已够用（token 是空格分隔的 bigram/整词，unicode61 恰好
 * 把「连续的 CJK」视为单 token —— 每个 2 字 bigram 就是一个 token）。
 * 代价是 FTS4 没有 `bm25()`，排序按 `updatedAt DESC`（详见检索文档的降级预案）。
 *
 * ## 同步纪律
 *
 * **写入必须先 DELETE 再 INSERT**：FTS3/4 表上的 `INSERT OR REPLACE` 不会清掉
 * 旧行的 token，会留下"幽灵命中"。统一由 `NoteSearchIndexer` 执行。
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "note_fts")
data class NoteFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val body: String
)
