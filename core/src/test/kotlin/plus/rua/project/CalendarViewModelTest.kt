package plus.rua.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val fixedInstant = Instant.parse("2026-05-15T00:00:00Z")
    private val testClock = FixedClock(fixedInstant)
    private fun createViewModel(): CalendarViewModel {
        val dispatcher = StandardTestDispatcher()
        val scope = CoroutineScope(dispatcher)
        return CalendarViewModel(coroutineScope = scope, clock = testClock)
    }

    // ---- getIsoWeekNumber ----

    @Test
    fun getIsoWeekNumber_regular_date() {
        val vm = createViewModel()
        assertEquals(20, vm.getIsoWeekNumber(LocalDate(2026, 5, 15)))
    }

    @Test
    fun getIsoWeekNumber_jan_1() {
        val vm = createViewModel()
        assertEquals(1, vm.getIsoWeekNumber(LocalDate(2026, 1, 1)))
    }

    @Test
    fun getIsoWeekNumber_dec_31() {
        val vm = createViewModel()
        assertEquals(53, vm.getIsoWeekNumber(LocalDate(2026, 12, 31)))
    }

    @Test
    fun getIsoWeekNumber_week_52_boundary() {
        val vm = createViewModel()
        assertEquals(52, vm.getIsoWeekNumber(LocalDate(2025, 12, 28)))
    }

    @Test
    fun getIsoWeekNumber_monday_starts_week() {
        val vm = createViewModel()
        assertEquals(20, vm.getIsoWeekNumber(LocalDate(2026, 5, 11)))
    }

    @Test
    fun getIsoWeekNumber_week_53_year() {
        val vm = createViewModel()
        assertEquals(53, vm.getIsoWeekNumber(LocalDate(2020, 12, 31)))
    }

    // ---- getMonthDays ----

    @Test
    fun getMonthDays_returns_correct_size() {
        val vm = createViewModel()
        // May 2026: 5 rows × 7 = 35 cells
        val days = vm.getMonthDays(2026, 5)
        assertEquals(35, days.size)
    }

    @Test
    fun getMonthDays_may_2026_starts_on_thursday() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 5)
        assertFalse(days[0].isCurrentMonth)
        @Suppress("DEPRECATION") // monthNumber — needed for Int comparison
        assertEquals(4, days[0].date.monthNumber)
        assertEquals(27, days[0].date.day)
    }

    @Test
    fun getMonthDays_may_2026_first_day_is_may_1() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 5)
        assertTrue(days[4].isCurrentMonth)
        assertEquals(1, days[4].date.day)
        @Suppress("DEPRECATION") // monthNumber — needed for Int comparison
        assertEquals(5, days[4].date.monthNumber)
    }

    @Test
    fun getMonthDays_may_2026_last_day_is_may_31() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 5)
        val may31 = days.first { it.isCurrentMonth && it.date.day == 31 }
        assertEquals(31, may31.date.day)
    }

    @Test
    fun getMonthDays_february_2026_28_days() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 2)
        val febDays = days.filter { it.isCurrentMonth }
        assertEquals(28, febDays.size)
    }

    @Test
    fun getMonthDays_february_2024_29_days_leap_year() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2024, 2)
        val febDays = days.filter { it.isCurrentMonth }
        assertEquals(29, febDays.size)
    }

    @Test
    fun getMonthDays_today_is_marked() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 5)
        val todayCell = days.first { it.isToday }
        assertEquals(15, todayCell.date.day)
        assertTrue(todayCell.isCurrentMonth)
    }

    @Test
    fun getMonthDays_selected_date_is_marked() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 5)
        val selectedCell = days.first { it.isSelected }
        assertEquals(15, selectedCell.date.day)
    }
}
