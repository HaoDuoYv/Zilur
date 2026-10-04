package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zhilu.domain.markup.InlineBrush
import com.example.zhilu.domain.markup.InlineFormulaConversion
import com.example.zhilu.domain.markup.InlineKind
import com.example.zhilu.domain.markup.InlineMarkup
import com.example.zhilu.domain.markup.InlineNodes
import com.example.zhilu.domain.markup.InlineSpan
import com.example.zhilu.domain.markup.InlineSpanAdjuster
import com.example.zhilu.domain.markup.MathSpans
import com.example.zhilu.domain.markup.applyTypedText
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.ZhiLuType
import kotlinx.coroutines.launch
import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalAccessibleEmphasis

/**
 * 正文编辑器（设计文档 §3.7）。
 *
 * ## 核心：标记**不进编辑缓冲**
 *
 * 缓冲区里只有**可见文本**，标记单存放在会话内的 [InlineSpan] 表里。
 * 于是光标、选区、输入法、退格全是原生的文本行为 —— 用户在编辑状态下
 * **看不到也删不到** `{{k:`，因为那些字符根本不在缓冲里。保存时才由
 * [InlineMarkup.materialize] 把标记物化回 `content`。
 *
 * 这不是"offset 又回来了"：落库的仍是文本，span 表是随时可从文本重建的**派生物**，
 * 只有一个真相；offset 漂移被关在一次编辑会话内，由 [InlineSpanAdjuster] 逐次修正。
 *
 * ## 外部同步
 *
 * 只在"传入的 [value] 与本地物化结果不一致"时才重新解析 —— 无条件重建会重置光标与
 * 输入法状态，表现为打字丢字、光标乱跳。
 */
@Composable
fun TextBlockEditor(
    value: String,
    onValueChange: (String) -> Unit,
    /** 本块 id：用于认领底部工具栏下发的「标记」指令（见 [MarkChannel]）。 */
    blockId: Long = -1L,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalExtendedColors.current.isDark
    val textStyle = MaterialTheme.typography.bodyLarge.merge(
        TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
    )

    var buffer by remember { mutableStateOf("") }
    var spans by remember { mutableStateOf<List<InlineSpan>>(emptyList()) }
    var selection by remember { mutableStateOf(TextRange.Zero) }
    var composition by remember { mutableStateOf<TextRange?>(null) }
    var lastBrush by remember { mutableStateOf(InlineBrush.HIGHLIGHT) }

    // 焦点用交互源读，不要用 Modifier.onFocusChanged ——
    // 挂在包装 Box 上时它观测不到内部 BasicTextField 的焦点（真机上表现为工具条永不出现）。
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // 底部工具栏的「标记」指令需要把焦点交给编辑框，否则用户还得再点一下正文
    val focusRequester = remember { FocusRequester() }

    // 注意：**不要在失焦时丢弃零长标记**。点工具条上的色块会让编辑框短暂失焦，
    // 若在那一刻清掉「标记中」，用户点了色块再打字是打不出颜色的
    // （真机踩过）。零长标记本来就不会落库（materialize 会滤掉），留着无害；
    // 结束时由「再点同色 / ✕」显式清理。

    // 外部同步。两条规则缺一不可：
    // 1) 只在"传入值与本地物化结果不一致"时才重建 —— 无条件重建会重置光标与输入法状态；
    // 2) **正在输入时不接受外部回灌** —— `onValueChange` 是异步回流的，父层可能先带着
    //    上一帧的中间值重组，此时若照着它重建，就会把用户刚打进去的字丢掉（真机字体丢失事故）。
    //    焦点在编辑框里时，本地状态就是权威；未聚焦时才采纳外部变更（AI 写入、撤销、重新加载）。
    LaunchedEffect(value) {
        if (value == InlineMarkup.materialize(buffer, spans)) return@LaunchedEffect
        if (focused) return@LaunchedEffect
        val parsed = InlineMarkup.parseSpans(value)
        buffer = parsed.visibleText
        spans = parsed.spans
        selection = TextRange(parsed.visibleText.length)
        composition = null
    }

    /** 本地改一次：更新 buffer/span 表，并把物化结果回写出去。 */
    fun commit(newText: String, newSpans: List<InlineSpan>) {
        val normalized = InlineSpanAdjuster.normalize(newSpans, newText)
        buffer = newText
        spans = normalized
        onValueChange(InlineMarkup.materialize(newText, normalized))
    }

    val pendingSpan = spans.firstOrNull { it.isCollapsed }
    val activeSpan = if (!selection.collapsed) {
        InlineSpanAdjuster.spanCovering(spans, selection.min, selection.max)
    } else {
        InlineSpanAdjuster.spanAt(spans, selection.max)
    }
    val currentTone = activeSpan?.tone ?: pendingSpan?.tone
    val currentBrush = activeSpan?.brush ?: pendingSpan?.brush ?: lastBrush
    val isPending = activeSpan == null && pendingSpan != null

    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()

    /**
     * 公式是**原子对象**：选区端点落在公式内部时，**向外吸附**到整条公式的两端。
     *
     * 编辑缓冲里 `$…$` 的源码仍在（Compose 的输入框不支持 inline content，所以编辑态
     * 只能显示源码，见 `buildToneStyledText` 的说明），字符层面上选区本来可以落进公式中间
     * —— 正是这条路把 `{{k:…}}` 织进了 `_{\text{发}}` 的花括号里（真机反馈的"加颜色导致公式失效"）。
     *
     * 吸附之后"选到一半的公式"会变成"整条公式"，而"整条公式被标记包住"是**可存**的形态
     * （`{{k:$W \le 2^{n-1}$}}`），既能上色又不会切坏公式。
     */
    fun snapRange(start: Int, end: Int): Pair<Int, Int> = InlineNodes.snapOutside(buffer, start, end)

    fun warnMathOnly() {
        scope.launch { snackbar.showSnackbar("公式里不能加标记") }
    }

    fun pickTone(tone: EmphasisTone) {
        if (selection.collapsed) {
            if (MathSpans.intersects(buffer, selection.max, selection.max)) {
                warnMathOnly()
                return
            }
            // 没有选区 → 进入「标记中」：光标处放一个零长 span，随后输入自动落入
            lastBrush = currentBrush
            commit(buffer, InlineSpanAdjuster.startPending(spans, selection.max, tone, currentBrush))
            return
        }
        val range = selection
        val (from, to) = snapRange(range.min, range.max)
        if (to <= from) {
            warnMathOnly()
            return
        }
        val same = activeSpan?.tone == tone && activeSpan.brush == currentBrush
        lastBrush = currentBrush
        commit(
            buffer,
            if (same) {
                InlineSpanAdjuster.clear(spans, from, to, buffer)
            } else {
                InlineSpanAdjuster.apply(spans, from, to, tone, currentBrush, buffer)
            }
        )
        // 收起选区，用户可以直接接着打字
        selection = TextRange(to.coerceIn(0, buffer.length))
    }

    fun pickBrush(brush: InlineBrush) {
        lastBrush = brush
        when {
            activeSpan != null && !selection.collapsed -> {
                val (from, to) = snapRange(selection.min, selection.max)
                if (to <= from) {
                    warnMathOnly()
                    return
                }
                commit(
                    buffer,
                    InlineSpanAdjuster.apply(
                        spans, from, to, activeSpan.tone, brush, buffer
                    )
                )
            }

            activeSpan != null -> {
                if (MathSpans.intersects(buffer, activeSpan.start, activeSpan.end)) {
                    warnMathOnly()
                    return
                }
                commit(
                    buffer,
                    InlineSpanAdjuster.apply(
                        spans, activeSpan.start, activeSpan.end, activeSpan.tone, brush, buffer
                    )
                )
            }

            pendingSpan != null -> {
                if (MathSpans.intersects(buffer, pendingSpan.start, pendingSpan.start)) {
                    warnMathOnly()
                    return
                }
                commit(
                    buffer,
                    InlineSpanAdjuster.startPending(
                        spans, pendingSpan.start, pendingSpan.tone, brush
                    )
                )
            }

            else -> {
                // 光标处既没有标记也没有「标记中」→ 用这个笔触**起一个**标记。
                // 没有这条分支时，加粗按钮是死的：它不带语义角色，四个角色色块又没被点过，
                // 三个 when 分支全不命中，点什么都不会发生（真机踩过）。
                // 角色的默认值取 KEY —— 加粗渲染时忽略 tone，这里只是把 span 结构凑齐。
                val offset = selection.max.coerceIn(0, buffer.length)
                if (MathSpans.intersects(buffer, offset, offset)) {
                    warnMathOnly()
                    return
                }
                commit(
                    buffer,
                    InlineSpanAdjuster.startPending(
                        spans = spans,
                        offset = offset,
                        tone = currentTone ?: EmphasisTone.KEY,
                        brush = brush
                    )
                )
            }
        }
    }

    fun clearMark() {
        when {
            !selection.collapsed -> commit(
                buffer,
                InlineSpanAdjuster.clear(spans, selection.min, selection.max, buffer)
            )

            pendingSpan != null -> commit(buffer, InlineSpanAdjuster.dropCollapsed(spans))
            activeSpan != null -> commit(
                buffer,
                InlineSpanAdjuster.clear(spans, activeSpan.start, activeSpan.end, buffer)
            )
        }
    }

    // 认领底部工具栏下发的「标记」指令（§3.8）。
    // 编辑器首次解析外部文本时 selection 已经在文本末尾，所以 pickTone 的
    // "无选区 → 在光标处进入标记中"正好落在末尾，符合"接着往下写一段带标记的话"的直觉。
    val markChannel = LocalMarkChannel.current
    val markRequest = markChannel?.request
    LaunchedEffect(markRequest, blockId) {
        val request = markRequest ?: return@LaunchedEffect
        if (request.blockId != blockId || blockId < 0L) return@LaunchedEffect
        val tone = request.tone
        if (tone == null) {
            clearMark()
        } else {
            pickTone(tone)
        }
        focusRequester.requestFocus()
        markChannel.request = null
    }

    /**
     * 工具条在**聚焦时常驻**。
     *
     * 这是真机验证后改的：原先只在"有选区或光标落在标记里"时显示，结果**没法起第一个标记** ——
     * 没有选区就没有工具条，也就进不了「标记中」，功能直接是死的。
     * （设计文档 §3.8 原本给底部工具栏留了「标记」入口来解决这件事，那是更克制的做法，
     * 但它需要把选区状态从编辑器提升到屏幕层，本次先不做。）
     */
    val showToolbar = focused || !selection.collapsed

    // 行内原子（P1 公式 + P2 行内代码）。原子区间从 buffer 现算 —— 与标记 span 走同一套坐标，
    // 所以文字增删时原子自动跟着伸缩，不需要额外维护。
    val atomNodes = remember(buffer) { InlineNodes.of(buffer) }
    var atomLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    // 光标落在哪个原子里：那一条公式**不隐藏**，恢复成可编辑的源码（Notion 式）。
    // 折叠态下光标也没有落脚点，所以这条同时解决了"活动原子怎么编辑"的问题。
    val activeAtomOffset = if (focused || !selection.collapsed) selection.max else null
    // 只有公式需要隐藏源码 + 覆盖层补画；行内代码就地套样式即可（见 AtomStyleTransformation）
    val hiddenMathAtoms = InlineNodes.hiddenMath(atomNodes, activeAtomOffset)
    // 可见性诊断出口。**默认 null，正式包里这一整条只是一次空判断**（连 lambda 都不存在）；
    // 类型与实现都住在 `src/debug`，由诊断屏（FormulaProbeActivity）下发。
    // 留这一根线是因为"公式消失"有两种成因（没画 / 画错位置），只看截图分不出来。
    val reportVisibility = LocalAtomVisibility.current
    if (reportVisibility != null) {
        SideEffect {
            reportVisibility(
                AtomVisibility(
                    activeOffset = activeAtomOffset,
                    allAtoms = atomNodes.map { "${it.kind}[${it.start},${it.end}]" },
                    hiddenAtoms = hiddenMathAtoms.map { "${it.kind}[${it.start},${it.end}]" }
                )
            )
        }
    }
    // 行内代码的就地样式：与只读路径（buildInlineLatexText）同一套观感 —— 等宽 + 淡底色
    val codeChipBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    // 行内链接：强调色 + 下划线（同样与只读路径一致）
    val linkInk = MaterialTheme.colorScheme.primary

    // 「转为公式块」：能力由屏幕层下发（见 LocalFormulaPromotion），
    // 光标落在某条行内公式里时才出现这个出口。
    val promoteFormula = LocalFormulaConversions.current?.promote
    val activeMathNode = if (focused || !selection.collapsed) {
        atomNodes.firstOrNull {
            it.kind == InlineKind.MATH && it.contains(selection.max)
        }
    } else {
        null
    }

    /**
     * 把光标所在的这条行内公式**提升成公式块**。
     *
     * 两步：① 从正文里摘掉它（走一次正常文本变更，落库仍是纯文本）；
     * ② 把裸 LaTeX 源码交给屏幕层，由 ViewModel 在本块之后插入一个 `LATEX` 块。
     * span 交给 [InlineSpanAdjuster.adjust] 按差分跟着挪 —— 与打字、粘贴同一条路径，
     * 不另写一套"删除公式时要怎么改标记"的逻辑。
     */
    fun promoteActiveMath() {
        val node = activeMathNode ?: return
        val promotion = promoteFormula ?: return
        val (rest, source) = InlineFormulaConversion.promote(buffer, node) ?: return
        val nextSpans = InlineSpanAdjuster.adjust(spans, buffer, rest)
        buffer = rest
        spans = nextSpans
        selection = TextRange(node.start.coerceIn(0, rest.length))
        onValueChange(InlineMarkup.materialize(rest, nextSpans))
        promotion(blockId, source)
    }

    // ── 公式图的绘制尺寸（设计文档 §12.3、§14.6）─────────────────────────
    // 源码是透明的：它**占的排版宽度**由文本自然决定，而公式图由 InlineAtomLayer 补画在它上面。
    // 这里刻意**不做任何逐轮逼近** —— 试过"量盒宽 → 调字距 → 再量"的反馈环，真机上换来的是
    // 字距被夹错符号（源码撑成两行）、把行尾换行符当成折行判据、样式表被重新排版清空（§14.6）。
    // 现在的规则是**一次算完、不再回头**：图只按「该行剩余宽度」与「行盒高度」缩小，绝不放大，
    // 于是它与只读态**逐像素同尺寸**。代价是源码天然比图宽时后面会留一段空白 ——
    // 那是可见的留白，比"公式被压小 / 画到别的行上"划算；长公式有「转为公式块」这个出口。
    val baseFontSizeSp = textStyle.fontSize.takeIf { it.isSp }?.value ?: 17f

    Box(modifier = modifier) {
        Column {
            BasicTextField(
                value = TextFieldValue(
                    annotatedString = buildToneStyledText(
                        buffer,
                        spans,
                        darkTheme,
                        LocalAccessibleEmphasis.current
                    ),
                    selection = selection,
                    composition = composition
                ),
                onValueChange = { new ->
                    // 打字路径上唯一的决策点（含手打辅助 §3.11.4），逻辑在
                    // domain/markup/EditorTextTransition.kt 里，由单测覆盖。
                    // 注意：**不能**把这段挂在 commit() 上 —— 打字不走 commit()，
                    // 它只被工具条的 pickTone/pickBrush/clearMark 调用。
                    val next = applyTypedText(buffer, spans, new.text, new.selection.max)
                    val textChanged = next.text != new.text
                    buffer = next.text
                    spans = next.spans
                    if (textChanged) {
                        // 手打标记被吞掉了语法字符 → 光标收尾，输入法组合态作废
                        selection = TextRange(next.caret)
                        composition = null
                    } else {
                        selection = new.selection
                        composition = new.composition
                    }
                    if (textChanged) lastBrush = spans.lastOrNull()?.brush ?: lastBrush
                    onValueChange(InlineMarkup.materialize(next.text, next.spans))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .heightIn(min = 112.dp),
                textStyle = textStyle,
                interactionSource = interactionSource,
                // 行内原子：公式的源码透明（只改样式、不改长度）+ 覆盖层补画公式图；
                // 行内代码就地套等宽样式。见设计文档 §7、§8.2。
                // `activeAtomOffset` 必须一起传：光标所在那条公式要**撤销透明**，
                // 否则它既不被覆盖层画（hiddenMath 已排除）、源码又是透明的 = 彻底看不见。
                visualTransformation = remember(
                    atomNodes,
                    activeAtomOffset,
                    codeChipBackground,
                    linkInk
                ) {
                    AtomStyleTransformation(
                        nodes = atomNodes,
                        activeAtomOffset = activeAtomOffset,
                        codeBackground = codeChipBackground,
                        linkInk = linkInk
                    )
                },
                onTextLayout = { atomLayout = it },
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (buffer.isEmpty()) {
                            Text(
                                text = "输入正文…",
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                        // 覆盖层与 innerTextField 同坐标系，所以直接并列即可，不用补 padding
                        InlineAtomLayer(
                            text = buffer,
                            nodes = hiddenMathAtoms,
                            layout = atomLayout,
                            spans = spans,
                            darkTheme = darkTheme,
                            accessibleEmphasis = LocalAccessibleEmphasis.current,
                            textSizeSp = baseFontSizeSp
                        )
                    }
                }
            )

            // 工具条**停靠在正文下方**，而不是浮在选区上方。
            //
            // 真机验证后改的：浮动定位有两个实际问题 ——
            // ① `getBoundingBox` 取的是"某个字符"的盒子，光标在文末时 offset == length 会抛异常，
            //    工具条直接不出现（这个已经单独修掉）；② 手机宽度下工具条比正文区还宽，
            //    贴边浮动会被卡片裁掉右侧的「下划线 / 清除」，而且位置随光标跳动，点不准。
            // 停靠后位置恒定、不会被裁，也不需要测量选区的盒子。
            if (showToolbar) {
                InlineMarkToolbar(
                    currentTone = currentTone,
                    currentBrush = currentBrush,
                    isPending = isPending,
                    darkTheme = darkTheme,
                    onPickTone = ::pickTone,
                    onPickBrush = ::pickBrush,
                    onClear = ::clearMark,
                    modifier = Modifier.padding(top = 10.dp),
                    // 光标落在一条行内公式里时，给一个"搬出去"的出口：
                    // 长公式待在句子中间会折行、被压小，本来就该升级成公式块（§14.2）
                    extraAction = if (promoteFormula != null && activeMathNode != null) {
                        { PromoteFormulaChip(onClick = { promoteActiveMath() }) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/**
 * 「转为公式块」小按钮。
 *
 * 造型跟着工具条其它项走（深底浅字），不加图标 —— 这一排已经有色块、四个笔触和一个 ✕，
 * 再塞一个图形按钮只会更难认；文字说得最清楚。
 */
@Composable
private fun PromoteFormulaChip(onClick: () -> Unit) {
    Text(
        text = "转为公式块",
        style = ZhiLuType.meta,
        color = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
