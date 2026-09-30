package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import plus.rua.project.MomentPost
import plus.rua.project.MomentsStorage
import plus.rua.project.MomentsUiState
import plus.rua.project.MomentsViewModel
import kotlin.time.Instant

/**
 * 朋友圈相册月份分组数据。
 *
 * @param month 月份数字（1..12）
 * @param posts 该月份下的动态列表（按时间降序）
 */
data class AlbumMonthGroup(
    val month: Int,
    val posts: List<MomentPost>,
)

/**
 * 朋友圈相册年份分组数据。
 *
 * @param year 年份（如 2025）
 * @param months 该年份下的月份分组列表（按月份降序）
 */
data class AlbumYearGroup(
    val year: Int,
    val months: List<AlbumMonthGroup>,
)

/**
 * 将朋友圈动态按年份与月份进行分组聚合（降序排列）。
 *
 * @param posts 原始动态列表
 * @param timeZone 时区（默认系统当前时区）
 * @return 结构化的年份分组列表
 */
fun groupPostsForAlbum(
    posts: List<MomentPost>,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<AlbumYearGroup> {
    if (posts.isEmpty()) return emptyList()

    val sorted = posts.sortedByDescending { it.timestamp }
    val yearGroups = sorted.groupBy { post ->
        Instant.fromEpochMilliseconds(post.timestamp).toLocalDateTime(timeZone).year
    }

    return yearGroups.map { (year, yearPosts) ->
        val monthGroups = yearPosts.groupBy { post ->
            @Suppress("DEPRECATION") // kotlinx-datetime monthNumber
            Instant.fromEpochMilliseconds(post.timestamp).toLocalDateTime(timeZone).monthNumber
        }.map { (month, monthPosts) ->
            AlbumMonthGroup(month = month, posts = monthPosts)
        }
        AlbumYearGroup(year = year, months = monthGroups)
    }
}

/**
 * 朋友圈相册页面（状态版），复刻微信朋友圈个人相册网格页面。
 * 顶部带有“朋友圈相册”居中标题和返回按钮，内容按年份与月份以 3 列网格展示所有动态的缩略图卡片。
 *
 * @param onBack 点击左上角返回按钮时触发
 * @param onPostClick 点击动态缩略图卡片时触发，参数为动态 ID
 * @param viewModel 朋友圈 ViewModel
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsAlbumScreen(
    onBack: () -> Unit,
    onPostClick: (String) -> Unit = {},
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPosts()
    }

    MomentsAlbumScreen(
        uiState = uiState,
        onBack = onBack,
        onPostClick = onPostClick,
        modifier = modifier,
    )
}

/**
 * 朋友圈相册页面（无状态版）。
 *
 * @param uiState 朋友圈当前 UI 状态
 * @param onBack 点击左上角返回按钮时触发
 * @param onPostClick 点击动态缩略图卡片时触发，参数为动态 ID
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsAlbumScreen(
    uiState: MomentsUiState,
    onBack: () -> Unit,
    onPostClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val groupedYears = remember(uiState.posts) {
        groupPostsForAlbum(uiState.posts)
    }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .semantics { testTagsAsResourceId = true }
            .testTag("moments_album_screen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部导航栏：居中标题“朋友圈相册”与左侧返回键
            MomentsAlbumTopBar(onBack = onBack)

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (groupedYears.isEmpty()) {
                    item(key = "empty_state") {
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 120.dp, bottom = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "暂无朋友圈相册",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    groupedYears.forEach { yearGroup ->
                        // 年份标题，如“2025 年”
                        item(key = "year_${yearGroup.year}") {
                            Text(
                                text = "${yearGroup.year} 年",
                                style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)
                                    .testTag("moments_album_year_${yearGroup.year}"),
                            )
                        }

                        // 该年份下的各个月份分组
                        yearGroup.months.forEach { monthGroup ->
                            item(key = "month_${yearGroup.year}_${monthGroup.month}") {
                                MomentsAlbumMonthSection(
                                    month = monthGroup.month,
                                    posts = monthGroup.posts,
                                    onPostClick = onPostClick,
                                    modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                        .testTag("moments_album_month_${yearGroup.year}_${monthGroup.month}"),
                                )
                            }
                        }
                    }
                }

                // 底部时间轴结束标志：— · —
                item(key = "album_footer") {
                    UserMomentsFooter(modifier = Modifier.testTag("moments_album_footer"))
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
 * 朋友圈相册顶部导航栏。
 *
 * @param onBack 返回按钮回调
 * @param modifier 布局修饰符
 */
@Composable
private fun MomentsAlbumTopBar(
    onBack: () -> Unit,
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
                .testTag("moments_album_back_button"),
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }

        Text(
            text = "朋友圈相册",
            style =
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * 朋友圈相册单个月份分组行组件：左侧月份标签（如“10 月”），右侧自适应 3 列动态网格。
 *
 * @param month 月份数字（1..12）
 * @param posts 该月份的动态列表
 * @param onPostClick 点击动态缩略图时的回调
 * @param modifier 布局修饰符
 */
@Composable
private fun MomentsAlbumMonthSection(
    month: Int,
    posts: List<MomentPost>,
    onPostClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val leftColumnWidth = 60.dp
        val spacing = 6.dp
        val availableWidth = maxWidth - leftColumnWidth - spacing
        val tileSize = ((availableWidth - spacing * 2) / 3).coerceAtMost(86.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // 左侧列：月份，如“10 月”
            Text(
                text = "$month 月",
                style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(leftColumnWidth),
            )

            // 右侧列：以 3 列一组自适应排列网格卡片
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                posts.chunked(3).forEach { rowPosts ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        rowPosts.forEach { post ->
                            MomentsAlbumTile(
                                post = post,
                                onClick = { onPostClick(post.id) },
                                modifier =
                                Modifier
                                    .size(tileSize)
                                    .testTag("moments_album_item_${post.id}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 朋友圈相册单个动态缩略图卡片。
 *
 * @param post 朋友圈动态
 * @param onClick 点击卡片时的回调
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsAlbumTile(
    post: MomentPost,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (post.photoPaths.isNotEmpty()) {
                MomentsThumbnailPhotos(
                    photoPaths = post.photoPaths,
                    onPhotoClick = { onClick() },
                    modifier = Modifier.fillMaxSize(),
                )
                if (post.visibility == "私密" || post.visibility.startsWith("私密")) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "私密动态",
                        tint = Color.White,
                        modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 4.dp, bottom = 4.dp)
                            .size(14.dp),
                    )
                }
            } else if (post.text.isNotBlank()) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                ) {
                    Text(
                        text = post.text,
                        style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (post.visibility == "私密" || post.visibility.startsWith("私密")) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "私密动态",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(12.dp),
                        )
                    }
                }
            }
        }
    }
}
