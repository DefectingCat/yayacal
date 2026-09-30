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
 * 朋友圈详情页，加载本地动态并持久化点赞、文字/图片评论。
 *
 * @param postId 要展示的动态 ID
 * @param onBack 点击返回、删除完成或动态不存在时触发
 * @param focusComment 从评论入口进入时是否自动聚焦输入框
 * @param viewModel 本地朋友圈状态与存储
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsDetailScreen(
    postId: String,
    onBack: () -> Unit,
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
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            MomentsLoadStatus(uiState.isLoading, uiState.error, false, { viewModel.refreshPost(postId) }, {})
        }
        return
    }
    MomentsPoll(postId) {
        if (!uiState.isLoading && post.comments.size <= 50) viewModel.refreshPost(postId)
    }
    Column(modifier.fillMaxSize()) {
        MomentsDetailScreen(
            post = post,
            username = uiState.username,
            avatarPath = uiState.avatarPath,
            currentAccountId = uiState.currentAccountId,
            onBack = onBack,
            onLike = { viewModel.toggleLike(post.id) },
            onDelete = if (post.authorId == uiState.currentAccountId) ({ viewModel.deletePost(post.id) }) else null,
            onChangeVisibility = if (post.authorId == uiState.currentAccountId) ({ viewModel.setVisibility(post.id, if (post.visibility == "私密") "公开" else "私密") }) else null,
            onDeleteComment = viewModel::deleteComment,
            onSendComment = { text, replyTo, photoUri -> viewModel.sendComment(context, post.id, text, replyTo, photoUri) },
            focusComment = focusComment,
            networkState = uiState,
            onRefresh = { viewModel.refreshPost(postId) },
            onLoadComments = viewModel::loadMoreComments,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 微信风格详情布局：动态正文、点赞头像行、带时间的评论列表与固定底部输入栏。
 *
 * @param post 当前动态
 * @param username 本地个人昵称
 * @param avatarPath 本地个人头像路径
 * @param onBack 点击返回时触发
 * @param onLike 点击“赞/取消”时触发
 * @param onDelete 确认删除本人动态时触发；为 null 时隐藏所有删除入口
 * @param onSendComment 点击发送或键盘发送时触发，返回成功后才清空草稿
 * @param focusComment 是否在首次进入时聚焦评论框
 * @param currentAccountId 当前操作账号，决定本人评论的删除入口
 * @param onChangeVisibility 作者点击切换可见性时触发
 * @param onDeleteComment 点击本人评论的删除按钮时触发
 * @param networkState 加载与分页状态
 * @param onRefresh 点击刷新或重试时触发
 * @param onLoadComments 点击加载更多评论时触发
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsDetailScreen(
    post: MomentPost,
    username: String,
    avatarPath: String?,
    onBack: () -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
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
                    error = "评论发送失败，请重试"
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
            Row(
                Modifier.fillMaxWidth().background(
                    if (backgroundColor == Color.White) Color(0xFFEDEDED) else barColor,
                ).statusBarsPadding().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("moments_detail_back")) {
                    Icon(Icons.Filled.ChevronLeft, "返回", modifier = Modifier.size(28.dp))
                }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("详情", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (post.visibility.startsWith("私密")) {
                        Icon(Icons.Filled.Lock, "私密动态", modifier = Modifier.padding(start = 4.dp).size(13.dp))
                    }
                }
                Box(Modifier.size(48.dp)) {
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
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("moments_detail_list")) {
                item(key = "post") {
                    MomentFeedItem(
                        post = post, authorName = post.authorName, avatarPath = post.authorAvatarPath,
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
                itemsIndexed(post.comments, key = { _, comment -> comment.id }) { index, comment ->
                    MomentCommentRow(
                        comment = comment,
                        avatarPath = comment.authorAvatarPath,
                        showCommentIcon = index == 0,
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
                    if (!comment.deleted && comment.authorId == currentAccountId) {
                        TextButton(onClick = { onDeleteComment(comment.id) }, modifier = Modifier.padding(start = 48.dp)) { Text("删除评论") }
                    }
                }
                item(key = "network") {
                    MomentsLoadStatus(networkState.isLoading, networkState.error, networkState.commentsCursor != null, onRefresh, onLoadComments)
                }
                item(key = "bottom") { Spacer(Modifier.height(20.dp)) }
            }
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
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = draft, onValueChange = { draft = it }, enabled = !isSending,
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
                        Text(if (isSending) "发送中" else "发送", fontSize = 13.sp)
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
    previewPhotos?.let { photos ->
        MomentsPhotoPreviewDialog(photos, previewIndex, onDismiss = { previewPhotos = null })
    }
}

@Composable
private fun MomentCommentRow(
    comment: MomentComment,
    avatarPath: String?,
    showCommentIcon: Boolean,
    onReply: () -> Unit,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkColor = momentsLinkColor()
    val panelColor = momentsBarColor()
    Row(modifier.fillMaxWidth().background(panelColor).padding(start = 12.dp, end = 8.dp)) {
        Box(Modifier.width(32.dp).padding(top = 16.dp)) {
            if (showCommentIcon) Icon(Icons.Outlined.ChatBubbleOutline, "评论", tint = linkColor, modifier = Modifier.size(18.dp))
        }
        Card(
            onClick = onReply,
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = panelColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.weight(1f).testTag("moment_comment_${comment.id}"),
        ) {
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
                            modifier = Modifier.padding(top = 6.dp).size(88.dp),
                        ) {
                            AsyncImage(uri = "file://$path", contentDescription = "评论图片", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
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

@Composable
internal fun MomentAvatar(avatarPath: String?, name: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        if (avatarPath != null) {
            val uri = if (avatarPath.startsWith("content://") || avatarPath.startsWith("file://")) avatarPath else "file://$avatarPath"
            AsyncImage(uri = uri, contentDescription = "$name 的头像", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
