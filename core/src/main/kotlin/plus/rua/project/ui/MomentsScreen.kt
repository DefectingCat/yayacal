package plus.rua.project.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.panpf.sketch.AsyncImage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import plus.rua.project.MomentPost
import plus.rua.project.MomentsStorage
import plus.rua.project.MomentsUiState
import plus.rua.project.MomentsViewModel
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 朋友圈页面，复刻经典微信朋友圈布局结构。
 * 包含沉浸式顶部渐变导航栏、可自定义相册封面（点击平滑展开放大动画与换封面）、用户名与悬浮跨界头像、以及底部朋友圈动态区域的空状态占位。
 *
 * @param onBack 点击返回按钮时触发
 * @param onPublish 点击右上角发布动态/相机按钮时触发
 * @param onCoverClick 点击相册封面占位区域时触发（为 null 时默认切换封面展开状态）
 * @param onAvatarClick 点击用户头像时触发（为 null 时默认拉起系统相册选择头像）
 * @param onPostClick 点击动态正文或互动区时触发，传递动态 ID
 * @param onCommentClick 点击动态“评论”操作时触发，传递动态 ID
 * @param viewModel 朋友圈 ViewModel，默认从本地偏好存储加载
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsScreen(
    onBack: () -> Unit,
    onPublish: () -> Unit = {},
    onCoverClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    onPostClick: (String) -> Unit = {},
    onCommentClick: (String) -> Unit = onPostClick,
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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPosts()
    }

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
        onCoverClick = {
            isCoverExpanded = !isCoverExpanded
            onCoverClick?.invoke()
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
        onDeletePost = viewModel::deletePost,
        onPostClick = onPostClick,
        onCommentClick = onCommentClick,
        onLikePost = viewModel::toggleLike,
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
 * @param onDeletePost 确认删除某条本人动态时触发
 * @param onPostClick 点击动态正文或互动区时触发
 * @param onCommentClick 点击“评论”操作时触发
 * @param onLikePost 点击“赞/取消”操作时触发
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
    onDeletePost: (String) -> Unit = {},
    onPostClick: (String) -> Unit = {},
    onCommentClick: (String) -> Unit = onPostClick,
    onLikePost: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var previewPhotos by remember { mutableStateOf<List<String>?>(null) }
    var previewIndex by remember { mutableStateOf(0) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
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

            if (uiState.posts.isEmpty()) {
                // 底部朋友圈动态部分：空状态占位
                item(key = "empty_content") {
                    MomentsEmptyContent(
                        onPublish = onPublish,
                    )
                }
            } else {
                items(uiState.posts, key = { it.id }) { post ->
                    Card(
                        onClick = { onPostClick(post.id) },
                        shape = RoundedCornerShape(0.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        MomentFeedItem(
                            post = post,
                            authorName = uiState.username,
                            avatarPath = uiState.avatarPath,
                            onDelete = { pendingDeleteId = post.id },
                            onLike = { onLikePost(post.id) },
                            onComment = { onCommentClick(post.id) },
                            onPhotoClick = { photos, index ->
                                previewPhotos = photos
                                previewIndex = index
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        if (post.isLikedByMe || post.comments.isNotEmpty()) {
                            Column(
                                Modifier.padding(start = 68.dp, end = 16.dp, bottom = 12.dp)
                                    .fillMaxWidth().background(momentsBarColor(), RoundedCornerShape(4.dp)).padding(8.dp),
                            ) {
                                if (post.isLikedByMe) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.FavoriteBorder, "点赞", tint = momentsLinkColor(), modifier = Modifier.size(14.dp))
                                        Text(uiState.username, color = momentsLinkColor(), fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                                post.comments.takeLast(3).forEach { comment ->
                                    Text(
                                        "${comment.authorName}${comment.replyToName?.let { " 回复 $it" }.orEmpty()}: ${comment.text.ifBlank { "[图片]" }}",
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF2F2F2), thickness = 0.6.dp)
                }
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

        // 大图预览弹窗
        if (previewPhotos != null) {
            MomentsPhotoPreviewDialog(
                photos = previewPhotos.orEmpty(),
                initialIndex = previewIndex,
                onDismiss = { previewPhotos = null },
            )
        }
        pendingDeleteId?.let { postId ->
            MomentsDeleteDialog(onDismiss = { pendingDeleteId = null }, onConfirm = {
                pendingDeleteId = null
                onDeletePost(postId)
            })
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

/**
 * 朋友圈动态列表单项组件，复刻微信朋友圈动态排版：
 * 左侧用户头像，右侧依次展示蓝字昵称、文本、配图（1图大图、4图2x2、其他3列网格）、所在位置、时间、删除与操作按钮。
 *
 * @param post 朋友圈动态
 * @param authorName 发布者昵称
 * @param avatarPath 头像本地路径
 * @param onDelete 点击“删除”按钮时触发；为 null 时隐藏删除入口，由调用方确认后删除
 * @param onLike 点击“赞/取消”操作时触发
 * @param onComment 点击“评论”操作时触发
 * @param showFullTimestamp 是否按详情页展示完整日期、删除图标与原比例单图
 * @param onPhotoClick 点击配图时触发，提供图片路径列表与当前点击索引
 * @param modifier 布局修饰符
 */
@Composable
fun MomentFeedItem(
    post: MomentPost,
    authorName: String,
    avatarPath: String?,
    onDelete: (() -> Unit)? = null,
    onLike: () -> Unit = {},
    onComment: () -> Unit = {},
    showFullTimestamp: Boolean = false,
    onPhotoClick: (photos: List<String>, index: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }
    val linkColor = momentsLinkColor()
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .testTag("moment_feed_item_${post.id}"),
    ) {
        // 1. 头像
        MomentAvatar(avatarPath, authorName, Modifier.size(42.dp))

        Spacer(modifier = Modifier.width(10.dp))

        // 2. 右侧主体
        Column(modifier = Modifier.weight(1f)) {
            // 昵称（微信经典 #576B95 蓝）
            Text(
                text = authorName,
                color = linkColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )

            // 正文
            if (post.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = post.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }

            // 配图展示
            if (post.photoPaths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                MomentFeedPhotos(
                    photos = post.photoPaths,
                    preserveSinglePhoto = showFullTimestamp,
                    onPhotoClick = { index -> onPhotoClick(post.photoPaths, index) },
                )
            }

            // 所在位置
            if (!post.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = post.location,
                    color = linkColor,
                    fontSize = 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 底部操作栏：时间戳、删除、操作按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (showFullTimestamp) formatMomentDetailTime(post.timestamp) else formatMomentTime(post.timestamp),
                    color = Color(0xFF999999),
                    fontSize = 12.sp,
                )

                if (onDelete != null) {
                    if (showFullTimestamp) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).testTag("moment_delete_${post.id}")) {
                            Icon(Icons.Outlined.DeleteOutline, "删除", tint = linkColor, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        TextButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp).testTag("moment_delete_${post.id}"),
                        ) {
                            Text("删除", color = linkColor, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 微信经典的“··”评论赞气泡图标
                Box {
                    Surface(
                        onClick = { showActions = true },
                        shape = RoundedCornerShape(4.dp),
                        color = momentsBarColor(),
                        modifier = Modifier.size(width = 32.dp, height = 24.dp).testTag("moment_actions_${post.id}"),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("··", color = linkColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false },
                        offset = DpOffset((-36).dp, (-44).dp),
                        shape = RoundedCornerShape(4.dp),
                        containerColor = Color(0xFF4C4C4C),
                        modifier = Modifier.semantics { testTagsAsResourceId = true },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    showActions = false
                                    onLike()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.width(100.dp).height(36.dp).testTag("moment_like_${post.id}"),
                            ) {
                                Icon(if (post.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (post.isLikedByMe) "取消" else "赞", fontSize = 14.sp)
                            }
                            VerticalDivider(Modifier.height(20.dp), color = Color.White.copy(alpha = 0.2f))
                            TextButton(
                                onClick = {
                                    showActions = false
                                    onComment()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.width(100.dp).height(36.dp).testTag("moment_comment_action_${post.id}"),
                            ) {
                                Icon(Icons.Outlined.ChatBubbleOutline, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("评论", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 朋友圈动态九宫格照片排版组件。
 *
 * @param photos 配图路径列表
 * @param onPhotoClick 点击某张图片回调，提供被点击图片在列表中的索引
 * @param preserveSinglePhoto 是否保留单图原始比例（详情页使用）
 * @param modifier 布局修饰符
 */
@Composable
private fun MomentFeedPhotos(
    photos: List<String>,
    onPhotoClick: (Int) -> Unit = {},
    preserveSinglePhoto: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (photos.size == 1) {
        val path = photos[0]
        val uri = if (path.startsWith("content://") || path.startsWith("file://")) path else "file://${File(path).absolutePath}"
        if (preserveSinglePhoto) {
            AsyncImage(
                uri = uri,
                contentDescription = "配图",
                contentScale = ContentScale.Fit,
                modifier = modifier.widthIn(max = 200.dp).heightIn(max = 240.dp).clickable { onPhotoClick(0) },
            )
            return
        }
        Box(
            modifier =
            modifier
                .size(width = 180.dp, height = 180.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFF2F2F2))
                .clickable { onPhotoClick(0) },
        ) {
            AsyncImage(
                uri = uri,
                contentDescription = "配图",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        val columns = if (photos.size == 4) 2 else 3
        val rows = photos.chunked(columns)
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier.fillMaxWidth(if (photos.size == 4) 0.68f else 1f),
        ) {
            rows.forEachIndexed { rowIndex, rowPhotos ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowPhotos.forEachIndexed { colIndex, photoPath ->
                        val photoIndex = rowIndex * columns + colIndex
                        val uri = if (photoPath.startsWith("content://") || photoPath.startsWith("file://")) photoPath else "file://${File(photoPath).absolutePath}"
                        Box(
                            modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFF2F2F2))
                                .clickable { onPhotoClick(photoIndex) },
                        ) {
                            AsyncImage(
                                uri = uri,
                                contentDescription = "配图",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    if (rowPhotos.size < columns) {
                        for (i in 0 until (columns - rowPhotos.size)) {
                            Spacer(
                                modifier =
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 将时间戳格式化为友好朋友圈时间显示。
 */
fun formatMomentTime(timestamp: Long): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val diff = (now - timestamp).coerceAtLeast(0)
    return when {
        diff < 60_000L -> "刚刚"

        diff < 3600_000L -> "${(diff / 60_000L).coerceAtLeast(1)}分钟前"

        diff < 86400_000L -> "${diff / 3600_000L}小时前"

        else -> {
            val instant = Instant.fromEpochMilliseconds(timestamp)
            val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            @Suppress("DEPRECATION") // kotlinx-datetime monthNumber
            "${dt.monthNumber}月${dt.dayOfMonth}日"
        }
    }
}
