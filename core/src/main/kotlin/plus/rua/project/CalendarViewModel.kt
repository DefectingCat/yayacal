package plus.rua.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import plus.rua.project.ui.COLLAPSE_THRESHOLD
import plus.rua.project.ui.getMonthGridInfo
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
 * 日历 UI 状态聚合，用于减少 Compose 重组次数。
 *
 * 将多个独立的 StateFlow 合并为单一状态流，
 * 避免 `collectAsState()` 分散订阅导致的重复重组。
 */
data class CalendarUiState(
    val selectedDate: LocalDate,
    val isCollapsed: Boolean,
    val isYearView: Boolean,
    val yearViewYear: Int,
    val collapseProgress: Float,
    val showLegalHoliday: Boolean
)

/**
 * 将六个 [Flow] 合并为一个 [Flow]，使用 [transform] 处理最新值。
 *
 * kotlinx-coroutines 1.11 仅内置到 5 参数的 [combine] 重载，
 * 此扩展用于扁平化 6 个 StateFlow 的合并，避免多层嵌套产生的中间流。
 */
private inline fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): Flow<R> = combine(
    combine(flow, flow2, flow3, flow4, flow5) { t1, t2, t3, t4, t5 ->
        Quintuple(t1, t2, t3, t4, t5)
    },
    flow6
) { quintuple, t6 ->
    transform(quintuple.first, quintuple.second, quintuple.third, quintuple.fourth, quintuple.fifth, t6)
}

private data class Quintuple<T1, T2, T3, T4, T5>(
    val first: T1,
    val second: T2,
    val third: T3,
    val fourth: T4,
    val fifth: T5
)

/**
 * 日历状态管理，持有选中日期、折叠状态和 ISO 周号计算逻辑。
 *
 * @param clock 时钟源，默认系统时钟；测试时可注入固定时钟
 */
class CalendarViewModel(
    private val clock: Clock = Clock.System
) : ViewModel() {
    private val today: LocalDate = clock.todayIn(TimeZone.currentSystemDefault())

    init {
        // 预计算当前月前后各 1 个月（在协程中异步执行）
        val currentYear = today.year
        val currentMonth = today.month.number

        @Suppress("DEPRECATION") // monthNumber 无替代 API
        val monthsToPrecompute = listOf(
            currentMonth - 1 to currentYear,
            currentMonth to currentYear,
            currentMonth + 1 to currentYear
        ).map { (month, year) ->
            val (normalizedMonth, normalizedYear) = when {
                month < 1 -> 12 to year - 1
                month > 12 -> 1 to year + 1
                else -> month to year
            }
            getMonthGridInfo(normalizedYear, normalizedMonth)
        }

        viewModelScope.launch {
            monthsToPrecompute.forEach { info ->
                val dates = (0 until info.totalDays).map { i ->
                    info.startDate.plus(DatePeriod(days = i))
                }
                LunarCache.default.precompute(dates)
            }
        }
    }

    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _isCollapsed = MutableStateFlow(false)
    val isCollapsed: StateFlow<Boolean> = _isCollapsed.asStateFlow()

    // collapseProgress: 0f=展开(月视图), 1f=折叠(周视图)
    private val _collapseProgress = MutableStateFlow(0f)
    val collapseProgress: StateFlow<Float> = _collapseProgress.asStateFlow()

    @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
    val currentMonth: Int get() = selectedDate.value.month.number

    val currentYear: Int get() = selectedDate.value.year

    private val _isYearView = MutableStateFlow(false)
    val isYearView: StateFlow<Boolean> = _isYearView.asStateFlow()

    private val _yearViewProgress = MutableStateFlow(0f)
    val yearViewProgress: StateFlow<Float> = _yearViewProgress.asStateFlow()

    private val _yearViewYear = MutableStateFlow(today.year)
    val yearViewYear: StateFlow<Int> = _yearViewYear.asStateFlow()

    /**
     * 个人轮班。与法定节假日完全独立,不受调休影响。
     * MVP 默认:2026-05-15 起,2 班 2 休循环。后续接入设置页与持久化。
     */
    private val _shiftPattern = MutableStateFlow<ShiftPattern?>(
        ShiftPattern(
            anchorDate = LocalDate(2026, 5, 15),
            cycle = listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF)
        )
    )
    val shiftPattern: StateFlow<ShiftPattern?> = _shiftPattern.asStateFlow()

    fun shiftKindAt(date: LocalDate): ShiftKind? = shiftPattern.value?.kindAt(date)

    /**
     * 是否在右上角显示法定调休角标。默认禁用,此时右上角让位给个人排班。
     * 开启后回到旧版布局:左上角=排班,右上角=法定调休。后续接入设置页持久化。
     */
    private val _showLegalHoliday = MutableStateFlow(false)
    val showLegalHoliday: StateFlow<Boolean> = _showLegalHoliday.asStateFlow()

    /** 聚合 UI 状态，减少 Compose 层分散订阅导致的重组。 */
    val uiState: StateFlow<CalendarUiState> = combine(
        _selectedDate,
        _isCollapsed,
        _isYearView,
        _yearViewYear,
        _collapseProgress,
        _showLegalHoliday
    ) { selectedDate, isCollapsed, isYearView, yearViewYear, collapseProgress, showLegalHoliday ->
        CalendarUiState(
            selectedDate = selectedDate,
            isCollapsed = isCollapsed,
            isYearView = isYearView,
            yearViewYear = yearViewYear,
            collapseProgress = collapseProgress,
            showLegalHoliday = showLegalHoliday
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CalendarUiState(today, false, false, today.year, 0f, false)
    )

    /**
     * 选中指定日期。
     *
     * @param date 目标日期
     */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /**
     * 切换年视图。折叠态下保持折叠（`isCollapsed` 不变），
     * 月视图层以折叠形态参与缩放转场；从年视图返回时仍是周视图。
     *
     * 切换瞬间立即翻转 isYearView，让对应方向的目标视图立刻接管渲染，
     * 当前视图被直接移除；动画只作用在目标视图的 scale/alpha 上。
     */
    fun toggleYearView() {
        if (_isYearView.value) {
            composeTraceBeginSection("YearView→MonthView")
            _yearViewProgress.value = 0f
            _isYearView.value = false
            composeTraceEndSection()
        } else {
            composeTraceBeginSection("MonthView→YearView")
            _yearViewYear.value = _selectedDate.value.year
            _yearViewProgress.value = 1f
            _isYearView.value = true
            composeTraceEndSection()
        }
    }

    /**
     * 切换法定调休角标显示。
     */
    fun toggleShowLegalHoliday() {
        _showLegalHoliday.value = !_showLegalHoliday.value
    }

    /**
     * 从年视图选择月份后返回月视图。
     */
    @Suppress("DEPRECATION") // monthNumber 无替代 API
    fun selectMonthFromYearView(month: Int) {
        composeTraceBeginSection("YearView:SelectMonth")
        val date = if (_yearViewYear.value == today.year && today.month.number == month) today
        else LocalDate(_yearViewYear.value, month, 1)
        _selectedDate.value = date
        _isYearView.value = false
        _yearViewProgress.value = 0f
        composeTraceEndSection()
    }

    fun incrementYear() {
        _yearViewYear.value = _yearViewYear.value + 1
    }

    fun decrementYear() {
        _yearViewYear.value = _yearViewYear.value - 1
    }

    fun setYearViewYear(year: Int) {
        _yearViewYear.value = year
    }

    /**
     * 展开状态下拖拽折叠，delta 正值推动 progress 向 1（折叠方向）。
     *
     * @param delta 拖拽增量，已归一化到 [0,1] 区间
     */
    fun onDrag(delta: Float) {
        _collapseProgress.value = (_collapseProgress.value + delta).coerceIn(0f, 1f)
    }

    /**
     * 展开状态拖拽结束，根据进度决定折叠或回弹。
     *
     * 拖拽超过阈值时自动折叠到周视图，否则回弹到月视图。
     */
    fun onDragEnd() {
        val progress = _collapseProgress.value
        if (progress > COLLAPSE_THRESHOLD) {
            _isCollapsed.value = true
            _collapseProgress.value = 1f
        } else {
            _isCollapsed.value = false
            _collapseProgress.value = 0f
        }
    }

    /**
     * 折叠状态下拉恢复，delta 为负值（向下拖）推动 progress 向 0。
     *
     * @param delta 拖拽增量，已归一化到 [0,1] 区间
     */
    fun onExpandDrag(delta: Float) {
        _collapseProgress.value = (_collapseProgress.value + delta).coerceIn(0f, 1f)
    }

    /**
     * 折叠状态拖拽结束，根据进度决定展开或回弹。
     *
     * 下拉超过阈值时自动展开到月视图，否则回弹到周视图。
     */
    fun onExpandDragEnd() {
        val progress = _collapseProgress.value
        if (progress < (1 - COLLAPSE_THRESHOLD)) {
            _isCollapsed.value = false
            _collapseProgress.value = 0f
        } else {
            _isCollapsed.value = true
            _collapseProgress.value = 1f
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
        val info = getMonthGridInfo(year, month)
        val result = (0 until info.totalDays).map { i ->
            val date = info.startDate.plus(DatePeriod(days = i))
            CalendarDay(
                date = date,
                isCurrentMonth = date.month.number == month && date.year == year,
                isToday = date == today,
                isSelected = date == selectedDate.value
            )
        }
        composeTraceEndSection()
        return result
    }
}
