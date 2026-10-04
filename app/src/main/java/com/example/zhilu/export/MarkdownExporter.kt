package com.example.zhilu.export

import android.content.Context
import android.graphics.Color
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.note.latex.renderLatexBitmap
import com.example.zhilu.ui.note.latex.sanitizeLatex
import java.io.File

object MarkdownExporter {
    fun exportNote(
        note: Note,
        media: List<Media> = emptyList(),
        todoItems: List<TodoItem> = emptyList()
    ): String = exportNoteInternal(note, media, todoItems, imageResolver = null, latexResolver = null)

    fun exportNotes(
        notes: List<Note>,
        media: List<Media> = emptyList(),
        todosByNoteId: Map<Long, List<TodoItem>> = emptyMap()
    ): String =
        notes.joinToString(separator = "\n\n") { note ->
            exportNoteInternal(note, media, todosByNoteId[note.id].orEmpty(), imageResolver = null, latexResolver = null).trimEnd()
        } + "\n"

    suspend fun exportNoteWithBase64(
        note: Note,
        media: List<Media> = emptyList(),
        todoItems: List<TodoItem> = emptyList(),
        mediaFileManager: MediaFileManager,
        context: Context
    ): String {
        val mediaById = media.associateBy { it.id.toString() }
        return exportNoteInternal(
            note = note,
            media = media,
            todoItems = todoItems,
            imageResolver = { block ->
                runCatching {
                    val targetMedia = ImageBlockContent.resolveMedia(block.content, mediaById)
                        ?: throw IllegalArgumentException("未找到媒体")
                    val cacheDir = File(context.cacheDir, "export_md_images").apply { mkdirs() }
                    val copied = mediaFileManager.copyToCache(targetMedia, cacheDir).getOrThrow()
                    mediaFileManager.toBase64(copied).getOrThrow()
                }.getOrNull()
            },
            latexResolver = { latex ->
                runCatching {
                    val sanitized = sanitizeLatex(latex)
                    val bitmap = renderLatexBitmap(sanitized, 36f, Color.BLACK).getOrThrow()
                    mediaFileManager.bitmapToBase64Png(bitmap)
                }.getOrNull()
            }
        )
    }

    private fun exportNoteInternal(
        note: Note,
        media: List<Media>,
        todoItems: List<TodoItem>,
        imageResolver: ((Block) -> String?)? = null,
        latexResolver: ((String) -> String?)? = null
    ): String {
        val mediaById = media.associateBy { it.id.toString() }
        return buildString {
            append("# ").append(note.title.ifBlank { "Untitled" }.escapeMarkdownHeading()).append("\n\n")
            if (note.tags.isNotEmpty()) {
                append(note.tags.joinToString(" ") { "#${it.name.toMarkdownTag()}" }).append("\n\n")
            }
            appendSections(note, mediaById, todoItems, imageResolver, latexResolver)
        }.trimEnd() + "\n"
    }

    /**
     * 按**小节**输出。有多个小节时，小节标题落成 `##` 二级标题。
     *
     * 原先这里把所有小节的块 `flatMap` 成一条平铺流，于是小节标题在 Markdown 里**整个消失** ——
     * 导出物读起来不知道哪一段属于哪一节。只有一个隐式小节（扁平笔记）时不加标题，
     * 那本来就没有分节，加一行 `## 未命名小节` 只会是噪声。
     */
    private fun StringBuilder.appendSections(
        note: Note,
        mediaById: Map<String, Media>,
        todoItems: List<TodoItem>,
        imageResolver: ((Block) -> String?)?,
        latexResolver: ((String) -> String?)?
    ) {
        val cards = note.cards
        if (cards.isEmpty()) {
            note.blocks.sortedBy { it.sortOrder }.forEach { block ->
                appendBlock(block, mediaById, todoItems, imageResolver, latexResolver, note.blocks)
                append("\n\n")
            }
            return
        }
        val showTitles = cards.size > 1
        cards.forEach { card ->
            if (showTitles) {
                append("## ")
                    .append(card.title.ifBlank { "未命名小节" }.escapeMarkdownHeading())
                    .append("\n\n")
            }
            val topLevel = card.blocks.filter { it.parentBranchId == null }.sortedBy { it.sortOrder }
            topLevel.forEach { block ->
                appendBlock(block, mediaById, todoItems, imageResolver, latexResolver, card.blocks)
                append("\n\n")
            }
        }
    }

    private fun StringBuilder.appendBlock(
        block: Block,
        mediaById: Map<String, Media>,
        todoItems: List<TodoItem>,
        imageResolver: ((Block) -> String?)?,
        latexResolver: ((String) -> String?)?,
        allBlocks: List<Block>
    ) {
        when (block.type) {
            BlockType.TEXT -> append(markdownInline(block.content))
            BlockType.IMAGE -> {
                val base64 = imageResolver?.invoke(block)
                val target = base64 ?: ImageBlockContent.resolveUri(block.content, mediaById)
                append("![](").append(target.escapeMarkdownUrl()).append(")")
            }
            BlockType.LINK -> append(linkMarkdown(block.content))
            BlockType.DIVIDER -> append("---")
            BlockType.LATEX -> {
                val base64 = latexResolver?.invoke(block.content.trim())
                if (base64 != null) {
                    append("![formula](").append(base64.escapeMarkdownUrl()).append(")")
                } else {
                    append("$$\n").append(block.content.trim()).append("\n$$")
                }
            }
            BlockType.CODE -> {
                val language = block.language.ifBlank { "text" }
                append("```").append(language).append("\n").append(block.content.trimEnd()).append("\n```")
            }
            BlockType.TODO -> append(todoMarkdown(block.content, todoItems))
            BlockType.BRANCH -> append(branchMarkdown(block, mediaById, todoItems, imageResolver, latexResolver, allBlocks))
        }
    }

    /**
     * 分支块 → 引用式折叠小节。
     *
     * 原先分支只写出一行标题、**子块整个丢掉** —— 导出的 Markdown 里分支内容是凭空消失的。
     * 现在用 `<details>`（GitHub / Typora / VS Code 都认）包住子块，与 App 内可折叠的观感一致。
     */
    private fun branchMarkdown(
        block: Block,
        mediaById: Map<String, Media>,
        todoItems: List<TodoItem>,
        imageResolver: ((Block) -> String?)?,
        latexResolver: ((String) -> String?)?,
        allBlocks: List<Block>
    ): String {
        val title = markdownInline(block.content).ifBlank { "分支" }
        val children = allBlocks.filter { it.parentBranchId == block.id }.sortedBy { it.sortOrder }
        if (children.isEmpty()) return title
        val body = buildString {
            children.forEachIndexed { index, child ->
                if (index > 0) append("\n\n")
                appendBlock(child, mediaById, todoItems, imageResolver, latexResolver, allBlocks)
            }
        }.trim()
        return "<details>\n<summary>$title</summary>\n\n$body\n\n</details>"
    }

    /**
     * 行内正文 → Markdown。
     *
     * **不再往 `.md` 里写 `<span style="color:…">`**（真机反馈："md 有异常的标签"）。
     * 原因有两层：
     * ① Markdown 规范里没有颜色，`<span>` 是内联 HTML，粘到聊天/邮件/不支持 HTML 的渲染器里
     *    会**原样露出标签**；
     * ② 原先的写法对**公式有害** —— `{{k:$W \le 2^{n-1}$}}` 会被包成
     *    `<span …> $W \le 2^{n-1}$</span>`，很多渲染器因此不再把里面的 `$…$` 当公式解析。
     *
     * 现在的规则简单得多：**语义标记只剥壳、留文字**（着色信息只保留在 HTML 导出里，
     * 那边是真的能上色）；公式走标准 `$…$` / `$$…$$`；行内代码走反引号；链接走 `[]()`。
     * 代价是 Markdown 里看不出强调色 —— 这是格式本身的限制，不是可以绕过去的 bug。
     */
    private fun markdownInline(content: String): String = buildString {
        inlineExportTokens(content).forEach { token ->
            when (token) {
                is InlineExportToken.Plain -> append(token.text)
                is InlineExportToken.Styled -> append(token.text)
                is InlineExportToken.Code -> append('`').append(token.text).append('`')
                is InlineExportToken.Link ->
                    append('[').append(token.text.escapeMarkdownText()).append("](")
                        .append(token.url.escapeMarkdownUrl()).append(')')
                is InlineExportToken.Formula -> {
                    val source = token.latex
                    if (token.display) {
                        append("$$\n").append(source).append("\n$$")
                    } else {
                        append('$').append(source).append('$')
                    }
                }
            }
        }
    }

    private fun todoMarkdown(content: String, todoItems: List<TodoItem>): String {
        if (todoItems.isNotEmpty()) {
            return todoItems.joinToString("\n") { todo ->
                val checkbox = if (todo.isCompleted) "[x]" else "[ ]"
                "- $checkbox ${todo.content.trim()}"
            }
        }
        val trimmed = content.trim()
        val completedPrefixes = listOf("[x]", "[X]", "- [x]", "- [X]")
        val incompletePrefixes = listOf("[ ]", "- [ ]")
        val completed = completedPrefixes.firstOrNull { trimmed.startsWith(it) }
        val incomplete = incompletePrefixes.firstOrNull { trimmed.startsWith(it) }
        return when {
            completed != null -> "- [x] ${trimmed.removePrefix(completed).trim()}"
            incomplete != null -> "- [ ] ${trimmed.removePrefix(incomplete).trim()}"
            else -> "- [ ] $trimmed"
        }
    }

    private fun linkMarkdown(content: String): String {
        val parts = content.split("|", limit = 2)
        val title = parts.first().ifBlank { parts.getOrNull(1).orEmpty() }
        val url = parts.getOrNull(1)?.ifBlank { title } ?: title
        return "[${title.escapeMarkdownText()}](${url.escapeMarkdownUrl()})"
    }

    private fun String.escapeMarkdownHeading(): String = replace("#", "\\#").trim()

    private fun String.escapeMarkdownText(): String =
        replace("\\", "\\\\")
            .replace("[", "\\[")
            .replace("]", "\\]")

    private fun String.escapeMarkdownUrl(): String = replace(" ", "%20").replace(")", "%29")

    private fun String.toMarkdownTag(): String =
        trim().replace(Regex("\\s+"), "-").replace(Regex("[^A-Za-z0-9_\\-\\u4e00-\\u9fa5]"), "")
}
