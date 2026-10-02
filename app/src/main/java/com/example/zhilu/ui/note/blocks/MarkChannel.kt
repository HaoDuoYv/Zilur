package com.example.zhilu.ui.note.blocks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.zhilu.domain.model.EmphasisTone

/**
 * 屏幕层下发给编辑器的一次"起标记"请求。
 *
 * [tone] 为 `null` 表示**取消**当前那条块的「标记中」状态。
 */
data class MarkRequest(val blockId: Long, val tone: EmphasisTone?)

/**
 * 底部工具栏「标记」入口 → 编辑器 的**一次性指令通道**（设计文档 §3.8）。
 *
 * ## 为什么是通道，而不是把状态提升到屏幕层
 *
 * 选区与「标记中」是**每条块各自的**会话内状态，活在 `TextBlockEditor` 里
 * （`buffer` / `spans` / `selection`）。要把它提升到屏幕层，等于把整个编辑器掏空、
 * 在每个用到它的地方重建一份 —— 代价极大，而且立刻多出两个真相来源。
 *
 * 反过来，屏幕层其实只需要"下达一个一次性指令"：编辑器自己认领（`blockId` 匹配才执行）、
 * 执行完清空请求。**单向、无回读、不引入第二份状态源。**代价是屏幕层无法回显
 * "现在处于标记中"（底部入口因此不做状态回显，回显仍由正文旁那条工具条负责）。
 */
class MarkChannel {
    var request by mutableStateOf<MarkRequest?>(null)
}

/** 默认 `null`：AI 对话、首页预览等用到块渲染的地方不受影响。 */
val LocalMarkChannel = staticCompositionLocalOf<MarkChannel?> { null }
