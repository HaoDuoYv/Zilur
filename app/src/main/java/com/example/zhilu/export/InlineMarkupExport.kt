package com.example.zhilu.export

import com.example.zhilu.domain.markup.InlineBrush
import com.example.zhilu.domain.markup.InlineMarkup
import com.example.zhilu.domain.model.EmphasisTone

/**
 * 导出侧的行内标记处理。
 *
 * 语法活在 `Block.content` 里，所以各导出格式**天然承载**它 —— 但直接把 `{{k:文字}}`
 * 写进 Markdown / HTML 会露出语法字符。这里把正文切成"普通段 / 被标记段"，
 * 由各导出器自己决定怎么转义与包装。
 *
 * 注意：分段只对**不含换行的**文本有效（内联标记本来就不跨行），
 * 因此按 `\n\n` 拆段落之后再分段是安全的。
 */
internal sealed interface InlineMarkupSegment {
    val text: String

    data class Plain(override val text: String) : InlineMarkupSegment

    data class Marked(
        override val text: String,
        val tone: EmphasisTone,
        val brush: InlineBrush
    ) : InlineMarkupSegment
}

internal fun inlineMarkupSegments(content: String): List<InlineMarkupSegment> {
    val parsed = InlineMarkup.parseSpans(content)
    if (parsed.spans.isEmpty()) return listOf(InlineMarkupSegment.Plain(parsed.visibleText))

    val segments = mutableListOf<InlineMarkupSegment>()
    var cursor = 0
    for (span in parsed.spans) {
        if (span.start > cursor) {
            segments += InlineMarkupSegment.Plain(parsed.visibleText.substring(cursor, span.start))
        }
        segments += InlineMarkupSegment.Marked(
            text = parsed.visibleText.substring(span.start, span.end),
            tone = span.tone,
            brush = span.brush
        )
        cursor = span.end
    }
    if (cursor < parsed.visibleText.length) {
        segments += InlineMarkupSegment.Plain(parsed.visibleText.substring(cursor))
    }
    return segments
}

/**
 * 导出用的配色：固定取**浅色主题**那套。
 *
 * 导出物是给别人看的静态文件，不该跟随用户的深色模式 —— 否则同一篇笔记白天导出和
 * 晚上导出的颜色不一样。取值与 `ui/theme/EmphasisTones.kt` 一致（语义色 = 主题色板的
 * primary，行内用加深墨色）。
 */
internal object InlineMarkupExportStyle {

    /** 语义角色原色，用于底色与下划线。 */
    fun toneColor(tone: EmphasisTone): String = when (tone) {
        EmphasisTone.KEY -> "#7C5C31"
        EmphasisTone.IDEA -> "#4B6B4F"
        EmphasisTone.WARN -> "#8A4550"
        EmphasisTone.TODO -> "#3D6E6B"
    }

    /** 行内加深墨色，用于文字色。 */
    fun inkColor(tone: EmphasisTone): String = when (tone) {
        EmphasisTone.KEY -> "#5E431F"
        EmphasisTone.IDEA -> "#33513A"
        EmphasisTone.WARN -> "#6B2C38"
        EmphasisTone.TODO -> "#275250"
    }

    /**
     * 内联 span 的 style 串（不含引号）。
     *
     * 底色/下划线用 8 位 hex 的 alpha 形式：`#RRGGBB21` ≈ 13% 不透明度，
     * 与 App 内的 20% 底色观感接近，又保证在白色文档背景上不会糊。
     */
    fun styleOf(tone: EmphasisTone, brush: InlineBrush): String {
        val ink = inkColor(tone)
        val raw = toneColor(tone)
        return when (brush) {
            InlineBrush.HIGHLIGHT -> "color:$ink;background:${raw}21"
            InlineBrush.COLOR -> "color:$ink"
            InlineBrush.UNDERLINE -> "color:$ink;border-bottom:2px solid ${raw}80"
            // 加粗不带语义色（它不分类内容），导出端也不该凭空给它上色
            InlineBrush.BOLD -> "font-weight:600"
        }
    }
}
