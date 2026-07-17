package plus.rua.project.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors

/**
 * 相机拍摄页面，使用 CameraX 提供应用内预览与拍照。
 *
 * 进入时请求 CAMERA 权限；权限通过后绑定预览，用户点击快门将照片写入临时文件，
 * 然后通过 [onPhotoCaptured] 回调把临时文件路径回传（由 Activity 跳转编辑器页）。
 *
 * @param onBack 取消拍摄返回回调
 * @param onPhotoCaptured 拍照成功回调，参数为临时照片文件绝对路径
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onPhotoCaptured: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(context.checkCameraPermission())
    }
    var isCapturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }

    // 运行时权限请求 launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // 进入时若无权限则发起请求
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { testTagsAsResourceId = true }
    ) {
        when {
            !hasCameraPermission -> PermissionDeniedContent(onBack = onBack)

            isCapturing -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )

            else -> CameraPreview(
                context = context,
                lifecycleOwner = lifecycleOwner,
                onBack = onBack,
                onCaptured = { path ->
                    isCapturing = false
                    onPhotoCaptured(path)
                },
                onError = { msg ->
                    isCapturing = false
                    captureError = msg
                },
                setCapturing = { isCapturing = it }
            )
        }

        captureError?.let { msg ->
            Text(
                text = msg,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 160.dp)
            )
        }
    }
}

@Composable
private fun CameraPreview(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onBack: () -> Unit,
    onCaptured: (String) -> Unit,
    onError: (String) -> Unit,
    setCapturing: (Boolean) -> Unit
) {
    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                bindCameraUseCases(
                    context = ctx,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    imageCapture = imageCapture,
                    lensFacing = lensFacing
                )
                previewView
            },
            update = { previewView ->
                bindCameraUseCases(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    imageCapture = imageCapture,
                    lensFacing = lensFacing
                )
            }
        )

        // 顶部返回按钮
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .testTag("camera_back")
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "返回",
                tint = Color.White
            )
        }

        // 底部控制栏：切换镜头（左）/ 快门（中）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier.testTag("camera_switch")
            ) {
                Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = "切换镜头",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // 快门按钮：外圈白色环 + 内圈白色实心
            IconButton(
                onClick = {
                    val photoFile = createTempPhotoFile(context)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    setCapturing(true)
                    imageCapture.takePicture(
                        outputOptions,
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                onCaptured(photoFile.absolutePath)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                onError("拍照失败：${exception.message}")
                            }
                        }
                    )
                },
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
                    .testTag("camera_shutter")
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun PermissionDeniedContent(onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "需要相机权限才能拍照",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "请在系统设置中授权后重试",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    imageCapture: ImageCapture,
    lensFacing: Int
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        runCatching {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageCapture
            )
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun Context.checkCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun createTempPhotoFile(context: Context): File {
    val dir = File(context.filesDir, "Pictures/date_recorder").apply { mkdirs() }
    return File(dir, "tmp_${System.currentTimeMillis()}.jpg")
}
