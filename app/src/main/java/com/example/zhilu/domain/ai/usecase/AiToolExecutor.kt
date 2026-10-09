package com.example.zhilu.domain.ai.usecase

import android.net.Uri
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.ai.model.AiToolDefinition
import com.example.zhilu.domain.markup.InlineMarkup
import com.example.zhilu.domain.markup.InlineMarkupNormalizer
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.CardAccent
import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewPlanWithNote
import com.example.zhilu.domain.model.ReviewQueue
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.reminder.ReviewQueueClassifier
import com.example.zhilu.domain.reminder.TodoReminderSync
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.domain.repository.TodoRepository
import com.example.zhilu.domain.usecase.ManageReviewPlanUseCase
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
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
 * 关键约束：
 * - 任何异常都会被吞掉并转为文本结果，绝不向上抛出中断对话；
 * - **写侧一律经 [InlineMarkupNormalizer] 规整正文**，因为 AI 是"文本直接落库"的入口之一
 *   （另一个是系统剪贴板粘贴），模型写坏语法时只该损失标记、不该污染正文；
 * - 增量写入优先用 `add_blocks`，而不是让模型整篇 `update_note` 覆盖 —— 后者是历史上
 *   "内容丢失"类事故的主要来源。
 */
@Singleton
class AiToolExecutor @Inject constructor(
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val todoRepository: TodoRepository,
    private val mediaRepository: MediaRepository,
    private val mediaFileManager: MediaFileManager,
    private val reviewRepository: ReviewRepository,
    /** 排期一律经它，别自己 `startPlan` + 提醒 —— 见 `ManageReviewPlanUseCase` 的说明。 */
    private val manageReviewPlan: ManageReviewPlanUseCase,
    /** 待办带提醒时间时要建 TODO 提醒，同样走单一同步点（见 `TodoReminderSync`）。 */
    private val todoReminderSync: TodoReminderSync
) {

    /**
     * 分区口径与复习中心共用（同一个无状态纯函数类）—— 模型说「今天要复习 3 篇」
     * 和界面上看到的必须是同一个数。直接持有而不是注入：它没有依赖，注入只多一层 DI 接线。
     */
    private val queueClassifier = ReviewQueueClassifier()


    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 分支子块的结构。
     *
     * 与 [blockItemSchema] 分开定义是因为 JSON 字符串没法自引用；**只开放一层**：
     * 应用里虽然允许嵌套分支，但真实笔记的折叠层级超过一层就没人看得懂了，
     * 而且模型的嵌套 JSON 出错率明显上升。（解析器本身是递归的，模型硬要嵌也能落库。）
     */
    private val childBlockSchema = """
        {
          "type": "object",
          "properties": {
            "type": {"type": "string", "enum": ["text", "latex", "code", "todo", "link", "divider", "image"], "description": "子块类型（子块里不要再放 branch）"},
            "content": {"type": "string", "description": "子块正文，同内容块规则"},
            "language": {"type": "string", "description": "仅 code 类型需要"},
            "emphasis": {"type": "string", "enum": ["none", "key", "idea", "warn", "todo"], "description": "子块的块级语义标记，同内容块规则"}
          },
          "required": ["type", "content"]
        }
    """.trimIndent()

    private val blockItemSchema = """
        {
          "type": "object",
          "properties": {
            "type": {"type": "string", "enum": ["text", "latex", "code", "todo", "link", "divider", "branch", "image"], "description": "块类型；缺省按 text 处理。divider 是分割线，不需要 content"},
            "content": {"type": "string", "description": "块正文。text：可含行内标记；latex：只写裸 LaTeX 源码（不含 \u0024\u0024 包裹）；image：只能填上下文「本次附带的图片」给出的路径或笔记里已有的图片地址，不可编造；divider：留空"},
            "language": {"type": "string", "description": "仅 code 类型需要，如 kotlin / python / java"},
            "emphasis": {"type": "string", "enum": ["none", "key", "idea", "warn", "todo"], "description": "块级语义标记：key=要点、idea=想法、warn=注意、todo=待办；缺省 none（不标记）"},
            "children": {"type": "array", "description": "仅 branch 类型使用：折叠分支内部的子块。用户展开分支才会看到，适合放推导过程、例题详解、补充材料", "items": $childBlockSchema}
          },
          "required": ["type"]
        }
    """.trimIndent()

    private val blockArraySchema = """
        {"type": "array", "description": "内容块列表，按顺序排列", "items": $blockItemSchema}
    """.trimIndent()

    private val cardsSchema = """
        {
          "type": "array",
          "description": "小节列表（推荐用它组织多段内容）。每个小节有独立标题，可单独指定身份色",
          "items": {
            "type": "object",
            "properties": {
              "title": {"type": "string", "description": "小节标题，如「惯性定理与规范形」"},
              "accent": {"type": "string", "enum": ["ink", "ochre", "teal", "crimson", "moss", "graphite", "violet", "vermilion"], "description": "小节身份色；vermilion=朱红，用来标'这节最重要'。缺省按顺序自动分配；一般不用填"},
              "blocks": $blockArraySchema
            },
            "required": ["title", "blocks"]
          }
        }
    """.trimIndent()

    /** 暴露给 LLM 的工具清单。 */
    val definitions: List<AiToolDefinition> = listOf(
        AiToolDefinition(
            name = "list_notes",
            description = "列出知识库中全部笔记的 id 与标题，用于了解已有内容。",
            parametersJson = """{"type":"object","properties":{},"required":[]}"""
        ),
        AiToolDefinition(
            name = "search_notes",
            description = "按关键词搜索笔记，返回匹配笔记的 id、标题与正文摘要。",
            parametersJson = """
                {"type":"object","properties":{"query":{"type":"string","description":"搜索关键词"}},"required":["query"]}
            """.trimIndent()
        ),
        AiToolDefinition(
            name = "get_note",
            description = "读取指定笔记的完整标题、小节与内容块（含块 id 与语义标记）。改笔记前先读它。",
            parametersJson = """
                {"type":"object","properties":{"noteId":{"type":"integer","description":"笔记 id"}},"required":["noteId"]}
            """.trimIndent()
        ),
        AiToolDefinition(
            name = "create_note",
            description = "创建一篇新笔记。多段内容请用 cards 组织成小节；只有一段短内容时才用 blocks。",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "title": {"type": "string", "description": "笔记标题"},
                    "cards": $cardsSchema,
                    "blocks": $blockArraySchema,
                    "tags": {"type": "array", "items": {"type": "string"}, "description": "标签名列表"}
                  },
                  "required": ["title"]
                }
            """.trimIndent()
        ),
        AiToolDefinition(
            name = "update_note",
            description = "改写已有笔记的标题 / 小节 / 正文（传入的内容会完整覆盖原来的部分）。只想追加内容请用 add_blocks，避免误删。",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "noteId": {"type": "integer"},
                    "title": {"type": "string"},
                    "cards": $cardsSchema,
                    "blocks": $blockArraySchema,
                    "tags": {"type": "array", "items": {"type": "string"}, "description": "完整替换标签列表"}
                  },
                  "required": ["noteId"]
                }
            """.trimIndent()
        ),
        AiToolDefinition(
            name = "add_blocks",
            description = "在已有笔记末尾追加内容块（不覆盖原有内容）。可指定追加到哪个小节。",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "noteId": {"type": "integer"},
                    "blocks": $blockArraySchema,
                    "card": {"type": "string", "description": "目标小节标题；同名小节已存在则追加进去，不存在则新建。不填时追加到最后一个小节"}
                  },
                  "required": ["noteId", "blocks"]
                }
            """.trimIndent()
        ),
        AiToolDefinition(
            name = "set_block_emphasis",
            description = "给某一条内容块设置或取消块级语义标记（整条小点的左缘色条 + 底色 + 标签词）。注意：任何写入都会重建块 id，因此要先用 get_note 取最新块 id。",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "noteId": {"type": "integer"},
                    "blockId": {"type": "integer", "description": "目标块 id，来自 get_note 的输出"},
                    "tone": {"type": "string", "enum": ["none", "key", "idea", "warn", "todo"], "description": "none=取消标记；key=要点、idea=想法、warn=注意、todo=待办"}
                  },
                  "required": ["noteId", "blockId", "tone"]
                }
            """.trimIndent()
        ),
        AiToolDefinition(
            name = "add_tags",
            description = "给指定笔记追加标签（保留原有标签）。",
            parametersJson = """
                {"type":"object","properties":{"noteId":{"type":"integer"},"tags":{"type":"array","items":{"type":"string"}}},"required":["noteId","tags"]}
            """.trimIndent()
        ),
        AiToolDefinition(
            name = "add_todos",
            description = "给笔记追加**可勾选**的待办项（会显示在该笔记的待办块里）。注意：待办项挂在笔记上，不是挂在某条块上；追加不会覆盖已有待办。",
            parametersJson =
                """{"type":"object","properties":{"noteId":{"type":"integer"},"todos":{"type":"array","items":{"type":"string"},"description":"待办事项文本，一条一项，不要带序号或勾选框"},"remindAt":{"type":"array","items":{"type":"integer"},"description":"可选，与 todos 一一对应的提醒时间（Unix 毫秒时间戳）。用户说了具体时间才填；说不清就先不填，别自己猜时间"}},"required":["noteId","todos"]}"""
        ),
        AiToolDefinition(
            name = "schedule_review",
            description = "为笔记开启复习计划（艾宾浩斯间隔：1 / 3 / 7 / 15 / 30 天）。用户说「帮我安排复习」「加入复习计划」时调用。已经在进行中的计划**不会重置进度**，会直接告知下次复习时间。",
            parametersJson =
                """{"type":"object","properties":{"noteId":{"type":"integer","description":"要安排复习的笔记 id"}},"required":["noteId"]}"""
        ),
        AiToolDefinition(
            name = "list_due_reviews",
            description = "列出复习计划的状态：哪些已经逾期、今天该复习什么、接下来一周的安排。用户问「今天要复习什么」「有哪些该复习了」「我的复习安排」时调用。",
            parametersJson = """{"type":"object","properties":{},"required":[]}"""
        ),

        AiToolDefinition(
            name = "delete_note",
            description = "删除一篇笔记。删除是软删除：笔记进入回收站，用户仍可恢复。用户明确要求删除时才调用。",
            parametersJson = """
                {"type":"object","properties":{"noteId":{"type":"integer","description":"要删除的笔记 id"}},"required":["noteId"]}
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
            "add_blocks" -> addBlocks(argumentsJson)
            "set_block_emphasis" -> setBlockEmphasis(argumentsJson)
            "add_tags" -> addTags(argumentsJson)
            "add_todos" -> addTodos(argumentsJson)
            "schedule_review" -> scheduleReview(argumentsJson)
            "list_due_reviews" -> listDueReviews()
            "delete_note" -> deleteNote(argumentsJson)
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
        val body = when (val result = noteRepository.getNoteById(noteId)) {
            is RepositoryResult.Success -> result.data ?: return "笔记 $noteId 不存在。"
            is RepositoryResult.Error -> return "读取失败：${result.message}"
        }
        // 待办项挂在**笔记**上（不是挂在块上），只读正文是看不到的 —— 不在这里列出来，
        // 模型就永远不知道用户已经有哪些待办，只会一路 add_todos 堆重复项。
        val todos = when (val result = todoRepository.observeByNoteId(noteId).first()) {
            is RepositoryResult.Success -> result.data
            is RepositoryResult.Error -> emptyList()
        }
        return buildString {
            append(formatNote(body))
            if (todos.isNotEmpty()) {
                append("\n\n待办项（").append(todos.count { !it.isCompleted }).append('/')
                    .append(todos.size).append(" 未完成）：")
                todos.forEach { todo ->
                    append("\n  ").append(if (todo.isCompleted) "[x] " else "[ ] ").append(todo.content)
                }
            }
        }
    }

    private suspend fun createNote(argumentsJson: String): String {
        val args = parseArgs(argumentsJson)
        val title = args.optString("title")?.takeIf { it.isNotBlank() }?.let(InlineMarkup::stripMarkup)
            ?: return "缺少 title 参数"
        val parsedCards = args.optArray("cards")?.let { parseCards(it) }
        val parsedBlocks = args.optArray("blocks")?.let { parseBlocks(it) }
        val tags = resolveTags(args.optStringArray("tags"))

        val note = if (parsedCards != null) {
            Note(title = title, cards = parsedCards.cards, tags = tags)
        } else {
            Note(title = title, blocks = parsedBlocks?.blocks.orEmpty(), tags = tags)
        }
        val dropped = (parsedCards?.droppedImages ?: 0) + (parsedBlocks?.droppedImages ?: 0)

        return when (val result = noteRepository.insertNote(note)) {
            is RepositoryResult.Success ->
                "已创建笔记「${result.data.title}」(id=${result.data.id})，共 ${result.data.contentBlocks.size} 个内容块" +
                    (if (result.data.cards.isEmpty()) "。" else "、${result.data.cards.size} 个小节。") +
                    droppedImageHint(dropped)
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

        val parsedCards = args.optArray("cards")?.let { parseCards(it) }
        val parsedBlocks = args.optArray("blocks")?.let { parseBlocks(it) }
        val title = args.optString("title")?.takeIf { it.isNotBlank() }?.let(InlineMarkup::stripMarkup)
        val tagNames = if (args.optArray("tags") != null) args.optStringArray("tags") else null

        // 扁平 blocks 无法表达多个小节：明说而不是静默把结构压平
        if (parsedBlocks != null && parsedCards == null && existing.cards.size > 1) {
            return "这篇笔记有 ${existing.cards.size} 个小节，扁平 blocks 无法表达小节结构。" +
                "请改用 cards 参数重写，或用 add_blocks 追加内容。"
        }

        var updated = existing.copy(title = title ?: existing.title)
        when {
            parsedCards != null -> updated = updated.copy(cards = parsedCards.cards, blocks = emptyList())
            parsedBlocks != null -> updated = if (updated.cards.isEmpty()) {
                updated.copy(blocks = parsedBlocks.blocks)
            } else {
                // 只有一个小节：把内容替换进那一个小节，保留其身份信息
                updated.copy(cards = listOf(updated.cards.first().copy(blocks = parsedBlocks.blocks)))
            }
        }
        if (tagNames != null) updated = updated.copy(tags = resolveTags(tagNames))

        return when (val result = noteRepository.updateNote(updated)) {
            is RepositoryResult.Success ->
                "已更新笔记「${result.data.title}」(id=${result.data.id})。" +
                    droppedImageHint((parsedCards?.droppedImages ?: 0) + (parsedBlocks?.droppedImages ?: 0))
            is RepositoryResult.Error -> "更新失败：${result.message}"
        }
    }

    private suspend fun addBlocks(argumentsJson: String): String {
        val args = parseArgs(argumentsJson)
        val noteId = args.optLong("noteId") ?: return "缺少 noteId 参数"
        val arr = args.optArray("blocks") ?: return "缺少 blocks 参数"
        val cardTitle = args.optString("card")?.trim()?.takeIf { it.isNotEmpty() }

        val existing = when (val result = noteRepository.getNoteById(noteId)) {
            is RepositoryResult.Success -> result.data ?: return "笔记 $noteId 不存在。"
            is RepositoryResult.Error -> return "读取失败：${result.message}"
        }
        val parsed = parseBlocks(arr)
        if (parsed.blocks.isEmpty()) {
            return "没有可写入的内容块。" + droppedImageHint(parsed.droppedImages)
        }

        val updated = when {
            existing.cards.isEmpty() && cardTitle == null ->
                existing.copy(blocks = existing.blocks + parsed.blocks)

            existing.cards.isEmpty() -> {
                // 还没有小节但要指定小节名：先把原有扁平正文收进一个「正文」小节，
                // 再建目标小节。绝不能直接丢掉 existing.blocks。
                val cards = mutableListOf<KnowledgeCard>()
                if (existing.blocks.isNotEmpty()) {
                    cards += buildCard(tempId = -1L, title = "正文", blocks = existing.blocks)
                }
                cards += buildCard(
                    tempId = -(cards.size + 1L),
                    title = cardTitle!!,
                    blocks = parsed.blocks
                )
                existing.copy(cards = cards, blocks = emptyList())
            }

            else -> {
                val index = cardTitle?.let { target ->
                    existing.cards.indexOfFirst { it.title.trim() == target }
                } ?: -1
                val cards = existing.cards.toMutableList()
                if (index >= 0) {
                    cards[index] = buildCard(
                        tempId = -1L,
                        title = cards[index].title,
                        blocks = cards[index].blocks + parsed.blocks,
                        accent = cards[index].accent
                    )
                } else {
                    cards += buildCard(
                        tempId = -1L,
                        title = cardTitle ?: "补充",
                        blocks = parsed.blocks
                    )
                }
                existing.copy(cards = cards)
            }
        }

        val target = when {
            cardTitle != null -> "小节「$cardTitle」"
            existing.cards.isNotEmpty() -> "最后一个小节"
            else -> "笔记正文"
        }
        return when (val result = noteRepository.updateNote(updated)) {
            is RepositoryResult.Success ->
                "已向「${result.data.title}」的${target}追加 ${parsed.blocks.size} 个内容块，" +
                    "现在共 ${result.data.contentBlocks.size} 块。" + droppedImageHint(parsed.droppedImages)
            is RepositoryResult.Error -> "追加失败：${result.message}"
        }
    }

    private suspend fun setBlockEmphasis(argumentsJson: String): String {
        val args = parseArgs(argumentsJson)
        val noteId = args.optLong("noteId") ?: return "缺少 noteId 参数"
        val blockId = args.optLong("blockId") ?: return "缺少 blockId 参数（先调用 get_note 获取）"
        val rawTone = args.optString("tone")?.trim()?.lowercase() ?: return "缺少 tone 参数"
        val tone: EmphasisTone? = if (rawTone == "none") {
            null
        } else {
            EmphasisTone.fromName(rawTone) ?: return "tone 只能是 none / key / idea / warn / todo"
        }

        val existing = when (val result = noteRepository.getNoteById(noteId)) {
            is RepositoryResult.Success -> result.data ?: return "笔记 $noteId 不存在。"
            is RepositoryResult.Error -> return "读取失败：${result.message}"
        }

        var found = false
        val cards = existing.cards.map { card ->
            card.copy(blocks = card.blocks.map { block -> block.withEmphasis(blockId, tone) { found = true } })
        }
        val blocks = if (existing.cards.isEmpty()) {
            existing.blocks.map { block -> block.withEmphasis(blockId, tone) { found = true } }
        } else {
            existing.blocks
        }
        if (!found) {
            return "在这篇笔记里找不到块 $blockId（任何写入都会重建块 id，请重新 get_note 取最新 id）。"
        }

        val updated = existing.copy(cards = cards, blocks = blocks)
        return when (val result = noteRepository.updateNote(updated)) {
            is RepositoryResult.Success -> if (tone == null) {
                "已取消块 $blockId 的语义标记。"
            } else {
                "已把块 $blockId 标记为「${tone.label}」。"
            }
            is RepositoryResult.Error -> "标记失败：${result.message}"
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

    /**
     * 追加可勾选的待办项。
     *
     * **待办项挂在笔记上，不是挂在块上**（`todo_items.noteId`）—— 待办块只是"这篇笔记的
     * 待办清单"的显示位。所以这里只要 noteId，不需要 blockId，也就绕开了"写入会重建块 id"
     * 那个坑。
     *
     * `remindAt` 与 `todos` 按位置一一对应（可省略、可比 todos 短）：给了时间的那些
     * 会经 [TodoReminderSync] 建出 TODO 提醒 —— 与笔记页新建待办走同一段，
     * 通知 id 的派生规则只有一份。
     */
    private suspend fun addTodos(argumentsJson: String): String {
        val args = parseArgs(argumentsJson)
        val noteId = args.optLong("noteId") ?: return "缺少 noteId 参数"
        val texts = args.optStringArray("todos").map { it.trim() }.filter { it.isNotEmpty() }
        if (texts.isEmpty()) return "没有可写入的待办项。"
        val remindAts = args.optLongArray("remindAt")

        val note = when (val result = noteRepository.getNoteById(noteId)) {
            is RepositoryResult.Success -> result.data ?: return "笔记 $noteId 不存在。"
            is RepositoryResult.Error -> return "读取失败：${result.message}"
        }

        val startOrder = when (val result = todoRepository.observeByNoteId(noteId).first()) {
            is RepositoryResult.Success -> result.data.size
            is RepositoryResult.Error -> 0
        }

        var added = 0
        var scheduled = 0
        var lastError: String? = null
        texts.forEachIndexed { index, text ->
            val remindAt = remindAts.getOrNull(index)
            val todo = TodoItem(
                noteId = noteId,
                content = text,
                remindAt = remindAt,
                sortOrder = startOrder + index
            )
            when (val result = todoRepository.addTodo(todo)) {
                is RepositoryResult.Success -> {
                    added++
                    if (remindAt != null) {
                        // 待办已经落库，提醒没建成只该少一条通知，不该把整条待办回滚
                        when (val sync = todoReminderSync.schedule(result.data, noteId, remindAt)) {
                            is RepositoryResult.Success -> scheduled++
                            is RepositoryResult.Error -> lastError = sync.message
                        }
                    }
                }
                is RepositoryResult.Error -> lastError = result.message
            }
        }

        val suffix = if (lastError == null) "" else "（${texts.size - added} 条失败：$lastError）"
        val remindHint = if (scheduled > 0) "，其中 $scheduled 条设了提醒" else ""
        return "已为「${note.title}」追加 $added 条待办项$remindHint$suffix。它们显示在该笔记的待办块里，可勾选完成。"
    }

    /**
     * 为笔记开启 / 恢复复习计划。
     *
     * 三种既有状态各有正确动作，**不能一律 `startPlan`**：
     * 已完成 → 重头开始；暂停中 → 原地继续（保留档位）；进行中 → 什么都不做，
     * 只把下次复习时间告诉模型。一律 `startPlan` 会把用户复习了半个月的进度抹回第 1 档。
     */
    private suspend fun scheduleReview(argumentsJson: String): String {
        val noteId = parseArgs(argumentsJson).optLong("noteId") ?: return "缺少 noteId 参数"
        val note = when (val result = noteRepository.getNoteById(noteId)) {
            is RepositoryResult.Success -> result.data ?: return "笔记 $noteId 不存在。"
            is RepositoryResult.Error -> return "读取失败：${result.message}"
        }
        val existing = when (val result = reviewRepository.getPlanByNoteId(noteId)) {
            is RepositoryResult.Success -> result.data
            is RepositoryResult.Error -> return "读取复习计划失败：${result.message}"
        }

        return when (reviewScheduleAction(existing)) {
            ReviewScheduleAction.AlreadyScheduled ->
                "「${note.title}」的复习计划已在第 ${existing!!.currentStep + 1} 档，" +
                    "下次复习：${describeDue(existing.nextReviewAt)}。"

            ReviewScheduleAction.Resume -> reportScheduled(note.title, "已恢复", manageReviewPlan.resume(existing!!))

            ReviewScheduleAction.CreateOrRestart ->
                reportScheduled(note.title, "已开启", manageReviewPlan.restart(noteId))
        }
    }

    private suspend fun reportScheduled(
        title: String,
        verb: String,
        scheduled: RepositoryResult<ReviewPlan>
    ): String = when (scheduled) {
        is RepositoryResult.Success ->
            "$verb「$title」的复习计划，下次复习：${describeDue(scheduled.data.nextReviewAt)}。"
        is RepositoryResult.Error -> "安排复习失败：${scheduled.message}"
    }

    /**
     * 复习计划现状：逾期 / 今天 / 接下来一周。
     *
     * 分区口径与复习中心完全一致（同一套 [ReviewQueueClassifier]，以今天 0 点为界），
     * 所以模型说"今天要复习 3 篇"和用户在界面上看到的一定是同一个数。
     */
    private suspend fun listDueReviews(): String {
        val plans = when (val result = reviewRepository.observePlans().first()) {
            is RepositoryResult.Success -> result.data
            is RepositoryResult.Error -> return "读取复习计划失败：${result.message}"
        }
        return formatDueReviews(plans, queueClassifier.classify(plans, startOfToday()))
    }

    private fun startOfToday(): Long = LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()


    private suspend fun deleteNote(argumentsJson: String): String {
        val noteId = parseArgs(argumentsJson).optLong("noteId") ?: return "缺少 noteId 参数"
        val existing = when (val result = noteRepository.getNoteById(noteId)) {
            is RepositoryResult.Success -> result.data ?: return "笔记 $noteId 不存在（可能已删除）。"
            is RepositoryResult.Error -> return "读取失败：${result.message}"
        }
        return when (val result = noteRepository.softDeleteNote(noteId)) {
            is RepositoryResult.Success -> "已删除笔记「${existing.title}」(id=$noteId)，它已移入回收站，用户仍可恢复。"
            is RepositoryResult.Error -> "删除失败：${result.message}"
        }
    }

    // ---- 解析与格式化 ----

    private fun parseArgs(argumentsJson: String): JsonObject =
        (json.parseToJsonElement(argumentsJson) as? JsonObject) ?: JsonObject(emptyMap())

    /** 解析结果：块列表 + 被丢弃的图片块数量（用于如实反馈给模型，而不是假装成功）。 */
    private data class ParsedBlocks(val blocks: List<Block>, val droppedImages: Int)

    private data class ParsedCards(val cards: List<KnowledgeCard>, val droppedImages: Int)

    /**
     * 解析小节列表。
     *
     * 关键：**必须给块写上 `cardId`（卡片的临时 id）** —— `NoteRepositoryImpl.replaceBlocks`
     * 是靠 `cardMap[block.cardId]` 把块挂回新建卡片的。漏了这一步，所有块都会以 `cardId = null`
     * 落库，再读出来就被 `hydrate` 当成孤儿块合并进第一张卡片，小节结构静默丢失。
     * `NoteUiState.toNote()` 是同样的写法。
     */
    private suspend fun parseCards(arr: JsonArray): ParsedCards {
        val cards = mutableListOf<KnowledgeCard>()
        var dropped = 0
        arr.forEachIndexed { index, element ->
            val obj = element as? JsonObject ?: return@forEachIndexed
            // 标题是纯文本，不渲染任何语法：模型把 {{k:…}} 写进标题只会原样显示成一堆花括号，
            // 所以这里先剥掉（正文才走 normalize 保留标记）。
            val title = obj.optString("title")?.let(InlineMarkup::stripMarkup)
                ?.trim().orEmpty().ifEmpty { "小节 ${index + 1}" }
            val accent = CardAccent.fromName(obj.optString("accent"))?.argb
            val parsed = obj.optArray("blocks")?.let { parseBlocks(it) } ?: ParsedBlocks(emptyList(), 0)
            dropped += parsed.droppedImages
            cards += buildCard(tempId = -(index + 1L), title = title, blocks = parsed.blocks, accent = accent)
        }
        return ParsedCards(cards, dropped)
    }

    /** 建一张带临时 id 的卡片，并把块的 cardId 指向它（见 [parseCards] 的说明）。 */
    private fun buildCard(tempId: Long, title: String, blocks: List<Block>, accent: Int? = null): KnowledgeCard =
        KnowledgeCard(
            id = tempId,
            title = title,
            blocks = blocks.map { it.copy(cardId = tempId) },
            accent = accent
        )

    private suspend fun parseBlocks(arr: JsonArray, tempIds: TempIds = TempIds()): ParsedBlocks {
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
                // 文本是"直接进来"的入口之一：先规整语法，再落库。
                // 模型写坏标记时最坏只损失标记，正文一字不少。
                content = InlineMarkupNormalizer.normalize(rawContent)
            }
            // 新块拿一个**唯一**的负临时 id：分支子块要用它当 parentBranchId，
            // 仓储层再把它重映射成真实 id（NoteRepositoryImpl.replaceBlocks 的 idMapping）。
            // 绝不能都留默认的 0 —— 那样多个分支的子块会全部挂到同一个父块下。
            val blockId = tempIds.next()
            blocks += Block(
                id = blockId,
                type = type,
                content = content,
                language = obj.optString("language") ?: "",
                sortOrder = blocks.size,
                emphasis = parseEmphasis(obj.optString("emphasis"))
            )

            // 分支子块：解析后接在父块后面，并把 parentBranchId 指向父块的临时 id
            if (type == BlockType.BRANCH) {
                val children = obj.optArray("children") ?: continue
                val parsedChildren = parseBlocks(children, tempIds)
                dropped += parsedChildren.droppedImages
                blocks += parsedChildren.blocks.mapIndexed { index, child ->
                    child.copy(parentBranchId = blockId, sortOrder = blocks.size + index)
                }
            }
        }
        return ParsedBlocks(blocks, dropped)
    }

    /**
     * 新块的临时 id 发号器。
     *
     * 递减负数，与 `NoteViewModel` 的 `nextBlockId--` 同一约定；
     * 只要保证唯一即可，真实 id 由仓储层插入后回填。
     */
    private class TempIds {
        private var next = -1L
        fun next(): Long = next--
    }

    private fun parseEmphasis(raw: String?): EmphasisTone? {
        val name = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "none" } ?: return null
        return EmphasisTone.fromName(name)
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

    /**
     * 把笔记渲染成模型可读、可回写的文本。
     *
     * 三件事与重构后的数据模型对齐：
     * - **小节结构**要显式给出，否则模型不知道内容分组，回写时会把结构压平；
     * - **块 id** 要给出，`set_block_emphasis` 才有目标；同时说明"任何写入都会重建 id"；
     * - **块级标记**用〔要点〕这类标记回显，行内标记留在正文里（模型据此知道哪些是关键）。
     */
    private fun formatNote(note: Note): String {
        val body = if (note.cards.isNotEmpty()) {
            note.cards.mapIndexed { index, card -> formatCard(index, card) }.joinToString("\n\n")
        } else {
            // 必须用 contentBlocks：有知识卡片的笔记，块挂在卡片下，note.blocks 是空的。
            note.contentBlocks.joinToString("\n\n") { formatBlock(it) }
        }
        val tags = if (note.tags.isEmpty()) "" else "标签：" + note.tags.joinToString("、") { it.name }
        return buildString {
            append("标题：").append(note.title).append('\n')
            if (tags.isNotEmpty()) append(tags).append('\n')
            append('\n')
            append(if (body.isBlank()) "（空笔记）" else body)
        }
    }

    private fun formatCard(index: Int, card: KnowledgeCard): String = buildString {
        append("■ 小节 ").append(index + 1).append("：").append(card.title.ifBlank { "（未命名）" })
        CardAccent.fromArgb(card.accent ?: -1)?.let { append("（身份色 ").append(it.label).append("）") }
        // 必须显式表达层级：卡片下的块是**扁平**存的，子块靠 parentBranchId 指回父分支。
        // 不缩进的话模型会以为它们与父块并列，进而用错 update_note / 追加到错误的位置。
        val printed = mutableSetOf<Long>()
        fun emit(block: Block, indent: String) {
            append('\n').append(indent).append(formatBlock(block))
            printed += block.id
        }
        card.blocks.filter { it.parentBranchId == null }.forEach { block ->
            emit(block, "  ")
            card.blocks.filter { it.parentBranchId == block.id }.forEach { child ->
                emit(child, "    └ ")
            }
        }
        // 兜底：父块缺失的孤儿子块也要让模型看见，否则它会以为这些内容不存在
        card.blocks.filterNot { it.id in printed }.forEach { emit(it, "  ⚠ ") }
    }

    private fun formatBlock(block: Block): String {
        val head = buildString {
            append("[块 ").append(block.id).append(']')
            block.emphasis?.let { append('〔').append(it.label).append('〕') }
            append(' ')
        }
        val body = when (block.type) {
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
        return head + body
    }

    /** 摘要：先剥离行内语法再截断，否则标记字符会挤占这 60 个字的预算。 */
    private fun snippetOf(note: Note): String {
        val text = note.contentBlocks.firstOrNull { it.type == BlockType.TEXT }?.content
            ?.let(InlineMarkup::stripMarkup)
            ?.replace('\n', ' ')
            ?.trim()
            ?.take(60)
        return if (text.isNullOrBlank()) "" else "｜$text"
    }

    /**
     * 有图片块因 URI 不可读被丢弃时如实说明。
     * 静默丢弃会让模型继续声称「图片已写入」，用户却看不到图——必须让模型知道才好纠正。
     */
    private fun droppedImageHint(count: Int): String =
        if (count <= 0) "" else "（有 $count 个图片块因图片路径不可读被跳过，请勿声称这些图片已写入）"
}

private fun Block.withEmphasis(
    blockId: Long,
    tone: EmphasisTone?,
    onFound: () -> Unit
): Block = if (id == blockId) {
    onFound()
    copy(emphasis = tone)
} else {
    this
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

/**
 * 回传给模型的到期时间，人类可读优先；`null`（暂停 / 毕业）写成「无」。
 *
 * 用可读日期而不是时间戳：模型拿它组织语言（「明天下午」/「10 月 9 日」），
 * 不解析它；给毫秒数只会让模型把它原样念给用户听。
 * `SimpleDateFormat` 既非线程安全、又吃当前 locale，所以不缓存成常量而每次
 * 现建（lint `ConstantLocale` 说的也是这件事）；工具执行是单线程串行的，
 * 这点开销可以忽略。
 */
private fun describeDue(dueAt: Long?): String =
    dueAt?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) } ?: "无"

/** `schedule_review` 该对既有计划做什么。 */
internal enum class ReviewScheduleAction {
    /** 还没有计划，或已毕业 —— 从第 1 档重新开始。 */
    CreateOrRestart,

    /** 暂停中 —— 原地继续，**保留档位**。 */
    Resume,

    /** 正在跑 —— 什么都不做，只把下次复习时间告诉模型。 */
    AlreadyScheduled
}

/**
 * 判定 `schedule_review` 的动作（纯函数，单独可测）。
 *
 * 这条规则是「AI 不会把用户复习了半个月的进度抹回第 1 档」的唯一保障，
 * 所以从工具实现里拎出来钉住。
 *
 * 注意判定顺序：**先看 `enabled`，再看 `completedAt`**。毕业的计划是
 * `enabled = false` 且 `completedAt != null`，该走「重新开始」而不是「继续」。
 */
internal fun reviewScheduleAction(existing: ReviewPlan?): ReviewScheduleAction = when {
    existing == null -> ReviewScheduleAction.CreateOrRestart
    existing.enabled -> ReviewScheduleAction.AlreadyScheduled
    existing.completedAt != null -> ReviewScheduleAction.CreateOrRestart
    else -> ReviewScheduleAction.Resume
}

/**
 * `list_due_reviews` 的回传文本（纯函数，单独可测）。
 *
 * 分区由 [ReviewQueueClassifier] 给出（与复习中心同一套口径），这里只管措辞。
 * 一个计划都没有时给明确的空态，而不是一行全是 0 的统计。
 */
internal fun formatDueReviews(plans: List<ReviewPlanWithNote>, queue: ReviewQueue): String {
    if (plans.isEmpty()) return "还没有任何复习计划。"

    return buildString {
        append("复习计划：逾期 ${queue.overdue.size} 个、今天 ${queue.today.size} 个、")
        append("接下来 ${ReviewQueueClassifier.UPCOMING_DAYS} 天 ${queue.upcoming.size} 个")
        if (queue.later.isNotEmpty()) append("、更远 ${queue.later.size} 个")
        if (queue.paused.isNotEmpty()) append("、已暂停 ${queue.paused.size} 个")
        if (queue.completed.isNotEmpty()) append("、已完成 ${queue.completed.size} 个")
        append("。")
        appendSection("已逾期", queue.overdue)
        appendSection("今天", queue.today)
        appendSection("接下来", queue.upcoming)
        // 「更远」也要给明细：只报数量的话，用户问"是哪篇"时模型手里没有名字。
        // 复习中心的「接下来」是 upcoming + later 的合并区（远期计划不能"消失"），
        // 所以这里分得更细不是口径冲突，是"界面合并、工具分档"。
        appendSection("更远", queue.later)
    }
}

private fun StringBuilder.appendSection(title: String, items: List<ReviewPlanWithNote>) {
    if (items.isEmpty()) return
    append("\n").append(title).append("：")
    items.forEach { item ->
        append("\n  - [").append(item.plan.noteId).append("] ").append(item.noteTitle)
            .append("（第 ").append(item.plan.currentStep + 1).append(" 档，")
            .append(describeDue(item.plan.nextReviewAt)).append("）")
    }
}

/**
 * 取整数数组（`remindAt` 这种与文本按位置对应的参数）。
 *
 * 非数字项丢成 null 而不是整段作废 —— `remindAt` 是可选参数，模型漏填一格
 * 不该让旁边几条待办的提醒一起消失。
 */
private fun JsonObject.optLongArray(key: String): List<Long?> =
    (this[key] as? JsonArray)?.map { (it as? JsonPrimitive)?.content?.toLongOrNull() } ?: emptyList()
