package com.example.zhilu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_cards",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["noteId"])
    ]
)
data class NoteCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val title: String,
    val sortOrder: Int,
    /**
     * 卡片身份色（`TagColors` 批次色值）。`null` = 用户没改过 → 按序号回退轮转色。
     *
     * 可空且不给 `defaultValue`：`ALTER TABLE ... ADD COLUMN accent INTEGER`
     * 对既有行填 NULL，与 schema 里的"无默认值"一致。
     */
    val accent: Int? = null
)
