package plus.rua.project.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.ImageResult
import com.github.panpf.sketch.request.LoadState
import com.github.panpf.sketch.request.Progress
import com.github.panpf.sketch.request.ProgressListener
import com.github.panpf.sketch.sketch
import com.github.panpf.sketch.util.DownloadData
import com.github.panpf.zoomimage.SketchZoomAsyncImage
import com.github.panpf.zoomimage.rememberSketchZoomState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import plus.rua.project.MomentPhotoMetadata
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

/**
 * 朋友圈全屏图片预览，默认加载高清预览，点击左下角按钮后才下载当前原图。
 * 支持多图翻页、双击/双指缩放、单击退出与长按保存；保存复用当前规格的下载缓存。
 *
 * @param photos 要预览的图片路径/URI 列表
 * @param initialIndex 初始展示的图片索引（从 0 开始）
 * @param onDismiss 关闭大图预览回调（单击屏幕或按返回键时触发）
 * @param photoMetadata 按原图地址索引的远端规格；缺失时默认使用列表缩略图，点击按钮后才下载原图
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsPhotoPreviewDialog(
    photos: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    photoMetadata: Map<String, MomentPhotoMetadata> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    if (photos.isEmpty()) return

    val validInitialIndex = initialIndex.coerceIn(0, photos.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = validInitialIndex,
        pageCount = { photos.size },
    )
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showActionMenu by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var failedSavePath by remember { mutableStateOf<String?>(null) }
    var originalPhotos by remember(photos) { mutableStateOf(emptySet<String>()) }
    var loadingOriginalPhotos by remember(photos) { mutableStateOf(emptySet<String>()) }
    var failedOriginalPhotos by remember(photos) { mutableStateOf(emptySet<String>()) }
    var thumbnailFallbacks by remember(photos) { mutableStateOf(emptySet<String>()) }
    val originalProgress = remember(photos) { mutableStateMapOf<String, Int>() }
    val loadOriginal: (String) -> Unit = { path ->
        if (path !in loadingOriginalPhotos && path !in originalPhotos) {
            loadingOriginalPhotos += path
            failedOriginalPhotos -= path
            originalProgress.remove(path)
            coroutineScope.launch {
                try {
                    val uri = momentsOriginalUri(path) ?: error("图片地址无效")
                    context.sketch.executeDownload(
                        ImageRequest(context, uri) {
                            addProgressListener(object : ProgressListener {
                                override fun onUpdateProgress(request: ImageRequest, progress: Progress) {
                                    if (progress.totalLength > 0) {
                                        coroutineScope.launch { originalProgress[path] = (progress.decimalProgress * 100).toInt().coerceIn(0, 100) }
                                    }
                                }
                            })
                        },
                    ).getOrThrow()
                    originalPhotos += path
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failedOriginalPhotos += path
                } finally {
                    loadingOriginalPhotos -= path
                }
            }
        }
    }
    val savePhoto: (String) -> Unit = { path ->
        if (!isSaving) {
            isSaving = true
            failedSavePath = null
            coroutineScope.launch {
                try {
                    if (saveImageToGallery(context, path)) {
                        Toast.makeText(context, "已保存到系统相册", Toast.LENGTH_SHORT).show()
                    } else {
                        failedSavePath = path
                    }
                } finally {
                    isSaving = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
        DialogProperties(
            decorFitsSystemWindows = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            val window = (view.parent as? DialogWindowProvider)?.window
                ?: (view.context as? Activity)?.window
            val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            val originalLightStatus = insetsController?.isAppearanceLightStatusBars
            val originalLightNav = insetsController?.isAppearanceLightNavigationBars
            insetsController?.isAppearanceLightStatusBars = false
            insetsController?.isAppearanceLightNavigationBars = false
            onDispose {
                if (originalLightStatus != null) insetsController.isAppearanceLightStatusBars = originalLightStatus
                if (originalLightNav != null) insetsController.isAppearanceLightNavigationBars = originalLightNav
            }
        }

        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB3C5AE))) {
            Box(
                modifier =
                modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .semantics { testTagsAsResourceId = true }
                    .testTag("moments_photo_preview_dialog"),
                contentAlignment = Alignment.Center,
            ) {
                // 1. 水平滑动图片分页
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val rawPath = photos[page]
                    val photoUri = momentsPreviewUri(rawPath, photoMetadata[rawPath], rawPath in originalPhotos, rawPath in thumbnailFallbacks)
                    val thumbnailUri = momentsThumbnailUri(rawPath)
                    val placeholderUri = if (rawPath in originalPhotos) momentsPreviewUri(rawPath, photoMetadata[rawPath], thumbnailOnly = rawPath in thumbnailFallbacks) else thumbnailUri
                    val asyncImageState = rememberAsyncImageState()
                    val zoomState = rememberSketchZoomState()
                    LaunchedEffect(photoUri, asyncImageState.result) {
                        val failed = asyncImageState.result as? ImageResult.Error
                        if (failed != null && failed.request.uri.toString() == photoUri) {
                            if (rawPath in originalPhotos) {
                                originalPhotos -= rawPath
                                failedOriginalPhotos += rawPath
                            } else if (photoUri != thumbnailUri) {
                                thumbnailFallbacks += rawPath
                            }
                        }
                    }

                    Box(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (showActionMenu) {
                                        showActionMenu = false
                                    } else {
                                        onDismiss()
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photoUri != null) {
                            if (photoUri != placeholderUri && asyncImageState.loadState !is LoadState.Success) {
                                AsyncImage(
                                    uri = placeholderUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            SketchZoomAsyncImage(
                                uri = photoUri,
                                contentDescription = "朋友圈大图预览",
                                state = asyncImageState,
                                zoomState = zoomState,
                                alignment = Alignment.Center,
                                contentScale = ContentScale.Fit,
                                onTap = {
                                    if (showActionMenu) {
                                        showActionMenu = false
                                    } else {
                                        onDismiss()
                                    }
                                },
                                onLongPress = {
                                    showActionMenu = true
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        when {
                            photoUri == null || asyncImageState.loadState is LoadState.Error -> MomentsStateContent(
                                title = "图片暂时打不开",
                                description = "可以重新加载，或返回后稍后再看",
                                isError = true,
                                actionLabel = if (photoUri == null) "返回" else "重新加载",
                                onAction = if (photoUri == null) {
                                    onDismiss
                                } else {
                                    { asyncImageState.restart() }
                                },
                            )

                            asyncImageState.loadState == null || asyncImageState.loadState is LoadState.Started -> MomentsLoadingSpinner(color = Color.White)
                        }
                    }
                }

                val currentPath = photos[pagerState.currentPage]
                if (!showActionMenu && isMomentsRemotePhoto(currentPath)) {
                    val isLoadingOriginal = currentPath in loadingOriginalPhotos
                    val isOriginal = currentPath in originalPhotos
                    val progress = originalProgress[currentPath]
                    val size = photoMetadata[currentPath]?.originalBytes?.takeIf { it > 0 }?.let(::momentsPhotoSizeLabel)
                    Surface(
                        onClick = { loadOriginal(currentPath) },
                        enabled = !isLoadingOriginal && !isOriginal,
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, bottom = if (photos.size > 1) 64.dp else 18.dp).testTag("moments_preview_original_button"),
                    ) {
                        Row(
                            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isLoadingOriginal) MomentsLoadingSpinner(color = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                text = when {
                                    isOriginal -> "已加载原图"
                                    isLoadingOriginal -> "加载原图${progress?.let { " $it%" } ?: "…"}"
                                    currentPath in failedOriginalPhotos -> "原图加载失败，点击重试"
                                    size != null -> "查看原图（$size）"
                                    else -> "查看原图"
                                },
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // 2. 顶部张数指示器（多图时展示，如 5/9）
                if (photos.size > 1 && !showActionMenu) {
                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("moments_preview_page_number"),
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${photos.size}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // 3. 底部白色圆点指示器（多图时展示）
                if (photos.size > 1 && !showActionMenu) {
                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("moments_preview_dots_indicator"),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(photos.size) { index ->
                                val isSelected = pagerState.currentPage == index
                                Box(
                                    modifier =
                                    Modifier
                                        .size(if (isSelected) 7.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) Color.White else Color.White.copy(alpha = 0.45f),
                                        ),
                                )
                            }
                        }
                    }
                }

                if (isSaving) {
                    Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MomentsLoadingSpinner(color = Color.White)
                        Text("正在保存图片…", color = Color.White, fontSize = 13.sp)
                    }
                }
                failedSavePath?.let { path ->
                    MomentsErrorNotice(
                        error = "保存图片失败",
                        title = "图片还没保存到相册",
                        hint = "请检查网络、相册权限和存储空间",
                        actionLabel = "重新保存",
                        onAction = { savePhoto(path) },
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp, start = 16.dp, end = 16.dp),
                    )
                }

                // 4. 微信风格长按底部操作菜单
                AnimatedVisibility(
                    visible = showActionMenu,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    MomentsPreviewActionMenu(
                        onSavePhoto = {
                            val currentPath = photos.getOrNull(pagerState.currentPage)
                            if (currentPath != null) {
                                momentsPreviewUri(currentPath, photoMetadata[currentPath], currentPath in originalPhotos, currentPath in thumbnailFallbacks)?.let(savePhoto)
                            }
                            showActionMenu = false
                        },
                        onFavorite = {
                            Toast.makeText(context, "已收藏", Toast.LENGTH_SHORT).show()
                            showActionMenu = false
                        },
                        onSendToFriend = {
                            Toast.makeText(context, "发送给朋友功能正在开发中", Toast.LENGTH_SHORT).show()
                            showActionMenu = false
                        },
                        onCancel = { showActionMenu = false },
                    )
                }
            }
        }
    }
}

/**
 * 微信大图预览长按底部操作菜单。
 *
 * @param onSavePhoto 保存图片回调
 * @param onFavorite 收藏回调
 * @param onSendToFriend 发送给朋友回调
 * @param onCancel 取消菜单回调
 * @param modifier 布局修饰符
 */
@Composable
private fun MomentsPreviewActionMenu(
    onSavePhoto: () -> Unit,
    onFavorite: () -> Unit,
    onSendToFriend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag("moments_preview_action_menu"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 主操作选项卡片
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF2C2C2C),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ActionMenuItem(text = "发送给朋友", onClick = onSendToFriend)
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                ActionMenuItem(text = "收藏", onClick = onFavorite)
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                ActionMenuItem(text = "保存图片", onClick = onSavePhoto)
            }
        }

        // 取消按钮卡片
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF2C2C2C),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ActionMenuItem(text = "取消", onClick = onCancel, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 菜单单项条目。
 */
@Composable
private fun ActionMenuItem(
    text: String,
    onClick: () -> Unit,
    fontWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 将本地文件路径或 content URI 规范化为 Sketch 可加载的 URI 字符串。
 *
 * @param path 原始路径字符串
 * @return 规范化的 URI 字符串
 */
fun resolvePhotoUri(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return if (path.startsWith("content://") || path.startsWith("file://") || path.startsWith("http://") || path.startsWith("https://") || path.startsWith("android.resource://")) {
        path
    } else {
        val file = File(path)
        if (file.exists()) "file://${file.absolutePath}" else "file://$path"
    }
}

/** 远端朋友圈图片才支持服务端分档；本地草稿和其他来源保留原地址。 */
internal fun isMomentsRemotePhoto(path: String): Boolean = path.toHttpUrlOrNull()?.encodedPath?.startsWith("/api/v1/media/") == true

/** 显式清除其他规格并添加缓存版本；旧后端仍支持 thumbnail 参数。 */
internal fun momentsThumbnailUri(path: String?): String? = resolvePhotoUri(path)?.let {
    if (isMomentsRemotePhoto(it)) {
        it.toHttpUrlOrNull()!!.newBuilder().removeAllQueryParameters("variant").setQueryParameter("thumbnail", "true").setQueryParameter("v", "2").build().toString()
    } else {
        it
    }
}

/** 仅在用户点击后使用此地址；保留操作开始时捕获的服务地址与查看账号。 */
internal fun momentsOriginalUri(path: String?): String? = resolvePhotoUri(path)?.let {
    if (isMomentsRemotePhoto(it)) {
        it.toHttpUrlOrNull()!!.newBuilder().removeAllQueryParameters("variant").removeAllQueryParameters("thumbnail").removeAllQueryParameters("v").build().toString()
    } else {
        it
    }
}

/** 默认只能选已就绪的高清预览或小缩略图；缩放和翻页不会改变原图选择。 */
internal fun momentsPreviewUri(path: String?, metadata: MomentPhotoMetadata? = null, original: Boolean = false, thumbnailOnly: Boolean = false): String? {
    val uri = resolvePhotoUri(path) ?: return null
    if (!isMomentsRemotePhoto(uri)) return uri
    if (original) return momentsOriginalUri(uri)
    if (thumbnailOnly || metadata?.previewAvailable != true) return momentsThumbnailUri(uri)
    // 旧后端忽略 variant 时仍按 thumbnail=true 返回小图，避免旧缓存元数据触发原图下载。
    return uri.toHttpUrlOrNull()!!.newBuilder().setQueryParameter("thumbnail", "true").setQueryParameter("variant", "preview").setQueryParameter("v", "2").build().toString()
}

internal fun momentsPhotoSizeLabel(bytes: Long): String = if (bytes >= 1_000_000) String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0) else "${(bytes + 999) / 1000} KB"

/**
 * 将指定路径的图片保存至系统相册。
 *
 * @param context 上下文
 * @param photoPath 当前显示规格的远端地址或本地路径；远端下载复用 Sketch 缓存
 * @return 是否保存成功
 */
suspend fun saveImageToGallery(context: Context, photoPath: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val inputStream: InputStream? = when {
            photoPath.startsWith("http://") || photoPath.startsWith("https://") -> {
                when (val data = context.sketch.executeDownload(ImageRequest(context, photoPath)).getOrThrow()) {
                    is DownloadData.Cache -> FileInputStream(File(data.path.toString()))
                    is DownloadData.Bytes -> ByteArrayInputStream(data.bytes)
                }
            }

            photoPath.startsWith("content://") -> context.contentResolver.openInputStream(Uri.parse(photoPath))

            else -> FileInputStream(File(photoPath.removePrefix("file://")))
        }
        inputStream?.use { input ->
            val remote = photoPath.startsWith("http")
            val extension = if (remote) "webp" else "jpg"
            val mimeType = if (remote) "image/webp" else "image/jpeg"
            val fileName = "yaya_moments_${System.currentTimeMillis()}.$extension"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YaYa")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                try {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("无法写入相册")
                    output.use { input.copyTo(it) }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    check(context.contentResolver.update(uri, values, null, null) > 0)
                    true
                } catch (error: Exception) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    throw error
                }
            } else {
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YaYa")
                check(directory.isDirectory || directory.mkdirs())
                val destination = File(directory, fileName)
                try {
                    FileOutputStream(destination).use { input.copyTo(it) }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    true
                } catch (error: Exception) {
                    destination.delete()
                    throw error
                }
            }
        } ?: false
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}
