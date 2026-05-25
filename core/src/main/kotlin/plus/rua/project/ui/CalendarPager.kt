package plus.rua.project.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import plus.rua.project.ShiftKind
import kotlin.math.abs


/**
 * 月度日历分页器，HorizontalPager 实现无限左右滑动切换月份。
 *
 * 使用 Int.MAX_VALUE 页数，中心页为起始月份。点击跨月日期时自动滚动到对应页。
 * 跳过初始 snapshotFlow 发射以保留"今天"选中状态。
 *
 * @param selectedDate 当前选中日期
 * @param today 今天的日期
 * @param onDateClick 日期点击回调
 * @param onMonthChanged 月份切换回调，滑动到新月份稳定后触发
 * @param collapseProgress 折叠进度，0f=展开，1f=折叠
 * @param rowHeightPx 锁定行高（像素）
 * @param effectiveWeeks 当前有效行数（含翻页插值）
 * @param shiftKindAt 日期 → 个人轮班类型的查询闭包
 * @param showLegalHoliday 是否显示法定调休背景色。详见 [DayCell] 的同名参数。
 * @param onRowHeightMeasured 首次行高测量回调
 * @param pagerState 外层共享的 PagerState，用于保持翻页状态
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
    shiftKindAt: (LocalDate) -> ShiftKind?,
    showLegalHoliday: Boolean,
    onRowHeightMeasured: ((Int) -> Unit)? = null,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val initialYear = remember { today.year }

    @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
    val initialMonth = remember { today.month.number }
    val coroutineScope = rememberCoroutineScope()

    // 跳过初始发射，保留首次渲染时的"今天"选中状态
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            val yearMonth = pageToYearMonth(page, initialYear, initialMonth)
            onMonthChanged(yearMonth.first, yearMonth.second)
        }
    }

    val currentPageOffsetFraction by remember {
        derivedStateOf { pagerState.currentPageOffsetFraction }
    }
    val currentPage by remember {
        derivedStateOf { pagerState.currentPage }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        modifier = modifier
    ) { page ->
        val pageOffset = abs(currentPageOffsetFraction)
        val isCurrentPage = page == currentPage
        val alpha = if (isCurrentPage) {
            1f - pageOffset
        } else {
            pageOffset
        }
        val (year, month) = pageToYearMonth(page, initialYear, initialMonth)
        CalendarMonthPage(
            year = year,
            month = month,
            selectedDate = selectedDate,
            today = today,
            onDateClick = { date ->
                onDateClick(date)
                // 点击跨月日期时，滚动到该月对应的页
                val clickedYear = date.year

                @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
                val clickedMonth = date.month.number
                if (clickedYear != year || clickedMonth != month) {
                    val targetPage =
                        yearMonthToPage(clickedYear, clickedMonth, initialYear, initialMonth)
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
            shiftKindAt = shiftKindAt,
            showLegalHoliday = showLegalHoliday,
            onRowHeightMeasured = onRowHeightMeasured,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        )
    }
}