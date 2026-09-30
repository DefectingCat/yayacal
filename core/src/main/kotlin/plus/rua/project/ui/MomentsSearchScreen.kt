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
 * @param viewModel 本地朋友圈状态；从详情返回时刷新动态和评论
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsSearchScreen(
    onBack: () -> Unit,
    onPostClick: (String) -> Unit,
    viewModel: MomentsViewModel = run {
        val context = LocalContext.current.applicationContext
        viewModel(
            factory = viewModelFactory {
                initializer { MomentsViewModel(MomentsStorage.fromContext(context), context.filesDir) }
            },
        )
    },
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPosts() }
    var query by rememberSaveable { mutableStateOf("") }
    val keyword = query.trim()
    val results = remember(uiState.posts, keyword) { searchMoments(uiState.posts, keyword) }
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
                    .semantics { contentDescription = "搜索我的朋友圈内容" }
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
                                Text("搜索我的朋友圈内容", color = mutedColor, fontSize = 17.sp, maxLines = 1)
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
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(momentsBackgroundColor())) {
                key(keyword) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 128.dp),
                        modifier = Modifier.fillMaxSize().testTag("moments_search_results"),
                    ) {
                        item(key = "summary") {
                            Text(
                                highlightedSearchText("找到与「$keyword」相关的朋友圈，共${results.size}条。", keyword, highlightColor),
                                color = mutedColor,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 24.dp).testTag("moments_search_count"),
                            )
                        }
                        items(results, key = { it.post.id }) { result ->
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
                        item(key = "footer") {
                            if (results.isEmpty()) {
                                Text(
                                    "没有找到相关的朋友圈",
                                    color = mutedColor,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(top = 48.dp).testTag("moments_search_empty"),
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                ) {
                                    Spacer(Modifier.width(24.dp).height(1.dp).background(mutedColor.copy(alpha = 0.3f)))
                                    Text("以上为全部搜索结果", color = mutedColor, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                    Spacer(Modifier.width(24.dp).height(1.dp).background(mutedColor.copy(alpha = 0.3f)))
                                }
                            }
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(momentsBackgroundColor())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    Text("没有搜到想找的朋友圈", color = momentsLinkColor(), fontSize = 14.sp)
                    Text("试试正文、位置或评论中的关键词", color = mutedColor, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
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
                            uri = if (path.startsWith("file://")) path else "file://$path",
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
