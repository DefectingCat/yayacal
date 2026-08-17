package plus.rua.project

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * [ConfessionChecker] 的纯 JVM 单测。
 *
 * 覆盖范围：公历 11 月 4 日正例（跨年份）、前后相邻日期反例、
 * 以及通过注入固定时钟的 [ConfessionChecker.isConfessionToday] 路径。
 */
class ConfessionCheckerTest {
    @Test
    fun isConfession_november4_returnsTrue() {
        val confessionDates =
            listOf(
                LocalDate(2024, 11, 4),
                LocalDate(2025, 11, 4),
                LocalDate(2026, 11, 4),
                LocalDate(2027, 11, 4),
            )
        confessionDates.forEach { date ->
            assertTrue(ConfessionChecker.isConfession(date), "$date 应为表白日")
        }
    }

    @Test
    fun isConfession_adjacentDates_returnsFalse() {
        val notConfession =
            listOf(
                LocalDate(2026, 11, 3), // 前一天
                LocalDate(2026, 11, 5), // 后一天
                LocalDate(2026, 9, 4), // 生日
                LocalDate(2026, 8, 19), // 七夕
            )
        notConfession.forEach { date ->
            assertFalse(ConfessionChecker.isConfession(date), "$date 不应为表白日")
        }
    }

    @Test
    fun isConfessionToday_fixedClockOnConfession_returnsTrue() {
        val checker = ConfessionChecker(clock = fixedClockAt("2026-11-04T12:00:00Z"))
        assertTrue(checker.isConfessionToday())
    }

    @Test
    fun isConfessionToday_fixedClockNotOnConfession_returnsFalse() {
        val checker = ConfessionChecker(clock = fixedClockAt("2026-11-03T12:00:00Z"))
        assertFalse(checker.isConfessionToday())
    }

    private fun fixedClockAt(instant: String): Clock = object : Clock {
        override fun now(): Instant = Instant.parse(instant)
    }
}
