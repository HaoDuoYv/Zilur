package com.example.zhilu.ui.create

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.repository.CloudRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.usecase.ImportKnowledgeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 底栏中央 ＋ 弹出的「新建 / 导入」弹层（设计原型 `zhilu_full_prototype.html`）。
 *
 * 新建入口从首页 FAB 移到这里之后，**导入也一并搬过来**：新建与导入本来就是
 * "往知识库里加东西"的同一件事，放在同一个弹层里比分散在设置页更好找。
 *
 * 导入**只有 `.dtk`** —— 单一入口、单一格式；旧的 JSON 备份通道已下线。
 */
@HiltViewModel
class CreateSheetViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val cloudRepository: CloudRepository,
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

    /**
     * 「云端恢复」：把云端那份 `.dtk` 下到缓存，然后走**和本地导入完全一样**的
     * 「预览 → 确认」流程 —— 不再写第二套导入路径，也就不会出现"云端恢复没有预览、
     * 直接覆盖数据库"这种事。
     *
     * 地址没填 / 没登录 / 没备份，都由仓库层返回一句可读的提示。
     */
    fun restoreFromCloud() {
        viewModelScope.launch {
            // 先把"没连上"的两种情况单独讲清楚：只说"还没有填写服务地址"，
            // 用户不知道要去哪填
            val settings = cloudRepository.settings.first()
            if (!settings.isConfigured) {
                _uiState.update {
                    it.copy(message = "还没有连接云端：去「我的 → 云端服务」填服务地址并登录")
                }
                return@launch
            }
            if (!settings.isSignedIn) {
                _uiState.update { it.copy(message = "云端还没登录：去「我的 → 云端服务」登录") }
                return@launch
            }

            _uiState.update { it.copy(isWorking = true) }
            when (val result = cloudRepository.downloadBackup()) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(isWorking = false) }
                    pendingDtkTempFile = result.data
                    parseDtk(Uri.fromFile(result.data))
                }

                is RepositoryResult.Error ->
                    _uiState.update { it.copy(isWorking = false, message = result.message) }
            }
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
                    // 解析失败时云端下载的临时件就没有用了，顺手清掉
                    pendingDtkTempFile?.delete()
                    pendingDtkTempFile = null
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
                .onSuccess { summary ->
                    val message = "已导入 ${summary.noteCount} 篇笔记" +
                        if (summary.mediaCount == 0) "" else "、${summary.mediaCount} 张图片"
                    _uiState.update { it.copy(isWorking = false, message = message) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isWorking = false, message = "导入失败：${throwable.message}")
                    }
                }
            cleanupTempFile()
        }
    }

    fun dismissDtkPreview() {
        pendingDtkUri = null
        _uiState.update { it.copy(dtkPreview = null) }
        cleanupTempFile()
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * 云端下载的临时件：导入或取消后必须删掉 —— 它是一份完整的笔记备份
     * （含图片），留在 cache 里既占空间也不该长期存在。
     */
    private fun cleanupTempFile() {
        pendingDtkTempFile?.delete()
        pendingDtkTempFile = null
    }

    private var pendingDtkUri: Uri? = null
    private var pendingDtkTempFile: File? = null
}
