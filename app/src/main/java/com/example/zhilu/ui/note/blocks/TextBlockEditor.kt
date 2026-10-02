package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.markup.InlineBrush
import com.example.zhilu.domain.markup.InlineMarkup
import com.example.zhilu.domain.markup.InlineSpan
import com.example.zhilu.domain.markup.InlineSpanAdjuster
import com.example.zhilu.domain.markup.applyTypedText
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

    fun pickTone(tone: EmphasisTone) {
        if (selection.collapsed) {
            // 没有选区 → 进入「标记中」：光标处放一个零长 span，随后输入自动落入
            lastBrush = currentBrush
            commit(buffer, InlineSpanAdjuster.startPending(spans, selection.max, tone, currentBrush))
            return
        }
        val range = selection
        val same = activeSpan?.tone == tone && activeSpan.brush == currentBrush
        val next = if (same) {
            InlineSpanAdjuster.clear(spans, range.min, range.max, buffer)
        } else {
            InlineSpanAdjuster.apply(spans, range.min, range.max, tone, currentBrush, buffer)
        }
        lastBrush = currentBrush
        commit(buffer, next)
        // 收起选区，用户可以直接接着打字
        selection = TextRange(range.max.coerceIn(0, buffer.length))
    }

    fun pickBrush(brush: InlineBrush) {
        lastBrush = brush
        when {
            activeSpan != null && !selection.collapsed ->
                commit(
                    buffer,
                    InlineSpanAdjuster.apply(
                        spans, selection.min, selection.max, activeSpan.tone, brush, buffer
                    )
                )

            activeSpan != null ->
                commit(
                    buffer,
                    InlineSpanAdjuster.apply(
                        spans, activeSpan.start, activeSpan.end, activeSpan.tone, brush, buffer
                    )
                )

            pendingSpan != null ->
                commit(
                    buffer,
                    InlineSpanAdjuster.startPending(
                        spans, pendingSpan.start, pendingSpan.tone, brush
                    )
                )

            else ->
                // 光标处既没有标记也没有「标记中」→ 用这个笔触**起一个**标记。
                // 没有这条分支时，加粗按钮是死的：它不带语义角色，四个角色色块又没被点过，
                // 三个 when 分支全不命中，点什么都不会发生（真机踩过）。
                // 角色的默认值取 KEY —— 加粗渲染时忽略 tone，这里只是把 span 结构凑齐。
                commit(
                    buffer,
                    InlineSpanAdjuster.startPending(
                        spans = spans,
                        offset = selection.max.coerceIn(0, buffer.length),
                        tone = currentTone ?: EmphasisTone.KEY,
                        brush = brush
                    )
                )
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
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}
