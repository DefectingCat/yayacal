@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package plus.rua.project.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.SwitchAccount
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.panpf.sketch.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import plus.rua.project.MomentAccount
import plus.rua.project.MomentPost
import plus.rua.project.MomentsStorage
import plus.rua.project.MomentsUiState
import plus.rua.project.MomentsViewModel
import java.io.File
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 朋友圈页面，复刻经典微信朋友圈布局结构。
 * 包含沉浸式顶部渐变导航栏、可自定义相册封面（点击平滑展开放大动画与换封面）、用户名与悬浮跨界头像、以及底部朋友圈动态区域。
 * 首次进入时选择账号并保存，后续直接进入上次使用账号的朋友圈；点击切换账号或长按头像时可重新选择。
 * 选择账号后头像平滑飞入朋友圈右上角，返回账号页时沿原路径回到账号卡片，同时淡入淡出页面。
 *
 * @param onBack 点击返回按钮时触发
 * @param onPublish 点击右上角发布动态/相机按钮时触发
 * @param onCoverClick 点击相册封面占位区域时触发（为 null 时默认切换封面展开状态）
 * @param onAvatarClick 点击用户头像时触发（为 null 时默认拉起系统相册选择头像）
 * @param onAuthorClick 点击动态作者头像时触发，进入该作者主页
 * @param onPostClick 点击动态正文或互动区时触发，传递动态 ID
 * @param onCommentClick 点击动态“评论”操作时触发，传递动态 ID
 * @param viewModel 朋友圈 ViewModel，加载当前账号的远端数据
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsScreen(
    onBack: () -> Unit,
    onPublish: () -> Unit = {},
    onCoverClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    onAuthorClick: (String) -> Unit = {},
    onPostClick: (String) -> Unit = {},
    onCommentClick: (String) -> Unit = onPostClick,
    viewModel: MomentsViewModel = run {
        val context = LocalContext.current.applicationContext
        viewModel(
            factory =
            viewModelFactory {
                initializer {
                    MomentsViewModel.fromContext(context)
                }
            },
        )
    },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var isCoverExpanded by remember { mutableStateOf(false) }
    var showConnection by remember { mutableStateOf(false) }
    if (showConnection) {
        MomentsConnectionDialog(onSaved = {
            showConnection = false
            viewModel.reconnect(context)
        }, onDismiss = { showConnection = false })
    }
    MomentsPoll(uiState.currentAccountId) { if (!uiState.isLoading && uiState.posts.size <= 20) viewModel.refreshPosts() }

    val storage = remember(context) { MomentsStorage.fromContext(context) }
    var isAccountSelected by remember { mutableStateOf(storage.getCurrentAccountId() != null) }
    var isTransitioning by remember { mutableStateOf(false) }
    var animatingAccount by remember { mutableStateOf<MomentAccount?>(null) }
    val animProgress = remember { Animatable(if (isAccountSelected) 1f else 0f) }

    val accountBoundsMap = remember { mutableStateMapOf<String, Rect>() }
    var headerAvatarRect by remember { mutableStateOf<Rect?>(null) }

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

    fun animateAccountTransition(account: MomentAccount, toFeed: Boolean) {
        if (!isTransitioning) {
            animatingAccount = account
            isTransitioning = true
            isAccountSelected = false
            coroutineScope.launch {
                // 先完成账号页首帧布局与头像定位，避免加载耗时挤占动画开头。
                withFrameNanos { }
                animProgress.animateTo(
                    targetValue = if (toFeed) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 520,
                        easing = FastOutSlowInEasing,
                    ),
                )
                isAccountSelected = toFeed
                isTransitioning = false
                animatingAccount = null
            }
        }
    }

    val onSelectAccount: (MomentAccount) -> Unit = { account ->
        if (!isTransitioning) {
            viewModel.switchAccount(context, account)
            animateAccountTransition(account, toFeed = true)
        }
    }
    val onSwitchAccount: () -> Unit = {
        animateAccountTransition(MomentAccount.findById(uiState.currentAccountId), toFeed = false)
    }
    val onAccountSelectBack: () -> Unit = {
        if (!isTransitioning) {
            val currentAccountId = storage.getCurrentAccountId()
            if (currentAccountId != null) {
                animateAccountTransition(MomentAccount.findById(currentAccountId), toFeed = true)
            } else {
                onBack()
            }
        }
    }
    BackHandler(enabled = !isAccountSelected, onBack = onAccountSelectBack)

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Layer 1：朋友圈主页面（始终渲染并参与测量，使头像能够准确捕获根坐标 Rect）
        val momentsFeedAlpha = if (isAccountSelected) 1f else animProgress.value
        MomentsScreen(
            uiState = uiState,
            isCoverExpanded = isCoverExpanded,
            isAvatarVisible = isAccountSelected && !isTransitioning,
            onAvatarPositioned = { rect ->
                headerAvatarRect = rect
            },
            onSwitchAccount = onSwitchAccount,
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
            onAvatarLongClick = onSwitchAccount,
            onAuthorClick = onAuthorClick,
            onPostClick = onPostClick,
            onCommentClick = onCommentClick,
            onLikePost = viewModel::toggleLike,
            onRefresh = { viewModel.refreshPosts() },
            onLoadMore = viewModel::loadMore,
            modifier =
            Modifier
                .fillMaxSize()
                .alpha(momentsFeedAlpha),
        )

        // Layer 2：账号选择页面（与朋友圈页面共用进度，双向淡入淡出）
        if (!isAccountSelected) {
            val selectAlpha =
                if (isTransitioning) {
                    (1f - animProgress.value * 2.2f).coerceAtLeast(0f)
                } else {
                    1f
                }
            MomentsAccountSelectScreen(
                currentAccountId = storage.getCurrentAccountId(),
                animatingAccountId = if (isTransitioning) animatingAccount?.id else null,
                onAccountClick = onSelectAccount,
                onAccountPositioned = { account, rect ->
                    accountBoundsMap[account.id] = rect
                },
                onBack = onAccountSelectBack,
                modifier =
                Modifier
                    .fillMaxSize()
                    .alpha(selectAlpha),
            )
            TextButton(
                onClick = { showConnection = true },
                enabled = !isTransitioning,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp).alpha(selectAlpha),
            ) { Text("连接设置") }
        }

        // Layer 3：飞行动画浮层（共用头像路径与样式，按进度正向进入或反向回到账号卡片）
        if (isTransitioning && animatingAccount != null) {
            val account = animatingAccount!!
            val startRect = accountBoundsMap[account.id] ?: Rect.Zero
            val density = LocalDensity.current

            val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
            val avatarSizePx = with(density) { 74.dp.toPx() }
            val paddingEndPx = with(density) { 16.dp.toPx() }
            val fallbackTopPx = with(density) { 260.dp.toPx() }
            val fallbackTargetRect =
                Rect(
                    left = screenWidthPx - paddingEndPx - avatarSizePx,
                    top = fallbackTopPx,
                    right = screenWidthPx - paddingEndPx,
                    bottom = fallbackTopPx + avatarSizePx,
                )

            val targetRect = headerAvatarRect?.takeIf { it.width > 0f } ?: fallbackTargetRect

            val p = animProgress.value
            val currentLeft = startRect.left + (targetRect.left - startRect.left) * p
            val currentTop = startRect.top + (targetRect.top - startRect.top) * p
            val currentWidth = startRect.width + (targetRect.width - startRect.width) * p
            val currentHeight = startRect.height + (targetRect.height - startRect.height) * p

            val cornerRadius = (12f - (12f - 10f) * p).dp
            val borderWidth = (1f + (2f - 1f) * p).dp
            val borderColor =
                androidx.compose.ui.graphics.lerp(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    Color.White,
                    p,
                )

            Surface(
                shape = RoundedCornerShape(cornerRadius),
                border = BorderStroke(borderWidth, borderColor),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = (3f - p).dp,
                modifier =
                Modifier
                    .offset { IntOffset(currentLeft.roundToInt(), currentTop.roundToInt()) }
                    .size(
                        with(density) { currentWidth.toDp() },
                        with(density) { currentHeight.toDp() },
                    )
                    .clip(RoundedCornerShape(cornerRadius)),
            ) {
                Image(
                    painter = painterResource(account.avatarResId),
                    contentDescription = "${account.name} 头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (isTransitioning) {
            // 过渡期间消费触摸，避免淡出的页面响应点击或滑动。
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
            )
        }
    }
}

/**
 * 朋友圈页面内容渲染组件（无状态）。
 *
 * @param uiState 朋友圈当前 UI 状态
 * @param isCoverExpanded 封面是否展开
 * @param isAvatarVisible 头像是否可见（用于头像位移动画期间临时隐藏）
 * @param onAvatarPositioned 头像在根坐标系中完成布局时的回调，传递其 Rect 坐标
 * @param onSwitchAccount 点击切换账号操作时触发（可选）
 * @param onBack 点击返回按钮时触发
 * @param onPublish 点击右上角发布动态/相机按钮时触发
 * @param onCoverClick 点击相册封面占位区域时触发
 * @param onChangeCoverClick 点击“换封面”按钮时触发
 * @param onAvatarClick 点击用户头像时触发
 * @param onAvatarLongClick 长按用户头像时触发
 * @param onAuthorClick 点击动态作者头像时触发，进入该作者主页
 * @param onPostClick 点击动态正文或互动区时触发
 * @param onCommentClick 点击“评论”操作时触发
 * @param onRefresh 下拉或点击刷新时触发
 * @param onLoadMore 点击加载更多时触发
 * @param onLikePost 点击“赞/取消”操作时触发
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsScreen(
    uiState: MomentsUiState,
    onBack: () -> Unit,
    isCoverExpanded: Boolean = false,
    isAvatarVisible: Boolean = true,
    onAvatarPositioned: ((Rect) -> Unit)? = null,
    onSwitchAccount: (() -> Unit)? = null,
    onPublish: () -> Unit = {},
    onCoverClick: () -> Unit = {},
    onChangeCoverClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onAvatarLongClick: (() -> Unit)? = null,
    onAuthorClick: (String) -> Unit = {},
    onPostClick: (String) -> Unit = {},
    onCommentClick: (String) -> Unit = onPostClick,
    onLikePost: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var previewPhotos by remember { mutableStateOf<List<String>?>(null) }
    var previewIndex by remember { mutableStateOf(0) }
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
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = uiState.isLoading, onRefresh = onRefresh) {
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
                        isAvatarVisible = isAvatarVisible,
                        onAvatarPositioned = onAvatarPositioned,
                    )
                }

                item(key = "network") { MomentsLoadStatus(uiState.isLoading, uiState.error, false, onRefresh, onLoadMore) }
                if (uiState.posts.isEmpty() && !uiState.isLoading && uiState.error == null) {
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
                                onAuthorClick = { onAuthorClick(post.authorId) },
                                authorName = post.authorName,
                                avatarPath = post.authorAvatarPath,
                                onLike = { onLikePost(post.id) },
                                onComment = { onCommentClick(post.id) },
                                onPhotoClick = { photos, index ->
                                    previewPhotos = photos
                                    previewIndex = index
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            if (post.likes.isNotEmpty() || post.comments.isNotEmpty()) {
                                Column(
                                    Modifier.padding(start = 68.dp, end = 16.dp, bottom = 12.dp)
                                        .fillMaxWidth().background(momentsBarColor(), RoundedCornerShape(4.dp)).padding(8.dp),
                                ) {
                                    if (post.likes.isNotEmpty()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.FavoriteBorder, "点赞", tint = momentsLinkColor(), modifier = Modifier.size(14.dp))
                                            Text(post.likes.joinToString("、") { it.name }, color = momentsLinkColor(), fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
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

                if (uiState.nextCursor != null) item(key = "more") { MomentsLoadStatus(uiState.isLoading, null, true, onRefresh, onLoadMore) }
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
        }
        // 顶部悬浮导航栏：随着列表滚动动态变化背景不透明度与图标颜色（封面展开时隐藏）
        if (!isCoverExpanded) {
            MomentsTopBar(
                title = "朋友圈",
                alpha = scrollAlpha,
                onBack = onBack,
                onPublish = onPublish,
                onSwitchAccount = onSwitchAccount,
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
    }
}

/**
 * 顶部导航栏，支持随滑动距离平滑渐变背景色与图标样式。
 *
 * @param title 导航栏标题
 * @param alpha 渐变透明度（0f 为完全透明，1f 为完全不透明）
 * @param onBack 点击返回按钮回调
 * @param onPublish 点击发布按钮回调
 * @param onSwitchAccount 点击切换账号回调（可选）
 * @param modifier 布局修饰符
 */
@Composable
private fun MomentsTopBar(
    title: String,
    alpha: Float,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    onSwitchAccount: (() -> Unit)? = null,
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
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
        ) {
            // 返回按钮
            IconButton(
                onClick = onBack,
                modifier =
                Modifier
                    .align(Alignment.CenterStart)
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

            // 标题相对整条顶栏居中，不随左右按钮宽度偏移
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
                textAlign = TextAlign.Center,
                modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 104.dp),
            )

            // 右上角操作区：切换账号 + 发布动态
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onSwitchAccount != null) {
                    IconButton(
                        onClick = onSwitchAccount,
                        modifier =
                        Modifier
                            .testTag("moments_switch_account_button")
                            .background(Color.Black.copy(alpha = scrimAlpha), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SwitchAccount,
                            contentDescription = "切换账号",
                            tint = iconColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

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
 * 左侧用户头像，右侧依次展示蓝字昵称、文本、配图（1图大图、4图2x2、其他3列网格）、所在位置、时间与操作按钮；详情模式可显示删除图标。
 *
 * @param post 朋友圈动态
 * @param authorName 发布者昵称
 * @param avatarPath 头像本地路径
 * @param onAuthorClick 点击作者头像时触发
 * @param onDelete 详情模式点击删除图标时触发；为 null 时隐藏删除入口，由调用方确认后删除
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
    onAuthorClick: () -> Unit = {},
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
        Card(onClick = onAuthorClick, elevation = CardDefaults.cardElevation(0.dp)) {
            MomentAvatar(avatarPath, authorName, Modifier.size(42.dp))
        }

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

                if (showFullTimestamp && onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).testTag("moment_delete_${post.id}")) {
                        Icon(Icons.Outlined.DeleteOutline, "删除", tint = linkColor, modifier = Modifier.size(16.dp))
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
        val uri = momentsThumbnailUri(path)
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
                        val uri = momentsThumbnailUri(photoPath)
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
