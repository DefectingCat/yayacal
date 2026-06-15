package plus.rua.project

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
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
    fun solarBirthdayNotFirstLunarDay21_stillReturnsTrue() = runTest {
        val info = cache.getOrCompute(LocalDate(2026, 9, 4))
        assertTrue(info.isBirthday)
    }

    @Test
    fun lunarBirthdayNotSeptember4_stillReturnsTrue() = runTest {
        val info = cache.getOrCompute(LocalDate(2026, 3, 9))
        assertTrue(info.isBirthday)
    }
}
