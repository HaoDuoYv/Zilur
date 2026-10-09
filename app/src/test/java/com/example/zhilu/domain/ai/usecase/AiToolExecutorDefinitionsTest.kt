package com.example.zhilu.domain.ai.usecase

import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 工具清单的静态校验。
 *
 * 这些 `parametersJson` 是直接拼出来的字符串，写坏了只有等真实对话时才炸
 * （甚至只是让模型拿到一个非法 schema、静默用错参数）。所以在这里用纯 JVM 测试钉住。
 */
class AiToolExecutorDefinitionsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val executor = AiToolExecutor(
        noteRepository = mockk(relaxed = true),
        tagRepository = mockk(relaxed = true),
        todoRepository = mockk(relaxed = true),
        mediaRepository = mockk(relaxed = true),
        mediaFileManager = mockk(relaxed = true),
        reviewRepository = mockk(relaxed = true),
        manageReviewPlan = mockk(relaxed = true),
        todoReminderSync = mockk(relaxed = true)
    )

    @Test
    fun `工具名不重复`() {
        val names = executor.definitions.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.isNotEmpty())
    }

    @Test
    fun `每个工具的 parametersJson 都是合法的 object schema`() {
        for (tool in executor.definitions) {
            val schema = json.parseToJsonElement(tool.parametersJson) as? JsonObject
                ?: error("${tool.name} 的 parametersJson 不是 JSON 对象")
            assertEquals("${tool.name} 的 schema type 必须是 object", "object", schema["type"]?.jsonPrimitive?.content)
            assertTrue("${tool.name} 缺少 properties", schema["properties"] is JsonObject)
        }
    }

    @Test
    fun `读写删三类操作都有对应工具`() {
        val names = executor.definitions.map { it.name }.toSet()
        // 读
        assertTrue("list_notes" in names)
        assertTrue("search_notes" in names)
        assertTrue("get_note" in names)
        // 写
        assertTrue("create_note" in names)
        assertTrue("update_note" in names)
        assertTrue("add_blocks" in names)
        assertTrue("set_block_emphasis" in names)
        assertTrue("add_tags" in names)
        assertTrue("add_todos" in names)
        // 删
        assertTrue("delete_note" in names)
        // 复习
        assertTrue("schedule_review" in names)
        assertTrue("list_due_reviews" in names)
    }

    @Test
    fun `add_todos 声明了可选的 remindAt`() {
        val raw = executor.definitions.first { it.name == "add_todos" }.parametersJson
        // 提醒时间必须是**可选**的：模型说不清时间时应当省略，而不是编一个
        assertTrue("add_todos 缺少 remindAt", raw.contains("\"remindAt\""))
        val required = Regex("\"required\":\\[(.*?)\\]").find(raw)?.groupValues?.get(1).orEmpty()
        assertTrue("remindAt 不该出现在 required 里", required.contains("remindAt").not())
        assertTrue(required.contains("noteId"))
        assertTrue(required.contains("todos"))
    }

    @Test
    fun `复习排期工具的说明点明了不会重置进度`() {
        val definition = executor.definitions.first { it.name == "schedule_review" }
        // 这句话是模型愿不愿意优先调它的关键 —— 用户最怕"AI 一说就把进度清零"
        assertTrue(definition.description.contains("不会重置进度"))
        assertTrue(definition.parametersJson.contains("\"noteId\""))
    }

    @Test
    fun `到期查询工具不需要参数`() {
        val schema = json.parseToJsonElement(
            executor.definitions.first { it.name == "list_due_reviews" }.parametersJson
        ) as JsonObject
        assertTrue("list_due_reviews 应当是零参数的", (schema["properties"] as JsonObject).isEmpty())
    }

    @Test
    fun `分支块声明了 children 且子块 schema 不含 branch`() {
        val raw = executor.definitions.first { it.name == "create_note" }.parametersJson
        assertTrue("块 schema 缺少 children", raw.contains("\"children\""))
        // 子块枚举里不应再出现 branch —— 折叠层级只开放一层
        val childEnum = Regex("\\\"enum\\\": \\[\\\"text\\\", \\\"latex\\\", \\\"code\\\", \\\"todo\\\", \\\"link\\\", \\\"divider\\\", \\\"image\\\"\\]")
        assertTrue("子块枚举应当不含 branch", childEnum.containsMatchIn(raw))
    }

    @Test
    fun `分隔线不要求 content`() {
        val raw = executor.definitions.first { it.name == "create_note" }.parametersJson
        assertTrue(raw.contains("\"required\": [\"type\"]"))
    }

    @Test
    fun `强调相关的工具都声明了 none 之外的四个语义角色`() {
        val emphasisTools = listOf("create_note", "update_note", "add_blocks", "set_block_emphasis")
        for (name in emphasisTools) {
            val raw = executor.definitions.first { it.name == name }.parametersJson
            for (tone in listOf("key", "idea", "warn", "todo", "none")) {
                assertTrue("$name 的 schema 里缺少强调角色 $tone", raw.contains("\"$tone\""))
            }
        }
    }

    @Test
    fun `卡片身份色的全部变体名都在 create_note 的 schema 里`() {
        val raw = executor.definitions.first { it.name == "create_note" }.parametersJson
        for (accent in listOf("ink", "ochre", "teal", "crimson", "moss", "graphite", "violet", "vermilion")) {
            assertTrue("create_note 的 schema 里缺少身份色 $accent", raw.contains("\"$accent\""))
        }
    }

    @Test
    fun `块 schema 出现在 cards 与 blocks 两处且保持一致`() {
        val raw = executor.definitions.first { it.name == "create_note" }.parametersJson
        // 两处都声明了块类型枚举
        val occurrences = Regex("\\\"enum\\\": \\[\\\"text\\\", \\\"latex\\\"").findAll(raw).count()
        assertTrue("块 schema 应同时用于 cards[].blocks 与顶层 blocks，实际 $occurrences 处", occurrences >= 2)
    }
}
