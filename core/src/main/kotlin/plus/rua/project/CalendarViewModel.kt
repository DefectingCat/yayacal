package plus.rua.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import plus.rua.project.ui.COLLAPSE_THRESHOLD
import plus.rua.project.ui.FLING_VELOCITY_THRESHOLD_DP
import kotlin.time.Clock

/**
 * 日历日期数据，用于网格单元格渲染。
 *
 * @param date 日期
 * @param isCurrentMonth 是否属于当前显示月份
 * @param isToday 是否为今天
 * @param isSelected 是否为选中日期
 */
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
 * @param clock 时钟源，默认系统时钟；测试时可注入固定时钟
 */
class CalendarViewModel(
    private val coroutineScope: CoroutineScope,
    private val clock: Clock = Clock.System
) {
    private val today: LocalDate = clock.todayIn(TimeZone.currentSystemDefault())

    var selectedDate by mutableStateOf(today)
        private set

    var isCollapsed by mutableStateOf(false)
        private set

    // collapseProgress: 0f=展开(月视图), 1f=折叠(周视图)
    private val _collapseAnimatable = Animatable(0f)
    val collapseProgress: Float get() = _collapseAnimatable.value

    private var yearViewJob: Job? = null

    @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
    val currentMonth: Int get() = selectedDate.month.number

    val currentYear: Int get() = selectedDate.year

    var isYearView by mutableStateOf(false)
        private set

    private val _yearViewAnimatable = Animatable(0f)
    val yearViewProgress: Float get() = _yearViewAnimatable.value

    @Suppress("DEPRECATION") // monthNumber 无替代 API
    var yearViewYear by mutableStateOf(today.year)
        internal set

    /**
     * 个人轮班。与法定节假日完全独立,不受调休影响。
     * MVP 默认:2026-05-15 起,2 班 2 休循环。后续接入设置页与持久化。
     */
    var shiftPattern: ShiftPattern? by mutableStateOf(
        ShiftPattern(
            anchorDate = LocalDate(2026, 5, 15),
            cycle = listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF)
        )
    )

    fun shiftKindAt(date: LocalDate): ShiftKind? = shiftPattern?.kindAt(date)

    /**
     * 是否在右上角显示法定调休角标。默认禁用,此时右上角让位给个人排班。
     * 开启后回到旧版布局:左上角=排班,右上角=法定调休。后续接入设置页持久化。
     */
    var showLegalHoliday by mutableStateOf(false)

    /**
     * 选中指定日期。
     *
     * @param date 目标日期
     */
    fun selectDate(date: LocalDate) {
        selectedDate = date
    }

    /**
     * 切换年视图。折叠态下保持折叠（`isCollapsed` 不变），
     * 月视图层以折叠形态参与缩放转场；从年视图返回时仍是周视图。
     *
     * 切换瞬间立即翻转 isYearView，让对应方向的目标视图立刻接管渲染，
     * 当前视图被直接移除；动画只作用在目标视图的 scale/alpha 上。
     */
    fun toggleYearView() {
        yearViewJob?.cancel()
        yearViewJob = coroutineScope.launch {
            if (isYearView) {
                // 年 → 月：先启动动画（年视图开始淡出），等一帧后翻转 isYearView（月视图开始组合）
                composeTraceBeginSection("YearView→MonthView")
                _yearViewAnimatable.snapTo(1f)
                val animJob = launch {
                    _yearViewAnimatable.animateTo(
                        0f, tween(400, easing = FastOutSlowInEasing)
                    )
                }
                withFrameNanos { }
                isYearView = false
                animJob.join()
                composeTraceEndSection()
            } else {
                // 月 → 年：如果折叠，先展开
                if (isCollapsed) {
                    _collapseAnimatable.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                    )
                    isCollapsed = false
                }
                // 先启动动画（月视图开始缩小），等一帧后翻转 isYearView（年视图开始组合）
                composeTraceBeginSection("MonthView→YearView")
                yearViewYear = selectedDate.year
                _yearViewAnimatable.snapTo(0f)
                val animJob = launch {
                    _yearViewAnimatable.animateTo(
                        1f, tween(400, easing = FastOutSlowInEasing)
                    )
                }
                withFrameNanos { }
                isYearView = true
                animJob.join()
                composeTraceEndSection()
            }
        }
    }

    /**
     * 从年视图选择月份后返回月视图。
     */
    @Suppress("DEPRECATION") // monthNumber 无替代 API
    fun selectMonthFromYearView(month: Int) {
        composeTraceBeginSection("YearView:SelectMonth")
        val date = if (yearViewYear == today.year && today.month.number == month) today
        else LocalDate(yearViewYear, month, 1)
        selectedDate = date
        isYearView = false
        yearViewJob?.cancel()
        yearViewJob = coroutineScope.launch {
            withFrameNanos { }
            _yearViewAnimatable.animateTo(
                0f, tween(400, easing = FastOutSlowInEasing)
            )
            composeTraceEndSection()
        }
    }

    fun incrementYear() {
        yearViewYear++
    }

    fun decrementYear() {
        yearViewYear--
    }

    /**
     * 展开状态下拖拽折叠，delta 正值推动 progress 向 1（折叠方向）。
     *
     * @param delta 拖拽增量，已归一化到 [0,1] 区间
     */
    fun onDrag(delta: Float) {
        coroutineScope.launch {
            val old = _collapseAnimatable.value
            val new = (old + delta).coerceIn(0f, 1f)
            _collapseAnimatable.snapTo(new)
        }
    }

    /**
     * 展开状态拖拽结束，根据进度和速度决定折叠或回弹。
     *
     * 拖拽超过阈值时自动折叠到周视图，否则回弹到月视图。
     *
     * @param velocityDpPerSec 松手时的 fling 速度 (dp/s)，正值=上滑（折叠方向），负值=下滑（展开方向）
     */
    fun onDragEnd(velocityDpPerSec: Float = 0f) {
        coroutineScope.launch {
            val progress = _collapseAnimatable.value
            val shouldCollapse = progress > 0.3f
            if (shouldCollapse) {
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

    /**
     * 折叠状态下下拉恢复，delta 为负值（向下拖）推动 progress 向 0。
     *
     * @param delta 拖拽增量，已归一化到 [0,1] 区间
     */
    fun onExpandDrag(delta: Float) {
        coroutineScope.launch {
            val old = _collapseAnimatable.value
            val new = (old + delta).coerceIn(0f, 1f)
            _collapseAnimatable.snapTo(new)
        }
    }

    /**
     * 折叠状态拖拽结束，根据进度和速度决定展开或回弹。
     *
     * 下拉超过阈值时自动展开到月视图，否则回弹到周视图。
     *
     * @param velocityDpPerSec 松手时的 fling 速度 (dp/s)，正值=上滑，负值=下滑
     */
    fun onExpandDragEnd(velocityDpPerSec: Float = 0f) {
        coroutineScope.launch {
            val progress = _collapseAnimatable.value
            val shouldExpand = progress < 0.7f
            if (shouldExpand) {
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

    /**
     * 计算给定年月的日历网格数据，包含跨月填充至完整行。
     *
     * 网格行数按实际需要计算（4/5/6行），每行7格，首行从该月1号所在周的周一开始。
     *
     * @param year 年份
     * @param month 月份（1-12）
     * @return 日历网格列表，每项包含日期、是否当月、是否今天、是否选中
     */
    @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
    fun getMonthDays(year: Int, month: Int): List<CalendarDay> {
        composeTraceBeginSection("getMonthDays:$year-$month")
        val firstOfMonth = LocalDate(year, month, 1)
        val dayOfWeekOffset = firstOfMonth.dayOfWeek.ordinal
        val startDate = firstOfMonth.minus(DatePeriod(days = dayOfWeekOffset))
        val nextMonth =
            if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        val daysInMonth = nextMonth.minus(DatePeriod(days = 1)).day
        val rows = ((dayOfWeekOffset + daysInMonth - 1) / 7) + 1
        val totalDays = rows * 7

        val result = (0 until totalDays).map { i ->
            val date = startDate.plus(DatePeriod(days = i))
            CalendarDay(
                date = date,
                isCurrentMonth = date.month.number == month && date.year == year,
                isToday = date == today,
                isSelected = date == selectedDate
            )
        }
        composeTraceEndSection()
        return result
    }
}