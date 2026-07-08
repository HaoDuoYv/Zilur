package com.example.zhilu.ui.camera

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun beginCapture() {
        _uiState.update { it.copy(isCapturing = true, error = null, capturedImageUri = null) }
    }

    fun failCapture(message: String, throwable: Throwable? = null) {
        _uiState.update {
            it.copy(
                isCapturing = false,
                error = throwable?.message ?: message
            )
        }
    }

    fun saveCapture(file: File) {
        viewModelScope.launch {
            val uri = Uri.fromFile(file).toString()
            val media = Media(
                uri = uri,
                size = file.length(),
                createdAt = System.currentTimeMillis()
            )
            when (val result = mediaRepository.insertMedia(media)) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            lastCaptureLabel = "Captured image #${result.data}",
                            capturedImageUri = ImageBlockContent.fromMedia(result.data, uri),
                            error = null
                        )
                    }
                }
                is RepositoryResult.Error -> failCapture(result.message, result.throwable)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
