package plus.rua.project.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
 * 点击月份标题切换年视图，以当前月为锚点缩放转场。
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
    val yearProgress = viewModel.yearViewProgress
    val headerHeightPx = monthHeaderHeightPx + weekdayHeaderHeightPx
    val rowPaddingPx = with(density) { ROW_PADDING_DP.dp.toPx() }.toInt()
    val cardGapPx = with(density) {
        lerp(
            CARD_GAP_EXPANDED_DP.toFloat(),
            CARD_GAP_COLLAPSED_DP.toFloat(),
            collapseProgress
        ).dp.toPx()
    }.toInt()

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

    val estimatedRowHeightPx = if (screenWidthPx > 0) {
        val cellWidth =
            (screenWidthPx - with(density) { (HORIZONTAL_PADDING_DP * 2).dp.toPx() }) / 7
        val rowPadding = with(density) { (ROW_PADDING_DP * 2).dp.toPx() }
        (cellWidth + rowPadding).toInt()
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

    // 过渡进度：0=目标视图刚出现，1=目标视图完全到位。
    // 月→年时 yearProgress 从 0→1，年→月时从 1→0，因此用 isYearView 同步翻转方向。
    val transitionProgress = if (viewModel.isYearView) yearProgress else 1f - yearProgress
    val targetAlpha = transitionProgress.coerceIn(0f, 1f)

    // 月视图层缩放：从 0.3f（年网格单格大小）放大到 1f
    val monthScale = lerp(0.3f, 1f, transitionProgress)
    // 年视图层缩放：从 3.3f（月视图被放大到一格那么大的反向比例）缩小到 1f
    val yearScale = lerp(3.3f, 1f, transitionProgress)

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
                        scaleX = monthScale
                        scaleY = monthScale
                        alpha = targetAlpha
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

        // 年视图层：仅在年视图激活时渲染；HorizontalPager 支持左右滑动切年。
        if (viewModel.isYearView) {
            HorizontalPager(
                state = yearPagerState,
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(state = yearPagerState),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = yearScale
                        scaleY = yearScale
                        alpha = targetAlpha
                        transformOrigin = TransformOrigin(anchorPivotX, anchorPivotY)
                    }
                    .padding(horizontal = HORIZONTAL_PADDING_DP.dp)
            ) { page ->
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
                    onYearChange = { newYear ->
                        val offset = newYear - pageYear
                        val targetPage = yearPagerState.currentPage + offset
                        if (targetPage != yearPagerState.currentPage) {
                            coroutineScope.launch { yearPagerState.animateScrollToPage(targetPage) }
                        }
                    }
                )
            }
        }
    }
}
