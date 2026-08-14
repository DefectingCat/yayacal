package plus.rua.project

import com.tyme.lunar.LunarDay
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 纪念日计算单元测试：天数口径、生日滚动、农历换算与钳制、里程碑选取。
 *
 * 农历换算的已知锚点：2026 年正月廿一 = 2026-03-09（与 LunarCacheBirthdayTest 同源）、
 * 2027 年春节为 2027-02-06，故 2027 年正月廿一 = 2027-02-26。
 */
class AnniversaryTest {
    @Test
    fun daysTogether_onStartDay_isZero() {
        assertEquals(0, daysTogether(LocalDate(2025, 11, 4)))
    }

    @Test
    fun daysTogether_2026_08_14_is283() {
        assertEquals(283, daysTogether(LocalDate(2026, 8, 14)))
    }

    @Test
    fun nextSolarAnniversary_beforeOccurrence_returnsThisYear() {
        assertEquals(
            LocalDate(2026, 9, 4),
            nextSolarAnniversary(LocalDate(2026, 8, 14), month = 9, day = 4),
        )
    }

    @Test
    fun nextSolarAnniversary_onOccurrence_returnsToday() {
        assertEquals(
            LocalDate(2026, 9, 4),
            nextSolarAnniversary(LocalDate(2026, 9, 4), month = 9, day = 4),
        )
    }

    @Test
    fun nextSolarAnniversary_afterOccurrence_rollsToNextYear() {
        assertEquals(
            LocalDate(2027, 9, 4),
            nextSolarAnniversary(LocalDate(2026, 9, 5), month = 9, day = 4),
        )
    }

    @Test
    fun nextLunarAnniversary_beforeOccurrence_returnsThisYear() {
        assertEquals(
            LocalDate(2026, 3, 9),
            nextLunarAnniversary(LocalDate(2026, 1, 1), lunarMonth = 1, lunarDay = 21),
        )
    }

    @Test
    fun nextLunarAnniversary_onOccurrence_returnsToday() {
        assertEquals(
            LocalDate(2026, 3, 9),
            nextLunarAnniversary(LocalDate(2026, 3, 9), lunarMonth = 1, lunarDay = 21),
        )
    }

    @Test
    fun nextLunarAnniversary_afterOccurrence_rollsToNextLunarYear() {
        assertEquals(
            LocalDate(2027, 2, 26),
            nextLunarAnniversary(LocalDate(2026, 8, 14), lunarMonth = 1, lunarDay = 21),
        )
    }

    @Test
    fun nextLunarAnniversary_dayBeyondMonthCount_clampsToLastDay() {
        // 2026 年腊月为小月（无三十），三十应钳制到廿九且不抛异常
        val result = nextLunarAnniversary(LocalDate(2026, 6, 1), lunarMonth = 12, lunarDay = 30)
        val expected = LunarDay.fromYmd(2026, 12, 29).getSolarDay()
        assertEquals(LocalDate(expected.year, expected.month, expected.day), result)
    }

    @Test
    fun nextMilestone_day283_picksDay365() {
        val m = nextMilestone(LocalDate(2026, 8, 14))
        assertEquals("365 天", m.label)
        assertEquals(LocalDate(2026, 11, 3), m.date)
        assertEquals(81, m.daysLeft)
        assertFalse(m.isToday)
    }

    @Test
    fun nextMilestone_onMilestoneDay_isToday() {
        val m = nextMilestone(LocalDate(2026, 11, 3))
        assertTrue(m.isToday)
        assertEquals(0, m.daysLeft)
        assertEquals("365 天", m.label)
    }

    @Test
    fun nextMilestone_afterDayMilestones_picksAnniversary() {
        val m = nextMilestone(LocalDate(2030, 1, 1))
        assertEquals("5 周年", m.label)
        assertEquals(LocalDate(2030, 11, 4), m.date)
        assertEquals(307, m.daysLeft)
    }
}
