@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package plus.rua.project.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.panpf.sketch.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import plus.rua.project.MomentAccount
import plus.rua.project.MomentPost
import plus.rua.project.MomentsStorage
import plus.rua.project.MomentsUiState
import plus.rua.project.MomentsViewModel
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 个人朋友圈相册视图页面（状态版），复刻微信朋友圈个人相册主页。
 * 包含沉浸式顶部栏（搜索、相册网格视图、消息通知）、相册封面（点击平滑展开放大动画与换封面）、用户名与头像、
 * 以及时间轴视图（“今天”发表/私密发表卡片、年份分组、月份与日期条目）。
 *
 * @param authorId 要展示的作者，为 null 时展示当前账号
 * @param onBack 点击返回按钮时触发
 * @param onPublish 点击“发表”按钮时触发
 * @param onPrivatePublish 点击“私密发表”按钮时触发
 * @param onSearch 点击右上角搜索按钮时触发
 * @param onViewModeChange 点击右上角视图切换按钮时触发
 * @param onNotifications 点击右上角消息通知按钮时触发
 * @param onCoverClick 点击相册封面时触发（为 null 时默认切换封面展开状态）
 * @param onAvatarClick 点击头像时触发（默认打开更换或恢复默认头像的操作菜单）
 * @param onPostClick 点击本人动态的缩略图或正文时触发，传递动态 ID
 * @param viewModel 朋友圈 ViewModel
 * @param modifier 布局修饰符
 */
@Composable
fun UserMomentsScreen(
    onBack: () -> Unit,
    authorId: String? = null,
    onPublish: () -> Unit = {},
    onPrivatePublish: () -> Unit = {},
    onSearch: () -> Unit = {},
    onViewModeChange: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onCoverClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    onPostClick: (String) -> Unit = {},
    viewModel: MomentsViewModel = run {
        val context = LocalContext.current.applicationContext
        viewModel(
            factory =
            viewModelFactory {
                initializer {
                    val currentAuthor = authorId ?: MomentAccount.findById(MomentsStorage.fromContext(context).getCurrentAccountId()).id
                    MomentsViewModel.fromContext(
                        context,
                        cacheTimeline = true,
                        authorId = currentAuthor,
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
    val isOwnProfile = authorId == null || authorId == viewModel.accountId
    var showAvatarActions by remember { mutableStateOf(false) }
    var isRestoringAvatar by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    val avatarSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPosts(authorId = authorId ?: viewModel.accountId)
    }

    BackHandler(enabled = isCoverExpanded) {
        isCoverExpanded = false
    }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri: Uri? ->
            if (uri != null && isOwnProfile) {
                viewModel.setAvatarFromUri(context, uri)
            }
        }

    val coverPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri: Uri? ->
            if (uri != null && isOwnProfile) {
                viewModel.setCoverFromUri(context, uri)
                isCoverExpanded = false
            }
        }

    if (showAvatarActions && isOwnProfile) {
        MomentsAvatarSheet(
            sheetState = avatarSheetState,
            isRestoring = isRestoringAvatar,
            error = avatarError,
            onChoosePhoto = {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRestoreDefault = {
                if (!isRestoringAvatar) {
                    isRestoringAvatar = true
                    avatarError = null
                    coroutineScope.launch {
                        try {
                            if (viewModel.resetAvatar()) {
                                avatarSheetState.hide()
                                showAvatarActions = false
                                Toast.makeText(context, "已恢复默认头像", Toast.LENGTH_SHORT).show()
                            } else {
                                avatarError = "账号已切换，请重新操作"
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            avatarError = e.message ?: "恢复失败，请重试"
                        } finally {
                            isRestoringAvatar = false
                        }
                    }
                }
            },
            onDismiss = { showAvatarActions = false },
        )
    }

    UserMomentsScreen(
        uiState = uiState,
        isOwnProfile = isOwnProfile,
        onRefresh = { viewModel.refreshPosts(authorId = authorId ?: viewModel.accountId) },
        onLoadMore = viewModel::loadMore,
        onDismissOperationError = viewModel::dismissOperationError,
        isCoverExpanded = isCoverExpanded,
        onBack = {
            if (isCoverExpanded) {
                isCoverExpanded = false
            } else {
                onBack()
            }
        },
        onPublish = onPublish,
        onPrivatePublish = onPrivatePublish,
        onSearch = onSearch,
        onViewModeChange = onViewModeChange,
        onNotifications = onNotifications,
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
            avatarError = null
            showAvatarActions = true
        },
        onAvatarLongClick = {
            avatarError = null
            showAvatarActions = true
        },
        onPostClick = onPostClick,
        modifier = modifier,
    )
}

/**
 * 个人朋友圈相册视图无状态内容组件，空列表的加载、错误与无动态状态复用朋友圈首页。
 *
 * @param isOwnProfile 是否正在展示本人主页，控制资料编辑及发布入口
 * @param onRefresh 未加载时下拉刷新触发
 * @param onDismissOperationError 点击操作失败提示的“知道了”时触发
 * @param onLoadMore 点击加载更多时触发
 * @param uiState 当前 UI 状态
 * @param isCoverExpanded 封面是否展开
 * @param onBack 点击返回按钮时触发
 * @param onPublish 点击“发表”按钮时触发
 * @param onPrivatePublish 点击“私密发表”按钮时触发
 * @param onSearch 点击右上角搜索按钮时触发
 * @param onViewModeChange 点击右上角视图切换按钮时触发
 * @param onNotifications 点击右上角消息通知按钮时触发
 * @param onCoverClick 点击相册封面时触发
 * @param onChangeCoverClick 点击“换封面”按钮时触发
 * @param onAvatarClick 点击头像时触发
 * @param onAvatarLongClick 长按头像时触发
 * @param onPostClick 点击本人动态的缩略图或正文时触发
 * @param modifier 布局修饰符
 */
@Composable
fun UserMomentsScreen(
    uiState: MomentsUiState,
    onBack: () -> Unit,
    isCoverExpanded: Boolean = false,
    isOwnProfile: Boolean = true,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onDismissOperationError: () -> Unit = {},
    onPublish: () -> Unit = {},
    onPrivatePublish: () -> Unit = {},
    onSearch: () -> Unit = {},
    onViewModeChange: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onCoverClick: () -> Unit = {},
    onChangeCoverClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onAvatarLongClick: (() -> Unit)? = null,
    onPostClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val refreshState = rememberPullToRefreshState()
    val scrollAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                calculateTopBarAlpha(listState.firstVisibleItemScrollOffset, maxScrollOffset = 360f)
            }
        }
    }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val todayPosts = remember(uiState.posts, today) {
        uiState.posts.filter { isTimestampToday(it.timestamp, today) }
    }
    val historyPosts = remember(uiState.posts, today) {
        uiState.posts.filter { !isTimestampToday(it.timestamp, today) }
    }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .semantics { testTagsAsResourceId = true }
            .testTag("user_moments_screen"),
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { if (!uiState.isLoading) onRefresh() },
            state = refreshState,
            indicator = {
                MomentsRefreshIndicator(
                    isRefreshing = uiState.isRefreshing,
                    state = refreshState,
                    color = lerp(Color.White, MaterialTheme.colorScheme.onSurfaceVariant, scrollAlpha),
                    containerColor = lerp(Color.Black.copy(alpha = 0.2f), MaterialTheme.colorScheme.surface, scrollAlpha),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // 头部：封面（支持展开与换封面） + 用户名 + 跨界悬浮头像
                item(key = "header") {
                    MomentsHeader(
                        username = uiState.username,
                        avatarPath = uiState.avatarPath,
                        coverPath = uiState.coverPath,
                        isCoverExpanded = isCoverExpanded,
                        onCoverClick = { if (isOwnProfile) onCoverClick() },
                        canEdit = isOwnProfile,
                        onChangeCoverClick = { if (isOwnProfile) onChangeCoverClick() },
                        onAvatarClick = { if (isOwnProfile) onAvatarClick() },
                        onAvatarLongClick = if (isOwnProfile) onAvatarLongClick else null,
                    )
                }

                uiState.operationError?.let { error ->
                    item(key = "operation_error") {
                        MomentsErrorNotice(error, title = "操作未完成", hint = "请稍后重新操作", actionLabel = "知道了", onAction = onDismissOperationError, modifier = Modifier.padding(16.dp))
                    }
                }

                if (uiState.posts.isEmpty()) {
                    item(key = "empty_content") {
                        if (!uiState.hasLoadedPosts && uiState.error == null) {
                            MomentsLoadingContent()
                        } else {
                            MomentsEmptyContent(
                                error = uiState.error,
                                accountId = uiState.currentAccountId,
                                onPublish = onPublish,
                                canPublish = isOwnProfile,
                                modifier = Modifier.fillParentMaxHeight(0.6f),
                            )
                        }
                    }
                } else {
                    uiState.error?.let { error ->
                        item(key = "network_error") {
                            MomentsErrorNotice(error = error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                        }
                    }
                    // “今天” 分组：发表与私密发表入口及今天发表的动态
                    item(key = "today_section") {
                        UserMomentsTodaySection(
                            todayPosts = todayPosts,
                            canPublish = isOwnProfile,
                            onPublish = onPublish,
                            onPrivatePublish = onPrivatePublish,
                            onPostClick = { post -> onPostClick(post.id) },
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        )
                    }

                    if (historyPosts.isNotEmpty()) {
                        val currentYear = today.year
                        var lastYear = currentYear
                        var lastDate: LocalDate? = null

                        historyPosts.forEach { post ->
                            val dt = Instant.fromEpochMilliseconds(post.timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
                            val postYear = dt.year
                            val postDate = dt.date

                            if (postYear != currentYear && postYear != lastYear) {
                                item(key = "year_$postYear") {
                                    Text(
                                        text = "$postYear 年",
                                        style =
                                        MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 24.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 16.dp),
                                    )
                                }
                                lastYear = postYear
                            }

                            @Suppress("DEPRECATION") // kotlinx-datetime monthNumber
                            val monthStr = if (postDate != lastDate) formatTimelineMonth(dt.monthNumber) else ""

                            @Suppress("DEPRECATION") // kotlinx-datetime dayOfMonth
                            val dayStr = if (postDate != lastDate) dt.dayOfMonth.toString() else ""
                            lastDate = postDate

                            item(key = post.id) {
                                UserMomentsTimelineItem(
                                    month = monthStr,
                                    day = dayStr,
                                    post = post,
                                    onPhotoClick = { onPostClick(post.id) },
                                    onClick = { onPostClick(post.id) },
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }

                    item(key = "more") {
                        MomentsPagingFooter(uiState.nextCursor != null, uiState.isLoadingMore, uiState.loadMoreError, onLoadMore, enabled = !uiState.isLoading)
                    }
                    // 底部时间轴结束标志：— · —
                    if (uiState.nextCursor == null && uiState.error == null && !uiState.isLoading) {
                        item(key = "timeline_footer") { UserMomentsFooter() }
                    }
                }

                // 底部手势栏安全留白
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

        // 顶部悬浮操作栏（带搜索、网格视图、通知图标，封面展开时隐藏）
        if (!isCoverExpanded) {
            UserMomentsTopBar(
                title = uiState.username + if (isOwnProfile && uiState.unreadCount > 0) " · ${uiState.unreadCount}条新消息" else "",
                alpha = scrollAlpha,
                onBack = onBack,
                onSearch = onSearch,
                onViewModeChange = onViewModeChange,
                onNotifications = onNotifications,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/**
 * 个人朋友圈相册视图顶部操作栏。
 */
@Composable
private fun UserMomentsTopBar(
    title: String,
    alpha: Float,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onViewModeChange: () -> Unit,
    onNotifications: () -> Unit,
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
                    .testTag("user_moments_back_button")
                    .background(Color.Black.copy(alpha = scrimAlpha), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = "返回",
                    tint = iconColor,
                    modifier = Modifier.size(28.dp),
                )
            }

            // 中间标题（滑动时渐变展示）
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

            // 右侧三个操作图标：搜索、网格相册视图、消息通知
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onSearch,
                    modifier =
                    Modifier
                        .testTag("user_moments_search_button")
                        .background(Color.Black.copy(alpha = scrimAlpha), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "搜索",
                        tint = iconColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                IconButton(
                    onClick = onViewModeChange,
                    modifier =
                    Modifier
                        .testTag("user_moments_grid_button")
                        .background(Color.Black.copy(alpha = scrimAlpha), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GridView,
                        contentDescription = "相册视图",
                        tint = iconColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                IconButton(
                    onClick = onNotifications,
                    modifier =
                    Modifier
                        .testTag("user_moments_notification_button")
                        .background(Color.Black.copy(alpha = scrimAlpha), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "消息通知",
                        tint = iconColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * “今天”栏目组件：包含左侧粗体“今天”标题，以及右侧“发表”与“私密发表”方形按钮卡片，和今天已发表的动态。
 */
@Composable
private fun UserMomentsTodaySection(
    todayPosts: List<MomentPost> = emptyList(),
    onPublish: () -> Unit,
    onPrivatePublish: () -> Unit,
    onPostClick: (post: MomentPost) -> Unit = {},
    canPublish: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // 左侧列：大标题“今天”
        Text(
            text = "今天",
            style =
            MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(68.dp),
        )

        // 右侧列：两张卡片（发表、私密发表）及今天发表的动态
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canPublish) {
                    // 发表卡片
                    Card(
                        onClick = onPublish,
                        shape = RoundedCornerShape(8.dp),
                        colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier =
                        Modifier
                            .size(86.dp)
                            .testTag("user_moments_publish_card"),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                tint = Color(0xFF576B95),
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "发表",
                                style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = Color(0xFF576B95),
                            )
                        }
                    }

                    // 私密发表卡片
                    Card(
                        onClick = onPrivatePublish,
                        shape = RoundedCornerShape(8.dp),
                        colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier =
                        Modifier
                            .size(86.dp)
                            .testTag("user_moments_private_publish_card"),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Color(0xFF576B95),
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "私密发表",
                                style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = Color(0xFF576B95),
                            )
                        }
                    }
                }
            }

            // 今天发表的动态
            if (todayPosts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    todayPosts.forEach { post ->
                        UserMomentsThumbnailCard(
                            post = post,
                            onPhotoClick = { onPostClick(post) },
                            onClick = { onPostClick(post) },
                            modifier = Modifier.testTag("user_moment_${post.id}"),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 历史时间轴动态单条组件（左侧月份与日期，右侧照片缩略图或纯文字卡片）。
 *
 * @param month 月份（如“九月”）
 * @param day 日期（如“29”）
 * @param post 朋友圈动态对象（提供时优先用于渲染卡片）
 * @param photoPath 备用照片路径
 * @param text 备用纯文字内容
 * @param onPhotoClick 点击单张配图时的回调
 * @param onClick 点击卡片时的回调
 * @param modifier 布局修饰符
 */
@Composable
fun UserMomentsTimelineItem(
    month: String,
    day: String,
    post: MomentPost? = null,
    photoPath: String? = null,
    text: String? = null,
    onPhotoClick: (Int) -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // 左侧日期列：如“九月”与“29”
        Column(
            modifier = Modifier.width(68.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (month.isNotBlank()) {
                Text(
                    text = month,
                    style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (day.isNotBlank()) {
                Text(
                    text = day,
                    style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 右侧动态卡片
        if (post != null) {
            UserMomentsThumbnailCard(
                post = post,
                onPhotoClick = onPhotoClick,
                onClick = onClick,
            )
        } else {
            val fallbackPost =
                remember(photoPath, text) {
                    MomentPost(
                        photoPaths = listOfNotNull(photoPath),
                        text = text.orEmpty(),
                    )
                }
            UserMomentsThumbnailCard(
                post = fallbackPost,
                onPhotoClick = onPhotoClick,
                onClick = onClick,
            )
        }
    }
}

/**
 * 个人相册单条动态缩略图卡片（支持单图铺满、双图并排、三图/四宫格拼贴、纯文字与私密锁标）。
 *
 * @param post 朋友圈动态
 * @param onPhotoClick 点击单张配图时的回调
 * @param onClick 点击纯文本或卡片时的通用回调
 * @param modifier 布局修饰符
 */
@Composable
fun UserMomentsThumbnailCard(
    post: MomentPost,
    onPhotoClick: (Int) -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (post.photoPaths.isNotEmpty()) {
        Card(
            onClick = { onPhotoClick(0) },
            shape = RoundedCornerShape(8.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = modifier.size(86.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                MomentsThumbnailPhotos(
                    photoPaths = post.photoPaths,
                    onPhotoClick = onPhotoClick,
                    modifier = Modifier.fillMaxSize(),
                )

                // 私密动态锁标（位于右下角）
                if (post.visibility == "私密" || post.visibility.startsWith("私密")) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "私密动态",
                        tint = Color.White,
                        modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 4.dp, bottom = 4.dp)
                            .size(16.dp),
                    )
                }
            }
        }
    } else if (post.text.isNotBlank()) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = modifier.fillMaxWidth().padding(end = 8.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = post.text,
                    style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp),
                )
                if (post.visibility == "私密" || post.visibility.startsWith("私密")) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "私密动态",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 6.dp)
                            .size(14.dp),
                    )
                }
            }
        }
    } else {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = modifier.size(86.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * 个人相册多图缩略图网格拼贴组件（单图全幅、双图左右并排、三图左一右二、四张及以上 2x2 四宫格）。
 *
 * @param photoPaths 配图文件路径列表
 * @param onPhotoClick 点击单张图片时的回调
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsThumbnailPhotos(
    photoPaths: List<String>,
    onPhotoClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when {
        photoPaths.isEmpty() -> Unit

        photoPaths.size == 1 -> {
            val uri = rememberPhotoUri(photoPaths[0])
            AsyncImage(
                uri = uri,
                state = rememberMomentsImageState(),
                contentDescription = "动态照片",
                contentScale = ContentScale.Crop,
                modifier =
                modifier
                    .fillMaxSize()
                    .clickable { onPhotoClick(0) },
            )
        }

        photoPaths.size == 2 -> {
            Row(
                modifier = modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (i in 0..1) {
                    val uri = rememberPhotoUri(photoPaths[i])
                    AsyncImage(
                        uri = uri,
                        state = rememberMomentsImageState(),
                        contentDescription = "动态照片 ${i + 1}",
                        contentScale = ContentScale.Crop,
                        modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onPhotoClick(i) },
                    )
                }
            }
        }

        photoPaths.size == 3 -> {
            Row(
                modifier = modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val leftUri = rememberPhotoUri(photoPaths[0])
                AsyncImage(
                    uri = leftUri,
                    state = rememberMomentsImageState(),
                    contentDescription = "动态照片 1",
                    contentScale = ContentScale.Crop,
                    modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onPhotoClick(0) },
                )
                Column(
                    modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (i in 1..2) {
                        val uri = rememberPhotoUri(photoPaths[i])
                        AsyncImage(
                            uri = uri,
                            state = rememberMomentsImageState(),
                            contentDescription = "动态照片 ${i + 1}",
                            contentScale = ContentScale.Crop,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable { onPhotoClick(i) },
                        )
                    }
                }
            }
        }

        else -> {
            val displayPhotos = photoPaths.take(4)
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 上排两张
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (i in 0..1) {
                        val uri = rememberPhotoUri(displayPhotos[i])
                        AsyncImage(
                            uri = uri,
                            state = rememberMomentsImageState(),
                            contentDescription = "动态照片 ${i + 1}",
                            contentScale = ContentScale.Crop,
                            modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onPhotoClick(i) },
                        )
                    }
                }
                // 下排两张
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (i in 2..3) {
                        val uri = rememberPhotoUri(displayPhotos[i])
                        AsyncImage(
                            uri = uri,
                            state = rememberMomentsImageState(),
                            contentDescription = "动态照片 ${i + 1}",
                            contentScale = ContentScale.Crop,
                            modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onPhotoClick(i) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 记忆并解析配图 URI，兼容 content:// 与本地文件路径。
 */
@Composable
private fun rememberPhotoUri(path: String?): String? = remember(path) { momentsThumbnailUri(path) }

/**
 * 判断指定时间戳（毫秒）是否落在给定的本地日期当天。
 */
fun isTimestampToday(
    timestamp: Long,
    today: LocalDate,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean {
    val postDate = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(timeZone).date
    return postDate == today
}

/**
 * 个人相册时间轴底部图标：— · —
 */
@Composable
internal fun UserMomentsFooter(modifier: Modifier = Modifier) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
            Modifier
                .width(28.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        )
        Box(
            modifier =
            Modifier
                .padding(horizontal = 8.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)),
        )
        Box(
            modifier =
            Modifier
                .width(28.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        )
    }
}

/**
 * 将月份数字（1..12）格式化为朋友圈时间轴经典的中文月份表示（如“十月”）。
 *
 * @param month 月份（1 到 12）
 * @return 对应的中文月份名称
 */
fun formatTimelineMonth(month: Int): String = when (month) {
    1 -> "一月"
    2 -> "二月"
    3 -> "三月"
    4 -> "四月"
    5 -> "五月"
    6 -> "六月"
    7 -> "七月"
    8 -> "八月"
    9 -> "九月"
    10 -> "十月"
    11 -> "十一月"
    12 -> "十二月"
    else -> "${month}月"
}
