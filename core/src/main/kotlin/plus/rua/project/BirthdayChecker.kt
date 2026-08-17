package plus.rua.project

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * 生日（公历 9 月 4 日）判定。
 *
 * 固定公历日期，直接比较月/日，不涉及农历换算。时钟可注入，便于测试。
 *
 * @param clock 时钟，默认系统时钟
 */
class BirthdayChecker(
    private val clock: Clock = Clock.System,
) {
    /** 判断今天（系统时区）是否为生日。 */
    fun isBirthdayToday(): Boolean = isBirthday(clock.todayIn(TimeZone.currentSystemDefault()))

    companion object {
        /** 判断指定公历日期是否为生日（9 月 4 日）。 */
        fun isBirthday(date: LocalDate): Boolean = date.month.number == 9 && date.dayOfMonth == 4
    }
}
