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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val START_PAGE = Int.MAX_VALUE / 2

@Composable
fun WeekPager(
    selectedDate: LocalDate,
    today: LocalDate,
    onDateClick: (LocalDate) -> Unit,
    onWeekChanged: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialWeekMonday = remember { selectedDate.toWeekMonday() }
    val pagerState = rememberPagerState(
        initialPage = START_PAGE,
        pageCount = { Int.MAX_VALUE }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val weekMonday = pageToWeekMonday(page, initialWeekMonday)
            onWeekChanged(weekMonday)
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        modifier = modifier
    ) { page ->
        val weekMonday = pageToWeekMonday(page, initialWeekMonday)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            (0 until 7).forEach { dayOffset ->
                val date = weekMonday.plus(DatePeriod(days = dayOffset))
                DayCell(
                    date = date,
                    isCurrentMonth = true,
                    isSelected = date == selectedDate,
                    isToday = date == today,
                    onClick = { onDateClick(date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun LocalDate.toWeekMonday(): LocalDate {
    val dayOfWeekOrdinal = dayOfWeek.ordinal // Monday=0 ... Sunday=6
    return minus(DatePeriod(days = dayOfWeekOrdinal))
}

private fun pageToWeekMonday(page: Int, initial: LocalDate): LocalDate {
    val offset = page - START_PAGE
    return initial.plus(DatePeriod(days = offset * 7))
}