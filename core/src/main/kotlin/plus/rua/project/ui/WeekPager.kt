package plus.rua.project.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import plus.rua.project.ShiftKind
import kotlin.math.abs

/**
 * 周视图分页器，折叠状态下显示选中日期所在周，支持左右滑动切换周。
 *
 * @param selectedDate 当前选中日期
 * @param today 今天的日期
 * @param onDateClick 日期点击回调
 * @param onWeekChanged 周切换回调，滑动到新周时触发，参数为该周周一日期
 * @param shiftKindAt 日期 → 个人轮班类型的查询闭包
 * @param showLegalHoliday 是否显示法定调休角标。详见 [DayCell] 的同名参数。
 * @param modifier 外部布局修饰符
 */
@Composable
fun WeekPager(
    selectedDate: LocalDate,
    today: LocalDate,
    onDateClick: (LocalDate) -> Unit,
    onWeekChanged: (LocalDate) -> Unit,
    shiftKindAt: (LocalDate) -> ShiftKind?,
    showLegalHoliday: Boolean,
    modifier: Modifier = Modifier
) {
    val initialWeekMonday = remember { selectedDate.toWeekMonday() }
    val pagerState = rememberPagerState(
        initialPage = START_PAGE,
        pageCount = { Int.MAX_VALUE }
    )

    // selectedDate 外部变更（如点击回到今天）时，滚动到对应周
    LaunchedEffect(selectedDate) {
        val targetMonday = selectedDate.toWeekMonday()
        val targetPage = START_PAGE + (initialWeekMonday.daysUntil(targetMonday) / 7)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            val weekMonday = pageToWeekMonday(page, initialWeekMonday)
            onWeekChanged(weekMonday)
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 0,
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        modifier = modifier
    ) { page ->
        val pageOffset = abs(pagerState.currentPageOffsetFraction)
        val isCurrentPage = page == pagerState.currentPage
        val alpha = if (isCurrentPage) {
            1f - pageOffset
        } else {
            pageOffset
        }
        val weekMonday = pageToWeekMonday(page, initialWeekMonday)
        Row(
            modifier = Modifier
                .alpha(alpha)
                .fillMaxWidth()
                .padding(vertical = ROW_PADDING_DP.dp)
        ) {
            (0 until 7).forEach { dayOffset ->
                val date = weekMonday.plus(DatePeriod(days = dayOffset))
                DayCell(
                    date = date,
                    isCurrentMonth = date.month == selectedDate.month
                            && date.year == selectedDate.year,
                    isSelected = date == selectedDate,
                    isToday = date == today,
                    shiftKind = shiftKindAt(date),
                    showLegalHoliday = showLegalHoliday,
                    onClick = { onDateClick(date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}