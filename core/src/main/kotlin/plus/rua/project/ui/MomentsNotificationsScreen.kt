package plus.rua.project.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.datetime.todayIn
import plus.rua.project.MomentNotification
import plus.rua.project.MomentNotificationType
import plus.rua.project.MomentsNotificationStorage
import plus.rua.project.MomentsNotificationsUiState
import plus.rua.project.MomentsNotificationsViewModel
import plus.rua.project.formatNotificationTimestamp
import java.io.File
import kotlin.time.Clock

/**
 * 朋友圈全部互动消息页面（状态版），复刻微信消息列表。
 *
 * @param onBack 点击左上角返回按钮时触发
 * @param onPostClick 点击消息条目时触发，参数为对应的动态 ID
 * @param viewModel 互动消息 ViewModel
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsNotificationsScreen(
    onBack: () -> Unit,
    onPostClick: (String) -> Unit = {},
    viewModel: MomentsNotificationsViewModel = run {
        val context = LocalContext.current.applicationContext
        viewModel(
            factory =
            viewModelFactory {
                initializer {
                    MomentsNotificationsViewModel(
                        storage = MomentsNotificationStorage.fromContext(context),
                    )
                }
            },
        )
    },
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    MomentsNotificationsScreen(
        uiState = uiState,
        onBack = onBack,
        onPostClick = onPostClick,
        onDeleteNotification = { viewModel.deleteNotification(it) },
        onClearAll = { viewModel.clearAll() },
        modifier = modifier,
    )
}

/**
 * 朋友圈全部互动消息页面（无状态版）。
 *
 * @param uiState 互动消息当前 UI 状态
 * @param onBack 点击左上角返回按钮时触发
 * @param onPostClick 点击消息条目时触发，参数为对应的动态 ID
 * @param onDeleteNotification 单条消息删除回调
 * @param onClearAll 清空全部消息回调
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsNotificationsScreen(
    uiState: MomentsNotificationsUiState,
    onBack: () -> Unit,
    onPostClick: (String) -> Unit = {},
    onDeleteNotification: (String) -> Unit = {},
    onClearAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var notificationToDelete by remember { mutableStateOf<MomentNotification?>(null) }

    val currentYear = remember {
        Clock.System.todayIn(TimeZone.currentSystemDefault()).year
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空消息列表") },
            text = { Text("确定要清空全部互动消息吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearDialog = false
                    },
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (notificationToDelete != null) {
        AlertDialog(
            onDismissRequest = { notificationToDelete = null },
            title = { Text("删除消息") },
            text = { Text("确定要删除这条互动消息吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        notificationToDelete?.let { onDeleteNotification(it.id) }
                        notificationToDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { notificationToDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .semantics { testTagsAsResourceId = true }
            .testTag("moments_notifications_screen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部导航栏：居中标题“全部互动消息”、返回按钮与更多操作菜单
            MomentsNotificationsTopBar(
                onBack = onBack,
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it },
                onClearAllClick = {
                    showMenu = false
                    showClearDialog = true
                },
            )

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (uiState.notifications.isEmpty()) {
                    item(key = "empty_state") {
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 120.dp, bottom = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "暂无互动消息",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(
                        items = uiState.notifications,
                        key = { it.id },
                    ) { notification ->
                        MomentsNotificationItem(
                            notification = notification,
                            currentYear = currentYear,
                            onClick = { onPostClick(notification.postId) },
                            onLongClick = { notificationToDelete = notification },
                        )
                    }
                }

                // 底部安全留白
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
    }
}

/**
 * 朋友圈互动消息顶部导航栏。
 *
 * @param onBack 返回按钮回调
 * @param showMenu 菜单是否展开
 * @param onShowMenuChange 菜单展示状态变化回调
 * @param onClearAllClick 清空选项点击回调
 * @param modifier 布局修饰符
 */
@Composable
private fun MomentsNotificationsTopBar(
    onBack: () -> Unit,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onClearAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(56.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier =
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .testTag("moments_notifications_back_button"),
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }

        Text(
            text = "全部互动消息",
            style =
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center),
        )

        Box(
            modifier =
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
        ) {
            IconButton(
                onClick = { onShowMenuChange(true) },
                modifier = Modifier.testTag("moments_notifications_more_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp),
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onShowMenuChange(false) },
            ) {
                DropdownMenuItem(
                    text = { Text("清空所有消息") },
                    onClick = onClearAllClick,
                    modifier = Modifier.testTag("moments_notifications_clear_item"),
                )
            }
        }
    }
}

/**
 * 单条互动消息行组件（左侧头像，中间昵称、点赞爱心/评论内容及时间，右侧对应动态缩略图）。
 *
 * @param notification 互动消息对象
 * @param currentYear 当前年份
 * @param onClick 单击回调
 * @param onLongClick 长按回调
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MomentsNotificationItem(
    notification: MomentNotification,
    currentYear: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkColor = Color(0xFF576B95)

    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .testTag("moments_notification_${notification.id}"),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 左侧头像
            NotificationAuthorAvatar(
                avatarPath = notification.authorAvatar,
                name = notification.authorName,
                modifier = Modifier.size(44.dp),
            )

            // 中间互动信息：昵称、互动操作与格式化时间戳
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = notification.authorName,
                    color = linkColor,
                    style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                when (notification.type) {
                    MomentNotificationType.LIKE -> {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = "赞",
                            tint = linkColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    MomentNotificationType.COMMENT -> {
                        Text(
                            text = notification.content.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurface,
                            style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Text(
                    text = formatNotificationTimestamp(notification.timestamp, currentYear),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                    ),
                )
            }

            // 右侧对应动态缩略图（若有配图展示首张缩略图，纯文字动态展示小文字块）
            if (notification.postPhotoPath != null) {
                val uri = rememberPhotoUri(notification.postPhotoPath)
                AsyncImage(
                    uri = uri,
                    contentDescription = "动态配图缩略图",
                    contentScale = ContentScale.Crop,
                    modifier =
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            } else if (notification.postText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = notification.postText,
                            style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(56.dp))
            }
        }

        // 细分割线（左起对齐正文起始处）
        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        )
    }
}

/**
 * 互动消息头像组件：若有本地或网络图片则加载图片，否则根据昵称生成确定性的柔和背景与首字符。
 *
 * @param avatarPath 头像路径
 * @param name 昵称
 * @param modifier 布局修饰符
 */
@Composable
private fun NotificationAuthorAvatar(
    avatarPath: String?,
    name: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = getAvatarBackgroundColor(name),
        modifier = modifier,
    ) {
        if (avatarPath != null) {
            val uri = rememberPhotoUri(avatarPath)
            AsyncImage(
                uri = uri,
                contentDescription = "$name 的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

/**
 * 根据名字哈希获取柔和的头像背景色。
 */
private fun getAvatarBackgroundColor(name: String): Color {
    val colors = listOf(
        Color(0xFF5C6BC0), // 靛蓝
        Color(0xFF26A69A), // 蓝绿
        Color(0xFFEF5350), // 珊瑚红
        Color(0xFFAB47BC), // 浅紫
        Color(0xFFFFA726), // 橙黄
        Color(0xFF42A5F5), // 浅蓝
        Color(0xFF8D6E63), // 浅褐
        Color(0xFF78909C), // 灰蓝
    )
    val index = (name.hashCode() and 0x7FFFFFFF) % colors.size
    return colors[index]
}

/**
 * 记忆并解析配图 URI。
 */
@Composable
private fun rememberPhotoUri(path: String?): String? = remember(path) {
    if (path == null) return@remember null
    if (path.startsWith("content://") || path.startsWith("file://")) {
        path
    } else {
        val file = File(path.removePrefix("file://"))
        if (file.exists()) "file://${file.absolutePath}" else path
    }
}
