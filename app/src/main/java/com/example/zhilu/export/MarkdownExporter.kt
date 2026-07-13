package com.example.zhilu.export

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.TodoItem

object MarkdownExporter {
    fun exportNote(
        note: Note,
        media: List<Media> = emptyList(),
        todoItems: List<TodoItem> = emptyList()
    ): String {
        val mediaById = media.associateBy { it.id.toString() }
        return buildString {
            append("# ").append(note.title.ifBlank { "Untitled" }.escapeMarkdownHeading()).append("\n\n")
            if (note.tags.isNotEmpty()) {
                append(note.tags.joinToString(" ") { "#${it.name.toMarkdownTag()}" }).append("\n\n")
            }
            note.blocks.sortedBy { it.sortOrder }.forEach { block ->
                appendBlock(block, mediaById, todoItems)
                append("\n\n")
            }
        }.trimEnd() + "\n"
    }

    fun exportNotes(
        notes: List<Note>,
        media: List<Media> = emptyList(),
        todosByNoteId: Map<Long, List<TodoItem>> = emptyMap()
    ): String =
        notes.joinToString(separator = "\n\n") { note ->
            exportNote(note, media, todosByNoteId[note.id].orEmpty()).trimEnd()
        } + "\n"

    private fun StringBuilder.appendBlock(
        block: Block,
        mediaById: Map<String, Media>,
        todoItems: List<TodoItem>
    ) {
        when (block.type) {
            BlockType.TEXT -> append(block.content)
            BlockType.IMAGE -> {
                val target = ImageBlockContent.resolveUri(block.content, mediaById)
                append("![](").append(target.escapeMarkdownUrl()).append(")")
            }
            BlockType.LINK -> append(linkMarkdown(block.content))
            BlockType.DIVIDER -> append("---")
            BlockType.LATEX -> append("$$\n").append(block.content.trim()).append("\n$$")
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
