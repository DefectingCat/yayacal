package plus.rua.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class CalendarDay(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean
)

class CalendarViewModel {
    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    var selectedDate by mutableStateOf(today)
        private set

    var isCollapsed by mutableStateOf(false)
        private set

    var collapseProgress by mutableStateOf(0f)
        private set

    val currentYear: Int get() = selectedDate.year
    @Suppress("DEPRECATION")
    val currentMonth: Int get() = selectedDate.monthNumber

    fun selectDate(date: LocalDate) {
        selectedDate = date
    }

    fun collapse() {
        isCollapsed = true
        collapseProgress = 1f
    }

    fun expand() {
        isCollapsed = false
        collapseProgress = 0f
    }

    fun getIsoWeekNumber(date: LocalDate): Int {
        val jan4 = LocalDate(date.year, 1, 4)
        val jan4DayOfWeek = jan4.dayOfWeek.ordinal
        val week1Monday = jan4.minus(DatePeriod(days = jan4DayOfWeek))
        val diff = week1Monday.daysUntil(date)
        val weekNumber = diff / 7 + 1
        return if (weekNumber < 1) {
            getIsoWeekNumber(LocalDate(date.year - 1, 12, 28))
        } else if (weekNumber > getIsoWeeksInYear(date.year)) {
            1
        } else {
            weekNumber
        }
    }

    private fun getIsoWeeksInYear(year: Int): Int {
        val dec28 = LocalDate(year, 12, 28)
        val jan4 = LocalDate(year, 1, 4)
        val jan4DayOfWeek = jan4.dayOfWeek.ordinal
        val week1Monday = jan4.minus(DatePeriod(days = jan4DayOfWeek))
        val diff = week1Monday.daysUntil(dec28)
        return diff / 7 + 1
    }

    @Suppress("DEPRECATION")
    fun getMonthDays(year: Int, month: Int): List<CalendarDay> {
        val firstOfMonth = LocalDate(year, month, 1)
        val dayOfWeekOffset = firstOfMonth.dayOfWeek.ordinal
        val startDate = firstOfMonth.minus(DatePeriod(days = dayOfWeekOffset))

        return (0 until 42).map { i ->
            val date = startDate.plus(DatePeriod(days = i))
            CalendarDay(
                date = date,
                isCurrentMonth = date.monthNumber == month && date.year == year,
                isToday = date == today,
                isSelected = date == selectedDate
            )
        }
    }
}
