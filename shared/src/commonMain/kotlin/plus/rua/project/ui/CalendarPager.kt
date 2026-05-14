package plus.rua.project.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private const val START_PAGE = Int.MAX_VALUE / 2

@Composable
fun CalendarPager(
    selectedDate: LocalDate,
    today: LocalDate,
    onDateClick: (LocalDate) -> Unit,
    onMonthChanged: (year: Int, month: Int) -> Unit,
    collapseProgress: Float,
    modifier: Modifier = Modifier
) {
    val initialYearMonth = remember { today.toYearMonth() }
    val pagerState = rememberPagerState(
        initialPage = START_PAGE,
        pageCount = { Int.MAX_VALUE }
    )
    val coroutineScope = rememberCoroutineScope()

    // Sync settled page to onMonthChanged
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val yearMonth = pageToYearMonth(page, initialYearMonth)
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
            collapseProgress = collapseProgress
        )
    }
}

@Suppress("DEPRECATION")
private fun LocalDate.toYearMonth(): Pair<Int, Int> = Pair(year, monthNumber)

private fun pageToYearMonth(page: Int, initial: Pair<Int, Int>): Pair<Int, Int> {
    val offset = page - START_PAGE
    val totalMonths = initial.first * 12 + (initial.second - 1) + offset
    return Pair(totalMonths / 12, totalMonths % 12 + 1)
}

private fun yearMonthToPage(yearMonth: Pair<Int, Int>, initial: Pair<Int, Int>): Int {
    val targetTotal = yearMonth.first * 12 + (yearMonth.second - 1)
    val initialTotal = initial.first * 12 + (initial.second - 1)
    return START_PAGE + (targetTotal - initialTotal)
}
