package com.example.zhilu.domain.ai.usecase

import android.net.Uri
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.ai.model.AiToolDefinition
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.TagRepository
import java.io.File
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
    private val tagRepository: TagRepository,
    private val mediaRepository: MediaRepository,
    private val mediaFileManager: MediaFileManager
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
                          "type": {"type": "string", "enum": ["text", "latex", "code", "todo", "link", "divider", "branch", "image"]},
                          "content": {"type": "string", "description": "块正文；latex 存放裸 LaTeX 源码（不含 \u0024\u0024 包裹）；image 存放图片 URI，只能使用上下文「本次附带的图片」里给出的路径，不可编造"},
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
                          "type": {"type": "string", "enum": ["text", "latex", "code", "todo", "link", "divider", "branch", "image"]},
                          "content": {"type": "string", "description": "text/latex/code/todo/link/branch 为正文；image 为图片 URI，必须沿用已有块里给出的 URI 或上下文「本次附带的图片」里的路径，不可编造或改写"},
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
        val parsed = args.optArray("blocks")?.let { parseBlocks(it) } ?: ParsedBlocks(emptyList(), 0)
        val tags = resolveTags(args.optStringArray("tags"))
        return when (val result = noteRepository.insertNote(Note(title = title, blocks = parsed.blocks, tags = tags))) {
            is RepositoryResult.Success ->
                "已创建笔记「${result.data.title}」(id=${result.data.id})，共 ${result.data.contentBlocks.size} 个内容块。" +
                    droppedImageHint(parsed.droppedImages)
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
        val parsed = args.optArray("blocks")?.let { parseBlocks(it) }
        val updated = existing.copy(
            title = title ?: existing.title,
            blocks = parsed?.blocks ?: existing.blocks
        )
        return when (val result = noteRepository.updateNote(updated)) {
            is RepositoryResult.Success ->
                "已更新笔记「${result.data.title}」(id=${result.data.id})。" +
                    droppedImageHint(parsed?.droppedImages ?: 0)
            is RepositoryResult.Error -> "更新失败：${result.message}"
        }
    }

    /**
     * 有图片块因 URI 不可读被丢弃时如实说明。
     * 静默丢弃会让模型继续声称「图片已写入」，用户却看不到图——必须让模型知道才好纠正。
     */
    private fun droppedImageHint(count: Int): String =
        if (count <= 0) "" else "（有 $count 个图片块因图片路径不可读被跳过，请勿声称这些图片已写入）"

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

    /** 解析结果：块列表 + 被丢弃的图片块数量（用于如实反馈给模型，而不是假装成功）。 */
    private data class ParsedBlocks(val blocks: List<Block>, val droppedImages: Int)

    private suspend fun parseBlocks(arr: JsonArray): ParsedBlocks {
        val blocks = mutableListOf<Block>()
        var dropped = 0
        for (element in arr) {
            val obj = element as? JsonObject ?: continue
            val type = blockTypeFromName(obj.optString("type") ?: "text")
            val rawContent = obj.optString("content") ?: ""
            val content: String
            if (type == BlockType.IMAGE) {
                val registered = registerImageContent(rawContent)
                if (registered == null) {
                    // 图片路径不可读：丢弃该块并计数，交由调用方如实告知模型。
                    dropped++
                    continue
                }
                content = registered
            } else {
                content = rawContent
            }
            blocks += Block(
                type = type,
                content = content,
                language = obj.optString("language") ?: "",
                sortOrder = blocks.size
            )
        }
        return ParsedBlocks(blocks, dropped)
    }

    /**
     * 把图片块内容规范成「mediaId|uri」，并确保这张图已登记进 media 表。
     *
     * 助手在聊天里附加的图片只落到内部存储（files/images），没有 media 表记录。
     * 若把裸 URI 直接写进笔记，这张图就脱离了媒体体系——分享/导出按 mediaId 找不到它，
     * 媒体清理也会把它当孤儿删掉。这里在写入前补登记，让它和用户从相册选的图完全等价。
     *
     * @return 规范化后的内容；无法解析为本地可读图片时返回 null，调用方应丢弃该块。
     */
    private suspend fun registerImageContent(rawContent: String): String? {
        val rawUri = ImageBlockContent.displayUri(rawContent).trim()
        if (rawUri.isBlank()) return null

        // content:// 等外部 URI 先复制进内部存储，file:// 直接沿用（避免重复拷贝）。
        val uri = if (rawUri.asLocalFile() != null) {
            rawUri
        } else {
            mediaFileManager.importUriToInternal(Uri.parse(rawUri)).getOrNull() ?: return null
        }
        val file = uri.asLocalFile()?.takeIf { it.isFile } ?: return null

        // 同一张图已登记过就复用，保证 update_note 反复回写不会插出重复记录。
        mediaByUri(uri)?.let { return ImageBlockContent.fromMedia(it.id, uri) }

        return when (val result = mediaRepository.insertMedia(Media(uri = uri, size = file.length()))) {
            is RepositoryResult.Success ->
                if (result.data > 0) ImageBlockContent.fromMedia(result.data, uri) else null
            is RepositoryResult.Error -> null
        }
    }

    private suspend fun mediaByUri(uri: String): Media? =
        when (val result = mediaRepository.getAllMedia()) {
            is RepositoryResult.Success -> result.data.firstOrNull { it.uri == uri }
            is RepositoryResult.Error -> null
        }

    private fun String.asLocalFile(): File? {
        val parsed = Uri.parse(this)
        return when (parsed.scheme?.lowercase()) {
            "file" -> parsed.path?.let(::File)
            null -> File(this)
            else -> null
        }
    }

    private fun blockTypeFromName(name: String): BlockType = when (name.lowercase()) {
        "latex" -> BlockType.LATEX
        "code" -> BlockType.CODE
        "todo" -> BlockType.TODO
        "link" -> BlockType.LINK
        "divider" -> BlockType.DIVIDER
        "branch" -> BlockType.BRANCH
        "image" -> BlockType.IMAGE
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
        // 必须用 contentBlocks：有知识卡片的笔记，块挂在卡片下，note.blocks 是空的。
        // 直接读 note.blocks 会让模型以为笔记是空的，进而回复「该笔记正文为空，需要我重新写入吗」。
        val body = note.contentBlocks.joinToString("\n\n") { block ->
            when (block.type) {
                BlockType.CODE -> "```${block.language.ifBlank { "text" }}\n${block.content}\n```"
                BlockType.LATEX -> "\$\$${block.content}\$\$"
                BlockType.LINK -> block.content
                BlockType.TODO -> "- [ ] ${block.content}"
                BlockType.DIVIDER -> "---"
                BlockType.BRANCH -> "【分支】${block.content}"
                // 图片块的 content 形如「mediaId|uri」，回读时只暴露真实 uri，
                // 这样模型回写 update_note 时能原样沿用，不会编造路径。
                BlockType.IMAGE -> "【图片】${ImageBlockContent.displayUri(block.content)}"
                else -> block.content
            }
        }
        val tags = if (note.tags.isEmpty()) "" else "\n标签：" + note.tags.joinToString { it.name }
        return "标题：${note.title}\n" + if (body.isBlank()) "（空笔记）" else body + tags
    }

    private fun snippetOf(note: Note): String {
        val text = note.contentBlocks.firstOrNull { it.type == BlockType.TEXT }?.content
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
