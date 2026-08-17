package plus.rua.project

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * [BirthdayChecker] 的纯 JVM 单测。
 *
 * 覆盖范围：公历 9 月 14 日正例（跨年份）、前后相邻日期反例、
 * 以及通过注入固定时钟的 [BirthdayChecker.isBirthdayToday] 路径。
 */
class BirthdayCheckerTest {
    @Test
    fun isBirthday_september14_returnsTrue() {
        // 固定公历日期，任意年份均成立
        val birthdayDates =
            listOf(
                LocalDate(2025, 9, 14),
                LocalDate(2026, 9, 14),
                LocalDate(2030, 9, 14),
            )
        birthdayDates.forEach { date ->
            assertTrue(BirthdayChecker.isBirthday(date), "$date 应为生日")
        }
    }

    @Test
    fun isBirthday_adjacentDates_returnsFalse() {
        val notBirthday =
            listOf(
                LocalDate(2026, 9, 13), // 前一天
                LocalDate(2026, 9, 15), // 后一天
                LocalDate(2026, 8, 14), // 前一月同日
                LocalDate(2026, 10, 14), // 后一月同日
            )
        notBirthday.forEach { date ->
            assertFalse(BirthdayChecker.isBirthday(date), "$date 不应为生日")
        }
    }

    @Test
    fun isBirthdayToday_fixedClockOnBirthday_returnsTrue() {
        val checker = BirthdayChecker(clock = fixedClockAt("2026-09-14T12:00:00Z"))
        assertTrue(checker.isBirthdayToday())
    }

    @Test
    fun isBirthdayToday_fixedClockNotOnBirthday_returnsFalse() {
        val checker = BirthdayChecker(clock = fixedClockAt("2026-09-13T12:00:00Z"))
        assertFalse(checker.isBirthdayToday())
    }

    private fun fixedClockAt(instant: String): Clock = object : Clock {
        override fun now(): Instant = Instant.parse(instant)
    }
}
