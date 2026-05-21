package plus.rua.project.ui

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 测试 CalendarUtils 中尚未被 [CalendarUtilsTest] 覆盖的函数：
 * - [calculateWeeksCountForPage]
 * - [relativeDayDescription]
 * - [formatLunarDate]
 */
class CalendarUtilsExtraTest {

    // ---- calculateWeeksCountForPage ----

    @Test
    fun calculateWeeksCountForPage_centerPage_returnsCurrentMonthRows() {
        // today = 2026/5/15 (May), May 2026 has 5 rows
        val today = LocalDate(2026, 5, 15)
        assertEquals(calculateWeeksCount(2026, 5), calculateWeeksCountForPage(START_PAGE, today))
    }

    @Test
    fun calculateWeeksCountForPage_forwardOnePage_returnsNextMonthRows() {
        // From May 2026, +1 -> June 2026
        val today = LocalDate(2026, 5, 15)
        assertEquals(
            calculateWeeksCount(2026, 6),
            calculateWeeksCountForPage(START_PAGE + 1, today)
        )
    }

    @Test
    fun calculateWeeksCountForPage_backwardOnePage_returnsPreviousMonthRows() {
        // From May 2026, -1 -> April 2026
        val today = LocalDate(2026, 5, 15)
        assertEquals(
            calculateWeeksCount(2026, 4),
            calculateWeeksCountForPage(START_PAGE - 1, today)
        )
    }

    @Test
    fun calculateWeeksCountForPage_crossYearForward() {
        // From December 2026, +1 -> January 2027
        val today = LocalDate(2026, 12, 10)
        assertEquals(
            calculateWeeksCount(2027, 1),
            calculateWeeksCountForPage(START_PAGE + 1, today)
        )
    }

    @Test
    fun calculateWeeksCountForPage_crossYearBackward() {
        // From January 2026, -1 -> December 2025
        val today = LocalDate(2026, 1, 10)
        assertEquals(
            calculateWeeksCount(2025, 12),
            calculateWeeksCountForPage(START_PAGE - 1, today)
        )
    }

    @Test
    fun calculateWeeksCountForPage_twelvePagesForward_returnsSameMonthOfNextYear() {
        val today = LocalDate(2026, 5, 15)
        // +12 -> May 2027
        assertEquals(
            calculateWeeksCount(2027, 5),
            calculateWeeksCountForPage(START_PAGE + 12, today)
        )
    }

    // ---- relativeDayDescription ----

    @Test
    fun relativeDayDescription_today_returnsToday() {
        val today = LocalDate(2026, 5, 19)
        assertEquals("今天", relativeDayDescription(today, today))
    }

    @Test
    fun relativeDayDescription_yesterday_returnsYesterday() {
        val today = LocalDate(2026, 5, 19)
        val yesterday = LocalDate(2026, 5, 18)
        assertEquals("昨天", relativeDayDescription(yesterday, today))
    }

    @Test
    fun relativeDayDescription_tomorrow_returnsTomorrow() {
        val today = LocalDate(2026, 5, 19)
        val tomorrow = LocalDate(2026, 5, 20)
        assertEquals("明天", relativeDayDescription(tomorrow, today))
    }

    @Test
    fun relativeDayDescription_twoDaysBefore_returnsXDaysAgo() {
        val today = LocalDate(2026, 5, 19)
        assertEquals("2天前", relativeDayDescription(LocalDate(2026, 5, 17), today))
    }

    @Test
    fun relativeDayDescription_twoDaysAfter_returnsXDaysLater() {
        val today = LocalDate(2026, 5, 19)
        assertEquals("2天后", relativeDayDescription(LocalDate(2026, 5, 21), today))
    }

    @Test
    fun relativeDayDescription_aWeekBefore_returnsCorrectDays() {
        val today = LocalDate(2026, 5, 19)
        assertEquals("7天前", relativeDayDescription(LocalDate(2026, 5, 12), today))
    }

    @Test
    fun relativeDayDescription_thirtyDaysAfter_returnsCorrectDays() {
        val today = LocalDate(2026, 5, 1)
        assertEquals("30天后", relativeDayDescription(LocalDate(2026, 5, 31), today))
    }

    @Test
    fun relativeDayDescription_crossMonthBackward_returnsCorrectDays() {
        val today = LocalDate(2026, 5, 2)
        assertEquals("3天前", relativeDayDescription(LocalDate(2026, 4, 29), today))
    }

    @Test
    fun relativeDayDescription_crossYearForward_returnsCorrectDays() {
        val today = LocalDate(2025, 12, 30)
        assertEquals("5天后", relativeDayDescription(LocalDate(2026, 1, 4), today))
    }

    // ---- formatLunarDate ----

    @Test
    fun formatLunarDate_january1_2026_returnsCorrectLunar() {
        // 2026/1/1 公历 -> 农历十一月十三
        val result = formatLunarDate(LocalDate(2026, 1, 1))
        assertEquals("农历十一月十三", result)
    }

    @Test
    fun formatLunarDate_lunarNewYear2026_returnsFirstDayOfFirstMonth() {
        // 2026年农历正月初一 = 2026/2/17 公历
        val result = formatLunarDate(LocalDate(2026, 2, 17))
        assertEquals("农历正月初一", result)
    }

    @Test
    fun formatLunarDate_specificDates_returnsExactLunar() {
        assertEquals("农历正月十三", formatLunarDate(LocalDate(2026, 3, 1)))
        assertEquals("农历五月十六", formatLunarDate(LocalDate(2026, 6, 30)))
        assertEquals("农历十一月十七", formatLunarDate(LocalDate(2026, 12, 25)))
    }

    @Test
    fun formatLunarDate_leapMonth_returnsLeapPrefix() {
        // 2023年闰二月：2023/3/23 对应农历闰二月初二
        val result = formatLunarDate(LocalDate(2023, 3, 23))
        assertEquals("农历闰二月初二", result)
    }
}
