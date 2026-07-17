package plus.rua.project.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
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
        if (!hasCameraPermission) {
            PermissionDeniedContent(onBack = onBack)
        } else {
            // 关键：CameraPreview 必须始终留在组合中，不能被 isCapturing 替换。
            // 一旦 isCapturing=true 时移除 CameraPreview，其 remember 的 imageCapture、
            // LaunchedEffect 绑定都会失效，相机 unbindAll + clearPipeline，
            // 而此时 takePicture 的异步请求还在排队，导致拍照无法完成（第一下点击失效）。
            // loading 指示改为叠加覆盖层。
            CameraPreview(
                context = context,
                lifecycleOwner = lifecycleOwner,
                isCapturing = isCapturing,
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

            if (isCapturing) {
                // 半透明遮罩 + loading，覆盖在预览之上但不移除预览
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
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
    isCapturing: Boolean,
    onBack: () -> Unit,
    onCaptured: (String) -> Unit,
    onError: (String) -> Unit,
    setCapturing: (Boolean) -> Unit
) {
    val imageCapture = remember {
        // 设置目标旋转角度，让 CameraX 写入正确的 EXIF orientation，
        // 配合 PhotoProcessor 读取 EXIF 后即可得到正向图片。
        ImageCapture.Builder()
            .setTargetRotation(context.getDisplayRotation())
            .build()
    }
    // 注意：不要在 onDispose 中 shutdown executor。
    // CameraX 1.5 的 takePicture 在 onImageSaved 之后内部仍可能向 executor 提交收尾任务，
    // 过早 shutdown 会导致 RejectedExecutionException 崩溃。应用进程结束时线程池自然回收。
    val executor = remember { Executors.newSingleThreadExecutor() }
    // 主线程 Handler，用于把拍照回调从 executor 线程切回 UI 线程后再触发跳转/状态更新
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    // 持有 PreviewView 引用：由 AndroidView.factory 创建后暴露给绑定逻辑。
    // 不用 remember{PreviewView(context)} 自建 —— AndroidView 需要自己管理 View 生命周期
    // （attach/detach/Surface 创建），外部 remember 的 View 与 AndroidView 内部生命周期冲突，
    // 会导致 Surface 反复 abandon/recreate，进而相机反复 bind/unbind。
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    // 等真正 attach 到窗口、Surface 就绪后再绑定相机
                    post {
                        previewView = this
                    }
                }
            }
            // 不在 update 里重新绑定相机，避免每次重组触发解绑重绑
        )

        // 镜头切换时重新绑定。
        // 注意：previewView 首次就绪也会触发（从 null → PreviewView），这是预期的首次绑定。
        LaunchedEffect(lensFacing, previewView) {
            val pv = previewView ?: return@LaunchedEffect
            bindCameraUseCases(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = pv,
                imageCapture = imageCapture,
                lensFacing = lensFacing
            )
        }

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

            // 快门按钮：外圈白色环 + 内圈白色实心。
            // 关键：拍照中（isCapturing=true）禁用，防止连点导致启动多个 PhotoEditor。
            IconButton(
                enabled = !isCapturing,
                onClick = {
                    val photoFile = createTempPhotoFile(context)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    setCapturing(true)
                    imageCapture.takePicture(
                        outputOptions,
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                // 拍照回调运行在 executor 线程，必须切回主线程
                                // 再触发 Activity 跳转与状态更新，避免线程安全问题
                                mainHandler.post { onCaptured(photoFile.absolutePath) }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "onError: 拍照失败 code=${exception.imageCaptureError} msg=${exception.message}", exception)
                                val msg = "拍照失败：${exception.message}"
                                mainHandler.post { onError(msg) }
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

private fun Context.getDisplayRotation(): Int {
    val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        display
    } else {
        @Suppress("DEPRECATION") //getDisplay 在 API30+ 弃用，旧版本必须用此 API
        (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
    }
    return when (display?.rotation) {
        android.view.Surface.ROTATION_90 -> 90
        android.view.Surface.ROTATION_180 -> 180
        android.view.Surface.ROTATION_270 -> 270
        else -> 0
    }
}

private const val TAG = "DateRecorder/Camera"

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
