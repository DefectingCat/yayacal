package plus.rua.project

import com.tyme.lunar.LunarDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
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

    @Test
    fun dateDetails_forTogether_matchesExpected() {
        val together = AnniversaryDates.TOGETHER
        val solarDay = com.tyme.solar.SolarDay.fromYmd(together.year, together.month.number, together.day)
        val lunarDay = solarDay.getLunarDay()
        val sixtyCycleYear = lunarDay.getLunarMonth().getLunarYear().getSixtyCycle().getName()
        val constellation = solarDay.getConstellation().getName()
        val term = solarDay.getTerm().getName()
        assertEquals("乙巳", sixtyCycleYear)
        assertEquals("九月", lunarDay.getLunarMonth().getName())
        assertEquals("十五", lunarDay.getName())
        assertEquals("天蝎", constellation)
        assertEquals("霜降", term)
    }

    @Test
    fun dateDetails_forDuckBirthday_matchesExpected() {
        val duck = LocalDate(2026, 9, 4)
        val solarDay = com.tyme.solar.SolarDay.fromYmd(duck.year, duck.month.number, duck.day)
        val lunarDay = solarDay.getLunarDay()
        val sixtyCycleYear = lunarDay.getLunarMonth().getLunarYear().getSixtyCycle().getName()
        val constellation = solarDay.getConstellation().getName()
        val term = solarDay.getTerm().getName()
        assertEquals("丙午", sixtyCycleYear)
        assertEquals("七月", lunarDay.getLunarMonth().getName())
        assertEquals("廿三", lunarDay.getName())
        assertEquals("处女", constellation)
        assertEquals("处暑", term)
    }

    @Test
    fun dateDetails_forDogBirthday2026_and_2027() {
        val dog2026 = LocalDate(2026, 3, 9)
        val dog2027 = LocalDate(2027, 2, 26)
        val solarDay2026 = com.tyme.solar.SolarDay.fromYmd(dog2026.year, dog2026.month.number, dog2026.day)
        val solarDay2027 = com.tyme.solar.SolarDay.fromYmd(dog2027.year, dog2027.month.number, dog2027.day)
        assertEquals("双鱼", solarDay2026.getConstellation().getName())
        assertEquals("双鱼", solarDay2027.getConstellation().getName())
        assertEquals("丙午", solarDay2026.getLunarDay().getLunarMonth().getLunarYear().getSixtyCycle().getName())
        assertEquals("丁未", solarDay2027.getLunarDay().getLunarMonth().getLunarYear().getSixtyCycle().getName())
    }

    @Test
    fun getConstellationWithSymbol_correct() {
        assertEquals("天蝎座 ♏", getConstellationWithSymbol(LocalDate(2025, 11, 4)))
        assertEquals("处女座 ♍", getConstellationWithSymbol(LocalDate(2026, 9, 4)))
        assertEquals("双鱼座 ♓", getConstellationWithSymbol(LocalDate(2026, 3, 9)))
        assertEquals("天秤座 ♎", getConstellationWithSymbol(LocalDate(2026, 10, 16)))
    }

    @Test
    fun getChineseWeekday_correct() {
        assertEquals("星期二", getChineseWeekday(LocalDate(2025, 11, 4)))
        assertEquals("星期五", getChineseWeekday(LocalDate(2026, 9, 4)))
    }

    @Test
    fun getDateDetailInfo_forTogether_fieldsPopulated() {
        val info = getDateDetailInfo(LocalDate(2025, 11, 4))
        assertEquals("2025 年 11 月 4 日", info.solarDateText)
        assertEquals("星期二", info.weekdayText)
        assertEquals("农历乙巳年九月十五", info.lunarGanzhiText)
        assertEquals("九月十五", info.lunarMonthDayText)
        assertEquals("天蝎座 ♏", info.constellationText)
        assertEquals("霜降", info.solarTermText)
    }

    @Test
    fun getUpcomingSolarAnniversaryInfo_duckBirthday_on2026_08_14() {
        val upcoming = getUpcomingSolarAnniversaryInfo(LocalDate(2026, 8, 14), 9, 4)
        assertEquals(LocalDate(2026, 9, 4), upcoming.targetSolarDate)
        assertEquals(21, upcoming.daysLeft)
        assertFalse(upcoming.isToday)
        assertEquals("2026 年 9 月 4 日 星期五", upcoming.targetSolarFormatted)
        assertEquals("农历丙午年七月廿三", upcoming.targetLunarFormatted)
    }

    @Test
    fun getUpcomingLunarAnniversaryInfo_dogBirthday_on2026_08_14() {
        val upcoming = getUpcomingLunarAnniversaryInfo(LocalDate(2026, 8, 14), 1, 21)
        assertEquals(LocalDate(2027, 2, 26), upcoming.targetSolarDate)
        assertEquals(196, upcoming.daysLeft)
        assertFalse(upcoming.isToday)
        assertEquals("2027 年 2 月 26 日 星期五", upcoming.targetSolarFormatted)
        assertEquals("农历丁未年正月廿一", upcoming.targetLunarFormatted)
    }

    @Test
    fun getAllMilestoneProgress_checksPassedAndUpcoming() {
        val milestones = getAllMilestoneProgress(LocalDate(2026, 8, 14))
        assertEquals(5, milestones.size)
        val m100 = milestones[0]
        assertEquals("100 天", m100.label)
        assertTrue(m100.isPassed)
        assertEquals(0, m100.daysLeft)

        val m365 = milestones[1]
        assertEquals("365 天 (1周年)", m365.label)
        assertFalse(m365.isPassed)
        assertEquals(81, m365.daysLeft)
    }
}
