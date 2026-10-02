package plus.rua.project.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import plus.rua.project.MomentSearchResult
import plus.rua.project.MomentsStorage
import plus.rua.project.MomentsViewModel
import plus.rua.project.searchMoments
import kotlin.time.Instant

/**
 * 本机个人朋友圈搜索：空输入时留白，输入后检索正文、位置和评论并展示动态预览。
 *
 * @param onBack 点击“取消”时触发，返回个人相册
 * @param onPostClick 点击搜索结果时触发，参数为动态 ID
 * @param authorId 搜索的作者，为 null 时搜索当前账号
 * @param viewModel 本地朋友圈状态；从详情返回时刷新动态和评论
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsSearchScreen(
    onBack: () -> Unit,
    authorId: String? = null,
    onPostClick: (String) -> Unit,
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val keyword = query.trim()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (keyword.isNotEmpty() && uiState.searchQuery == keyword && uiState.hasLoadedPosts) {
            viewModel.refreshPosts(authorId = authorId ?: viewModel.accountId, keyword = keyword)
        }
    }
    LaunchedEffect(keyword) {
        if (keyword.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        viewModel.refreshPosts(authorId = authorId ?: viewModel.accountId, keyword = keyword)
    }
    val results = remember(uiState.posts, uiState.searchQuery, keyword) {
        (if (uiState.searchQuery == keyword) uiState.posts else emptyList()).map { post ->
            searchMoments(listOf(post), keyword).firstOrNull() ?: plus.rua.project.MomentSearchResult(post, listOf("评论"), "动态中的评论包含搜索内容")
        }
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val barColor = if (darkTheme) momentsBarColor() else Color(0xFFEDEDED)
    val inputColor = if (darkTheme) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White
    val mutedColor = if (darkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF888888)
    val highlightColor = if (darkTheme) Color(0xFF63D18B) else Color(0xFF078743)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(barColor)
            .semantics { testTagsAsResourceId = true }
            .testTag("moments_search_screen")
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp),
                cursorBrush = SolidColor(highlightColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "搜索朋友圈内容" }
                    .testTag("moments_search_input"),
                decorationBox = { innerTextField ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(inputColor)
                            .padding(start = 10.dp),
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = mutedColor, modifier = Modifier.size(22.dp))
                        Box(modifier = Modifier.weight(1f).padding(start = 6.dp, top = 12.dp, bottom = 12.dp)) {
                            if (query.isEmpty()) {
                                Text("搜索朋友圈内容", color = mutedColor, fontSize = 17.sp, maxLines = 1)
                            }
                            innerTextField()
                        }
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    query = ""
                                    focusRequester.requestFocus()
                                    keyboard?.show()
                                },
                                modifier = Modifier.testTag("moments_search_clear"),
                            ) {
                                Icon(Icons.Filled.Cancel, contentDescription = "清空搜索", tint = mutedColor, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                },
            )
            TextButton(
                onClick = {
                    keyboard?.hide()
                    onBack()
                },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.testTag("moments_search_cancel"),
            ) {
                Text("取消", color = momentsLinkColor(), fontSize = 17.sp)
            }
        }

        if (keyword.isNotEmpty()) {
            val isCurrentSearch = uiState.searchQuery == keyword
            MomentsRefreshBox(
                isRefreshing = !isCurrentSearch || uiState.isRefreshing,
                onRefresh = { viewModel.refreshPosts(authorId = authorId ?: viewModel.accountId, keyword = keyword) },
                enabled = !uiState.isLoading && isCurrentSearch,
                content = {
                    key(keyword) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("moments_search_results"),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            if (results.isEmpty()) {
                                item(key = "state") {
                                    when {
                                        !isCurrentSearch -> MomentsLoadingContent()

                                        uiState.error != null -> MomentsErrorContent(uiState.error.orEmpty(), title = "搜索暂时未能完成", modifier = Modifier.fillParentMaxHeight())

                                        !uiState.hasLoadedPosts -> MomentsLoadingContent()

                                        else -> MomentsStateContent(
                                            title = "没有找到相关的朋友圈",
                                            description = "试试正文、位置或评论中的其他关键词",
                                            seed = keyword,
                                            modifier = Modifier.fillParentMaxHeight().testTag("moments_search_empty"),
                                        )
                                    }
                                }
                            } else {
                                uiState.error?.let { error ->
                                    item(key = "error") { MomentsErrorNotice(error, modifier = Modifier.padding(16.dp)) }
                                }
                                item(key = "summary") {
                                    Text(
                                        highlightedSearchText("找到与「$keyword」相关的朋友圈，已加载${results.size}条。", keyword, highlightColor),
                                        color = mutedColor,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(24.dp).testTag("moments_search_count"),
                                    )
                                }
                                items(results, key = { it.post.id }) { result ->
                                    Box(Modifier.padding(horizontal = 24.dp)) {
                                        SearchResultCard(
                                            result = result,
                                            keyword = keyword,
                                            highlightColor = highlightColor,
                                            mutedColor = mutedColor,
                                            onClick = {
                                                keyboard?.hide()
                                                focusManager.clearFocus()
                                                onPostClick(result.post.id)
                                            },
                                        )
                                    }
                                }
                                item(key = "more") {
                                    MomentsPagingFooter(uiState.nextCursor != null, uiState.isLoadingMore, uiState.loadMoreError, viewModel::loadMore, enabled = !uiState.isLoading)
                                }
                                if (uiState.nextCursor == null && !uiState.isLoading && uiState.error == null) {
                                    item(key = "end") {
                                        Text("以上为全部搜索结果", color = mutedColor, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth().background(momentsBackgroundColor()),
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    result: MomentSearchResult,
    keyword: String,
    highlightColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
) {
    val date = remember(result.post.timestamp) {
        Instant.fromEpochMilliseconds(result.post.timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = momentsBackgroundColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).testTag("moments_search_result_${result.post.id}"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result.post.photoPaths.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    result.post.photoPaths.take(3).forEach { path ->
                        AsyncImage(
                            uri = momentsThumbnailUri(path),
                            state = rememberMomentsImageState(),
                            contentDescription = "动态配图",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(86.dp).clip(RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
            Text(
                highlightedSearchText(result.previewText.replace('\n', ' ').replace('\r', ' '), keyword, highlightColor),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${date.year}年${date.month.ordinal + 1}月${date.day}日", color = mutedColor, fontSize = 14.sp)
            Text(
                highlightedSearchText("${result.matchedFields.joinToString("、")}与「$keyword」相关", keyword, highlightColor),
                color = mutedColor,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 按字面查找并高亮所有关键词，英文忽略大小写；与检索规则保持一致。 */
private fun highlightedSearchText(text: String, keyword: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    if (keyword.isNotEmpty()) {
        var start = text.indexOf(keyword, ignoreCase = true)
        while (start >= 0) {
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium), start, start + keyword.length)
            start = text.indexOf(keyword, startIndex = start + keyword.length, ignoreCase = true)
        }
    }
}
