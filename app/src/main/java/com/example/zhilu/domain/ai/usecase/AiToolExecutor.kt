package com.example.zhilu.domain.ai.usecase

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.ai.model.AiToolDefinition
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.TagRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 工具执行器：把 LLM 的结构化工具调用映射为对 Note/Tag 仓库的真实操作。
 * 每个工具返回一段文本（结果或错误），回传给 LLM 作为 tool 消息，供其组织最终回答。
 *
 * 关键约束：任何异常都会被吞掉并转为文本结果，绝不向上抛出中断对话。
 */
@Singleton
class AiToolExecutor @Inject constructor(
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 暴露给 LLM 的工具清单。 */
    val definitions: List<AiToolDefinition> = listOf(
        AiToolDefinition(
            name = "list_notes",
            description = "列出知识库中全部笔记的 id 与标题，用于了解已有内容。",
            parametersJson = """{"type":"object","properties":{},"required":[]}"""
        ),
        AiToolDefinition(
            name = "search_notes",
            description = "按关键词搜索笔记，返回匹配笔记的 id、标题与内容摘要。",
            parametersJson = """
                {"type":"object","properties":{"query":{"type":"string","description":"搜索关键词"}},"required":["query"]}
            """.trimIndent()
        ),
        AiToolDefinition(
            name = "get_note",
            description = "读取指定笔记的完整标题与内容块。",
            parametersJson = """
                {"type":"object","properties":{"noteId":{"type":"integer","description":"笔记 id"}},"required":["noteId"]}
            """.trimIndent()
        ),
        AiToolDefinition(
            name = "create_note",
            description = "创建一篇新笔记并落地为知识库内容。包含标题、内容块与标签。",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "title": {"type": "string", "description": "笔记标题"},
                    "blocks": {
                      "type": "array",
                      "description": "内容块列表，按顺序排列",
                      "items": {
                        "type": "object",
                        "properties": {
                          "type": {"type": "string", "enum": ["text", "latex", "code", "todo", "link", "divider", "branch"]},
                          "content": {"type": "string", "description": "块正文；latex 存放裸 LaTeX 源码（不含 \u0024\u0024 包裹）"},
                          "language": {"type": "string", "description": "仅 code 类型必填，如 kotlin / python / java"}
                        },
                        "required": ["type", "content"]
                      }
                    },
                    "tags": {"type": "array", "items": {"type": "string"}, "description": "标签名列表"}
                  },
                  "required": ["title"]
                }
            """.trimIndent()
        ),
        AiToolDefinition(
            name = "update_note",
            description = "更新已有笔记的标题与内容块（传入完整内容块以覆盖原内容）。",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "noteId": {"type": "integer"},
                    "title": {"type": "string"},
                    "blocks": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "type": {"type": "string", "enum": ["text", "latex", "code", "todo", "link", "divider", "branch"]},
                          "content": {"type": "string"},
                          "language": {"type": "string"}
                        },
                        "required": ["type", "content"]
                      }
                    }
                  },
                  "required": ["noteId"]
                }
            """.trimIndent()
        ),
        AiToolDefinition(
            name = "add_tags",
            description = "给指定笔记追加标签。",
            parametersJson = """
                {"type":"object","properties":{"noteId":{"type":"integer"},"tags":{"type":"array","items":{"type":"string"}}},"required":["noteId","tags"]}
            """.trimIndent()
        )
    )

    /**
     * 执行工具调用。失败时返回错误文本而非抛异常。
     */
    suspend fun execute(name: String, argumentsJson: String): String = try {
        when (name) {
            "list_notes" -> listNotes()
            "search_notes" -> searchNotes(argumentsJson)
            "get_note" -> getNote(argumentsJson)
            "create_note" -> createNote(argumentsJson)
            "update_note" -> updateNote(argumentsJson)
            "add_tags" -> addTags(argumentsJson)
            else -> "未知工具：$name"
        }
    } catch (e: Exception) {
        "工具执行失败：${e.message ?: "未知错误"}"
    }

    // ---- 工具实现 ----

    private suspend fun listNotes(): String {
        return when (val result = noteRepository.getAllNotes().first()) {
            is RepositoryResult.Success -> {
                if (result.data.isEmpty()) "当前知识库为空。"
                else result.data.joinToString("\n") { "- [${it.id}] ${it.title}" }
            }
            is RepositoryResult.Error -> "读取笔记失败：${result.message}"
        }
    }

    private suspend fun searchNotes(argumentsJson: String): String {
        val query = parseArgs(argumentsJson).optString("query")
        if (query.isNullOrBlank()) return "缺少 query 参数"
        return when (val result = noteRepository.searchNotes(query)) {
            is RepositoryResult.Success -> {
                if (result.data.isEmpty()) "未找到与「$query」相关的笔记。"
                else result.data.joinToString("\n") { note ->
                    "- [${note.id}] ${note.title}" + snippetOf(note)
                }
            }
            is RepositoryResult.Error -> "搜索失败：${result.message}"
        }
    }

    private suspend fun getNote(argumentsJson: String): String {
        val noteId = parseArgs(argumentsJson).optLong("noteId") ?: return "缺少 noteId 参数"
        return when (val result = noteRepository.getNoteById(noteId)) {
            is RepositoryResult.Success -> result.data?.let(::formatNote) ?: "笔记 $noteId 不存在。"
            is RepositoryResult.Error -> "读取失败：${result.message}"
        }
    }

    private suspend fun createNote(argumentsJson: String): String {
        val args = parseArgs(argumentsJson)
        val title = args.optString("title")?.takeIf { it.isNotBlank() } ?: return "缺少 title 参数"
        val blocks = args.optArray("blocks")?.let(::parseBlocks).orEmpty()
        val tags = resolveTags(args.optStringArray("tags"))
        return when (val result = noteRepository.insertNote(Note(title = title, blocks = blocks, tags = tags))) {
            is RepositoryResult.Success ->
                "已创建笔记「${result.data.title}」(id=${result.data.id})，共 ${result.data.blocks.size} 个内容块。"
            is RepositoryResult.Error -> "创建失败：${result.message}"
        }
    }

    private suspend fun updateNote(argumentsJson: String): String {
        val args = parseArgs(argumentsJson)
        val noteId = args.optLong("noteId") ?: return "缺少 noteId 参数"
        val existing = when (val result = noteRepository.getNoteById(noteId)) {
            is RepositoryResult.Success -> result.data ?: return "笔记 $noteId 不存在。"
            is RepositoryResult.Error -> return "读取失败：${result.message}"
        }
        val title = args.optString("title")
        val blocks = args.optArray("blocks")?.let(::parseBlocks)
        val updated = existing.copy(
            title = title ?: existing.title,
            blocks = blocks ?: existing.blocks
        )
        return when (val result = noteRepository.updateNote(updated)) {
            is RepositoryResult.Success -> "已更新笔记「${result.data.title}」(id=${result.data.id})。"
            is RepositoryResult.Error -> "更新失败：${result.message}"
        }
    }

    private suspend fun addTags(argumentsJson: String): String {
        val args = parseArgs(argumentsJson)
        val noteId = args.optLong("noteId") ?: return "缺少 noteId 参数"
        val existing = when (val result = noteRepository.getNoteById(noteId)) {
            is RepositoryResult.Success -> result.data ?: return "笔记 $noteId 不存在。"
            is RepositoryResult.Error -> return "读取失败：${result.message}"
        }
        val newNames = args.optStringArray("tags")
        val merged = existing.tags.map { it.name }.toMutableSet().apply { addAll(newNames) }
        val tags = resolveTags(merged.toList())
        return when (val result = noteRepository.updateNote(existing.copy(tags = tags))) {
            is RepositoryResult.Success -> "已为笔记「${result.data.title}」更新标签：${tags.joinToString { it.name }}"
            is RepositoryResult.Error -> "更新标签失败：${result.message}"
        }
    }

    // ---- 解析与格式化 ----

    private fun parseArgs(argumentsJson: String): JsonObject =
        (json.parseToJsonElement(argumentsJson) as? JsonObject) ?: JsonObject(emptyMap())

    private fun parseBlocks(arr: JsonArray): List<Block> =
        arr.mapIndexed { index, element ->
            val obj = element as? JsonObject
            val type = obj?.optString("type") ?: "text"
            val content = obj?.optString("content") ?: ""
            val language = obj?.optString("language") ?: ""
            Block(
                type = blockTypeFromName(type),
                content = content,
                language = language,
                sortOrder = index
            )
        }

    private fun blockTypeFromName(name: String): BlockType = when (name.lowercase()) {
        "latex" -> BlockType.LATEX
        "code" -> BlockType.CODE
        "todo" -> BlockType.TODO
        "link" -> BlockType.LINK
        "divider" -> BlockType.DIVIDER
        "branch" -> BlockType.BRANCH
        else -> BlockType.TEXT
    }

    private suspend fun resolveTags(names: List<String>): List<Tag> =
        names.map { name ->
            when (val existing = tagRepository.getTagByName(name)) {
                is RepositoryResult.Success -> existing.data ?: insertTag(name)
                is RepositoryResult.Error -> insertTag(name)
            }
        }.filter { it.id > 0 }

    private suspend fun insertTag(name: String): Tag =
        when (val result = tagRepository.insertTag(Tag(name = name))) {
            is RepositoryResult.Success -> Tag(id = result.data, name = name)
            is RepositoryResult.Error -> Tag(name = name)
        }

    private fun formatNote(note: Note): String {
        val body = note.blocks.joinToString("\n\n") { block ->
            when (block.type) {
                BlockType.CODE -> "```${block.language.ifBlank { "text" }}\n${block.content}\n```"
                BlockType.LATEX -> "\$\$${block.content}\$\$"
                BlockType.LINK -> block.content
                BlockType.TODO -> "- [ ] ${block.content}"
                BlockType.DIVIDER -> "---"
                BlockType.BRANCH -> "【分支】${block.content}"
                else -> block.content
            }
        }
        val tags = if (note.tags.isEmpty()) "" else "\n标签：" + note.tags.joinToString { it.name }
        return "标题：${note.title}\n" + if (body.isBlank()) "（空笔记）" else body + tags
    }

    private fun snippetOf(note: Note): String {
        val text = note.blocks.firstOrNull { it.type == BlockType.TEXT }?.content
            ?.replace('\n', ' ')
            ?.take(60)
        return if (text.isNullOrBlank()) "" else "｜$text"
    }
}

// ---- JsonObject 便捷扩展 ----

private fun JsonObject.optString(key: String): String? =
    (this[key] as? JsonPrimitive)?.content

private fun JsonObject.optLong(key: String): Long? =
    (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

private fun JsonObject.optArray(key: String): JsonArray? =
    this[key] as? JsonArray

private fun JsonObject.optStringArray(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
