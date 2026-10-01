package com.example.zhilu.ui.settings

import android.net.Uri
import com.example.zhilu.domain.model.AiConfig
import com.example.zhilu.data.datastore.ThemeMode
import com.example.zhilu.domain.usecase.ImportKnowledgeUseCase

data class SettingsUiState(
    val noteCount: Int = 0,
    val tagCount: Int = 0,
    val mediaCount: Int = 0,
    val totalMediaSize: Long = 0L,
    val notificationPermissionGranted: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val remindersEnabled: Boolean = true,
    val aiConfig: AiConfig = AiConfig(),
    val aiTestInProgress: Boolean = false,
    val aiTestResult: String? = null,
    val exportMessage: String? = null,
    val isWorking: Boolean = false,
    val error: String? = null,
    /** .dtk 单篇导入的预览确认状态。 */
    val importPreview: ImportKnowledgeUseCase.Preview? = null,
    val pendingImportUri: Uri? = null
)