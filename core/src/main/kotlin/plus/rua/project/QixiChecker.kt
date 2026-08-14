package plus.rua.project

import com.tyme.solar.SolarDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * 七夕（农历七月初七）判定。
 *
 * 判定不依赖 tyme4kt 的节日名称字符串，直接比较农历月/日，并排除闰七月
 * （如 2006 年闰七月初七不算七夕）。时钟可注入，便于测试。
 *
 * @param clock 时钟，默认系统时钟
 */
class QixiChecker(
    private val clock: Clock = Clock.System,
) {
    /** 判断今天（系统时区）是否为七夕。 */
    fun isQixiToday(): Boolean = isQixi(clock.todayIn(TimeZone.currentSystemDefault()))

    companion object {
        /** 判断指定公历日期是否为七夕（农历七月初七，非闰月）。 */
        fun isQixi(date: LocalDate): Boolean {
            val lunarDay = SolarDay.fromYmd(date.year, date.month.number, date.day).getLunarDay()
            val lunarMonth = lunarDay.getLunarMonth()
            return !lunarMonth.isLeap() && lunarMonth.month == 7 && lunarDay.day == 7
        }
    }
}
