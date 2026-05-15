package plus.rua.project.ui

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** 无限分页中心页，用于 HorizontalPager 的起始位置 */
const val START_PAGE = Int.MAX_VALUE / 2

/** 折叠判定阈值：progress > 此值时折叠，< 此值时展开 */
const val COLLAPSE_THRESHOLD = 0.5f

/** 滑动偏移插值阈值：abs(offsetFraction) > 此值时启用插值 */
const val OFFSET_FRACTION_THRESHOLD = 0.01f

/** 行内 vertical padding (dp) */
const val ROW_PADDING_DP = 4

/** 日历网格水平 padding (dp) */
const val HORIZONTAL_PADDING_DP = 16

/** BottomCard 拖拽手势范围 (dp) */
const val DRAG_RANGE_DP = 200

/** 日历与 BottomCard 之间的间距 (dp)：展开时 */
const val CARD_GAP_EXPANDED_DP = 24

/** 日历与 BottomCard 之间的间距 (dp)：折叠时 */
const val CARD_GAP_COLLAPSED_DP = 12

/** 线性插值 */
fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

/**
 * 计算月份在日历网格中需要的行数（4/5/6）。
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
 */
fun calculateWeeksCountForPage(page: Int, today: LocalDate): Int {
    val initialYear = today.year
    val initialMonth = today.month.number
    val offset = page - START_PAGE
    val totalMonths = initialYear * 12 + (initialMonth - 1) + offset
    val year = totalMonths / 12
    val month = totalMonths % 12 + 1
    return calculateWeeksCount(year, month)
}

/**
 * 页码转年月。
 */
fun pageToYearMonth(page: Int, initialYear: Int, initialMonth: Int): Pair<Int, Int> {
    val offset = page - START_PAGE
    val totalMonths = initialYear * 12 + (initialMonth - 1) + offset
    return Pair(totalMonths / 12, totalMonths % 12 + 1)
}

/**
 * 年月转页码。
 */
fun yearMonthToPage(year: Int, month: Int, initialYear: Int, initialMonth: Int): Int {
    val targetTotal = year * 12 + (month - 1)
    val initialTotal = initialYear * 12 + (initialMonth - 1)
    return START_PAGE + (targetTotal - initialTotal)
}

/**
 * 获取日期所在周的周一。
 */
fun LocalDate.toWeekMonday(): LocalDate {
    val dayOfWeekOrdinal = dayOfWeek.ordinal
    return minus(DatePeriod(days = dayOfWeekOrdinal))
}

/**
 * 根据 pager 页码计算该页对应的周周一日期。
 */
fun pageToWeekMonday(page: Int, initial: LocalDate): LocalDate {
    val offset = page - START_PAGE
    return initial.plus(DatePeriod(days = offset * 7))
}
