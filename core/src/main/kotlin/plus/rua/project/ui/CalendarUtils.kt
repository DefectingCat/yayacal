package plus.rua.project.ui

import com.tyme.solar.SolarDay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** 无限分页中心页，用于 HorizontalPager 的起始位置 */
const val START_PAGE = Int.MAX_VALUE / 2

/** 折叠判定阈值：折叠时 progress > 此值触发，展开时 progress < (1-此值) 触发 */
const val COLLAPSE_THRESHOLD = 0.08f

/** 滑动偏移插值阈值：abs(offsetFraction) > 此值时启用插值 */
const val OFFSET_FRACTION_THRESHOLD = 0.01f

/** 行内 vertical padding (dp) */
const val ROW_PADDING_DP = 6

/** 日历网格水平 padding (dp) */
const val HORIZONTAL_PADDING_DP = 16

/** BottomCard 拖拽手势范围最小值 (dp)，防止行数少时 dragRange 过小 */
const val DRAG_RANGE_MIN_DP = 100

/** fling 速度阈值 (dp/s)，超过此速度按方向直接折叠/展开，不受 progress 阈值限制 */
const val FLING_VELOCITY_THRESHOLD_DP = 800

/** 日历与 BottomCard 之间的间距 (dp)：展开时 */
const val CARD_GAP_EXPANDED_DP = 24

/** 日历与 BottomCard 之间的间距 (dp)：折叠时 */
const val CARD_GAP_COLLAPSED_DP = 12

/** 线性插值 */
fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

/**
 * 计算月份在日历网格中需要的行数（4/5/6）。
 *
 * @param year 年份
 * @param month 月份（1-12）
 * @return 网格行数
 */
fun calculateWeeksCount(year: Int, month: Int): Int {
    val firstOfMonth = LocalDate(year, month, 1)
    val offset = firstOfMonth.dayOfWeek.ordinal
    val nextMonth = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    val daysInMonth = nextMonth.minus(DatePeriod(days = 1)).day
    return ((offset + daysInMonth - 1) / 7) + 1
}

/**
 * 根据 pager 页码计算该页月份的行数。
 *
 * @param page 分页器页码
 * @param today 今天的日期，用于确定起始月份
 * @return 网格行数
 */
fun calculateWeeksCountForPage(page: Int, today: LocalDate): Int {
    val initialYear = today.year

    @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
    val initialMonth = today.month.number
    val offset = page - START_PAGE
    val totalMonths = initialYear * 12 + (initialMonth - 1) + offset
    val year = totalMonths / 12
    val month = totalMonths % 12 + 1
    return calculateWeeksCount(year, month)
}

/**
 * 页码转年月。
 *
 * 中心页 (Int.MAX_VALUE/2) 对应起始月份，向左递减、向右递增，
 * 自动处理跨年（12月→1月）。
 *
 * @param page 分页器页码
 * @param initialYear 起始年份（中心页对应的年份）
 * @param initialMonth 起始月份（中心页对应的月份，1-12）
 * @return Pair(year, month)
 */
fun pageToYearMonth(page: Int, initialYear: Int, initialMonth: Int): Pair<Int, Int> {
    val offset = page - START_PAGE
    val totalMonths = initialYear * 12 + (initialMonth - 1) + offset
    return Pair(totalMonths / 12, totalMonths % 12 + 1)
}

/**
 * 年月转页码。
 *
 * [pageToYearMonth] 的逆运算，用于点击跨月日期时定位目标页。
 *
 * @param year 目标年份
 * @param month 目标月份（1-12）
 * @param initialYear 起始年份
 * @param initialMonth 起始月份
 * @return 分页器页码
 */
fun yearMonthToPage(year: Int, month: Int, initialYear: Int, initialMonth: Int): Int {
    val targetTotal = year * 12 + (month - 1)
    val initialTotal = initialYear * 12 + (initialMonth - 1)
    return START_PAGE + (targetTotal - initialTotal)
}

/**
 * 获取日期所在周的周一。
 *
 * ISO 8601 周从周一开始。周一返回自身，其他日期回退到该周周一。
 *
 * @receiver 目标日期
 * @return 该日期所在周的周一
 */
fun LocalDate.toWeekMonday(): LocalDate {
    val dayOfWeekOrdinal = dayOfWeek.ordinal
    return minus(DatePeriod(days = dayOfWeekOrdinal))
}

/**
 * 根据 pager 页码计算该页对应的周周一日期。
 *
 * 中心页对应参考周一，向左/右每页偏移一周。用于 WeekPager 的单周视图渲染。
 *
 * @param page 分页器页码
 * @param initial 参考周一日期（中心页对应的周一）
 * @return 该页周一的 LocalDate
 */
fun pageToWeekMonday(page: Int, initial: LocalDate): LocalDate {
    val offset = page - START_PAGE
    return initial.plus(DatePeriod(days = offset * 7))
}

/**
 * 计算选中日期相对于今天的天数描述。
 *
 * 例如今天 19 日，选中 18 日返回"昨天"，17 日返回"2天前"，
 * 20 日返回"明天"，21 日返回"2天后"，选中当天返回"今天"。
 *
 * @param selectedDate 选中日期
 * @param today 今天日期
 * @return 相对天数描述
 */
fun relativeDayDescription(selectedDate: LocalDate, today: LocalDate): String {
    val diff = today.daysUntil(selectedDate)
    return when {
        diff == 0 -> "今天"
        diff == -1 -> "昨天"
        diff == 1 -> "明天"
        diff < 0 -> "${-diff}天前"
        else -> "${diff}天后"
    }
}

/**
 * 将公历日期格式化为农历日期字符串。
 *
 * 格式为"农历{月}{日}"，例如"农历四月初三"。
 *
 * @param date 公历日期
 * @return 农历日期描述
 */
@Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
fun formatLunarDate(date: LocalDate): String {
    val solarDay = SolarDay.fromYmd(date.year, date.monthNumber, date.day)
    val lunarDay = solarDay.getLunarDay()
    val lunarMonth = lunarDay.getLunarMonth()
    return "农历${lunarMonth.getName()}${lunarDay.getName()}"
}
