package com.example.zhilu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val imagesJson: String? = null,
    val fileText: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val createdAt: Long,
    /**
     * 用户消息附带的引用列表（JSON 数组，含引用时刻的内容快照）。
     *
     * 与 [imagesJson] 同一约定：可空列、无 DEFAULT —— 老行为 NULL（没有引用），
     * 语义正好一致，也避开 `defaultValue` 与迁移 DDL 逐字对齐的坑。
     */
    val refsJson: String? = null,
    /** 本条消息引用的同会话消息 id；NULL = 不是引用消息。 */
    val quotedMessageId: Long? = null
)
