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
 * **光标所在的那一条必须显示源码**（[activeAtomOffset]）：它是"进公式改错字"的唯一入口，
 * 而它同时被两处处理 —— 覆盖层不再画图（`InlineNodes.hiddenMath` 把它排除），
 * 这里如果不把透明撤销，那条公式就会**彻底看不见**（图上只剩一块选区底色）。
 * 两个判断必须同源：都用同一个 [activeAtomOffset]。
 *
 * **行内代码**：就地套等宽 + 底色，源码照常显示（没有"盒宽与图不符"的问题，
 * 也就不需要覆盖层与控盒）。
 *
 * ## 为什么这里**没有** `mathStyles`
 *
 * 曾经有过一版：给源码设字号/字距，把"不可见源码占的盒子"调成正好等于公式图，消掉公式后面
 * 的空白。方向没错，但它必须靠"量盒宽 → 改样式 → 再量"的反馈环收敛，而那一步引入了整串问题
 * （字距 sp/px 串了单位被夹成 +16sp、把行尾换行符当成折行判据、按文本偏移做的样式表被重新
 * 排版清空）。量化之后收益只有 17~50px 的留白，不值得为它保留一条需要收敛证明的回路 ——
 * 规则改成"一次算完"（见 [AtomBox]）。
 */
internal data class AtomStyleTransformation(
    private val nodes: List<InlineNode>,
    /** 光标落点（null = 失焦）。落在某条公式内时，那一条**不透明**，恢复成可编辑源码。 */
    private val activeAtomOffset: Int? = null,
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
                    // 只改样式、不改长度 → 偏移映射仍是恒等的
                    InlineKind.MATH -> {
                        val isActive = activeAtomOffset != null && node.contains(activeAtomOffset)
                        if (isActive) {
                            // 光标在公式内：源码必须看得见（否则公式"消失"、也没法改错字）。
                            // 用等宽淡色，与"这是 LaTeX 源码"的观感一致。
                            addStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = codeInk ?: Color.Unspecified
                                ),
                                start,
                                end
                            )
                        } else {
                            addStyle(SpanStyle(color = Color.Transparent), start, end)
                        }
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

/** 覆盖层绘制公式图时的可用尺寸，取"该行剩余宽度"与"行盒高度"。 */
internal fun atomDrawScale(
    imageWidth: Int,
    imageHeight: Int,
    availableWidthPx: Float,
    availableHeightPx: Float
): Float = AtomBox.drawScale(imageWidth, imageHeight, availableWidthPx, availableHeightPx)

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
    textSizeSp: Float
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
                textSizeSp = textSizeSp
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
    textSizeSp: Float
) {
    val density = LocalDensity.current
    val start = runCatching { layout.getCursorRect(node.start) }.getOrNull() ?: return

    // 原子自己占的那个盒子。宽度与"是否折行"都由**原子自己的字符**量出来 ——
    // `getCursorRect(node.end)` 取的是原子之后那个偏移，原子结束在行尾时会落到下一行
    // （详见 atomRunMetrics 的说明）。
    val run = atomRunMetrics(layout, node.start, node.end)
    val wrapped = run?.wrapped ?: true
    val boxWidthPx = run?.widthPx?.takeIf { !wrapped } ?: (start.height * 2f)
    // 绘制可用空间：宽度取"这一行右边还剩多少"（超出去就会压到后面的文字），
    // 高度取行盒高度（超出去就会压到相邻行）。**不再拿源码占宽当上限** ——
    // 源码占宽是排版量，公式图不该被它约束（设计文档 §14.6）。
    val lineWidthPx = atomLineWidth(layout, node.start, start.left)
        .takeIf { it > 0f } ?: boxWidthPx

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

    // 覆盖层的盒子：**只定位，既不定宽也不定高** —— 由内容（图）自己撑开。
    //
    // 这两个约束各裁掉过一截，别再往回加：
    // - **定宽**（按"源码占宽"）：源码比图窄时图被横向裁掉（真机实测：68 字符的源码被压成 208px，
    //   而图有 573px）；
    // - **定高**（按行盒高度）：图比行盒高时被纵向裁掉，表现为含 CJK 的公式像被切了下半截
    //   ——[AtomBox.MAX_HEIGHT_OVERFLOW] 本来就允许图比行盒高 25%，定高等于把这个余量又裁掉。
    //
    // 这个盒子是纯粹的绘制层、不参与排版（源码那一层是透明的），所以尺寸只该由画什么决定。
    Box(
        modifier = Modifier
            .offset { IntOffset(start.left.roundToInt(), start.top.roundToInt()) },
        // 左对齐：盒子此时正好等于图，居中没有意义（占位换算由 offset 负责）
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            image != null -> {
                val scale = atomDrawScale(image.width, image.height, lineWidthPx, start.height)
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
