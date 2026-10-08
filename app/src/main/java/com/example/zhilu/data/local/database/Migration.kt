package com.example.zhilu.data.local.database

object Migration {
    private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `review_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `currentStep` INTEGER NOT NULL, `nextReviewAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `completedAt` INTEGER)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_review_plans_noteId` ON `review_plans` (`noteId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `review_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `noteId` INTEGER NOT NULL, `reviewedAt` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `previousStep` INTEGER NOT NULL, `nextStep` INTEGER NOT NULL, `nextReviewAt` INTEGER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_events_planId` ON `review_events` (`planId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_events_noteId` ON `review_events` (`noteId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `todo_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` INTEGER, `content` TEXT NOT NULL, `remindAt` INTEGER, `completedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_noteId` ON `todo_items` (`noteId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_remindAt` ON `todo_items` (`remindAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `reminder_instances` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `noteId` INTEGER, `dueAt` INTEGER NOT NULL, `status` INTEGER NOT NULL, `notificationId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `firedAt` INTEGER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_instances_type_sourceId_status` ON `reminder_instances` (`type`, `sourceId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_instances_noteId` ON `reminder_instances` (`noteId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_instances_dueAt` ON `reminder_instances` (`dueAt`)")
        }
    }

    internal val MIGRATION_2_3: androidx.room.migration.Migration = object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE note_blocks ADD COLUMN language TEXT NOT NULL DEFAULT ''")
        }
    }

    internal val MIGRATION_3_4: androidx.room.migration.Migration = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `note_cards` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `noteId` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_cards_noteId` ON `note_cards` (`noteId`)")
            db.execSQL("ALTER TABLE note_blocks ADD COLUMN cardId INTEGER")
            db.execSQL("ALTER TABLE note_blocks ADD COLUMN parentBranchId INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_blocks_cardId` ON `note_blocks` (`cardId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_blocks_parentBranchId` ON `note_blocks` (`parentBranchId`)")
        }
    }

    internal val MIGRATION_4_5: androidx.room.migration.Migration = object : androidx.room.migration.Migration(4, 5) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_conversations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_conversations_updatedAt` ON `ai_conversations` (`updatedAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_messages` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `conversationId` INTEGER NOT NULL,
                    `role` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `imagesJson` TEXT,
                    `fileText` TEXT,
                    `toolCallId` TEXT,
                    `toolName` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`conversationId`) REFERENCES `ai_conversations`(`id`) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_messages_conversationId` ON `ai_messages` (`conversationId`)")
        }
    }

    /**
     * v6：块级语义标记 + 卡片身份色。
     *
     * 用户明确表示本次不需要兼容老数据，但仍走正规迁移 ——
     * 两条 `ALTER TABLE ADD COLUMN` 的成本比破坏性迁移更低，也不违反项目对
     * `fallbackToDestructiveMigration()` 的禁令。既有行拿到
     * `emphasis = 0`（未标记）与 `accent = NULL`（按序号回退轮转色），外观立刻合理。
     *
     * ⚠️ `emphasis` 的 `DEFAULT 0` 必须与 `NoteBlockEntity` 上的
     * `@ColumnInfo(defaultValue = "0")` 逐字一致；`accent` 可空且两边都不给默认值。
     */
    internal val MIGRATION_5_6: androidx.room.migration.Migration = object : androidx.room.migration.Migration(5, 6) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE note_blocks ADD COLUMN emphasis INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE note_cards ADD COLUMN accent INTEGER")
        }
    }

    /**
     * v7：AI 消息支持引用与消息引用。
     *
     * - `refsJson`：用户消息附带的引用列表（JSON 数组，**含引用时刻冻结的内容快照**）。
     * - `quotedMessageId`：本条消息引用（回复）的同会话消息 id。
     *
     * 两列都可空且不给 DEFAULT，与既有的 `imagesJson TEXT` 同一约定：
     * 既有行拿 NULL（本来就没有引用），语义正好一致，也避开 `defaultValue`
     * 与 DDL 逐字对齐的坑（对照 v6 `emphasis` 的教训）。
     */
    internal val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE ai_messages ADD COLUMN refsJson TEXT")
            db.execSQL("ALTER TABLE ai_messages ADD COLUMN quotedMessageId INTEGER")
        }
    }

    val all = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
