package plus.rua.project

import com.tyme.lunar.LunarDay
import com.tyme.lunar.LunarMonth
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * 纪念日固定日期的唯一数据源。
 *
 * 月视图的生日角标（[LunarCache]）与纪念日页面共用此处的常量，
 * 避免两处硬编码漂移。
 */
object AnniversaryDates {
    /** 在一起的起始日期（含当天，当天为「第 1 天」）。 */
    val TOGETHER: LocalDate = LocalDate(2025, 11, 4)

    /** 鸭鸭生日：阳历 9 月 4 日。 */
    const val DUCK_BIRTHDAY_MONTH = 9

    /** 鸭鸭生日日期。 */
    const val DUCK_BIRTHDAY_DAY = 4

    /** 小狗生日：农历正月廿一。tyme4kt 农历月 1..12，正月 = 1。 */
    const val DOG_LUNAR_MONTH = 1

    /** 小狗生日农历日。 */
    const val DOG_LUNAR_DAY = 21

    /** 小狗生日的农历文案展示。 */
    const val DOG_LUNAR_LABEL = "农历正月廿一"

    /** 恋爱天数里程碑（按「第 N 天」口径，即起始日为第 1 天）。 */
    val DAY_MILESTONES = listOf(100, 365, 520, 1000, 1314)
}

/**
 * 到某个目标日的倒数信息。
 *
 * @param targetDate 目标日期（已滚动到今天或未来）
 * @param daysLeft 距今天数，当天为 0
 * @param isToday 目标是否就是今天
 */
data class DayCountdown(
    val targetDate: LocalDate,
    val daysLeft: Int,
    val isToday: Boolean,
)

/**
 * 恋爱里程碑（天数节点或整周年）。
 *
 * @param label 展示名，如「365 天」「2 周年」
 * @param date 里程碑日期
 * @param daysLeft 距今天数，当天为 0
 * @param isToday 里程碑是否就是今天
 */
data class Milestone(
    val label: String,
    val date: LocalDate,
    val daysLeft: Int,
    val isToday: Boolean,
)

/**
 * 在一起已经过的天数（差值口径，不含起始当天；起始当天为 0）。
 */
fun daysTogether(today: LocalDate): Int = AnniversaryDates.TOGETHER.daysUntil(today)

/**
 * 构造目标日的倒数信息，[target] 早于 [today] 时行为未定义（调用方应先滚动到未来）。
 */
fun toCountdown(target: LocalDate, today: LocalDate): DayCountdown = DayCountdown(
    targetDate = target,
    daysLeft = today.daysUntil(target),
    isToday = target == today,
)

/**
 * 下一个阳历年度纪念日（如生日）。
 *
 * 今年月日已过则滚动到明年，当天命中返回当天。
 *
 * @param today 今天
 * @param month 阳历月
 * @param day 阳历日
 */
fun nextSolarAnniversary(
    today: LocalDate,
    month: Int,
    day: Int,
): LocalDate {
    var candidate = LocalDate(today.year, month, day)
    if (candidate < today) {
        candidate = LocalDate(today.year + 1, month, day)
    }
    return candidate
}

/**
 * 下一个农历年度纪念日（如农历生日）。
 *
 * 每年用 tyme4kt 重新换算农历→阳历（同一农历日每年阳历日期漂移 ±1 个月），
 * 腊月三十等不存在的农历日自动钳制到该月最后一天，
 * 换算结果早于今天则农历年 +1 重算（覆盖跨年腊月），当天命中返回当天。
 *
 * @param today 今天
 * @param lunarMonth 农历月 1..12（tyme4kt 约定，暂不支持闰月负数）
 * @param lunarDay 农历日 1..30，超出当月实际天数时钳到最后一天
 */
fun nextLunarAnniversary(
    today: LocalDate,
    lunarMonth: Int,
    lunarDay: Int,
): LocalDate {
    val todayLunarMonth = LunarDay.fromYmd(today.year, today.monthNumber, today.day).getLunarMonth()
    val todayLunarYear = todayLunarMonth.getLunarYear().year
    repeat(3) { offset ->
        val year = todayLunarYear + offset
        val dayCount = LunarMonth.fromYm(year, lunarMonth).getDayCount()
        val clampedDay = lunarDay.coerceAtMost(dayCount)
        val solar = LunarDay.fromYmd(year, lunarMonth, clampedDay).getSolarDay()
        val candidate = LocalDate(solar.year, solar.month, solar.day)
        if (candidate >= today) return candidate
    }
    // 农历年 +1 必然出现合法日期，理论不可达；兜底取最后一档换算结果
    val year = todayLunarYear + 3
    val dayCount = LunarMonth.fromYm(year, lunarMonth).getDayCount()
    val solar = LunarDay.fromYmd(year, lunarMonth, lunarDay.coerceAtMost(dayCount)).getSolarDay()
    return LocalDate(solar.year, solar.month, solar.day)
}

/**
 * 下一个恋爱里程碑：天数节点（100/365/520/1000/1314 天）与整周年（11-04）取最近者。
 *
 * 天数节点按「第 N 天」口径（第 365 天 = 起始日 + 364 天）；
 * 周年从 2 周年起算（1 周年由 365 天节点覆盖）。
 *
 * @param today 今天
 * @param start 在一起起始日
 */
fun nextMilestone(
    today: LocalDate,
    start: LocalDate = AnniversaryDates.TOGETHER,
): Milestone {
    val candidates =
        AnniversaryDates.DAY_MILESTONES.map { n ->
            Milestone(
                label = "$n 天",
                date = start.plus(DatePeriod(days = n - 1)),
                daysLeft = 0,
                isToday = false,
            )
        } + (2..100).map { years ->
            Milestone(
                label = "$years 周年",
                date = start.plus(DatePeriod(years = years)),
                daysLeft = 0,
                isToday = false,
            )
        }
    return candidates
        .filter { it.date >= today }
        .minBy { it.date }
        .let {
            Milestone(
                label = it.label,
                date = it.date,
                daysLeft = today.daysUntil(it.date),
                isToday = it.date == today,
            )
        }
}
