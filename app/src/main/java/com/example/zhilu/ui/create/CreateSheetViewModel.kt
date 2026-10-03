package com.example.zhilu.ui.create

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.usecase.ImportKnowledgeUseCase
import com.example.zhilu.domain.usecase.RestoreJsonBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 底栏中央 ＋ 弹出的「新建 / 导入」弹层（设计原型 `zhilu_full_prototype.html`）。
 *
 * 新建入口从首页 FAB 移到这里之后，**导入也一并搬过来**：新建与导入本来就是
 * "往知识库里加东西"的同一件事，放在同一个弹层里比分散在设置页更好找。
 */
@HiltViewModel
class CreateSheetViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val restoreJsonBackupUseCase: RestoreJsonBackupUseCase,
    private val importKnowledgeUseCase: ImportKnowledgeUseCase
) : ViewModel() {

    data class UiState(
        val isWorking: Boolean = false,
        /** 一次性提示；由 UI 弹完即消费。 */
        val message: String? = null,
        /** .dtk 的预览确认（与设置页同一套交互）。 */
        val dtkPreview: ImportKnowledgeUseCase.Preview? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 新建空白笔记；成功后把新 id 交给调用方去导航。 */
    fun createBlankNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            when (val result = noteRepository.insertNote(Note())) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(isWorking = false) }
                    onCreated(result.data.id)
                }

                is RepositoryResult.Error ->
                    _uiState.update { it.copy(isWorking = false, message = "新建失败：${result.message}") }
            }
        }
    }

    fun importJson(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            runCatching { restoreJsonBackupUseCase(uri) }.fold(
                onSuccess = { summary ->
                    val message = "已导入 ${summary.noteCount} 条笔记" +
                        if (summary.mediaCount == 0) "" else "、${summary.mediaCount} 张图片"
                    _uiState.update { it.copy(isWorking = false, message = message) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(isWorking = false, message = "导入失败：${throwable.message}")
                    }
                }
            )
        }
    }

    /** 选完 .dtk 先解析出预览，让用户确认后再落库（与设置页口径一致）。 */
    fun parseDtk(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            importKnowledgeUseCase.parsePreview(uri)
                .onSuccess { preview ->
                    _uiState.update { it.copy(isWorking = false, dtkPreview = preview) }
                    pendingDtkUri = uri
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isWorking = false, message = "无法解析文件：${throwable.message}")
                    }
                }
        }
    }

    fun confirmDtkImport() {
        val uri = pendingDtkUri ?: return
        pendingDtkUri = null
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, dtkPreview = null) }
            importKnowledgeUseCase.import(uri)
                .onSuccess { _uiState.update { it.copy(isWorking = false, message = "导入成功") } }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isWorking = false, message = "导入失败：${throwable.message}")
                    }
                }
        }
    }

    fun dismissDtkPreview() {
        pendingDtkUri = null
        _uiState.update { it.copy(dtkPreview = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private var pendingDtkUri: Uri? = null
}
