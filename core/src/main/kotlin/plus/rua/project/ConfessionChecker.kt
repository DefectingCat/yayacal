package plus.rua.project

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * 表白日（公历 11 月 4 日）判定。
 *
 * 固定公历日期，直接比较月/日。时钟可注入，便于测试。
 *
 * @param clock 时钟，默认系统时钟
 */
class ConfessionChecker(
    private val clock: Clock = Clock.System,
) {
    /** 判断今天（系统时区）是否为表白日。 */
    fun isConfessionToday(): Boolean = isConfession(clock.todayIn(TimeZone.currentSystemDefault()))

    companion object {
        /** 判断指定公历日期是否为表白日（11 月 4 日）。 */
        fun isConfession(date: LocalDate): Boolean = date.month.number == 11 && date.dayOfMonth == 4
    }
}
