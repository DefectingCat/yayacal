package plus.rua.project

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarCacheBirthdayTest {
    private val cache = LunarCache()

    @Test
    fun solarBirthdaySeptember4_returnsTrue() = runTest {
        val info = cache.getOrCompute(LocalDate(2026, 9, 4))
        assertTrue("阳历 9 月 4 日应为生日", info.isBirthday)
    }

    @Test
    fun lunarBirthdayFirstMonthDay21_returnsTrue() = runTest {
        // 2026 年农历正月二十一对应阳历 2026-03-09
        val info = cache.getOrCompute(LocalDate(2026, 3, 9))
        assertTrue("农历正月二十一应为生日", info.isBirthday)
    }

    @Test
    fun regularDate_returnsFalse() = runTest {
        val info = cache.getOrCompute(LocalDate(2026, 6, 15))
        assertFalse("普通日期不应为生日", info.isBirthday)
    }

    @Test
    fun formatLunarDate_springFestival_returnsCorrectLunarDate() = runTest {
        // 2026-02-17 是春节（正月初一），不应输出节日名而应输出农历日期
        val result = cache.formatLunarDate(LocalDate(2026, 2, 17))
        assertEquals("农历正月初一", result)
    }

    @Test
    fun formatLunarDate_regularDay_returnsCorrectLunarDate() = runTest {
        // 2026-05-15 是农历三月廿九
        val result = cache.formatLunarDate(LocalDate(2026, 5, 15))
        assertEquals("农历三月廿九", result)
    }

    @Test
    fun formatLunarDate_firstDayOfMonth_includesDayName() = runTest {
        // 2026-03-19 是农历二月初一；formatLunarDate 始终返回"农历月名+日名"
        val result = cache.formatLunarDate(LocalDate(2026, 3, 19))
        assertEquals("农历二月初一", result)
    }
}
