package plus.rua.project.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
 * 日历主界面，包含月/周视图切换和折叠动画。
 *
 * 折叠时日历从月视图收缩为周视图（1行），BottomCard 同步上移填充空间。
 * 支持动态行数（4/5/6行），滑动切换月份时 BottomCard 跟手移动。
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

    val pagerState = rememberPagerState(initialPage = START_PAGE, pageCount = { Int.MAX_VALUE })

    // 展开时同步 CalendarPager 页面到 selectedDate 所在月份
    LaunchedEffect(viewModel.isCollapsed) {
        if (!viewModel.isCollapsed) {
            @Suppress("DEPRECATION") // monthNumber 无替代 API
            val targetPage = yearMonthToPage(
                viewModel.selectedDate.year,
                viewModel.selectedDate.month.number,
                today.year,
                today.month.number
            )
            if (targetPage != pagerState.currentPage) {
                pagerState.scrollToPage(targetPage)
            }
        }
    }

    val collapseProgress = viewModel.collapseProgress
    val headerHeightPx = monthHeaderHeightPx + weekdayHeaderHeightPx
    val rowPaddingPx = with(density) { ROW_PADDING_DP.dp.toPx() }.toInt()
    val cardGapPx = with(density) {
        lerp(
            CARD_GAP_EXPANDED_DP.toFloat(),
            CARD_GAP_COLLAPSED_DP.toFloat(),
            collapseProgress
        ).dp.toPx()
    }.toInt()

    // 翻页时在相邻月份行数之间插值，使 BottomCard 高度平滑过渡
    // abs(fraction) > 阈值时启用插值，避免静止时的浮点抖动
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

    // 预估行高：DayCell aspectRatio=1，宽度 = (screenWidth - horizontalPadding) / 7
    // 加上 Row 的 vertical padding (6dp × 2)
    // 用于 rowHeightPx 尚未测量时的 fallback，避免首次布局高度为 0
    val estimatedRowHeightPx = if (screenWidthPx > 0) {
        val cellWidth =
            (screenWidthPx - with(density) { (HORIZONTAL_PADDING_DP * 2).dp.toPx() }) / 7
        val rowPadding = with(density) { (ROW_PADDING_DP * 2).dp.toPx() }
        (cellWidth + rowPadding).toInt()
    } else 0

    val effectiveRowHeightPx = if (rowHeightPx > 0) rowHeightPx else estimatedRowHeightPx

    val effectiveWeeks = interpolatedWeeks

    // 折叠时网格高度公式（与 CalendarMonthPage 一致）：
    // collapseProgress=0 展开时 gridH = rowH × weeks；collapseProgress=1 折叠时 gridH = rowH × 1
    // 中间态：gridH = rowH × (1 + (weeks-1) × (1-collapseProgress))
    // 直接计算而非 derivedStateOf：effectiveRowHeightPx 依赖 rowHeightPx state，
    // derivedStateOf 无法追踪非 State 局部变量，rowHeightPx 从 0 变为测量值时 gridHeightPx 不会更新
    val gridHeightPx = if (effectiveRowHeightPx > 0) {
        val rowH = effectiveRowHeightPx.toFloat()
        if (collapseProgress > OFFSET_FRACTION_THRESHOLD) {
            (rowH * (1 + (effectiveWeeks - 1) * (1f - collapseProgress))).toInt()
        } else {
            (rowH * effectiveWeeks).toInt()
        }
    } else 0

    // BottomCard 高度 = 屏幕剩余空间（屏幕高度 - 日历区域高度）
    val calendarAreaHeightPx = headerHeightPx + gridHeightPx + rowPaddingPx + cardGapPx
    val cardHeightPx =
        if (screenHeightPx > 0 && calendarAreaHeightPx > 0) screenHeightPx - calendarAreaHeightPx else 0

    // 行高已知时约束 pager 高度防止内容溢出；否则让 pager 自由扩展以触发首次行高测量
    val pagerModifier = if (rowHeightPx > 0 && gridHeightPx > 0) {
        Modifier
            .height(with(density) { gridHeightPx.toDp() })
            .clipToBounds()
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .onSizeChanged { size ->
                screenWidthPx = size.width
                screenHeightPx = size.height
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = HORIZONTAL_PADDING_DP.dp)) {
            MonthHeader(
                year = currentYear,
                month = currentMonth,
                weekNumber = viewModel.getIsoWeekNumber(viewModel.selectedDate),
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
            // 完全折叠且无动画时切换到 WeekPager（单行高效渲染），
            // 否则使用 CalendarPager（含折叠动画和下拉恢复过程）
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
                                // 跨月周：选中下个月的1号
                                @Suppress("DEPRECATION") // monthNumber 无替代 API
                                LocalDate(weekSunday.year, weekSunday.month.number, 1)
                            }
                            else -> weekMonday
                        }
                        viewModel.selectDate(date)
                    },
                    modifier = pagerModifier
                )
            } else {
                CalendarPager(
                    selectedDate = viewModel.selectedDate,
                    today = today,
                    onDateClick = { date -> viewModel.selectDate(date) },
                    onMonthChanged = { year, month ->
                        // 优先选中当月内的今天，否则选中该月1号
                        @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
                        val date = if (year == today.year && today.month.number == month) today
                        else LocalDate(year, month, 1)
                        viewModel.selectDate(date)
                    },
                    collapseProgress = viewModel.collapseProgress,
                    rowHeightPx = rowHeightPx,
                    effectiveWeeks = effectiveWeeks,
                    onRowHeightMeasured = { h ->
                        if (h > 0) rowHeightPx = h
                    },
                    pagerState = pagerState,
                    modifier = pagerModifier
                )
            }
        }

        // 拖拽范围 = 折叠时日历实际高度变化量 (weeks-1)×rowHeight，使手指移动与视觉变化 1:1 对应
        val dragRangeMinPx = with(density) { DRAG_RANGE_MIN_DP.dp.toPx() }
        val dragRangePx = if (effectiveRowHeightPx > 0) {
            maxOf((effectiveWeeks - 1) * effectiveRowHeightPx.toFloat(), dragRangeMinPx)
        } else {
            dragRangeMinPx
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
