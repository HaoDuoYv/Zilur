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

    @Test
    fun migrate5To6_addsEmphasisAndCardAccentWithDefaults() {
        helper.createDatabase(TEST_DB_V6, 5).apply {
            execSQL(
                """
                INSERT INTO notes (id, title, createdAt, updatedAt, isFavorite)
                VALUES (1, '老笔记', 100, 101, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO note_cards (id, noteId, title, sortOrder)
                VALUES (20, 1, '老卡片', 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO note_blocks (id, noteId, cardId, type, content, language, sortOrder)
                VALUES (30, 1, 20, 1, '老正文', '', 0)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_V6, 6, true, Migration.MIGRATION_5_6).apply {
            // 既有块的 emphasis 拿默认值 0（未标记），正文不能动
            query("SELECT content, emphasis FROM note_blocks WHERE id = 30").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("老正文", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertFalse(cursor.moveToNext())
            }
            // 既有卡片的 accent 为 NULL，渲染时回退到轮转色
            query("SELECT title, accent FROM note_cards WHERE id = 20").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("老卡片", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertFalse(cursor.moveToNext())
            }
            // 新写入能带上真正的值
            execSQL("UPDATE note_blocks SET emphasis = 3 WHERE id = 30")
            query("SELECT emphasis FROM note_blocks WHERE id = 30").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate6To7_addsRefsJsonAndQuotedMessageId() {
        helper.createDatabase(TEST_DB_V7, 6).apply {
            execSQL(
                "INSERT INTO ai_conversations (id, title, createdAt, updatedAt) VALUES (1, '老对话', 100, 101)"
            )
            execSQL(
                "INSERT INTO ai_messages (id, conversationId, role, content, createdAt) VALUES (10, 1, 'USER', '老消息', 102)"
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_V7, 7, true, Migration.MIGRATION_6_7).apply {
            // 既有消息：refsJson 与 quotedMessageId 都是 NULL（本来就没有引用），正文不能动
            query("SELECT content, refsJson, quotedMessageId FROM ai_messages WHERE id = 10").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("老消息", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertFalse(cursor.moveToNext())
            }
            // 新写入能带上真正的值（含引用快照的 JSON）
            execSQL(
                """
                UPDATE ai_messages
                SET refsJson = '[{"kind":"NOTE","noteId":3,"title":"笔记","snapshot":"正文"}]',
                    quotedMessageId = 10
                WHERE id = 10
                """.trimIndent()
            )
            query("SELECT refsJson, quotedMessageId FROM ai_messages WHERE id = 10").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    "[{\"kind\":\"NOTE\",\"noteId\":3,\"title\":\"笔记\",\"snapshot\":\"正文\"}]",
                    cursor.getString(0)
                )
                assertEquals(10L, cursor.getLong(1))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val TEST_DB_V5 = "migration-test-v5"
        const val TEST_DB_V6 = "migration-test-v6"
        const val TEST_DB_V7 = "migration-test-v7"
    }
}
