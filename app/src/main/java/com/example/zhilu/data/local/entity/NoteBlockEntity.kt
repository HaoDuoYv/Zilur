package com.example.zhilu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_blocks",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["noteId"]),
        Index(value = ["type"]),
        Index(value = ["cardId"]),
        Index(value = ["parentBranchId"])
    ]
)
data class NoteBlockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val cardId: Long? = null,
    val type: Int,
    val content: String,
    val sortOrder: Int,
    val language: String = "",
    val parentBranchId: Long? = null,
    /**
     * 块级语义标记：0 = 未标记，1..4 = `EmphasisTone.value`。
     *
     * `defaultValue` 必须与 `MIGRATION_5_6` 的 DDL 逐字一致 ——
     * `MigrationTest.runMigrationsAndValidate` 会拿导出的 schema 比对，
     * 一边有 `DEFAULT 0`、另一边没有就会直接失败。
     */
    @ColumnInfo(defaultValue = "0")
    val emphasis: Int = 0
)
