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
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.time.Clock
import plus.rua.project.CalendarViewModel

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
    val currentMonth by remember { derivedStateOf { viewModel.selectedDate.month.number } }
    val density = LocalDensity.current

    var monthHeaderHeightPx by remember { mutableIntStateOf(0) }
    var weekdayHeaderHeightPx by remember { mutableIntStateOf(0) }
    var rowHeightPx by remember { mutableIntStateOf(0) }
    var screenWidthPx by remember { mutableIntStateOf(0) }
    var screenHeightPx by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(initialPage = START_PAGE, pageCount = { Int.MAX_VALUE })

    val p = viewModel.collapseProgress
    val headerHeightPx = monthHeaderHeightPx + weekdayHeaderHeightPx
    val rowPaddingPx = with(density) { ROW_PADDING_DP.dp.toPx() }.toInt()
    val cardGapPx = with(density) { CARD_GAP_DP.dp.toPx() }.toInt()

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
    // 加上 Row 的 vertical padding (4dp × 2)
    val estimatedRowHeightPx = if (screenWidthPx > 0) {
        val cellWidth = (screenWidthPx - with(density) { (HORIZONTAL_PADDING_DP * 2).dp.toPx() }) / 7
        val rowPadding = with(density) { (ROW_PADDING_DP * 2).dp.toPx() }
        (cellWidth + rowPadding).toInt()
    } else 0

    val effectiveRowHeightPx = if (rowHeightPx > 0) rowHeightPx else estimatedRowHeightPx

    // 折叠时网格高度公式（与 CalendarMonthPage 一致）：
    // gridH = rowH × (1 + (weeks-1) × (1-p))
    val effectiveWeeks = interpolatedWeeks

    // gridHeightPx 必须直接计算而非 derivedStateOf，因为 effectiveRowHeightPx 依赖 rowHeightPx state，
    // derivedStateOf 无法追踪非 State 的局部变量变化，导致 rowHeightPx 从 0 变为测量值时 gridHeightPx 不更新
    val gridHeightPx = if (effectiveRowHeightPx > 0) {
        val rowH = effectiveRowHeightPx.toFloat()
        if (p > OFFSET_FRACTION_THRESHOLD) {
            (rowH * (1 + (effectiveWeeks - 1) * (1f - p))).toInt()
        } else {
            (rowH * effectiveWeeks).toInt()
        }
    } else 0

    val calendarAreaHeightPx = headerHeightPx + gridHeightPx + rowPaddingPx + cardGapPx
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
                modifier = Modifier.fillMaxWidth().padding(bottom = ROW_PADDING_DP.dp).onSizeChanged { size ->
                    weekdayHeaderHeightPx = size.height
                }
            )
            // 完全折叠且无动画时显示 WeekPager，否则显示 CalendarPager（含下拉恢复过程）
            if (viewModel.isCollapsed && viewModel.collapseProgress >= 1f) {
                WeekPager(
                    selectedDate = viewModel.selectedDate,
                    today = today,
                    onDateClick = { date -> viewModel.selectDate(date) },
                    onWeekChanged = { weekMonday ->
                        val weekSunday = weekMonday.plus(DatePeriod(days = 6))
                        val date = if (today in weekMonday..weekSunday) today else weekMonday
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