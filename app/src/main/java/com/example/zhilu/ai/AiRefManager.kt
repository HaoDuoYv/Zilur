package com.example.zhilu.ai

import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.distinctByTarget
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨页「待附加引用」交接：笔记编辑页点「引用到 AI」时把引用放进这里，再导航到助手页；
 * 助手 ViewModel 观察 [pendingRefs] 吸附为输入栏引用 chip，并通过 [consumePending] 清空。
 *
 * 进程级单例，独立于任何 ViewModel / 导航栈，因此跨页面存活。
 */
@Singleton
class AiRefManager @Inject constructor() {

    private val _pendingRefs = MutableStateFlow<List<AiRef>>(emptyList())
    val pendingRefs: StateFlow<List<AiRef>> = _pendingRefs.asStateFlow()

    /**
     * 笔记页追加一个待引用目标。
     *
     * 去重按**坐标键**（`AiRef.targetKey()`）而不是 `equals`：同一目标先后被引用会带
     * 不同快照，按全字段比较会把它们当成两个引用；保留最后出现的那份（最新快照）。
     */
    fun addPending(ref: AiRef) {
        _pendingRefs.value = (_pendingRefs.value + ref).distinctByTarget()
    }

    /** 助手页拉取并清空待引用。 */
    fun consumePending(): List<AiRef> {
        val refs = _pendingRefs.value
        _pendingRefs.value = emptyList()
        return refs
    }

    fun clearPending() {
        _pendingRefs.value = emptyList()
    }
}
