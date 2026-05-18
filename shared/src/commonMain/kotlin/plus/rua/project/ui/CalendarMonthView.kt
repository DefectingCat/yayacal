package plus.rua.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import plus.rua.project.CalendarViewModel
import kotlin.math.abs
import kotlin.time.Clock

/**
 * 日历主界面，包含月/周视图切换、折叠动画和年视图缩放转场。
 *
 * 折叠时日历从月视图收缩为周视图（1行），BottomCard 同步上移填充空间。
 * 通过左下角 FAB 菜单切换月/年视图，以当前月为锚点缩放转场。
 *
 * @param modifier 外部布局修饰符
 */
@Composable
fun CalendarMonthView(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { CalendarViewModel(coroutineScope) }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val currentYear by remember { derivedStateOf { viewModel.selectedDate.year } }

    @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
    val currentMonth by remember { derivedStateOf { viewModel.selectedDate.month.number } }
    val density = LocalDensity.current

    var monthHeaderHeightPx by remember { mutableIntStateOf(0) }
    var weekdayHeaderHeightPx by remember { mutableIntStateOf(0) }
    var rowHeightPx by remember { mutableIntStateOf(0) }
    var screenWidthPx by remember { mutableIntStateOf(0) }
    var screenHeightPx by remember { mutableIntStateOf(0) }
    var calendarContentHeightPx by remember { mutableIntStateOf(0) }
    var isMenuExpanded by remember { mutableStateOf(false) }

    // 视图切换时自动关闭菜单
    LaunchedEffect(viewModel.isYearView) {
        isMenuExpanded = false
    }

    val pagerState = rememberPagerState(initialPage = START_PAGE, pageCount = { Int.MAX_VALUE })

    // 年视图分页器
    val yearPagerState = rememberPagerState(
        initialPage = START_PAGE,
        pageCount = { Int.MAX_VALUE }
    )

    // 进入年视图时同步 yearPagerState 到当前年
    LaunchedEffect(viewModel.isYearView) {
        if (viewModel.isYearView) {
            if (yearPagerState.currentPage != START_PAGE) {
                yearPagerState.scrollToPage(START_PAGE)
            }
        }
    }

    // 年视图翻页时同步 yearViewYear
    LaunchedEffect(yearPagerState) {
        snapshotFlow { yearPagerState.settledPage }.collect { page ->
            val offset = page - START_PAGE
            val targetYear = viewModel.selectedDate.year + offset
            if (targetYear != viewModel.yearViewYear) {
                viewModel.yearViewYear = targetYear
            }
        }
    }

    // 折叠态 WeekPager 切月时，持续同步 CalendarPager 的 pagerState
    LaunchedEffect(viewModel.selectedDate) {
        @Suppress("DEPRECATION") // monthNumber 无替代 API
        val targetPage = yearMonthToPage(
            viewModel.selectedDate.year, viewModel.selectedDate.month.number,
            today.year, today.month.number
        )
        if (targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    val collapseProgress = viewModel.collapseProgress
    val headerHeightPx = monthHeaderHeightPx + weekdayHeaderHeightPx

    // 预计算固定 dp→px，避免每帧重复 density 转换
    val cardGapExpandedPx = remember { with(density) { CARD_GAP_EXPANDED_DP.dp.toPx() } }
    val cardGapCollapsedPx = remember { with(density) { CARD_GAP_COLLAPSED_DP.dp.toPx() } }
    val rowPaddingPx = remember { with(density) { ROW_PADDING_DP.dp.toPx() } }.toInt()
    val cardGapPx = lerp(cardGapExpandedPx, cardGapCollapsedPx, collapseProgress).toInt()

    val interpolatedWeeks by remember {
        derivedStateOf {
            val fraction = pagerState.currentPageOffsetFraction
            if (abs(fraction) > OFFSET_FRACTION_THRESHOLD) {
                val cp = pagerState.currentPage
                val baseWeeks = calculateWeeksCountForPage(cp, today)
                val targetPage = cp + if (fraction > 0) 1 else -1
                val targetWeeks = calculateWeeksCountForPage(targetPage, today)
                lerp(baseWeeks.toFloat(), targetWeeks.toFloat(), abs(fraction))
            } else {
                calculateWeeksCountForPage(pagerState.currentPage, today).toFloat()
            }
        }
    }

    // 预计算固定 dp→px，避免每帧重复 density 转换
    val horizontalPaddingPx = remember { with(density) { (HORIZONTAL_PADDING_DP * 2).dp.toPx() } }
    val rowPadding2Px = remember { with(density) { (ROW_PADDING_DP * 2).dp.toPx() } }

    val estimatedRowHeightPx = if (screenWidthPx > 0) {
        val cellWidth = (screenWidthPx - horizontalPaddingPx) / 7
        (cellWidth + rowPadding2Px).toInt()
    } else 0

    val effectiveRowHeightPx = if (rowHeightPx > 0) rowHeightPx else estimatedRowHeightPx
    val effectiveWeeks = interpolatedWeeks

    val gridHeightPx = if (effectiveRowHeightPx > 0) {
        val rowH = effectiveRowHeightPx.toFloat()
        if (collapseProgress > OFFSET_FRACTION_THRESHOLD) {
            (rowH * (1 + (effectiveWeeks - 1) * (1f - collapseProgress))).toInt()
        } else {
            (rowH * effectiveWeeks).toInt()
        }
    } else 0

    val calendarAreaHeightPx = headerHeightPx + gridHeightPx + rowPaddingPx + cardGapPx

    val cardHeightPx =
        if (screenHeightPx > 0 && calendarAreaHeightPx > 0) screenHeightPx - calendarAreaHeightPx else 0

    val pagerModifier = if (rowHeightPx > 0 && gridHeightPx > 0) {
        Modifier
            .height(with(density) { gridHeightPx.toDp() })
            .clipToBounds()
    } else {
        Modifier
    }

    // 年视图锚点缩放：当前月在 4×3 网格中的归一化位置
    val anchorPivotX = ((currentMonth - 1) % 3 + 0.5f) / 3f
    val anchorPivotY = ((currentMonth - 1) / 3 + 0.5f) / 4f

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .onSizeChanged { size ->
                screenWidthPx = size.width
                screenHeightPx = size.height
            }
    ) {
        // 月视图层：仅在非年视图时渲染，年视图激活时立即移除。
        if (!viewModel.isYearView) {
            val dragRangeMinPx = with(density) { DRAG_RANGE_MIN_DP.dp.toPx() }
            val dragRangePx = if (effectiveRowHeightPx > 0) {
                maxOf((effectiveWeeks - 1) * effectiveRowHeightPx.toFloat(), dragRangeMinPx)
            } else {
                dragRangeMinPx
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = if (viewModel.isYearView) viewModel.yearViewProgress else 1f - viewModel.yearViewProgress
                        scaleX = lerp(0.3f, 1f, progress)
                        scaleY = lerp(0.3f, 1f, progress)
                        alpha = progress.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(anchorPivotX, anchorPivotY)
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = HORIZONTAL_PADDING_DP.dp)
                ) {
                    MonthHeader(
                        year = currentYear,
                        month = currentMonth,
                        weekNumber = viewModel.getIsoWeekNumber(viewModel.selectedDate),
                        showToday = viewModel.selectedDate != today,
                        onToday = {
                            viewModel.selectDate(today)
                            @Suppress("DEPRECATION") // monthNumber 无替代 API
                            val targetPage = yearMonthToPage(
                                today.year, today.month.number,
                                today.year, today.month.number
                            )
                            if (targetPage != pagerState.currentPage) {
                                coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                            }
                        },
                        modifier = Modifier.onSizeChanged { size ->
                            monthHeaderHeightPx = size.height
                        }
                    )
                    WeekdayHeader(
                        modifier = Modifier.fillMaxWidth().padding(bottom = ROW_PADDING_DP.dp)
                            .onSizeChanged { size ->
                                weekdayHeaderHeightPx = size.height
                            }
                    )
                    if (viewModel.isCollapsed && viewModel.collapseProgress >= 1f) {
                        WeekPager(
                            selectedDate = viewModel.selectedDate,
                            today = today,
                            onDateClick = { date -> viewModel.selectDate(date) },
                            onWeekChanged = { weekMonday ->
                                val weekSunday = weekMonday.plus(DatePeriod(days = 6))
                                val date = when {
                                    today in weekMonday..weekSunday -> today
                                    weekMonday.month != weekSunday.month -> {
                                        if (weekMonday < viewModel.selectedDate) {
                                            @Suppress("DEPRECATION") // monthNumber 无替代 API
                                            LocalDate(weekSunday.year, weekSunday.month.number, 1)
                                        } else {
                                            weekMonday
                                        }
                                    }

                                    else -> weekMonday
                                }
                                viewModel.selectDate(date)
                            },
                            shiftKindAt = { date -> viewModel.shiftKindAt(date) },
                            showLegalHoliday = viewModel.showLegalHoliday,
                            modifier = pagerModifier
                        )
                    } else {
                        CalendarPager(
                            selectedDate = viewModel.selectedDate,
                            today = today,
                            onDateClick = { date -> viewModel.selectDate(date) },
                            onMonthChanged = { year, month ->
                                @Suppress("DEPRECATION") // monthNumber 无替代 API
                                val date =
                                    if (year == today.year && today.month.number == month) today
                                    else LocalDate(year, month, 1)
                                viewModel.selectDate(date)
                            },
                            collapseProgress = viewModel.collapseProgress,
                            rowHeightPx = rowHeightPx,
                            effectiveWeeks = effectiveWeeks,
                            shiftKindAt = { date -> viewModel.shiftKindAt(date) },
                            showLegalHoliday = viewModel.showLegalHoliday,
                            onRowHeightMeasured = { h ->
                                if (h > 0) rowHeightPx = h
                            },
                            pagerState = pagerState,
                            modifier = pagerModifier
                        )
                    }
                }

                if (cardHeightPx > 0) {
                    BottomCard(
                        viewModel = viewModel,
                        dragRangePx = dragRangePx,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { cardHeightPx.toDp() })
                            .align(Alignment.BottomCenter)
                    )
                }
            }
        }

        // 年视图层：标题固定，HorizontalPager 只包裹网格。
        if (viewModel.isYearView) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = if (viewModel.isYearView) viewModel.yearViewProgress else 1f - viewModel.yearViewProgress
                        val scale = lerp(3.3f, 1f, progress)
                        scaleX = scale
                        scaleY = scale
                        alpha = progress.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(anchorPivotX, anchorPivotY)
                    }
                    .padding(horizontal = HORIZONTAL_PADDING_DP.dp)
            ) {
                YearHeader(
                    year = viewModel.yearViewYear,
                    onYearChange = { newYear ->
                        val offset = newYear - viewModel.yearViewYear
                        val targetPage = yearPagerState.currentPage + offset
                        if (targetPage != yearPagerState.currentPage) {
                            coroutineScope.launch { yearPagerState.animateScrollToPage(targetPage) }
                        }
                    }
                )
                HorizontalPager(
                    state = yearPagerState,
                    beyondViewportPageCount = 1,
                    flingBehavior = PagerDefaults.flingBehavior(state = yearPagerState),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val pageOffset = abs(yearPagerState.currentPageOffsetFraction)
                    val isCurrentPage = page == yearPagerState.currentPage
                    val crossFadeAlpha = if (isCurrentPage) {
                        1f - pageOffset
                    } else {
                        pageOffset
                    }
                    val pageYear = viewModel.selectedDate.year + (page - START_PAGE)
                    YearGridView(
                        year = pageYear,
                        selectedMonth = if (pageYear == currentYear) currentMonth else 0,
                        today = today,
                        onMonthClick = { month ->
                            viewModel.selectMonthFromYearView(month)
                            @Suppress("DEPRECATION") // monthNumber 无替代 API
                            val targetPage = yearMonthToPage(
                                viewModel.yearViewYear, month,
                                today.year, today.month.number
                            )
                            if (targetPage != pagerState.currentPage) {
                                coroutineScope.launch { pagerState.scrollToPage(targetPage) }
                            }
                        },
                        modifier = Modifier.alpha(crossFadeAlpha)
                    )
                }
            }
        }

        // FAB 浮动按钮
        FloatingActionButton(
            onClick = { isMenuExpanded = !isMenuExpanded },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 32.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            MenuIcon()
        }

        // Scrim：菜单展开时覆盖全屏，点击关闭
        AnimatedVisibility(
            visible = isMenuExpanded,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { isMenuExpanded = false }
                    }
                    .background(Color.Black.copy(alpha = 0.32f))
            )
        }

        // 缩放动画菜单
        AnimatedVisibility(
            visible = isMenuExpanded,
            enter = scaleIn(
                initialScale = 0.2f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                transformOrigin = TransformOrigin(0f, 1f)
            ) + fadeIn(tween(150)),
            exit = scaleOut(
                targetScale = 0.2f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                transformOrigin = TransformOrigin(0f, 1f)
            ) + fadeOut(tween(100)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 32.dp + 56.dp + 8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.width(140.dp)) {
                    MenuItem(
                        text = "月视图",
                        selected = !viewModel.isYearView,
                        onClick = {
                            isMenuExpanded = false
                            if (viewModel.isYearView) viewModel.toggleYearView()
                        }
                    )
                    MenuItem(
                        text = "年视图",
                        selected = viewModel.isYearView,
                        onClick = {
                            isMenuExpanded = false
                            if (!viewModel.isYearView) viewModel.toggleYearView()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 2.dp.toPx()
        val lineSpacing = 4.dp.toPx()
        val totalHeight = strokeWidth * 3 + lineSpacing * 2
        val startY = (size.height - totalHeight) / 2
        repeat(3) { i ->
            drawLine(
                color = Color.White,
                start = Offset(0f, startY + i * (strokeWidth + lineSpacing)),
                end = Offset(size.width, startY + i * (strokeWidth + lineSpacing)),
                strokeWidth = strokeWidth
            )
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
