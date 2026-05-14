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
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.time.Clock
import plus.rua.project.CalendarViewModel

private const val START_PAGE = Int.MAX_VALUE / 2
private const val ROW_PADDING_DP = 4

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
    var currentYear by remember { mutableIntStateOf(viewModel.currentYear) }
    var currentMonth by remember { mutableIntStateOf(viewModel.currentMonth) }
    val density = LocalDensity.current

    var monthHeaderHeightPx by remember { mutableIntStateOf(0) }
    var weekdayHeaderHeightPx by remember { mutableIntStateOf(0) }
    var rowHeightPx by remember { mutableIntStateOf(0) }
    @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
    var currentWeeksCount by remember { mutableIntStateOf(calculateWeeksCount(today.year, today.monthNumber)) }
    var screenWidthPx by remember { mutableIntStateOf(0) }
    var screenHeightPx by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(initialPage = START_PAGE, pageCount = { Int.MAX_VALUE })

    val p = viewModel.collapseProgress
    val headerHeightPx = monthHeaderHeightPx + weekdayHeaderHeightPx
    val rowPaddingPx = with(density) { ROW_PADDING_DP.dp.toPx() }.toInt()

    // 滑动偏移插值行数
    val offsetFraction by remember { derivedStateOf { pagerState.currentPageOffsetFraction } }
    val interpolatedWeeks = if (abs(offsetFraction) > 0.01f) {
        val targetPage = if (offsetFraction > 0) pagerState.currentPage + 1 else pagerState.currentPage - 1
        val targetWeeks = calculateWeeksCountForPage(targetPage, today)
        lerp(currentWeeksCount.toFloat(), targetWeeks.toFloat(), abs(offsetFraction))
    } else {
        currentWeeksCount.toFloat()
    }

    // 预估行高：DayCell aspectRatio=1，宽度 = (screenWidth - horizontalPadding) / 7
    // 加上 Row 的 vertical padding (4dp × 2)
    val estimatedRowHeightPx = if (screenWidthPx > 0) {
        val cellWidth = (screenWidthPx - with(density) { 32.dp.toPx() }) / 7
        val rowPadding = with(density) { 8.dp.toPx() }
        (cellWidth + rowPadding).toInt()
    } else 0

    val effectiveRowHeightPx = if (rowHeightPx > 0) rowHeightPx else estimatedRowHeightPx

    // 折叠时网格高度公式（与 CalendarMonthPage 一致）：
    // gridH = rowH × (1 + (weeks-1) × (1-p))
    val gridHeightPx = if (effectiveRowHeightPx > 0) {
        val rowH = effectiveRowHeightPx.toFloat()
        val weeks = interpolatedWeeks
        if (p > 0.01f) {
            (rowH * (1 + (weeks - 1) * (1f - p))).toInt()
        } else {
            (rowH * weeks).toInt()
        }
    } else 0

    val calendarAreaHeightPx = headerHeightPx + gridHeightPx + rowPaddingPx
    val cardHeightPx = if (screenHeightPx > 0 && calendarAreaHeightPx > 0) screenHeightPx - calendarAreaHeightPx else 0

    // 当 rowHeightPx 已知时，用计算的高度约束 pager；否则让 pager 自由扩展以测量行高
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
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            MonthHeader(
                year = currentYear,
                month = currentMonth,
                weekNumber = viewModel.getIsoWeekNumber(viewModel.selectedDate),
                modifier = Modifier.onSizeChanged { size ->
                    monthHeaderHeightPx = size.height
                }
            )
            WeekdayHeader(
                modifier = Modifier.fillMaxWidth().onSizeChanged { size ->
                    weekdayHeaderHeightPx = size.height
                }.padding(bottom = ROW_PADDING_DP.dp)
            )
            // 完全折叠且无动画时显示 WeekPager，否则显示 CalendarPager（含下拉恢复过程）
            if (viewModel.isCollapsed && viewModel.collapseProgress >= 1f) {
                WeekPager(
                    selectedDate = viewModel.selectedDate,
                    today = today,
                    onDateClick = { date -> viewModel.selectDate(date) },
                    onWeekChanged = { weekMonday ->
                        val weekSunday = weekMonday.plus(DatePeriod(days = 6))
                        val date = if (today >= weekMonday && today <= weekSunday) today else weekMonday
                        viewModel.selectDate(date)
                        currentYear = date.year
                        @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
                        currentMonth = date.monthNumber
                    }
                )
            } else {
                CalendarPager(
                    selectedDate = viewModel.selectedDate,
                    today = today,
                    onDateClick = { date -> viewModel.selectDate(date) },
                    onMonthChanged = { year, month ->
                        val date = if (year == today.year && today.monthNumber == month) today
                                   else LocalDate(year, month, 1)
                        viewModel.selectDate(date)
                        currentYear = year
                        currentMonth = month
                    },
                    collapseProgress = viewModel.collapseProgress,
                    rowHeightPx = rowHeightPx,
                    onWeeksChanged = { weeks ->
                        currentWeeksCount = weeks
                    },
                    onRowHeightMeasured = { h ->
                        if (h > 0 && rowHeightPx == 0) rowHeightPx = h
                    },
                    pagerState = pagerState,
                    modifier = pagerModifier
                )
            }
        }

        if (cardHeightPx > 0) {
            BottomCard(
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { cardHeightPx.toDp() })
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

@Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
private fun calculateWeeksCountForPage(page: Int, today: LocalDate): Int {
    val initialYear = today.year
    val initialMonth = today.monthNumber
    val offset = page - START_PAGE
    val totalMonths = initialYear * 12 + (initialMonth - 1) + offset
    val year = totalMonths / 12
    val month = totalMonths % 12 + 1
    return calculateWeeksCount(year, month)
}
