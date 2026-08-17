package plus.rua.project

import com.tyme.lunar.LunarDay
import com.tyme.lunar.LunarMonth
import com.tyme.solar.SolarDay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
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

    /** 玫瑰日：每年 10 月 16 日。 */
    const val ROSE_DAY_MONTH = 10

    /** 玫瑰日日期。 */
    const val ROSE_DAY_DAY = 16

    /** 农历七夕节：七月初七。 */
    const val QIXI_LUNAR_MONTH = 7

    /** 七夕节农历日。 */
    const val QIXI_LUNAR_DAY = 7
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
    val todayLunarMonth = LunarDay.fromYmd(today.year, today.month.number, today.day).getLunarMonth()
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

/**
 * 纪念日期的文化与天文详情信息（用于纪念日期展示页）。
 *
 * @param solarDateText 公历日期字符串，如「2025 年 11 月 04 日」
 * @param weekdayText 星期几，如「星期二」
 * @param lunarGanzhiText 农历完整文案（含干支），如「农历乙巳年九月十五」
 * @param lunarMonthDayText 农历月日简写，如「九月十五」
 * @param constellationText 星座带符号，如「天蝎座 ♏」
 * @param solarTermText 节气名，如「霜降」
 */
data class DateDetailInfo(
    val solarDateText: String,
    val weekdayText: String,
    val lunarGanzhiText: String,
    val lunarMonthDayText: String,
    val constellationText: String,
    val solarTermText: String,
)

/**
 * 下一次纪念日/生日的预告信息。
 *
 * @param targetSolarDate 下一次发生时的公历日期
 * @param daysLeft 距今天数（0 为当天）
 * @param isToday 是否恰为今天
 * @param targetSolarFormatted 下一次公历格式化字符串，如「2026年9月4日 星期五」
 * @param targetLunarFormatted 下一次对应的农历文本，如「农历丙午年七月廿三」
 */
data class UpcomingAnniversaryInfo(
    val targetSolarDate: LocalDate,
    val daysLeft: Int,
    val isToday: Boolean,
    val targetSolarFormatted: String,
    val targetLunarFormatted: String,
)

/**
 * 恋爱里程碑进度项。
 *
 * @param label 里程碑标签，如「100 天」「1 周年 (365天)」
 * @param tagline 浪漫寄语，如「初心相伴」「岁岁同行」
 * @param targetDate 达成日期
 * @param isPassed 是否已达成
 * @param isToday 是否就是今天
 * @param daysLeft 距今天数（未达成为正数，已达成为 0）
 */
data class MilestoneProgressInfo(
    val label: String,
    val tagline: String,
    val targetDate: LocalDate,
    val isPassed: Boolean,
    val isToday: Boolean,
    val daysLeft: Int,
)

/**
 * 将公历日期转换为带有 emoji 符号的星座名称。
 */
fun getConstellationWithSymbol(date: LocalDate): String {
    val solarDay = SolarDay.fromYmd(date.year, date.month.number, date.day)
    val name = solarDay.getConstellation().getName()
    val symbol =
        when (name) {
            "白羊" -> "♈"
            "金牛" -> "♉"
            "双子" -> "♊"
            "巨蟹" -> "♋"
            "狮子" -> "♌"
            "处女" -> "♍"
            "天秤" -> "♎"
            "天蝎" -> "♏"
            "射手" -> "♐"
            "摩羯" -> "♑"
            "水瓶" -> "♒"
            "双鱼" -> "♓"
            else -> ""
        }
    return "${name}座 $symbol".trim()
}

/**
 * 获取日期的中文星期名称（如「星期二」）。
 */
fun getChineseWeekday(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "星期一"
    DayOfWeek.TUESDAY -> "星期二"
    DayOfWeek.WEDNESDAY -> "星期三"
    DayOfWeek.THURSDAY -> "星期四"
    DayOfWeek.FRIDAY -> "星期五"
    DayOfWeek.SATURDAY -> "星期六"
    DayOfWeek.SUNDAY -> "星期日"
}

/**
 * 格式化农历干支年月日文本（如「农历乙巳年九月十五」）。
 */
fun formatGanzhiLunarDate(date: LocalDate): String {
    val solarDay = SolarDay.fromYmd(date.year, date.month.number, date.day)
    val lunarDay = solarDay.getLunarDay()
    val lunarMonth = lunarDay.getLunarMonth()
    val sixtyCycleYear = lunarMonth.getLunarYear().getSixtyCycle().getName()
    val leapPrefix = if (lunarMonth.isLeap()) "闰" else ""
    return "农历${sixtyCycleYear}年${leapPrefix}${lunarMonth.getName()}${lunarDay.getName()}"
}

/**
 * 获取指定公历日期的详细文化/天文属性。
 */
fun getDateDetailInfo(date: LocalDate): DateDetailInfo {
    val solarDay = SolarDay.fromYmd(date.year, date.month.number, date.day)
    val lunarDay = solarDay.getLunarDay()
    val lunarMonth = lunarDay.getLunarMonth()
    val sixtyCycleYear = lunarMonth.getLunarYear().getSixtyCycle().getName()
    val leapPrefix = if (lunarMonth.isLeap()) "闰" else ""
    val lunarGanzhi = "农历${sixtyCycleYear}年${leapPrefix}${lunarMonth.getName()}${lunarDay.getName()}"
    val lunarMonthDay = "${leapPrefix}${lunarMonth.getName()}${lunarDay.getName()}"
    val constellation = getConstellationWithSymbol(date)
    val term = solarDay.getTerm().getName()
    val weekday = getChineseWeekday(date)
    val solarText = "${date.year} 年 ${date.month.number} 月 ${date.day} 日"

    return DateDetailInfo(
        solarDateText = solarText,
        weekdayText = weekday,
        lunarGanzhiText = lunarGanzhi,
        lunarMonthDayText = lunarMonthDay,
        constellationText = constellation,
        solarTermText = term,
    )
}

/**
 * 阳历纪念日的下一次预告计算。
 */
fun getUpcomingSolarAnniversaryInfo(
    today: LocalDate,
    month: Int,
    day: Int,
): UpcomingAnniversaryInfo {
    val target = nextSolarAnniversary(today, month, day)
    val daysLeft = today.daysUntil(target)
    val weekday = getChineseWeekday(target)
    val solarText = "${target.year} 年 ${target.month.number} 月 ${target.day} 日 $weekday"
    val lunarText = formatGanzhiLunarDate(target)

    return UpcomingAnniversaryInfo(
        targetSolarDate = target,
        daysLeft = daysLeft,
        isToday = target == today,
        targetSolarFormatted = solarText,
        targetLunarFormatted = lunarText,
    )
}

/**
 * 农历纪念日（如农历生日、七夕）的下一次预告计算。
 */
fun getUpcomingLunarAnniversaryInfo(
    today: LocalDate,
    lunarMonth: Int,
    lunarDay: Int,
): UpcomingAnniversaryInfo {
    val target = nextLunarAnniversary(today, lunarMonth, lunarDay)
    val daysLeft = today.daysUntil(target)
    val weekday = getChineseWeekday(target)
    val solarText = "${target.year} 年 ${target.month.number} 月 ${target.day} 日 $weekday"
    val lunarText = formatGanzhiLunarDate(target)

    return UpcomingAnniversaryInfo(
        targetSolarDate = target,
        daysLeft = daysLeft,
        isToday = target == today,
        targetSolarFormatted = solarText,
        targetLunarFormatted = lunarText,
    )
}

/**
 * 获取恋爱全里程碑进度列表。
 */
fun getAllMilestoneProgress(
    today: LocalDate,
    start: LocalDate = AnniversaryDates.TOGETHER,
): List<MilestoneProgressInfo> {
    val milestones =
        listOf(
            Triple("100 天", "初心相伴", start.plus(DatePeriod(days = 99))),
            Triple("365 天 (1周年)", "四季流转", start.plus(DatePeriod(days = 364))),
            Triple("520 天", "热烈相爱", start.plus(DatePeriod(days = 519))),
            Triple("1000 天", "千日相守", start.plus(DatePeriod(days = 999))),
            Triple("1314 天", "一生一世", start.plus(DatePeriod(days = 1313))),
        )
    return milestones.map { (label, tagline, date) ->
        val isPassed = date < today
        val isToday = date == today
        val daysLeft = if (date >= today) today.daysUntil(date) else 0
        MilestoneProgressInfo(
            label = label,
            tagline = tagline,
            targetDate = date,
            isPassed = isPassed,
            isToday = isToday,
            daysLeft = daysLeft,
        )
    }
}
