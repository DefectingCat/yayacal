package plus.rua.project

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarCacheRoseDayTest {
    private val cache = LunarCache()

    @Test
    fun october16_returnsTrue() = runTest {
        val info = cache.getOrCompute(LocalDate(2026, 10, 16))
        assertTrue("10 月 16 日应为玫瑰日", info.isRoseDay)
    }

    @Test
    fun september16_returnsFalse() = runTest {
        val info = cache.getOrCompute(LocalDate(2026, 9, 16))
        assertFalse("非 10 月的 16 日不应为玫瑰日", info.isRoseDay)
    }

    @Test
    fun regularDate_returnsFalse() = runTest {
        val info = cache.getOrCompute(LocalDate(2026, 6, 15))
        assertFalse("普通日期不应为玫瑰日", info.isRoseDay)
    }
}
