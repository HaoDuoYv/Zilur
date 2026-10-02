package com.example.zhilu.domain.markup

/**
 * 一次文本输入之后的编辑器状态：缓冲区文本 + span 表 + 光标。
 *
 * 三个字段就是编辑器全部的本地状态（设计文档 §3.7），没有任何 Compose 依赖，
 * 所以整条"输入 → 状态"的转移都能在 JVM 单测里跑。
 */
data class EditorTextState(
    val text: String,
    val spans: List<InlineSpan>,
    val caret: Int
)

/**
 * 把一次输入落到编辑器状态上。**打字路径上唯一的决策点。**
 *
 * 两条分支：
 * 1. **手打辅助**（§3.11.4）：新文本末尾刚好是一个完整标记 → 把它转成 span，
 *    并从缓冲区里去掉语法字符。手打语法于是和划词工具条产出完全一样的形态。
 * 2. **普通输入**：按新增/删除的字符数平移既有 span（`InlineSpanAdjuster.adjust`）。
 *
 * ## 为什么单独抽出来
 *
 * 这段逻辑原先写在 Compose 的 `onValueChange` lambda 里，第一版还不小心挂到了
 * `commit()` 上——而打字根本不走 `commit()`（它只被工具条的 pickTone/clearMark 调用），
 * 于是真机上打 `{{k:test}}` 只是被转义成字面量。抽成纯函数之后，
 * **"标记到底有没有被转换"这件事由单测保证，不再依赖能不能点中那个输入框。**
 *
 * @param previousText 输入前的缓冲区文本
 * @param spans 输入前的 span 表（坐标基于 [previousText]）
 * @param newText 输入后的缓冲区文本（来自 `TextFieldValue.text`）
 * @param newCaret 输入后的光标位置（来自 `TextFieldValue.selection.max`）
 */
fun applyTypedText(
    previousText: String,
    spans: List<InlineSpan>,
    newText: String,
    newCaret: Int
): EditorTextState {
    val typed = typedMarkerAtEnd(newText)
    if (typed != null && typed.visibleText.length <= newText.length) {
        // 标记只可能在末尾，所以标记之前的偏移全都不变。
        //
        // 但**不能**直接沿用 adjust 的结果：规则 2（"在 span 末尾插入即扩展"）会把这次
        // 打进来的标记文字也算进前一条标记里——用户刚在一条「注意」后面打 `{{i:想法}}`，
        // 前缀标记会被扩到覆盖「想法」，随后 normalize 的"先到先得"再把新标记整条丢掉。
        // 所以这里做两步：先按普通规则平移，再把新标记的区间从其它 span 里**挖掉**，
        // 最后才把新 span 并进去（clear 原本就是给工具条做"清除选区内标记"用的，语义正好）。
        val adjusted = InlineSpanAdjuster.adjust(spans, previousText, newText)
        val carved = InlineSpanAdjuster.clear(
            spans = adjusted,
            start = typed.range.first,
            end = typed.range.last + 1,
            visibleText = typed.visibleText
        )
        val merged = carved + InlineSpan(
            tone = typed.tone,
            brush = typed.brush,
            start = typed.range.first,
            end = typed.range.last + 1
        )
        return EditorTextState(
            text = typed.visibleText,
            spans = InlineSpanAdjuster.normalize(merged, typed.visibleText),
            // 光标落到标记正文之后：用户可以接着往下写
            caret = typed.visibleText.length
        )
    }

    val adjusted = InlineSpanAdjuster.adjust(spans, previousText, newText)
    return EditorTextState(
        text = newText,
        spans = InlineSpanAdjuster.normalize(adjusted, newText),
        caret = newCaret.coerceIn(0, newText.length)
    )
}
