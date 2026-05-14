package plus.rua.project.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import plus.rua.project.CalendarViewModel

@Composable
fun CalendarMonthView(
    viewModel: CalendarViewModel = remember { CalendarViewModel() },
    modifier: Modifier = Modifier
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var currentYear by remember { mutableIntStateOf(viewModel.currentYear) }
    var currentMonth by remember { mutableIntStateOf(viewModel.currentMonth) }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        MonthHeader(
            year = currentYear,
            month = currentMonth,
            weekNumber = viewModel.getIsoWeekNumber(viewModel.selectedDate)
        )
        CalendarPager(
            selectedDate = viewModel.selectedDate,
            today = today,
            onDateClick = { date -> viewModel.selectDate(date) },
            onMonthChanged = { year, month ->
                currentYear = year
                currentMonth = month
            },
            modifier = Modifier.weight(1f)
        )
    }
}
