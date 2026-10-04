package com.example.zhilu.ui.note.blocks

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 行内公式的**可见性诊断**出口：把"这一帧要画哪几条公式"报给订阅方。
 *
 * - 默认值是 **null**：正式包里这一整条只是一次空判断，连 lambda 都不存在（零成本）；
 * - **谁去收集、怎么显示全在 `src/debug`**（`com.example.zhilu.debug.LocalAtomVisibility`
 *   与 `FormulaProbeActivity`）—— 这里只有"数据长什么样"这一个契约，没有任何代码逻辑。
 *
 * 与 `MarkChannel` / `LocalFormulaConversions` 同一模式：能力由上方下发，
 * 编辑器不反向依赖屏幕层。留这一根线的原因是"公式消失"有两种完全不同的成因
 * （没画 / 画错位置），只看截图分不出来 —— 上一轮的"点进公式公式消失"就是靠它定位的。
 */
internal val LocalAtomVisibility = staticCompositionLocalOf<((AtomVisibility) -> Unit)?> { null }

/**
 * 公式原子的**可见性状态**快照。
 *
 * @param activeOffset 光标当前落点（null = 编辑器失焦 / 没有选区）
 * @param allAtoms 解析出来的全部原子，形如 `MATH[7,34]`
 * @param hiddenAtoms 其中**要隐藏源码并补画公式图**的那些（= 除光标所在原子之外的公式）
 */
internal data class AtomVisibility(
    val activeOffset: Int?,
    val allAtoms: List<String>,
    val hiddenAtoms: List<String>
)
