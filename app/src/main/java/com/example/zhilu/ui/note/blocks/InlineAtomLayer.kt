package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zhilu.domain.markup.InlineKind
import com.example.zhilu.domain.markup.InlineNode
import com.example.zhilu.domain.markup.InlineSpan
import com.example.zhilu.ui.note.latex.LatexRenderState
import com.example.zhilu.ui.note.latex.rememberLatexImage
import com.example.zhilu.ui.theme.emphasisInkColor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 给行内原子套样式。
 *
 * **公式**：源码整段透明（只改样式、不改长度），位置与几何交给 [InlineAtomLayer] 补画公式图。
 * 这是 P0 实测出来的做法（设计文档 §8.2）。曾经想过把原子折叠成 0 宽再画覆盖层，
 * 真机上直接失败：折叠后原子不占宽度，覆盖层画上去就压住了后面的文字。保留宽度则换来：
 *
 * 1. **偏移映射是恒等的** —— 不需要 `OffsetMapping`，也就没有"映射算错、光标乱跳"这类 bug；
 * 2. 覆盖层正好落在源码让出的空位里，不会遮挡任何文字；
 * 3. 选区/光标行为与普通文字完全一致（选中的就是那段源码，只是看不见）。
 *
 * **行内代码**：就地套等宽 + 底色，源码照常显示（没有"盒宽与图不符"的问题，
 * 也就不需要覆盖层与控盒）。
 *
 * [mathStyles] 是公式"控盒"的关键：源码不可见，所以给它设字号与字距不会影响观感，
 * 只会改变它占的宽度与高度 —— 于是可以把盒子调成正好等于公式图（见 [AtomBoxStyle]）。
 */
internal data class AtomStyleTransformation(
    private val nodes: List<InlineNode>,
    private val mathStyles: Map<Int, AtomBoxStyle> = emptyMap(),
    private val codeBackground: Color = Color.Transparent,
    private val codeInk: Color? = null,
    /** 行内链接的强调色（与主题强调色一致）与下划线。 */
    private val linkInk: Color? = null
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (nodes.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val annotated = buildAnnotatedString {
            append(text)
            nodes.forEach { node ->
                val start = node.start.coerceIn(0, text.length)
                val end = node.end.coerceIn(0, text.length)
                if (end <= start) return@forEach
                when (node.kind) {
                    InlineKind.MATH -> {
                        val style = mathStyles[node.start]
                        addStyle(
                            SpanStyle(
                                color = Color.Transparent,
                                fontSize = style?.fontSizeSp?.sp ?: TextUnit.Unspecified,
                                letterSpacing = style?.letterSpacingSp?.sp ?: TextUnit.Unspecified
                            ),
                            start,
                            end
                        )
                    }

                    // 与只读路径（buildInlineLatexText）用同一套观感：等宽 + 淡底色
                    InlineKind.CODE -> addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = codeInk ?: Color.Unspecified
                        ),
                        start,
                        end
                    )

                    // 链接：强调色 + 下划线。编辑态**显示源码**（`[文字](url)` 全文），
                    // 因为要能改它；标注的是"这一段是链接"，而不是把 URL 藏起来。
                    InlineKind.LINK -> addStyle(
                        SpanStyle(
                            color = linkInk ?: Color.Unspecified,
                            textDecoration = TextDecoration.Underline
                        ),
                        start,
                        end
                    )
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

/**
 * 一个原子该用多大的字号、多宽的字距，才能让源码占的盒子等于公式图。
 *
 * 两者都是**逐轮逼近**出来的：覆盖层报出图的实测像素尺寸，编辑器拿它和当前盒子比，
 * 差值超过阈值才更新（阈值是防"布局 → 测量 → 改样式 → 再布局"死循环的关键）。
 */
internal data class AtomBoxStyle(
    /** 源码该用的**绝对**字号（sp）：把行高撑到图的高度。源码不可见，放多大都不影响观感。 */
    val fontSizeSp: Float,
    /** 字距（sp）。负值把源码占宽压到图的宽度 —— 同理不影响观感。 */
    val letterSpacingSp: Float
)

/** 每个原子最多调几次样式：反馈环的收敛保险，见 `TextBlockEditor` 里的说明。 */
internal const val MAX_TUNING_PASSES = 4

/** 每轮只走这么大比例的修正量（阻尼），避免在目标附近来回过冲。 */
internal const val TUNING_DAMPING = 0.6f

/**
 * 行内原子图层：在原子源码让出的空位里画公式图。
 *
 * 坐标与 `BasicTextField` 的文本同源 —— 调用方把它放进 `decorationBox` 的那个 `Box`，
 * 与 `innerTextField()` 并列即可，不需要手工补 padding。
 */
@Composable
internal fun InlineAtomLayer(
    text: String,
    nodes: List<InlineNode>,
    layout: TextLayoutResult?,
    spans: List<InlineSpan>,
    darkTheme: Boolean,
    accessibleEmphasis: Boolean,
    textSizeSp: Float,
    /** 图渲染完成后回报实测像素尺寸，供"控盒"逐轮逼近。 */
    onMeasured: (nodeStart: Int, widthPx: Int, heightPx: Int) -> Unit = { _, _, _ -> }
) {
    if (layout == null || nodes.isEmpty() || text.isEmpty()) return
    nodes.forEach { node ->
        key(node.start, node.end) {
            InlineAtom(
                node = node,
                text = text,
                layout = layout,
                spans = spans,
                darkTheme = darkTheme,
                accessibleEmphasis = accessibleEmphasis,
                textSizeSp = textSizeSp,
                onMeasured = onMeasured
            )
        }
    }
}

@Composable
private fun InlineAtom(
    node: InlineNode,
    text: String,
    layout: TextLayoutResult,
    spans: List<InlineSpan>,
    darkTheme: Boolean,
    accessibleEmphasis: Boolean,
    textSizeSp: Float,
    onMeasured: (Int, Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val start = runCatching { layout.getCursorRect(node.start) }.getOrNull() ?: return
    val end = runCatching { layout.getCursorRect(node.end) }.getOrNull()

    // 原子自己占的那个盒子。公式被折行截断时（`end` 落到下一行）退化成两倍行高，
    // 免得按跨行宽度算出一个横跨整行的巨框。
    val boxWidthPx = if (end != null && end.top == start.top) {
        (end.left - start.left).coerceAtLeast(start.height)
    } else {
        start.height * 2f
    }

    // 被标记覆盖时，公式用角色墨色画 —— 这是"公式和文字一样能着色"的落点。
    val tone = spans
        .firstOrNull { it.start <= node.start && it.end >= node.end && !it.isCollapsed }
        ?.tone
    val color = tone
        ?.let { emphasisInkColor(it, darkTheme, accessibleEmphasis) }
        ?: MaterialTheme.colorScheme.onSurface

    val state = rememberLatexImage(
        latex = node.contentOf(text),
        textSizeSp = textSizeSp,
        color = color
    )
    val image = (state as? LatexRenderState.Success)?.image

    // 图到位后回报尺寸（只在真正变化时触发，避免重组风暴）
    if (image != null) {
        SideEffect { onMeasured(node.start, image.width, image.height) }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(start.left.roundToInt(), start.top.roundToInt()) }
            .size(
                width = with(density) { boxWidthPx.toDp() },
                height = with(density) { start.height.toDp() }
            ),
        // 左对齐而不是居中：盒子宽度等于"源码占宽"，通常比公式图宽得多。
        // 居中会让公式看上去被推向中间、和缩进对不上；左对齐则读成"公式后面跟了个空格"。
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            image != null -> {
                // 目标：**与只读态同尺寸**。只读路径的占位高度等于图高、行高随图长，
                // 所以这里也不该"削足适履"地把公式压进行高，而是以宽度贴合为主、
                // 行高交给控盒去撑（`AtomBoxStyle.fontSizeSp`）—— 两边观感才对得上。
                // 仅当图比盒子高出很多（控盒还没收敛完的那几帧）才临时收一下，避免压到相邻行。
                val heightLimit = if (image.height > start.height * 2f) {
                    (start.height * 2f) / image.height
                } else {
                    1f
                }
                val scale = min(
                    (boxWidthPx / image.width).coerceAtMost(1.6f),
                    heightLimit
                ).coerceAtLeast(0.4f)
                Image(
                    bitmap = image,
                    contentDescription = node.contentOf(text),
                    modifier = Modifier.size(
                        width = with(density) { (image.width * scale).toDp() },
                        height = with(density) { (image.height * scale).toDp() }
                    )
                )
            }

            // 渲染失败 / 还在渲染：把源码小字留在原位，用户至少能看见并修改它
            else -> Text(
                text = node.contentOf(text),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}
