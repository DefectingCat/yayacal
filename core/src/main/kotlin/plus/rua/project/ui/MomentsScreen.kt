package plus.rua.project.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import plus.rua.project.MomentsStorage
import plus.rua.project.MomentsUiState
import plus.rua.project.MomentsViewModel

/**
 * 朋友圈页面，复刻经典微信朋友圈布局结构。
 * 包含沉浸式顶部渐变导航栏、可自定义相册封面（点击平滑展开放大动画与换封面）、用户名与悬浮跨界头像、以及底部朋友圈动态区域的空状态占位。
 *
 * @param onBack 点击返回按钮时触发
 * @param onPublish 点击右上角发布动态/相机按钮时触发
 * @param onCoverClick 点击相册封面占位区域时触发（为 null 时默认切换封面展开状态）
 * @param onAvatarClick 点击用户头像时触发（为 null 时默认拉起系统相册选择头像）
 * @param viewModel 朋友圈 ViewModel，默认从本地偏好存储加载
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsScreen(
    onBack: () -> Unit,
    onPublish: () -> Unit = {},
    onCoverClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    viewModel: MomentsViewModel = run {
        val context = LocalContext.current.applicationContext
        viewModel(
            factory =
            viewModelFactory {
                initializer {
                    MomentsViewModel(
                        storage = MomentsStorage.fromContext(context),
                        filesDir = context.filesDir,
                    )
                }
            },
        )
    },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isCoverExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = isCoverExpanded) {
        isCoverExpanded = false
    }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.setAvatarFromUri(context, uri)
            }
        }

    val coverPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.setCoverFromUri(context, uri)
                isCoverExpanded = false
            }
        }

    MomentsScreen(
        uiState = uiState,
        isCoverExpanded = isCoverExpanded,
        onBack = {
            if (isCoverExpanded) {
                isCoverExpanded = false
            } else {
                onBack()
            }
        },
        onPublish = onPublish,
        onCoverClick =
        onCoverClick ?: {
            isCoverExpanded = !isCoverExpanded
        },
        onChangeCoverClick = {
            coverPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onAvatarClick =
        onAvatarClick ?: {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onAvatarLongClick = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        modifier = modifier,
    )
}

/**
 * 朋友圈页面内容渲染组件（无状态）。
 *
 * @param uiState 朋友圈当前 UI 状态
 * @param isCoverExpanded 封面是否展开
 * @param onBack 点击返回按钮时触发
 * @param onPublish 点击右上角发布动态/相机按钮时触发
 * @param onCoverClick 点击相册封面占位区域时触发
 * @param onChangeCoverClick 点击“换封面”按钮时触发
 * @param onAvatarClick 点击用户头像时触发
 * @param onAvatarLongClick 长按用户头像时触发
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsScreen(
    uiState: MomentsUiState,
    onBack: () -> Unit,
    isCoverExpanded: Boolean = false,
    onPublish: () -> Unit = {},
    onCoverClick: () -> Unit = {},
    onChangeCoverClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onAvatarLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scrollAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                calculateTopBarAlpha(listState.firstVisibleItemScrollOffset, maxScrollOffset = 360f)
            }
        }
    }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .semantics { testTagsAsResourceId = true }
            .testTag("moments_screen"),
    ) {
        // 主滚动列表：从屏幕最顶端开始绘制，使相册封面能够沉浸延伸至状态栏之下
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // 头部：相册封面 + 用户名 + 跨界悬浮头像
            item(key = "header") {
                MomentsHeader(
                    username = uiState.username,
                    avatarPath = uiState.avatarPath,
                    coverPath = uiState.coverPath,
                    isCoverExpanded = isCoverExpanded,
                    onCoverClick = onCoverClick,
                    onChangeCoverClick = onChangeCoverClick,
                    onAvatarClick = onAvatarClick,
                    onAvatarLongClick = onAvatarLongClick,
                )
            }

            // 底部朋友圈动态部分：空状态占位
            item(key = "empty_content") {
                MomentsEmptyContent(
                    onPublish = onPublish,
                )
            }

            // 底部系统手势栏安全留白
            item(key = "bottom_spacer") {
                Spacer(
                    modifier =
                    Modifier
                        .navigationBarsPadding()
                        .height(16.dp),
                )
            }
        }

        // 顶部悬浮导航栏：随着列表滚动动态变化背景不透明度与图标颜色（封面展开时隐藏）
        if (!isCoverExpanded) {
            MomentsTopBar(
                title = "朋友圈",
                alpha = scrollAlpha,
                onBack = onBack,
                onPublish = onPublish,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/**
 * 顶部导航栏，支持随滑动距离平滑渐变背景色与图标样式。
 *
 * @param title 导航栏标题
 * @param alpha 渐变透明度（0f 为完全透明，1f 为完全不透明）
 * @param onBack 点击返回按钮回调
 * @param onPublish 点击发布按钮回调
 * @param modifier 布局修饰符
 */
@Composable
private fun MomentsTopBar(
    title: String,
    alpha: Float,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = 56.dp + statusBarHeight

    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha)
    val iconColor = lerp(Color.White, MaterialTheme.colorScheme.onSurface, alpha)
    val scrimAlpha = (1f - alpha).coerceIn(0f, 1f) * 0.3f

    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(topBarHeight)
            .background(backgroundColor),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 返回按钮
            IconButton(
                onClick = onBack,
                modifier =
                Modifier
                    .testTag("moments_back_button")
                    .background(Color.Black.copy(alpha = scrimAlpha), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = "返回",
                    tint = iconColor,
                    modifier = Modifier.size(28.dp),
                )
            }

            // 标题
            Text(
                text = title,
                style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 右上角发布相机按钮
            IconButton(
                onClick = onPublish,
                modifier =
                Modifier
                    .testTag("moments_publish_button")
                    .background(Color.Black.copy(alpha = scrimAlpha), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = "发布动态",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * 底部朋友圈动态空状态占位组件。
 *
 * @param onPublish 点击快捷发布按钮回调
 * @param modifier 布局修饰符
 */
@Composable
private fun MomentsEmptyContent(
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 44.dp, bottom = 64.dp)
            .testTag("moments_empty_placeholder"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(80.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DynamicFeed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "暂无朋友圈动态",
            style =
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "轻触右上角相机，分享你的生活点滴与精彩瞬间",
            style =
            MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onPublish,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.testTag("moments_empty_publish_button"),
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "发布第一条动态",
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * 根据列表垂直滚动偏移量计算顶部栏的不透明度 (0f - 1f)。
 *
 * @param scrollOffset 列表当前垂直滚动偏移量（像素）
 * @param maxScrollOffset 达到完全不透明所需的滚动距离阈值（像素）
 * @return 0f 到 1f 之间的不透明度
 */
fun calculateTopBarAlpha(
    scrollOffset: Int,
    maxScrollOffset: Float = 300f,
): Float {
    if (maxScrollOffset <= 0f) return 1f
    return (scrollOffset / maxScrollOffset).coerceIn(0f, 1f)
}
