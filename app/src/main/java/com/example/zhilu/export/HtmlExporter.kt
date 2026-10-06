package com.example.zhilu.export

import android.content.Context
import android.graphics.Color
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.KnowledgeCard
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
                // 公式图在这个导出里会被引用多次（行内公式 + 块级公式），按源码缓存，
                // 同一个 `2^n` 只渲染一次 —— 大公式渲染很贵，整篇重复渲染会明显变慢。
                val formulaCache = mutableMapOf<Pair<String, String>, String>()

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
                    appendSections(note, mediaById, formulaCache)
                    appendLine("</article>")
                    appendLine("</body>")
                    appendLine("</html>")
                }
            }
        }

    /**
     * 按**小节**输出，而不是把块揉成一条平铺流。
     *
     * 平铺的代价是导出物里小节标题整个消失 —— 阅读时看不出"这一段属于哪一节"，
     * 而 App 内恰恰是按小节组织的（默认收起时你看到的就是目录）。导出物要"大致一致"，
     * 起码得保住这一层结构。
     */
    private suspend fun StringBuilder.appendSections(
        note: Note,
        mediaById: Map<String, Media>,
        formulaCache: MutableMap<Pair<String, String>, String>
    ) {
        val cards = note.cards
        if (cards.isEmpty()) {
            appendBlocks(note.blocks.sortedBy { it.sortOrder }, mediaById, formulaCache)
            return
        }
        cards.forEachIndexed { index, card ->
            val accent = cardAccentHex(card, index)
            val accentTint = cardAccentTintHex(accent)
            val topLevel = card.blocks.filter { it.parentBranchId == null }.sortedBy { it.sortOrder }
            val count = topLevel.size
            appendLine("<section class=\"card\" style=\"--accent:$accent;--accent-tint:$accentTint\">")
            appendLine("<header class=\"card-head\">")
            appendLine("<span class=\"badge\">${(index + 1).toString().padStart(2, '0')}</span>")
            appendLine(
                "<h2>${escapeHtml(card.title.ifBlank { "未命名小节" })}</h2>"
            )
            if (count > 0) appendLine("<span class=\"points\">$count 点</span>")
            appendLine("</header>")
            appendLine("<div class=\"card-body\">")
            appendBlocks(topLevel, mediaById, formulaCache, card.blocks)
            appendLine("</div>")
            appendLine("</section>")
        }
    }

    private suspend fun StringBuilder.appendBlocks(
        blocks: List<Block>,
        mediaById: Map<String, Media>,
        formulaCache: MutableMap<Pair<String, String>, String>,
        allBlocks: List<Block> = blocks
    ) {
        blocks.forEach { block ->
            appendBlock(block, mediaById, formulaCache, allBlocks)
        }
    }

    private suspend fun StringBuilder.appendBlock(
        block: Block,
        mediaById: Map<String, Media>,
        formulaCache: MutableMap<Pair<String, String>, String>,
        allBlocks: List<Block>
    ) {
        when (block.type) {
            BlockType.TEXT -> {
                // 空行分段：`append`（不换行）保留段内单换行的 <br> 语义
                block.content.split("\n\n").forEach { paragraph ->
                    if (paragraph.isNotBlank()) {
                        appendLine("<p>${htmlInline(paragraph, formulaCache).replace("\n", "<br>")}</p>")
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
                val base64 = formulaImage(block.content.trim(), display = true, formulaCache).getOrNull()
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
                appendLine("<details open class=\"branch\">")
                appendLine("<summary>${htmlInline(block.content.ifBlank { "分支" }, formulaCache)}</summary>")
                val children = allBlocks
                    .filter { it.parentBranchId == block.id }
                    .sortedBy { it.sortOrder }
                if (children.isNotEmpty()) {
                    appendLine("<div class=\"branch-body\">")
                    appendBlocks(children, mediaById, formulaCache, allBlocks)
                    appendLine("</div>")
                }
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

    /**
     * 公式 → base64 PNG。
     *
     * [display] 决定渲染字号与配色：块级公式按 36sp 黑字（与之前一致），行内公式按 22sp
     * 并**跟随语义色**（整条公式被 `{{k:…}}` 包住时，App 内也是给它上色的）。
     */
    private suspend fun formulaImage(
        latex: String,
        display: Boolean,
        formulaCache: MutableMap<Pair<String, String>, String>,
        inkColor: String? = null
    ): Result<String> = runCatching {
        val key = latex to (inkColor ?: "")
        // 命中缓存就直接用，不再渲染 —— 同一个 `2^n` 在一篇里常出现多次
        formulaCache[key]?.let { return@runCatching it }
        val sanitized = sanitizeLatex(latex)
        val color = inkColor?.let { Color.parseColor(it) } ?: Color.BLACK
        val bitmap = renderLatexBitmap(
            sanitized,
            textSize = if (display) 36f else 22f,
            color = color
        ).getOrThrow()
        mediaFileManager.bitmapToBase64Png(bitmap).also { formulaCache[key] = it }
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
     * 行内正文 → HTML。
     *
     * 分流顺序固定为 token 流给出的顺序：公式 → 图片，代码 → `<code>`，链接 → `<a>`，
     * 标记段 → 带配色的 `<span>`。**之前这里只有"转义 + span"两条分支**，
     * 于是 `$n$` 这类行内公式被当成普通文字原样吐出去 —— README 里写的
     * "LaTeX 公式渲染为图片"只在块级公式上成立。
     */
    private suspend fun htmlInline(
        content: String,
        formulaCache: MutableMap<Pair<String, String>, String>
    ): String = buildString {
        inlineExportTokens(content).forEach { token ->
            when (token) {
                is InlineExportToken.Plain -> append(escapeHtml(token.text))
                is InlineExportToken.Code -> append("<code>").append(escapeHtml(token.text)).append("</code>")
                is InlineExportToken.Link -> append("<a href=\"")
                    .append(escapeHtml(token.url))
                    .append("\" target=\"_blank\">")
                    .append(escapeHtml(token.text))
                    .append("</a>")
                is InlineExportToken.Styled -> append("<span style=\"")
                    .append(InlineMarkupExportStyle.styleOf(token.tone, token.brush))
                    .append("\">")
                    .append(escapeHtml(token.text))
                    .append("</span>")
                is InlineExportToken.Formula -> {
                    val ink = token.tone?.let { InlineMarkupExportStyle.inkColor(it) }
                    val base64 = formulaImage(token.latex, token.display, formulaCache, ink).getOrNull()
                    if (base64 == null) {
                        // 渲染失败就退回源码：给人看的文件里，"看得见公式写的是什么"比留白强
                        append("<code>").append(escapeHtml(token.latex)).append("</code>")
                    } else {
                        append("<img class=\"formula\" src=\"").append(base64)
                            .append("\" alt=\"").append(escapeHtml(token.latex)).append("\">")
                    }
                }
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
        /**
         * 配色与排版对齐 App 内的"纸墨"观感：卡片小节 = 带身份色竖条的浅色卡片，
         * 编号徽标用该节的身份色，正文行高 1.8。
         */
        private val ACCENTS = listOf(
            "#8C6A3F", "#4B6B4F", "#8A4550", "#3D6E6B", "#5E5A8C", "#B23B32", "#3F6478"
        )

        private fun cardAccentHex(card: KnowledgeCard, index: Int): String {
            val stored = card.accent
            // `Color.parseColor` 返回的是**带 alpha 的 ARGB**（即使输入是 6 位 hex，alpha 也是 FF）。
            // 直接用 `%06X` 打印会把 alpha 也算进去 → `#FF8C6A3F`，那是 8 位 hex，
            // 浏览器当非法值丢掉，整节的强调色就没了。必须先掩掉 alpha。
            val argb = stored ?: Color.parseColor(ACCENTS[index % ACCENTS.size])
            return String.format("#%06X", 0xFFFFFF and argb)
        }

        /**
         * 徽标底色 = 身份色 12% 混白。
         *
         * 不用 CSS `color-mix()`：它要 Chrome 111+，而导出的文件是要发出去的，
         * 别人的浏览器/WebView 版本不可控 —— 算好一个纯 hex 最稳。
         */
        private fun cardAccentTintHex(hex: String): String {
            // parseColor 结果带 alpha，但这里只取 R/G/B，所以不受影响
            val rgb = Color.parseColor(hex)
            val r = (Color.red(rgb) * 0.12f + 255 * 0.88f).toInt()
            val g = (Color.green(rgb) * 0.12f + 255 * 0.88f).toInt()
            val b = (Color.blue(rgb) * 0.12f + 255 * 0.88f).toInt()
            return String.format("#%02X%02X%02X", r, g, b)
        }

        private const val STYLE = """
            :root { color-scheme: light; }
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", Roboto, sans-serif; background: #f3f1ec; margin: 0; padding: 16px; color: #23201c; }
            article { max-width: 760px; margin: 0 auto; background: #fffdf9; padding: 24px; border-radius: 14px; box-shadow: 0 1px 3px rgba(0,0,0,0.07); }
            h1 { font-size: 26px; margin: 0 0 12px; letter-spacing: 0.5px; }
            .tags { margin-bottom: 20px; }
            .tag { display: inline-block; background: #ece7dd; color: #6b5b45; padding: 2px 10px; border-radius: 12px; font-size: 12px; margin: 0 6px 6px 0; }
            p { line-height: 1.8; margin: 10px 0; }
            img { max-width: 100%; border-radius: 8px; display: block; margin: 12px 0; }
            img.formula { display: inline-block; margin: 2px 2px; vertical-align: middle; border-radius: 0; }
            pre { background: #2B2118; color: #E8D5BC; padding: 14px 16px; border-radius: 12px; border: 1px solid #3D3028; overflow-x: auto; }
            code { font-family: "SFMono-Regular", Consolas, monospace; font-size: 0.92em; background: #f1eee8; padding: 1px 5px; border-radius: 4px; }
            pre code { background: none; padding: 0; color: inherit; }
            hr { border: none; border-top: 1px solid #e6e1d8; margin: 18px 0; }
            .latex { text-align: center; margin: 14px 0; }
            .latex img { display: inline-block; }
            .todo-list { list-style: none; padding: 0; }
            .todo-list li { margin: 6px 0; line-height: 1.7; }
            .checkbox { margin-right: 6px; }
            .placeholder { color: #9ca3af; font-style: italic; }
            .card { border: 1px solid #eae5dc; border-left: 3px solid var(--accent); border-radius: 10px; padding: 14px 16px; margin: 14px 0; background: #fffefb; }
            .card-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
            .card-head h2 { font-size: 18px; margin: 0; flex: 1 1 auto; }
            .badge { font-family: "SFMono-Regular", Consolas, monospace; font-size: 13px; font-weight: 600; color: var(--accent); background: var(--accent-tint); border-radius: 7px; padding: 4px 8px; }
            .points { font-size: 12px; color: #8a8175; background: #f3f0ea; border-radius: 10px; padding: 2px 8px; }
            .card-body { margin-top: 10px; }
            details.branch { border: 1px solid #eae5dc; border-radius: 8px; padding: 10px 12px; margin: 10px 0; background: #fdfcf9; }
            summary { font-weight: 600; cursor: pointer; line-height: 1.6; }
            .branch-body { margin-top: 8px; padding-left: 10px; border-left: 2px solid #eee7dc; }
        """
    }
}
