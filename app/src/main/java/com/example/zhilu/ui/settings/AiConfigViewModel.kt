package com.example.zhilu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.data.ai.LlmApiClient
import com.example.zhilu.data.ai.dto.ChatCompletionRequest
import com.example.zhilu.data.ai.dto.ChatMessageDto
import com.example.zhilu.data.ai.dto.textContent
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.domain.model.AiService
import com.example.zhilu.domain.model.AiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** AI 配置页的状态：已保存的列表 + 正在编辑的草稿。 */
data class AiConfigUiState(
    val settings: AiSettings = AiSettings.EMPTY,
    /** null = 列表态；非 null = 正在编辑这个服务（[AiService.id] 为空表示新建）。 */
    val draft: AiService? = null,
    /** 草稿是否展开「高级设置」区块。 */
    val advancedExpanded: Boolean = false,
    val testInProgress: Boolean = false,
    val testResult: String? = null,
    val message: String? = null
) {
    val active: AiService? get() = settings.resolveActive()
    val editing: Boolean get() = draft != null
}

@HiltViewModel
class AiConfigViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val llmApiClient: LlmApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiConfigUiState())
    val uiState: StateFlow<AiConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.aiSettings.collect { settings ->
                // 列表变化时**不动草稿**：用户可能正在填表单，被一次无关的持久化
                // 回流冲掉输入是最恼人的一类 bug。
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    // ── 列表操作 ──────────────────────────────────────────────────────

    /** 新建：草稿的 id 为空串，保存时才发真 id。 */
    fun startCreate() {
        _uiState.update {
            it.copy(
                draft = AiService(id = "", name = "", endpoint = "", apiKey = "", model = ""),
                advancedExpanded = false,
                testResult = null
            )
        }
    }

    fun startEdit(service: AiService) {
        _uiState.update {
            it.copy(draft = service, advancedExpanded = false, testResult = null)
        }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(draft = null, advancedExpanded = false, testResult = null) }
    }

    fun updateDraft(transform: (AiService) -> AiService) {
        _uiState.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = transform(draft), testResult = null)
        }
    }

    fun toggleAdvanced() {
        _uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    }

    fun saveDraft() {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (!draft.isConfigured) {
            _uiState.update { it.copy(testResult = "端点、API Key、模型三项都要填") }
            return
        }
        viewModelScope.launch {
            // 新建时发一个真 id：切换与删除都按 id 定位，不靠数组下标。
            val toSave = if (draft.id.isBlank()) draft.copy(id = UUID.randomUUID().toString()) else draft
            val existing = state.settings.services
            val services = if (existing.any { it.id == toSave.id }) {
                existing.map { if (it.id == toSave.id) toSave else it }
            } else {
                existing + toSave
            }
            // 第一个服务自动成为当前使用 —— 否则用户添加完还得再点一次"切换"，
            // 而那时界面上还没有别的可选项，属于无谓的一步。
            val activeId = state.settings.activeId ?: toSave.id
            userPreferences.setAiSettings(
                state.settings.copy(services = services, activeId = activeId)
            )
            _uiState.update { it.copy(draft = null, advancedExpanded = false, message = "已保存") }
        }
    }

    fun delete(service: AiService) {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            val services = settings.services.filterNot { it.id == service.id }
            userPreferences.setAiSettings(
                settings.copy(
                    services = services,
                    // 删掉的正好是当前使用时清空 activeId，交给 resolveActive 回退到第一个启用的
                    activeId = settings.activeId?.takeIf { it != service.id }
                )
            )
            _uiState.update { it.copy(message = "已删除「${service.displayName}」") }
        }
    }

    /** 一键切换：这就是「切换 AI」弹层点一下做的事。 */
    fun setActive(service: AiService) {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            userPreferences.setAiSettings(settings.copy(activeId = service.id))
            _uiState.update { it.copy(message = "已切换到「${service.displayName}」") }
        }
    }

    fun setEnabled(service: AiService, enabled: Boolean) {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            userPreferences.setAiSettings(
                settings.copy(
                    services = settings.services.map {
                        if (it.id == service.id) it.copy(enabled = enabled) else it
                    }
                )
            )
        }
    }

    fun setFallbackOnFailure(enabled: Boolean) {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            userPreferences.setAiSettings(settings.copy(fallbackOnFailure = enabled))
        }
    }

    // ── 连接测试 ──────────────────────────────────────────────────────

    /**
     * 测试草稿的连接。
     *
     * 用**草稿**而不是已保存的值：用户改完端点想先试一下再存，测已保存的没意义。
     */
    fun testDraft() {
        val draft = _uiState.value.draft ?: return
        if (!draft.isConfigured) {
            _uiState.update { it.copy(testResult = "端点、API Key、模型三项都要填") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(testInProgress = true, testResult = null) }
            val outcome = runCatching {
                llmApiClient.chat(
                    endpoint = draft.endpoint,
                    apiKey = draft.apiKey,
                    request = ChatCompletionRequest(
                        model = draft.model,
                        messages = listOf(
                            ChatMessageDto(role = "user", content = textContent("你好，请回复「连接成功」"))
                        ),
                        temperature = draft.temperature.toDouble(),
                        maxTokens = draft.maxTokens
                    )
                )
            }
            _uiState.update {
                it.copy(
                    testInProgress = false,
                    testResult = outcome.fold(
                        onSuccess = { "连接成功" },
                        onFailure = { e -> "连接失败：${e.message ?: "未知错误"}" }
                    )
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** 首次进入且一个服务都没有时，直接落到"新建"表单，省掉一次点击。 */
    fun autoStartCreateIfEmpty() {
        viewModelScope.launch {
            val settings = userPreferences.aiSettings.first()
            if (settings.services.isEmpty() && _uiState.value.draft == null) {
                startCreate()
            }
        }
    }
}
