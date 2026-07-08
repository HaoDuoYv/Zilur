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

    val all = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
