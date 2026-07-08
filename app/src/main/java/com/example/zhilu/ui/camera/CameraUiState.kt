package com.example.zhilu.ui.camera

data class CameraUiState(
    val isCapturing: Boolean = false,
    val lastCaptureLabel: String? = null,
    val capturedImageUri: String? = null,
    val error: String? = null
)
