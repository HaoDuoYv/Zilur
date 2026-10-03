package com.example.zhilu.ui.note.blocks

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 行内公式与公式块之间的两个转换方向，由屏幕层下发。
 *
 * 与 [MarkChannel] 同一个模式：编辑块埋在 `BlockCard` → `BlockContent` 下面，
 * 而"插入一个新块 / 改块的类型"只有屏幕层的 ViewModel 做得了。为此把回调一层层穿过
 * `NoteEditScreen → BlockCard（两处调用）→ BlockContent → TextBlockEditor/LatexBlockEditor`
 * 要改七八处、每处都只是原样转发 —— 用一个 local 把能力直接下发。
 *
 * - [promote]：行内公式 → 公式块（`(源块 id, 裸 LaTeX 源码)`）
 * - [demote]：公式块 → 行内公式（`源块 id`）
 *
 * 为 null 表示当前场景不支持转换（例如只读浏览）。
 */
data class FormulaConversions(
    val promote: (blockId: Long, latexSource: String) -> Unit,
    val demote: (blockId: Long) -> Unit
)

val LocalFormulaConversions = staticCompositionLocalOf<FormulaConversions?> { null }
