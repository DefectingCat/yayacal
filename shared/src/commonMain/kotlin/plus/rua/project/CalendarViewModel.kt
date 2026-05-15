package plus.rua.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class CalendarDay(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean
)

/**
 * 日历状态管理，持有选中日期、折叠状态和 ISO 周号计算逻辑。
 *
 * @param coroutineScope 协程作用域，用于驱动折叠动画
 */
class CalendarViewModel(private val coroutineScope: CoroutineScope) {
    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    var selectedDate by mutableStateOf(today)
        private set

    var isCollapsed by mutableStateOf(false)
        private set

    // collapseProgress: 0f=展开(月视图), 1f=折叠(周视图)
    private val _collapseAnimatable = Animatable(0f)
    val collapseProgress: Float get() = _collapseAnimatable.value

    val currentYear: Int get() = selectedDate.year
    val currentMonth: Int get() = selectedDate.month.number

    fun selectDate(date: LocalDate) {
        selectedDate = date
    }

    fun onDrag(delta: Float) {
        coroutineScope.launch {
            val new = (_collapseAnimatable.value + delta).coerceIn(0f, 1f)
            _collapseAnimatable.snapTo(new)
        }
    }

    // 拖拽超过 50% 时自动折叠到周视图，否则回弹到月视图
    fun onDragEnd() {
        coroutineScope.launch {
            val current = _collapseAnimatable.value
            if (current > 0.5f) {
                _collapseAnimatable.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                )
                isCollapsed = true
            } else {
                _collapseAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                )
            }
        }
    }

    // 折叠状态下下拉恢复：delta 为负值（向下拖）推动 progress 向 0
    fun onExpandDrag(delta: Float) {
        coroutineScope.launch {
            val new = (_collapseAnimatable.value + delta).coerceIn(0f, 1f)
            _collapseAnimatable.snapTo(new)
        }
    }

    // 下拉超过 50% 时自动展开到月视图，否则回弹到周视图
    fun onExpandDragEnd() {
        coroutineScope.launch {
            val current = _collapseAnimatable.value
            if (current < 0.5f) {
                _collapseAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                )
                isCollapsed = false
            } else {
                _collapseAnimatable.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                )
            }
        }
    }

    /**
     * 计算给定日期的 ISO 8601 周号。
     *
     * @param date 目标日期
     * @return ISO 周号（1-53）
     */
    fun getIsoWeekNumber(date: LocalDate): Int {
        val jan4 = LocalDate(date.year, 1, 4)
        val jan4DayOfWeek = jan4.dayOfWeek.ordinal
        val week1Monday = jan4.minus(DatePeriod(days = jan4DayOfWeek))
        val diff = week1Monday.daysUntil(date)
        val weekNumber = diff / 7 + 1
        return if (weekNumber < 1) {
            getIsoWeekNumber(LocalDate(date.year - 1, 12, 28))
        } else if (weekNumber > getIsoWeeksInYear(date.year)) {
            1
        } else {
            weekNumber
        }
    }

    private fun getIsoWeeksInYear(year: Int): Int {
        val dec28 = LocalDate(year, 12, 28)
        val jan4 = LocalDate(year, 1, 4)
        val jan4DayOfWeek = jan4.dayOfWeek.ordinal
        val week1Monday = jan4.minus(DatePeriod(days = jan4DayOfWeek))
        val diff = week1Monday.daysUntil(dec28)
        return diff / 7 + 1
    }

    fun getMonthDays(year: Int, month: Int): List<CalendarDay> {
        val firstOfMonth = LocalDate(year, month, 1)
        val dayOfWeekOffset = firstOfMonth.dayOfWeek.ordinal
        val startDate = firstOfMonth.minus(DatePeriod(days = dayOfWeekOffset))
        val nextMonth = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        val daysInMonth = nextMonth.minus(DatePeriod(days = 1)).day
        val rows = ((dayOfWeekOffset + daysInMonth - 1) / 7) + 1
        val totalDays = rows * 7

        return (0 until totalDays).map { i ->
            val date = startDate.plus(DatePeriod(days = i))
            CalendarDay(
                date = date,
                isCurrentMonth = date.month.number == month && date.year == year,
                isToday = date == today,
                isSelected = date == selectedDate
            )
        }
    }
}
