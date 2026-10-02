package plus.rua.project.ui

import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.panpf.sketch.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import plus.rua.project.MomentComment
import plus.rua.project.MomentPost
import plus.rua.project.MomentsStorage
import plus.rua.project.MomentsViewModel
import kotlin.time.Instant

/**
 * 朋友圈详情页，加载远端动态并提交点赞、文字/图片评论。
 *
 * @param postId 要展示的动态 ID
 * @param onAuthorClick 点击作者头像时触发，传递作者 ID
 * @param onBack 点击返回时触发
 * @param focusComment 从评论入口进入时是否自动聚焦输入框
 * @param viewModel 远端朋友圈加载与互动状态
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsDetailScreen(
    postId: String,
    onBack: () -> Unit,
    onAuthorClick: (String) -> Unit = {},
    focusComment: Boolean = false,
    viewModel: MomentsViewModel = run {
        val context = LocalContext.current.applicationContext
        viewModel(
            factory = viewModelFactory {
                initializer { MomentsViewModel.fromContext(context) }
            },
        )
    },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPost(postId) }
    val post = uiState.posts.find { it.id == postId }
    if (post == null) {
        Column(modifier.fillMaxSize().background(momentsBackgroundColor()).semantics { testTagsAsResourceId = true }.testTag("moments_detail_screen")) {
            MomentsDetailTopBar(onBack = onBack)
            MomentsRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshPost(postId) },
                enabled = !uiState.isLoading && !uiState.unavailable,
                content = {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "state") {
                            when {
                                uiState.unavailable -> MomentsStateContent(
                                    title = "这条动态已不可见",
                                    description = "内容可能已删除，或当前账号无法查看",
                                    isError = true,
                                    actionLabel = "返回",
                                    onAction = onBack,
                                    modifier = Modifier.fillParentMaxHeight(),
                                )

                                uiState.error != null -> MomentsErrorContent(uiState.error.orEmpty(), modifier = Modifier.fillParentMaxHeight())

                                else -> MomentsLoadingContent(layout = MomentsLoadingLayout.Detail)
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        return
    }
    MomentsPoll(postId) {
        if (!uiState.isLoading && post.comments.size <= 50) viewModel.refreshPost(postId)
    }
    Column(modifier.fillMaxSize()) {
        MomentsDetailScreen(
            post = post,
            currentAccountId = uiState.currentAccountId,
            onBack = onBack,
            onAuthorClick = onAuthorClick,
            onLike = { viewModel.toggleLike(post.id) },
            onDelete = if (post.authorId == uiState.currentAccountId) ({ viewModel.deletePost(post.id) }) else null,
            onChangeVisibility = if (post.authorId == uiState.currentAccountId) ({ viewModel.setVisibility(post.id, if (post.visibility == "私密") "公开" else "私密") }) else null,
            onDeleteComment = viewModel::deleteComment,
            onSendComment = { text, replyTo, photoUri -> viewModel.sendComment(context, post.id, text, replyTo, photoUri) },
            focusComment = focusComment,
            networkState = uiState,
            onRefresh = { viewModel.refreshPost(postId) },
            onLoadComments = viewModel::loadMoreComments,
            onDismissOperationError = viewModel::dismissOperationError,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 微信风格详情布局：动态正文、点赞头像行、带时间的评论列表与固定底部输入栏。
 *
 * @param post 当前动态
 * @param onAuthorClick 点击作者头像时触发，传递作者 ID
 * @param onBack 点击返回时触发
 * @param onLike 点击“赞/取消”时触发
 * @param onDelete 确认删除本人动态时触发；为 null 时隐藏所有删除入口
 * @param onSendComment 点击发送或键盘发送时触发，返回成功后才清空草稿
 * @param focusComment 是否在首次进入时聚焦评论框
 * @param currentAccountId 当前操作账号，决定本人评论的删除入口
 * @param onChangeVisibility 作者点击切换可见性时触发
 * @param onDeleteComment 确认删除当前账号仍可删除的评论时触发
 * @param networkState 加载与分页状态
 * @param onRefresh 未加载时下拉页面触发
 * @param onDismissOperationError 点击操作失败提示的“知道了”时触发
 * @param onLoadComments 点击加载更多评论时触发
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsDetailScreen(
    post: MomentPost,
    onBack: () -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onLike: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSendComment: suspend (String, String?, Uri?) -> Boolean,
    focusComment: Boolean = false,
    currentAccountId: String? = null,
    onChangeVisibility: (() -> Unit)? = null,
    onDeleteComment: (String) -> Unit = {},
    networkState: plus.rua.project.MomentsUiState = plus.rua.project.MomentsUiState(),
    onRefresh: () -> Unit = {},
    onLoadComments: () -> Unit = {},
    onDismissOperationError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val visibleComments = post.comments.filterNot { it.deleted }
    var commentMenuId by remember(post.id, currentAccountId) { mutableStateOf<String?>(null) }
    var deleteCommentId by remember(post.id, currentAccountId) { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(post.id) { mutableStateOf("") }
    var replyToId by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var replyToName by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var photoUri by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var showEmoji by rememberSaveable { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewPhotos by remember { mutableStateOf<List<String>?>(null) }
    var previewIndex by remember { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri.toString()
    }
    val barColor = momentsBarColor()
    val backgroundColor = momentsBackgroundColor()
    val linkColor = momentsLinkColor()
    val send = {
        if (!isSending && (draft.isNotBlank() || photoUri != null)) {
            isSending = true
            error = null
            scope.launch {
                try {
                    if (onSendComment(draft, replyToId, photoUri?.let(Uri::parse))) {
                        draft = ""
                        replyToName = null
                        replyToId = null
                        photoUri = null
                        showEmoji = false
                        focusManager.clearFocus()
                        keyboard?.hide()
                        // 数据更新后的尾项始终是留白，滚到末尾展示刚发送的评论。
                        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                    } else {
                        error = "评论发送失败，请重试"
                    }
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    error = exception.message ?: "评论发送失败，请重试"
                } finally {
                    isSending = false
                }
            }
        }
    }
    LaunchedEffect(focusComment) {
        if (focusComment) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(post.comments) {
        if (visibleComments.none { it.id == commentMenuId }) commentMenuId = null
        if (visibleComments.none { it.id == deleteCommentId && canDeleteMomentComment(it, currentAccountId) }) deleteCommentId = null
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) commentMenuId = null
    }
    BackHandler(enabled = showEmoji || replyToName != null) {
        showEmoji = false
        replyToName = null
        replyToId = null
        focusManager.clearFocus()
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier.fillMaxSize().background(backgroundColor).imePadding()
                .semantics { testTagsAsResourceId = true }.testTag("moments_detail_screen"),
        ) {
            MomentsDetailTopBar(
                onBack = onBack,
                isPrivate = post.visibility.startsWith("私密"),
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = { showMore = true }) { Icon(Icons.Filled.MoreHoriz, "更多") }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            onChangeVisibility?.let { change ->
                                DropdownMenuItem(text = { Text(if (post.visibility == "私密") "设为公开" else "设为仅自己可见") }, onClick = {
                                    showMore = false
                                    change()
                                })
                            }
                            DropdownMenuItem(text = { Text("删除该朋友圈") }, onClick = {
                                showMore = false
                                showDelete = true
                            })
                        }
                    }
                },
            )
            MomentsRefreshBox(
                isRefreshing = networkState.isRefreshing,
                onRefresh = onRefresh,
                enabled = !networkState.isLoading,
                content = {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("moments_detail_list")) {
                        networkState.error?.let { error ->
                            item(key = "network_error") { MomentsErrorNotice(error, modifier = Modifier.padding(16.dp)) }
                        }
                        networkState.operationError?.let { error ->
                            item(key = "operation_error") {
                                MomentsErrorNotice(error, title = "操作未完成", hint = "请稍后重新操作", actionLabel = "知道了", onAction = onDismissOperationError, modifier = Modifier.padding(16.dp))
                            }
                        }
                        item(key = "post") {
                            MomentFeedItem(
                                post = post, authorName = post.authorName, avatarPath = post.authorAvatarPath,
                                onAuthorClick = { onAuthorClick(post.authorId) },
                                onDelete = onDelete?.let { { showDelete = true } },
                                onLike = onLike,
                                onComment = {
                                    replyToName = null
                                    replyToId = null
                                    showEmoji = false
                                    focusRequester.requestFocus()
                                    keyboard?.show()
                                },
                                showFullTimestamp = true,
                                onPhotoClick = { photos, index ->
                                    previewPhotos = photos
                                    previewIndex = index
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                            )
                        }
                        if (post.likes.isNotEmpty()) {
                            item(key = "likes") {
                                Row(
                                    Modifier.padding(horizontal = 12.dp).fillMaxWidth().background(barColor)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.FavoriteBorder, "点赞", tint = linkColor, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(14.dp))
                                    post.likes.forEach { liker -> MomentAvatar(liker.avatarPath, liker.name, Modifier.size(30.dp).padding(end = 4.dp)) }
                                }
                            }
                        }
                        itemsIndexed(visibleComments, key = { _, comment -> comment.id }) { index, comment ->
                            MomentCommentRow(
                                comment = comment,
                                avatarPath = comment.authorAvatarPath,
                                showCommentIcon = index == 0,
                                canDelete = canDeleteMomentComment(comment, currentAccountId),
                                menuExpanded = commentMenuId == comment.id,
                                onShowMenu = {
                                    showEmoji = false
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    commentMenuId = comment.id
                                },
                                onDismissMenu = { commentMenuId = null },
                                onCopy = {
                                    commentMenuId = null
                                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("评论", comment.text))) }
                                },
                                onDelete = {
                                    commentMenuId = null
                                    deleteCommentId = comment.id
                                },
                                onReply = {
                                    if (!comment.deleted) {
                                        replyToName = comment.authorName
                                        replyToId = comment.id
                                        showEmoji = false
                                        focusRequester.requestFocus()
                                        keyboard?.show()
                                    }
                                },
                                onPhotoClick = {
                                    previewPhotos = listOfNotNull(comment.photoPath)
                                    previewIndex = 0
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                        if (visibleComments.isEmpty() && networkState.commentsCursor == null) {
                            item(key = "no_comments") {
                                Text("还没有评论，聊聊你的想法吧", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp))
                            }
                        }
                        item(key = "more") {
                            MomentsPagingFooter(networkState.commentsCursor != null, networkState.isLoadingMore, networkState.loadMoreError, onLoadComments, enabled = !networkState.isLoading)
                        }
                        item(key = "bottom") { Spacer(Modifier.height(20.dp)) }
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Column(Modifier.fillMaxWidth().background(barColor).navigationBarsPadding()) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                if (replyToName != null) {
                    Row(Modifier.padding(start = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("回复 $replyToName", color = linkColor, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            replyToName = null
                            replyToId = null
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, "取消回复", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                photoUri?.let { uri ->
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            uri = uri,
                            contentDescription = "待发送图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp),
                        )
                        IconButton(onClick = { photoUri = null }, enabled = !isSending) {
                            Icon(Icons.Filled.Close, "移除评论图片")
                        }
                    }
                }
                error?.let {
                    MomentsErrorNotice(it, title = "评论还没发出去", hint = "已保留文字和图片", actionLabel = "重新发送", onAction = if (isSending) null else send, modifier = Modifier.padding(8.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = draft, onValueChange = {
                            draft = it
                            error = null
                        }, enabled = !isSending,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 20.sp),
                        cursorBrush = SolidColor(linkColor), maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp).background(backgroundColor, RoundedCornerShape(4.dp))
                            .focusRequester(focusRequester).testTag("moments_comment_input").padding(horizontal = 8.dp, vertical = 8.dp),
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) Text("评论", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp)
                                inner()
                            }
                        },
                    )
                    IconButton(onClick = {
                        showEmoji = !showEmoji
                        if (showEmoji) {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        } else {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                    }, enabled = !isSending, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.SentimentSatisfiedAlt, "表情", modifier = Modifier.size(24.dp))
                    }
                    IconButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !isSending,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Outlined.Image, "选择评论图片", modifier = Modifier.size(24.dp))
                    }
                    Button(
                        onClick = { send() },
                        enabled = !isSending && (draft.isNotBlank() || photoUri != null),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF07C160), contentColor = Color.White),
                        modifier = Modifier.height(36.dp).testTag("moments_comment_send"),
                    ) {
                        if (isSending) MomentsLoadingSpinner(color = Color.White, modifier = Modifier.size(16.dp)) else Text("发送", fontSize = 13.sp)
                    }
                }
                if (showEmoji) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.fillMaxWidth().height(132.dp),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        items(listOf("😊", "😂", "🥰", "😍", "😘", "😎", "😭", "😅", "👍", "❤️", "🎉", "🌹", "🐱", "🐶", "🦆", "✨", "🙏", "🤔", "😴", "🥳", "🤗", "👏", "💪", "🍀")) { emoji ->
                            TextButton(onClick = { draft += emoji }, enabled = !isSending) { Text(emoji, fontSize = 22.sp) }
                        }
                    }
                }
            }
        }
    }
    if (showDelete && onDelete != null) {
        MomentsDeleteDialog(onDismiss = { showDelete = false }, onConfirm = {
            showDelete = false
            onDelete()
        })
    }
    visibleComments.find { it.id == deleteCommentId && canDeleteMomentComment(it, currentAccountId) }?.let { comment ->
        MomentsCommentDeleteDialog(
            onDismiss = { deleteCommentId = null },
            onConfirm = {
                // 读取仍待确认的 ID，避免连续点击重复提交；账号变化会重置该状态。
                if (deleteCommentId == comment.id) {
                    deleteCommentId = null
                    onDeleteComment(comment.id)
                }
            },
        )
    }
    previewPhotos?.let { photos ->
        MomentsPhotoPreviewDialog(photos, previewIndex, onDismiss = { previewPhotos = null })
    }
}

@Composable
private fun MomentCommentRow(
    comment: MomentComment,
    avatarPath: String?,
    showCommentIcon: Boolean,
    canDelete: Boolean,
    menuExpanded: Boolean,
    onShowMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onReply: () -> Unit,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkColor = momentsLinkColor()
    val panelColor = momentsBarColor()
    val selectedColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF3A3A3A) else Color(0xFFE1E1E1)
    val canCopy = comment.text.isNotBlank() && !comment.deleted
    val onLongClick = if (canCopy || canDelete) onShowMenu else null
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(panelColor).padding(start = 12.dp, end = 8.dp)) {
            Box(Modifier.width(32.dp).padding(top = 16.dp)) {
                if (showCommentIcon) Icon(Icons.Outlined.ChatBubbleOutline, "评论", tint = linkColor, modifier = Modifier.size(18.dp))
            }
            Card(
                onClick = onReply,
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = if (menuExpanded) selectedColor else panelColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.weight(1f).testTag("moment_comment_${comment.id}"),
            ) {
                // 组合手势覆盖整张卡片；嵌套图片使用相同长按入口，长按不会落入单击回调。
                Column(Modifier.fillMaxWidth().combinedClickable(onClick = onReply, onLongClick = onLongClick, onLongClickLabel = "评论操作")) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(Modifier.padding(vertical = 10.dp)) {
                        MomentAvatar(avatarPath, comment.authorName, Modifier.size(30.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    comment.authorName,
                                    color = linkColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(formatMomentDetailTime(comment.timestamp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
                            }
                            val body = buildAnnotatedString {
                                comment.replyToName?.let { name ->
                                    append("回复 ")
                                    withStyle(SpanStyle(color = linkColor)) { append(name) }
                                    append(": ")
                                }
                                append(comment.text)
                            }
                            if (body.isNotEmpty()) Text(body, fontSize = 14.sp, lineHeight = 20.sp)
                            comment.photoPath?.let { path ->
                                Card(
                                    onClick = onPhotoClick,
                                    shape = RoundedCornerShape(3.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    modifier = Modifier.padding(top = 6.dp).size(88.dp).testTag("moment_comment_image_${comment.id}"),
                                ) {
                                    AsyncImage(
                                        uri = momentsThumbnailUri(path),
                                        state = rememberMomentsImageState(),
                                        contentDescription = "评论图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().combinedClickable(onClick = onPhotoClick, onLongClick = onLongClick, onLongClickLabel = "评论操作"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (menuExpanded) {
            MomentsCommentActionsMenu(
                onCopy = if (canCopy) onCopy else null,
                onDelete = if (canDelete) onDelete else null,
                onDismiss = onDismissMenu,
            )
        }
    }
}

/** 本人动态删除确认框。取消与返回仅关闭；点击确定才调用删除。 */
@Composable
internal fun MomentsDeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = momentsBackgroundColor(),
            modifier = Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag("moments_delete_dialog"),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("提示", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp))
                Text(
                    "删除该朋友圈？",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 28.dp),
                )
                HorizontalDivider(thickness = 0.5.dp)
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(0.dp), modifier = Modifier.weight(1f).height(50.dp).testTag("moments_delete_cancel")) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    }
                    Box(Modifier.width(0.5.dp).height(50.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    TextButton(onClick = onConfirm, shape = RoundedCornerShape(0.dp), modifier = Modifier.weight(1f).height(50.dp).testTag("moments_delete_confirm")) {
                        Text("确定", color = momentsLinkColor(), fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/** 动态、评论和点赞头像；远端加载失败时保留人物占位。 */
@Composable
internal fun MomentAvatar(avatarPath: String?, name: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        val uri = remember(avatarPath) { resolvePhotoUri(avatarPath) }
        if (uri != null) {
            AsyncImage(uri = uri, state = rememberMomentsImageState(avatar = true), contentDescription = "$name 的头像", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Person, "$name 的头像", modifier = Modifier.padding(4.dp)) }
        }
    }
}

@Composable
internal fun momentsLinkColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFA9BADD) else Color(0xFF576B95)

@Composable
internal fun momentsBarColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.surfaceContainer else Color(0xFFF7F7F7)

@Composable
internal fun momentsBackgroundColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.surface else Color.White

/** 使用本地时区显示详情页时间；测试可指定时区。 */
fun formatMomentDetailTime(timestamp: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dateTime = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(timeZone)
    return "${dateTime.year}年${dateTime.month.ordinal + 1}月${dateTime.day}日 ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}

/** 加载、错误和正文共用的详情导航栏；点击返回时调用 onBack，操作菜单由 actions 提供。 */
@Composable
private fun MomentsDetailTopBar(
    onBack: () -> Unit,
    isPrivate: Boolean = false,
    actions: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(momentsBarColor()).statusBarsPadding().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("moments_detail_back")) {
            Icon(Icons.Filled.ChevronLeft, "返回", modifier = Modifier.size(28.dp))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("详情", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (isPrivate) Icon(Icons.Filled.Lock, "私密动态", modifier = Modifier.padding(start = 4.dp).size(13.dp))
        }
        Box(Modifier.size(48.dp)) { actions() }
    }
}
