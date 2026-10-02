package com.example.zhilu.export

import android.content.Context
import android.graphics.Color
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.note.latex.renderLatexBitmap
import com.example.zhilu.ui.note.latex.sanitizeLatex
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HtmlExporter(
    private val context: Context,
    private val mediaFileManager: MediaFileManager
) {

    suspend fun exportNote(note: Note, media: List<Media> = emptyList()): Result<String> =
        withContext(Dispatchers.Default) {
            runCatching {
                val mediaById = media.associateBy { it.id.toString() }
                buildString {
                    appendLine("<!DOCTYPE html>")
                    appendLine("<html lang=\"zh-CN\">")
                    appendLine("<head>")
                    appendLine("<meta charset=\"UTF-8\">")
                    appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                    appendLine("<title>${escapeHtml(note.title.ifBlank { "知识分享" })}</title>")
                    appendLine("<style>")
                    appendLine(STYLE)
                    appendLine("</style>")
                    appendLine("</head>")
                    appendLine("<body>")
                    appendLine("<article>")
                    appendLine("<h1>${escapeHtml(note.title.ifBlank { "知识分享" })}</h1>")
                    if (note.tags.isNotEmpty()) {
                        appendLine("<div class=\"tags\">")
                        note.tags.forEach { tag ->
                            appendLine("<span class=\"tag\">${escapeHtml("#${tag.name}")}</span>")
                        }
                        appendLine("</div>")
                    }
                    val blocks = note.cards.takeIf { it.isNotEmpty() }
                        ?.flatMap { it.blocks }
                        ?.sortedBy { it.sortOrder }
                        ?: note.blocks.sortedBy { it.sortOrder }
                    blocks.forEach { block ->
                        appendBlock(block, mediaById)
                    }
                    appendLine("</article>")
                    appendLine("</body>")
                    appendLine("</html>")
                }
            }
        }

    private suspend fun StringBuilder.appendBlock(block: Block, mediaById: Map<String, Media>) {
        when (block.type) {
            BlockType.TEXT -> {
                val paragraphs = block.content.split("\n\n")
                paragraphs.forEach { paragraph ->
                    if (paragraph.isNotBlank()) {
                        appendLine("<p>${htmlInline(paragraph).replace("\n", "<br>")}</p>")
                    }
                }
            }
            BlockType.IMAGE -> {
                val base64 = imageToBase64(block, mediaById).getOrNull()
                if (base64 != null) {
                    appendLine("<img src=\"$base64\" alt=\"图片\">")
                } else {
                    appendLine("<p class=\"placeholder\">[图片不可读]</p>")
                }
            }
            BlockType.LINK -> {
                val parts = block.content.split("|", limit = 2)
                val title = parts.firstOrNull().orEmpty().ifBlank { parts.getOrNull(1).orEmpty() }
                val url = parts.getOrNull(1)?.ifBlank { title } ?: title
                appendLine("<p><a href=\"${escapeHtml(url)}\" target=\"_blank\">${escapeHtml(title.ifBlank { url })}</a></p>")
            }
            BlockType.DIVIDER -> appendLine("<hr>")
            BlockType.LATEX -> {
                val base64 = latexToBase64(block.content).getOrNull()
                if (base64 != null) {
                    appendLine("<div class=\"latex\"><img src=\"$base64\" alt=\"公式\"></div>")
                } else {
                    appendLine("<pre class=\"latex-fallback\">${escapeHtml(block.content)}</pre>")
                }
            }
            BlockType.CODE -> {
                val language = block.language.ifBlank { "text" }
                appendLine("<pre><code class=\"language-$language\">${escapeHtml(block.content.trimEnd())}</code></pre>")
            }
            BlockType.TODO -> {
                val items = parseTodoItems(block.content)
                appendLine("<ul class=\"todo-list\">")
                items.forEach { (checked, text) ->
                    val clazz = if (checked) "todo-checked" else "todo-unchecked"
                    appendLine("<li class=\"$clazz\"><span class=\"checkbox\">${if (checked) "☑" else "☐"}</span> ${escapeHtml(text)}</li>")
                }
                appendLine("</ul>")
            }
            BlockType.BRANCH -> {
                appendLine("<details open>")
                appendLine("<summary>${if (block.content.isBlank()) "分支" else htmlInline(block.content)}</summary>")
                appendLine("</details>")
            }
        }
    }

    private fun imageToBase64(block: Block, mediaById: Map<String, Media>): Result<String> = runCatching {
        val media = ImageBlockContent.resolveMedia(block.content, mediaById)
            ?: throw IllegalArgumentException("未找到媒体")
        val cacheDir = File(context.cacheDir, "export_images").apply { mkdirs() }
        val copied = mediaFileManager.copyToCache(media, cacheDir).getOrThrow()
        mediaFileManager.toBase64(copied).getOrThrow()
    }

    private suspend fun latexToBase64(latex: String): Result<String> = runCatching {
        val sanitized = sanitizeLatex(latex)
        val bitmap = renderLatexBitmap(sanitized, textSize = 36f, color = Color.BLACK).getOrThrow()
        mediaFileManager.bitmapToBase64Png(bitmap)
    }

    private fun parseTodoItems(content: String): List<Pair<Boolean, String>> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()
        return trimmed.lines().map { line ->
            val text = line.trim()
            when {
                text.startsWith("- [x] ", ignoreCase = true) -> true to text.removePrefix("- [x] ")
                text.startsWith("[x] ", ignoreCase = true) -> true to text.removePrefix("[x] ")
                text.startsWith("- [ ] ") -> false to text.removePrefix("- [ ] ")
                text.startsWith("[ ] ") -> false to text.removePrefix("[ ] ")
                else -> false to text
            }
        }
    }

    /**
     * 行内标记 → HTML。文本照常转义，标记转成带配色的 `<span>`。
     */
    private fun htmlInline(content: String): String = buildString {
        inlineMarkupSegments(content).forEach { segment ->
            when (segment) {
                is InlineMarkupSegment.Plain -> append(escapeHtml(segment.text))
                is InlineMarkupSegment.Marked -> append("<span style=\"")
                    .append(InlineMarkupExportStyle.styleOf(segment.tone, segment.brush))
                    .append("\">")
                    .append(escapeHtml(segment.text))
                    .append("</span>")
            }
        }
    }

    private fun escapeHtml(input: String): String = buildString {
        input.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

    companion object {
        private const val STYLE = """
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f5f7fa; margin: 0; padding: 16px; color: #1f1f1f; }
            article { max-width: 720px; margin: 0 auto; background: #fff; padding: 24px; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
            h1 { font-size: 24px; margin: 0 0 12px; }
            .tags { margin-bottom: 16px; }
            .tag { display: inline-block; background: #eef2ff; color: #4f46e5; padding: 2px 8px; border-radius: 12px; font-size: 12px; margin-right: 6px; }
            p { line-height: 1.7; margin: 8px 0; }
            img { max-width: 100%; border-radius: 8px; display: block; margin: 12px 0; }
            pre { background: #f4f4f5; padding: 12px; border-radius: 8px; overflow-x: auto; }
            code { font-family: "SFMono-Regular", Consolas, monospace; font-size: 14px; }
            hr { border: none; border-top: 1px solid #e5e7eb; margin: 16px 0; }
            .latex { text-align: center; margin: 12px 0; }
            .latex img { display: inline-block; }
            .todo-list { list-style: none; padding: 0; }
            .todo-list li { margin: 6px 0; }
            .checkbox { margin-right: 6px; }
            .placeholder { color: #9ca3af; font-style: italic; }
            details { border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px; margin: 12px 0; }
            summary { font-weight: 600; cursor: pointer; }
        """
    }
}
