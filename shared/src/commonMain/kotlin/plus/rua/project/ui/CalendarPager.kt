package plus.rua.project.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** 无限分页中心页，用于 HorizontalPager 的起始位置 */
private const val START_PAGE = Int.MAX_VALUE / 2

/**
 * 月度日历分页器，HorizontalPager 实现无限左右滑动切换月份。
 *
 * @param selectedDate 当前选中日期
 * @param today 今天的日期
 * @param onDateClick 日期点击回调
 * @param onMonthChanged 月份切换回调，滑动到新月份时触发
 * @param collapseProgress 折叠进度，0f=展开，1f=折叠
 * @param rowHeightPx 从外层传入的锁定行高（像素），折叠过程中不变
 * @param modifier 外部布局修饰符
 */
@Composable
fun CalendarPager(
    selectedDate: LocalDate,
    today: LocalDate,
    onDateClick: (LocalDate) -> Unit,
    onMonthChanged: (year: Int, month: Int) -> Unit,
    collapseProgress: Float,
    rowHeightPx: Int,
    effectiveWeeks: Float,
    onWeeksChanged: ((Int) -> Unit)? = null,
    onRowHeightMeasured: ((Int) -> Unit)? = null,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val initialYearMonth = remember { today.toYearMonth() }
    val coroutineScope = rememberCoroutineScope()

    // Sync settled page to onMonthChanged (skip initial emission to preserve "today" selection)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            val yearMonth = pageToYearMonth(page, initialYearMonth)
            onWeeksChanged?.invoke(calculateWeeksCount(yearMonth.first, yearMonth.second))
            onMonthChanged(yearMonth.first, yearMonth.second)
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        modifier = modifier
    ) { page ->
        val (year, month) = pageToYearMonth(page, initialYearMonth)
        CalendarMonthPage(
            year = year,
            month = month,
            selectedDate = selectedDate,
            today = today,
            onDateClick = { date ->
                onDateClick(date)
                // If clicking a date in a different month, scroll to that page
                val clickedYearMonth = date.toYearMonth()
                if (clickedYearMonth != pageToYearMonth(page, initialYearMonth)) {
                    val targetPage = yearMonthToPage(clickedYearMonth, initialYearMonth)
                    if (targetPage != pagerState.currentPage) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                }
            },
            collapseProgress = collapseProgress,
            rowHeightPx = rowHeightPx,
            effectiveWeeks = effectiveWeeks,
            onRowHeightMeasured = onRowHeightMeasured
        )
    }
}

@Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
private fun LocalDate.toYearMonth(): Pair<Int, Int> = Pair(year, monthNumber)

// 页码→年月：偏移量 + 初始月份的绝对月数，再拆分回年月
private fun pageToYearMonth(page: Int, initial: Pair<Int, Int>): Pair<Int, Int> {
    val offset = page - START_PAGE
    val totalMonths = initial.first * 12 + (initial.second - 1) + offset
    return Pair(totalMonths / 12, totalMonths % 12 + 1)
}

// 年月→页码：目标与初始的绝对月数差 + 起始页
private fun yearMonthToPage(yearMonth: Pair<Int, Int>, initial: Pair<Int, Int>): Int {
    val targetTotal = yearMonth.first * 12 + (yearMonth.second - 1)
    val initialTotal = initial.first * 12 + (initial.second - 1)
    return START_PAGE + (targetTotal - initialTotal)
}

// 计算月份在日历网格中需要的行数（4/5/6）
internal fun calculateWeeksCount(year: Int, month: Int): Int {
    val firstOfMonth = LocalDate(year, month, 1)
    val offset = firstOfMonth.dayOfWeek.ordinal
    val nextMonth = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    val daysInMonth = nextMonth.minus(DatePeriod(days = 1)).dayOfMonth
    return ((offset + daysInMonth - 1) / 7) + 1
}
