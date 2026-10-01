package com.example.zhilu.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate2To3_preservesNoteBlocksAndDefaultsLanguage() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO notes (id, title, createdAt, updatedAt, isFavorite)
                VALUES (1, 'Migration note', 100, 101, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO note_blocks (id, noteId, type, content, sortOrder)
                VALUES (10, 1, 0, 'Existing block content', 7)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, Migration.MIGRATION_2_3).apply {
            query(
                """
                SELECT id, noteId, type, content, language, sortOrder
                FROM note_blocks
                WHERE id = 10
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(10L, cursor.getLong(0))
                assertEquals(1L, cursor.getLong(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals("Existing block content", cursor.getString(3))
                assertEquals("", cursor.getString(4))
                assertEquals(7, cursor.getInt(5))
                assertFalse(cursor.moveToNext())
            }
            close()
        }
    }

    @Test
    fun migrate4To5_createsAiConversationTables() {
        helper.createDatabase(TEST_DB_V5, 4).apply {
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_V5, 5, true, Migration.MIGRATION_4_5).apply {
            execSQL(
                "INSERT INTO ai_conversations (id, title, createdAt, updatedAt) VALUES (1, '测试对话', 100, 101)"
            )
            execSQL(
                "INSERT INTO ai_messages (id, conversationId, role, content, createdAt) VALUES (10, 1, 'USER', '你好', 102)"
            )
            query(
                "SELECT id, conversationId, role, content FROM ai_messages WHERE id = 10"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(10L, cursor.getLong(0))
                assertEquals(1L, cursor.getLong(1))
                assertEquals("USER", cursor.getString(2))
                assertEquals("你好", cursor.getString(3))
                assertFalse(cursor.moveToNext())
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val TEST_DB_V5 = "migration-test-v5"
    }
}
