package com.example.zhilu.domain.markup

import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.domain.markup.InlineMarkup.isMarkupEscapeAt

/**
 * 语法规整：把"文本直接进来"的入口产出的破损语法修好。
 *
 * **为什么还需要它**：编辑器走的是"标记不进编辑缓冲"（设计文档 §3.7），
 * 用户在编辑框里根本写不出坏语法。但还有两个入口是文本直接落库的：
 * 系统剪贴板粘贴、AI 工具写入（`create_note` / `update_note`）。
 * 这两个入口必须过一遍本文件。
 *
 * **与解析容错的分工**：解析器负责"读得安全"（非法写法原样显示），
 * normalize 负责"写得规整"（把破损语法收干净）。
 *
 * **唯一验收标准：最坏情况是标记掉了、文字还在。**
 * 因此本文件对"可能属于用户内容的字符"一律保守——只丢弃能够确定是破损标记残留的部分
 * （`{{` + 角色前缀，及其同一行内紧随的顶层 `}}`），孤立出现的 `}}` 会被转义保留，
 * 而不是被当成垃圾吃掉。
 */
object InlineMarkupNormalizer {

    /** `{{` 后面长得像"角色前缀"（字母 + 可选 `-x` + `:`）就算一次标记意图。 */
    private val ROLE_PREFIX = Regex("\\{\\{[A-Za-z](-[A-Za-z])?:")

    /** 跨行标记：内容里带换行的（宽口径，允许换行、不含花括号）。 */
    private val MULTILINE_MARKER = Regex("\\{\\{([kiwt])(-[cu])?:([^{}]+)\\}\\}")

    /**
     * 规整任意来源的正文，返回可以落库的存储形态。
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return text
        val split = splitMultilineMarkers(text)
        return repairAndMaterialize(split)
    }

    /**
     * 跨行标记拆成逐行标记：`{{k:abc\ndef}}` → `{{k:abc}}\n{{k:def}}`。
     *
     * 这比"只保留第一行"更贴近用户/AI 的原意，而且与编辑器产出的形态一致——
     * span 表本身就规定内联标记不跨行，两行都标了就会物化成两个标记。
     */
    private fun splitMultilineMarkers(text: String): String {
        if (!text.contains('\n')) return text
        var current = text
        // 一次替换可能只拆掉一层（剩余部分仍可能含换行再次命中），跑几轮直到稳定。
        repeat(MAX_SPLIT_ROUNDS) {
            var changed = false
            current = MULTILINE_MARKER.replace(current) { match ->
                val content = match.groupValues[3]
                if (!content.contains('\n')) {
                    match.value
                } else {
                    changed = true
                    val role = match.groupValues[1] + match.groupValues[2]
                    content.split('\n').joinToString("\n") { line ->
                        if (line.isEmpty()) line else "{{$role:$line}}"
                    }
                }
            }
            if (!changed) return current
        }
        return current
    }

    /**
     * 扫描一遍，顺手修掉破损标记，再物化成规范形态。
     */
    private fun repairAndMaterialize(text: String): String {
        val visible = StringBuilder(text.length)
        val spans = mutableListOf<InlineSpan>()
        var index = 0
        // 丢弃了 `{{` + 角色前缀之后，等待同一行内紧随的顶层 `}}` 一并丢掉
        // （`{{k:}}` 这种空内容标记的残留闭合）。
        var expectClose = false

        while (index < text.length) {
            val char = text[index]

            // 换行：跨行的期待作废，避免吃掉下一行的正常内容
            if (char == '\n') {
                expectClose = false
                visible.append(char)
                index++
                continue
            }
            if (text.isMarkupEscapeAt(index, '{')) {
                visible.append("{{")
                index += 3
                continue
            }
            if (text.isMarkupEscapeAt(index, '}')) {
                visible.append("}}")
                index += 3
                continue
            }
            if (char == '}' && text.getOrNull(index + 1) == '}' && expectClose) {
                expectClose = false
                index += 2
                continue
            }
            if (char == '{' && text.getOrNull(index + 1) == '{') {
                val marker = InlineMarkup.MARKER_REGEX.find(text, index)
                if (marker != null && marker.range.first == index) {
                    val tone = EmphasisTone.fromCode(marker.groupValues[1][0])
                    if (tone != null) {
                        val brush = InlineBrush.fromSuffix(marker.groupValues[2].ifEmpty { null })
                        val start = visible.length
                        visible.append(marker.groupValues[3])
                        spans += InlineSpan(tone, brush, start, visible.length)
                        index = marker.range.last + 1
                        continue
                    }
                }
                val prefix = ROLE_PREFIX.find(text, index)
                if (prefix != null && prefix.range.first == index) {
                    // 有标记意图但成不了合法标记：丢掉语法字符，保留正文
                    index = prefix.range.last + 1
                    expectClose = true
                    continue
                }
            }
            visible.append(char)
            index++
        }
        return InlineMarkup.materialize(visible.toString(), spans)
    }

    private const val MAX_SPLIT_ROUNDS = 4
}

/** 手打辅助的识别结果，见 [typedMarkerAtEnd]。 */
data class TypedMarker(
    val visibleText: String,
    val tone: EmphasisTone,
    val brush: InlineBrush,
    /** 标记换成正文之后，这段正文在 [visibleText] 里的区间。 */
    val range: IntRange
)

/**
 * 只在**文本末尾**匹配刚打完的标记：人是顺序输入的，敲下 `}}` 的那一刻标记必然在末尾。
 * 这条限制换来一个关键性质——**转换不影响前面任何 span 的偏移**（只删掉尾巴上的语法字符），
 * 因此不需要重算整张 span 表，也就不会动到编辑器最脆弱的那段逻辑。
 */
private val TYPED_MARKER_AT_END = Regex("\\{\\{([A-Za-z])(-[A-Za-z])?:([^{}\\n]*)\\}\\}$")

/**
 * 手打辅助（设计文档 §3.11.4）。
 *
 * 编辑器的既定形态是"标记不进编辑缓冲"（§3.7），于是**手打语法原本不会生效**——
 * 用户敲 `{{k:要点}}` 只会得到一串字面量。这个函数补上那条路：
 * 当用户在末尾敲出完整标记时，就地把它转成 span 并把语法字符从缓冲里去掉，
 * 缓冲区此后仍然"只有可见文本"，与划词工具条产出的形态完全一致。
 *
 * 已知边界（有意为之）：粘贴进来的多段标记、以及正文中间的标记不会被转换，
 * 只有末尾这一个会被识别。要覆盖任意位置需要重算偏移表，收益与风险都不划算。
 */
fun typedMarkerAtEnd(text: String): TypedMarker? {
    val match = TYPED_MARKER_AT_END.find(text) ?: return null
    val tone = EmphasisTone.fromCode(match.groupValues[1][0]) ?: return null
    val body = match.groupValues[3]
    if (body.isEmpty()) return null
    val prefix = text.substring(0, match.range.first)
    val start = prefix.length
    return TypedMarker(
        visibleText = prefix + body,
        tone = tone,
        brush = InlineBrush.fromSuffix(match.groupValues[2].ifEmpty { null }),
        range = start until (start + body.length)
    )
}
