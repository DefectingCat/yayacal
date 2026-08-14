package plus.rua.project

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [QixiChecker] 的纯 JVM 单测。
 *
 * 覆盖范围：七夕（农历七月初七）正例、相邻日期反例、闰七月排除、
 * 以及通过注入固定时钟的 [QixiChecker.isQixiToday] 路径。
 * 公历基准日期经 tyme4kt 独立验证（如 2026-08-19 即农历 2026 年七月初七）。
 */
class QixiCheckerTest {
    @Test
    fun isQixi_knownQixiDates_returnsTrue() {
        // 2024 / 2025 / 2026 三年的七夕公历日期
        val qixiDates =
            listOf(
                LocalDate(2024, 8, 10),
                LocalDate(2025, 8, 29),
                LocalDate(2026, 8, 19),
            )
        qixiDates.forEach { date ->
            assertTrue(QixiChecker.isQixi(date), "$date 应为七夕")
        }
    }

    @Test
    fun isQixi_adjacentDates_returnsFalse() {
        val notQixi =
            listOf(
                LocalDate(2026, 8, 18), // 七月初六
                LocalDate(2026, 8, 20), // 七月初八
                LocalDate(2026, 8, 14), // 七月初二
            )
        notQixi.forEach { date ->
            assertFalse(QixiChecker.isQixi(date), "$date 不应为七夕")
        }
    }

    @Test
    fun isQixi_leapSeventhMonth_returnsFalse() {
        // 2006 年闰七月：七月初七 = 2006-07-31（正七夕），闰七月初七 = 2006-08-30（不算）
        assertTrue(QixiChecker.isQixi(LocalDate(2006, 7, 31)))
        assertFalse(QixiChecker.isQixi(LocalDate(2006, 8, 30)))
    }

    @Test
    fun isQixiToday_fixedClockOnQixi_returnsTrue() {
        val checker = QixiChecker(clock = fixedClockAt("2026-08-19T12:00:00Z"))
        assertTrue(checker.isQixiToday())
    }

    @Test
    fun isQixiToday_fixedClockNotOnQixi_returnsFalse() {
        val checker = QixiChecker(clock = fixedClockAt("2026-08-14T12:00:00Z"))
        assertFalse(checker.isQixiToday())
    }

    private fun fixedClockAt(instant: String): Clock =
        object : Clock {
            override fun now(): Instant = Instant.parse(instant)
        }
}
