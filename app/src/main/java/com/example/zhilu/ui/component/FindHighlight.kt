package com.example.zhilu.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 页内查找的高亮上下文（设计文档 §6.4）。
 *
 * 用 CompositionLocal 下发而不是逐层传参：从 `NoteEditScreen` 到真正渲染文字的
 * `RichText` 中间隔着 `KnowledgeCardItem → CardBlockList → EditableBlock/ReadOnlyBlock →
 * BlockCard → BlockContent → ExpandableTextContent` 六层，为一个临时态改六个签名不值当。
 *
 * 默认是 `null`：AI 对话气泡等其它用到 `RichText` 的地方不受影响。
 */
data class FindHighlight(
    val query: String,
    val background: Color,
    val textColor: Color
)

val LocalFindHighlight = staticCompositionLocalOf<FindHighlight?> { null }
