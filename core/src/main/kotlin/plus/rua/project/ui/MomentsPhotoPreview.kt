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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.zoomimage.SketchZoomAsyncImage
import com.github.panpf.zoomimage.rememberSketchZoomState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 朋友圈全屏大图预览组件，完整复刻微信朋友圈图片预览体验：
 * 纯黑背景沉浸式查看、多图左右滑动切换、底部白色圆点与顶部张数指示器、双击/双指缩放、单击退出、长按呼出底部操作菜单。
 *
 * @param photos 要预览的图片路径/URI 列表
 * @param initialIndex 初始展示的图片索引（从 0 开始）
 * @param onDismiss 关闭大图预览回调（单击屏幕或按返回键时触发）
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsPhotoPreviewDialog(
    photos: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
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
                val photoUri = remember(rawPath) { resolvePhotoUri(rawPath) }
                val asyncImageState = rememberAsyncImageState()
                val zoomState = rememberSketchZoomState()

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
                            coroutineScope.launch {
                                val success = saveImageToGallery(context, currentPath)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (success) "已保存到系统相册" else "保存失败",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
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

/**
 * 将指定路径的图片保存至系统相册。
 *
 * @param context 上下文
 * @param photoPath 图片路径（content:// 或本地文件路径）
 * @return 是否保存成功
 */
suspend fun saveImageToGallery(context: Context, photoPath: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val inputStream: InputStream? =
            if (photoPath.startsWith("http://") || photoPath.startsWith("https://")) {
                java.net.URL(photoPath).openConnection().apply {
                    connectTimeout = 10000
                    readTimeout = 30000
                }.getInputStream()
            } else if (photoPath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(photoPath))
            } else {
                val cleanPath = photoPath.removePrefix("file://")
                FileInputStream(File(cleanPath))
            }

        if (inputStream == null) return@runCatching false

        val remote = photoPath.startsWith("http")
        val extension = if (remote) "webp" else "jpg"
        val mimeType = if (remote) "image/webp" else "image/jpeg"
        val fileName = "yaya_moments_${System.currentTimeMillis()}.$extension"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YaYa")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@runCatching false

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                inputStream.use { input ->
                    input.copyTo(outputStream)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
            true
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val yaYaDir = File(picturesDir, "YaYa").apply { if (!exists()) mkdirs() }
            val destFile = File(yaYaDir, fileName)
            FileOutputStream(destFile).use { outputStream ->
                inputStream.use { input ->
                    input.copyTo(outputStream)
                }
            }
            true
        }
    }.getOrDefault(false)
}
