package plus.rua.project.ui

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarUtilsTest {

    // ---- calculateWeeksCount ----

    @Test
    fun calculateWeeksCount_normalFebruary_returns4Rows() {
        // Feb 2027: starts on Monday, 28 days -> exactly 4 rows
        assertEquals(4, calculateWeeksCount(2027, 2))
    }

    @Test
    fun calculateWeeksCount_leapYearFebruary_returns5Rows() {
        // Feb 2024: starts on Thursday, 29 days -> 5 rows
        assertEquals(5, calculateWeeksCount(2024, 2))
    }

    @Test
    fun calculateWeeksCount_sixRowMonth_returns6Rows() {
        // Mar 2026: starts on Sunday (ordinal=6), 31 days -> 6 rows
        assertEquals(6, calculateWeeksCount(2026, 3))
    }

    @Test
    fun calculateWeeksCount_monthStartingMonday_31days_returns5Rows() {
        // Jan 2027: starts on Friday (ordinal=4), 31 days
        // offset=4, days=31, (4+31-1)/7 + 1 = 34/7 + 1 = 4+1 = 5
        assertEquals(5, calculateWeeksCount(2027, 1))
    }

    @Test
    fun calculateWeeksCount_monthStartingSunday_returns6Rows() {
        // Jun 2025: starts on Sunday (ordinal=6), 30 days
        // offset=6, days=30, (6+30-1)/7 + 1 = 35/7 + 1 = 5+1 = 6
        assertEquals(6, calculateWeeksCount(2025, 6))
    }

    @Test
    fun calculateWeeksCount_30dayMonthStartingSaturday_returns5Rows() {
        // Nov 2025: starts on Saturday (ordinal=5), 30 days
        // offset=5, days=30, (5+30-1)/7 + 1 = 34/7 + 1 = 4+1 = 5
        assertEquals(5, calculateWeeksCount(2025, 11))
    }

    @Test
    fun calculateWeeksCount_december() {
        // Dec 2026: starts on Wednesday (ordinal=2), 31 days
        // offset=2, days=31, (2+31-1)/7 + 1 = 32/7 + 1 = 4+1 = 5
        assertEquals(5, calculateWeeksCount(2026, 12))
    }

    // ---- pageToYearMonth / yearMonthToPage ----

    @Test
    fun pageToYearMonth_centerPage_returnsInitialYearMonth() {
        val (year, month) = pageToYearMonth(START_PAGE, 2026, 5)
        assertEquals(2026, year)
        assertEquals(5, month)
    }

    @Test
    fun pageToYearMonth_forwardOnePage_returnsNextMonth() {
        val (year, month) = pageToYearMonth(START_PAGE + 1, 2026, 5)
        assertEquals(2026, year)
        assertEquals(6, month)
    }

    @Test
    fun pageToYearMonth_backwardOnePage_returnsPreviousMonth() {
        val (year, month) = pageToYearMonth(START_PAGE - 1, 2026, 5)
        assertEquals(2026, year)
        assertEquals(4, month)
    }

    @Test
    fun pageToYearMonth_crossYearBoundary_forward() {
        // From Dec 2026, forward 1 page -> Jan 2027
        val (year, month) = pageToYearMonth(START_PAGE + 1, 2026, 12)
        assertEquals(2027, year)
        assertEquals(1, month)
    }

    @Test
    fun pageToYearMonth_crossYearBoundary_backward() {
        // From Jan 2026, backward 1 page -> Dec 2025
        val (year, month) = pageToYearMonth(START_PAGE - 1, 2026, 1)
        assertEquals(2025, year)
        assertEquals(12, month)
    }

    @Test
    fun pageToYearMonth_manyPagesForward() {
        // 12 pages forward from May 2026 -> May 2027
        val (year, month) = pageToYearMonth(START_PAGE + 12, 2026, 5)
        assertEquals(2027, year)
        assertEquals(5, month)
    }

    @Test
    fun yearMonthToPage_centerMonth_returnsStartPage() {
        assertEquals(START_PAGE, yearMonthToPage(2026, 5, 2026, 5))
    }

    @Test
    fun yearMonthToPage_nextMonth_returnsNextPage() {
        assertEquals(START_PAGE + 1, yearMonthToPage(2026, 6, 2026, 5))
    }

    @Test
    fun yearMonthToPage_previousMonth_returnsPreviousPage() {
        assertEquals(START_PAGE - 1, yearMonthToPage(2026, 4, 2026, 5))
    }

    @Test
    fun yearMonthToPage_crossYearBoundary() {
        assertEquals(START_PAGE + 1, yearMonthToPage(2027, 1, 2026, 12))
    }

    @Test
    fun pageToYearMonth_yearMonthRoundTrip() {
        // Converting page -> yearMonth -> page should return the original page
        val initialYear = 2026
        val initialMonth = 5
        for (offset in -24..24) {
            val page = START_PAGE + offset
            val (y, m) = pageToYearMonth(page, initialYear, initialMonth)
            val roundTrip = yearMonthToPage(y, m, initialYear, initialMonth)
            assertEquals(page, roundTrip, "Round-trip failed for offset=$offset")
        }
    }

    // ---- LocalDate.toWeekMonday ----

    @Test
    fun toWeekMonday_monday_returnsItself() {
        val monday = LocalDate(2026, 5, 11) // Monday
        assertEquals(monday, monday.toWeekMonday())
    }

    @Test
    fun toWeekMonday_tuesday_returnsPreviousMonday() {
        val tuesday = LocalDate(2026, 5, 12)
        assertEquals(LocalDate(2026, 5, 11), tuesday.toWeekMonday())
    }

    @Test
    fun toWeekMonday_sunday_returnsPreviousMonday() {
        val sunday = LocalDate(2026, 5, 17) // Sunday
        assertEquals(LocalDate(2026, 5, 11), sunday.toWeekMonday())
    }

    @Test
    fun toWeekMonday_crossMonthBoundary() {
        // June 1, 2026 is a Monday - so May 31 (Sunday) should return May 25
        val sunday = LocalDate(2026, 5, 31)
        assertEquals(LocalDate(2026, 5, 25), sunday.toWeekMonday())
    }

    @Test
    fun toWeekMonday_crossYearBoundary() {
        // Jan 1, 2026 is a Thursday. Monday of that week is Dec 29, 2025
        val thursday = LocalDate(2026, 1, 1)
        assertEquals(LocalDate(2025, 12, 29), thursday.toWeekMonday())
    }

    @Test
    fun toWeekMonday_saturday_returnsPreviousMonday() {
        val saturday = LocalDate(2026, 5, 16)
        assertEquals(LocalDate(2026, 5, 11), saturday.toWeekMonday())
    }

    @Test
    fun toWeekMonday_wednesday_returnsPreviousMonday() {
        val wednesday = LocalDate(2026, 5, 13)
        assertEquals(LocalDate(2026, 5, 11), wednesday.toWeekMonday())
    }

    // ---- pageToWeekMonday ----

    @Test
    fun pageToWeekMonday_centerPage_returnsInitial() {
        val initial = LocalDate(2026, 5, 11) // Monday
        assertEquals(initial, pageToWeekMonday(START_PAGE, initial))
    }

    @Test
    fun pageToWeekMonday_forwardOnePage_returnsNextWeekMonday() {
        val initial = LocalDate(2026, 5, 11)
        assertEquals(LocalDate(2026, 5, 18), pageToWeekMonday(START_PAGE + 1, initial))
    }

    @Test
    fun pageToWeekMonday_backwardOnePage_returnsPreviousWeekMonday() {
        val initial = LocalDate(2026, 5, 11)
        assertEquals(LocalDate(2026, 5, 4), pageToWeekMonday(START_PAGE - 1, initial))
    }

    @Test
    fun pageToWeekMonday_forwardMultiplePages() {
        val initial = LocalDate(2026, 5, 11)
        assertEquals(LocalDate(2026, 6, 8), pageToWeekMonday(START_PAGE + 4, initial))
    }

    @Test
    fun pageToWeekMonday_backwardMultiplePages_crossMonth() {
        val initial = LocalDate(2026, 5, 11)
        assertEquals(LocalDate(2026, 4, 13), pageToWeekMonday(START_PAGE - 4, initial))
    }

    // ---- lerp ----

    @Test
    fun lerp_fractionZero_returnsStart() {
        assertEquals(0f, lerp(0f, 100f, 0f), 0.01f)
    }

    @Test
    fun lerp_fractionOne_returnsEnd() {
        assertEquals(100f, lerp(0f, 100f, 1f), 0.01f)
    }

    @Test
    fun lerp_fractionHalf_returnsMidpoint() {
        assertEquals(50f, lerp(0f, 100f, 0.5f), 0.01f)
    }

    @Test
    fun lerp_negativeRange() {
        assertEquals(0f, lerp(-50f, 50f, 0.5f), 0.01f)
    }

    @Test
    fun lerp_sameStartAndEnd_returnsSame() {
        assertEquals(42f, lerp(42f, 42f, 0.5f), 0.01f)
    }

    @Test
    fun lerp_fractionGreaterThanOne() {
        assertEquals(150f, lerp(0f, 100f, 1.5f), 0.01f)
    }

    @Test
    fun lerp_negativeFraction() {
        assertEquals(-50f, lerp(0f, 100f, -0.5f), 0.01f)
    }
}
