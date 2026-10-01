package com.example.zhilu.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.Spacing
import java.io.File

@Composable
fun CameraScreen(
    navController: NavHostController,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current
    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    // 拍照结果回传编辑页：读取方在 NoteEditScreen 的 ON_RESUME 中消费同一 key。
    LaunchedEffect(state.capturedImageUri) {
        state.capturedImageUri?.let { uri ->
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set("capturedImageUri", uri)
            navController.popBackStack()
        }
    }
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return@LaunchedEffect
        }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    context as androidx.lifecycle.LifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
                imageCapture = capture
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CameraPermissionPanel(
                    onGrantPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }

            CameraTopBar(
                onBack = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.TopStart)
            )

            if (hasCameraPermission) {
                CameraShutter(
                    isCapturing = state.isCapturing,
                    onCapture = {
                        val capture = imageCapture
                        if (capture == null) {
                            viewModel.failCapture("Camera is not ready")
                            return@CameraShutter
                        }
                        val file = createImageFile(context.filesDir)
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        viewModel.beginCapture()
                        capture.takePicture(
                            options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    viewModel.saveCapture(file)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    viewModel.failCapture("Capture failed", exception)
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = Spacing.Xl)
                )
            }
        }
    }
}

private fun createImageFile(root: File): File {
    val directory = File(root, "media").also { it.mkdirs() }
    val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
        .format(System.currentTimeMillis())
    return File(directory, "IMG-$timestamp.jpg")
}