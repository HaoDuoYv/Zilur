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
            val blocks = note.cards.takeIf { it.isNotEmpty() }
                ?.flatMap { it.blocks }
                ?.sortedBy { it.sortOrder }
                ?: note.blocks.sortedBy { it.sortOrder }
            blocks.forEach { block ->
                appendBlock(block, mediaById, todoItems, imageResolver, latexResolver)
                append("\n\n")
            }
        }.trimEnd() + "\n"
    }

    private fun StringBuilder.appendBlock(
        block: Block,
        mediaById: Map<String, Media>,
        todoItems: List<TodoItem>,
        imageResolver: ((Block) -> String?)?,
        latexResolver: ((String) -> String?)?
    ) {
        when (block.type) {
            BlockType.TEXT -> append(block.content)
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
            BlockType.BRANCH -> append(block.content)
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
