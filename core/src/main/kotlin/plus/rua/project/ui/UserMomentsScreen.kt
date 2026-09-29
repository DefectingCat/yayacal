package plus.rua.project.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import plus.rua.project.MomentsStorage
import plus.rua.project.MomentsUiState
import plus.rua.project.MomentsViewModel
import java.io.File
import kotlin.time.Instant

/**
 * 个人朋友圈相册视图页面（状态版），复刻微信朋友圈个人相册主页。
 * 包含沉浸式顶部栏（搜索、相册网格视图、消息通知）、相册封面（点击平滑展开放大动画与换封面）、用户名与头像、
 * 以及时间轴视图（“今天”发表/私密发表卡片、年份分组、月份与日期条目）。
 *
 * @param onBack 点击返回按钮时触发
 * @param onPublish 点击“发表”按钮时触发
 * @param onPrivatePublish 点击“私密发表”按钮时触发
 * @param onSearch 点击右上角搜索按钮时触发
 * @param onViewModeChange 点击右上角视图切换按钮时触发
 * @param onNotifications 点击右上角消息通知按钮时触发
 * @param onCoverClick 点击相册封面时触发（为 null 时默认切换封面展开状态）
 * @param onAvatarClick 点击头像时触发（默认从系统相册选取并设置头像）
 * @param viewModel 朋友圈 ViewModel
 * @param modifier 布局修饰符
 */
@Composable
fun UserMomentsScreen(
    onBack: () -> Unit,
    onPublish: () -> Unit = {},
    onPrivatePublish: () -> Unit = {},
    onSearch: () -> Unit = {},
    onViewModeChange: () -> Unit = {},
    onNotifications: () -> Unit = {},
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

    UserMomentsScreen(
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
 * 个人朋友圈相册视图无状态内容组件。
 *
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
 * @param modifier 布局修饰符
 */
@Composable
fun UserMomentsScreen(
    uiState: MomentsUiState,
    onBack: () -> Unit,
    isCoverExpanded: Boolean = false,
    onPublish: () -> Unit = {},
    onPrivatePublish: () -> Unit = {},
    onSearch: () -> Unit = {},
    onViewModeChange: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onCoverClick: () -> Unit = {},
    onChangeCoverClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onAvatarLongClick: (() -> Unit)? = null,
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
            .testTag("user_moments_screen"),
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
                    onCoverClick = onCoverClick,
                    onChangeCoverClick = onChangeCoverClick,
                    onAvatarClick = onAvatarClick,
                    onAvatarLongClick = onAvatarLongClick,
                )
            }

            // “今天” 分组：发表与私密发表入口
            item(key = "today_section") {
                UserMomentsTodaySection(
                    onPublish = onPublish,
                    onPrivatePublish = onPrivatePublish,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                )
            }

            if (uiState.posts.isNotEmpty()) {
                items(uiState.posts, key = { it.id }) { post ->
                    val instant = Instant.fromEpochMilliseconds(post.timestamp)
                    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())

                    @Suppress("DEPRECATION") // kotlinx-datetime monthNumber
                    val monthStr = formatTimelineMonth(dt.monthNumber)

                    @Suppress("DEPRECATION") // kotlinx-datetime dayOfMonth
                    val dayStr = dt.dayOfMonth.toString()
                    UserMomentsTimelineItem(
                        month = monthStr,
                        day = dayStr,
                        photoPath = post.photoPaths.firstOrNull(),
                        text = post.text,
                        onClick = {
                            if (post.photoPaths.isNotEmpty()) {
                                previewPhotos = post.photoPaths
                                previewIndex = 0
                            } else {
                                onPublish()
                            }
                        },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            } else {
                // 年份分组标题（示例：2025 年）
                item(key = "year_2025") {
                    Text(
                        text = "2025 年",
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

                // 历史时间轴动态项（示例：十月 16 日图文记录）
                item(key = "item_oct_16") {
                    UserMomentsTimelineItem(
                        month = "十月",
                        day = "16",
                        photoPath = uiState.avatarPath,
                        onClick = onPublish,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }

            // 底部时间轴结束标志：— · —
            item(key = "timeline_footer") {
                UserMomentsFooter()
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

        // 顶部悬浮操作栏（带搜索、网格视图、通知图标，封面展开时隐藏）
        if (!isCoverExpanded) {
            UserMomentsTopBar(
                title = uiState.username,
                alpha = scrollAlpha,
                onBack = onBack,
                onSearch = onSearch,
                onViewModeChange = onViewModeChange,
                onNotifications = onNotifications,
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
 * “今天”栏目组件：包含左侧粗体“今天”标题，以及右侧“发表”与“私密发表”方形按钮卡片。
 */
@Composable
private fun UserMomentsTodaySection(
    onPublish: () -> Unit,
    onPrivatePublish: () -> Unit,
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

        // 右侧列：两张卡片（发表、私密发表）
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
}

/**
 * 历史时间轴动态单条组件（左侧月份与日期，右侧照片缩略图或纯文字卡片）。
 */
@Composable
private fun UserMomentsTimelineItem(
    month: String,
    day: String,
    photoPath: String?,
    text: String? = null,
    onClick: () -> Unit,
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
            Text(
                text = month,
                style =
                MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

        val photoUri =
            remember(photoPath) {
                photoPath?.let { path ->
                    if (path.startsWith("content://")) {
                        path
                    } else {
                        val file = File(path.removePrefix("file://"))
                        if (file.exists()) "file://${file.absolutePath}" else null
                    }
                }
            }

        if (photoUri != null) {
            // 右侧动态照片缩略图
            Card(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.size(86.dp),
            ) {
                AsyncImage(
                    uri = photoUri,
                    contentDescription = "动态照片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (!text.isNullOrBlank()) {
            // 纯文字动态：微信个人相册灰底文字块
            Card(
                onClick = onClick,
                shape = RoundedCornerShape(6.dp),
                colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            ) {
                Text(
                    text = text,
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
                modifier = Modifier.size(86.dp),
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
}

/**
 * 个人相册时间轴底部图标：— · —
 */
@Composable
private fun UserMomentsFooter(modifier: Modifier = Modifier) {
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
