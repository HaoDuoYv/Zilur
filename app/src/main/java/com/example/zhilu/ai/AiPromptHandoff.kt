package com.example.zhilu.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨页「AI 创建开场白」交接：底栏 ＋ 的「AI 创建」把一句预填话术放进这里，再切到助手页；
 * 助手 ViewModel 观察 [prompt] 并吸附为输入框内容，通过 [consume] 清空。
 *
 * ## 为什么不用路由参数（`assistant?prefill=...`）
 *
 * 带参数的路由会**新建一个导航 entry**：`launchSingleTop = false` 时压入第二个助手页实例，
 * 随之造出第二个 `AssistantViewModel` —— 正在跑的任务状态、当前会话、输入草稿全部重置
 * （真机 bug：任务在后台生成，切回来却是一张白纸）。路由参数还会在
 * 「不经过导航、直接拼路由字符串」的入口把 `{prefill}` 这种**模板占位符**当成字面值填进输入框。
 *
 * 交接放在进程级单例里（与 [AiRefManager] 同一套路数）：导航本身退化成一次干净的
 * 底栏切换（`restoreState` 复用原有 entry 与 ViewModel），预填话术另行送达。
 */
@Singleton
class AiPromptHandoff @Inject constructor() {

    private val _prompt = MutableStateFlow("")

    /** 待消费的开场白（空串 = 没有）。 */
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    /** 投递一句开场白（后投的覆盖先投的，用户要的是"刚点的这句"）。 */
    fun handoff(prompt: String) {
        _prompt.value = prompt
    }

    /** 助手页取走并清空。 */
    fun consume(): String {
        val current = _prompt.value
        _prompt.value = ""
        return current
    }
}
