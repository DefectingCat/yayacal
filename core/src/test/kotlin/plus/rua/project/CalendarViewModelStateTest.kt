package plus.rua.project

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class StateTestFixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/**
 * 覆盖 [CalendarViewModel] 中与日期选择、年视图、班次、拖拽 progress 等
 * 同步可观察状态相关的逻辑。
 */
class CalendarViewModelStateTest {

    // 固定 today = 2026/5/15
    private val fixedInstant = Instant.parse("2026-05-15T00:00:00Z")
    private val testClock = StateTestFixedClock(fixedInstant)

    private fun createViewModel(): CalendarViewModel {
        return CalendarViewModel(clock = testClock)
    }

    // ---- 初始状态 ----

    @Test
    fun init_selectedDateIsToday() {
        val vm = createViewModel()
        assertEquals(LocalDate(2026, 5, 15), vm.selectedDate.value)
    }

    @Test
    fun init_isCollapsedDefaultsFalse() {
        assertFalse(createViewModel().isCollapsed.value)
    }

    @Test
    fun init_collapseProgressDefaultsZero() {
        assertEquals(0f, createViewModel().collapseProgress.value, 0.001f)
    }

    @Test
    fun init_isYearViewDefaultsFalse() {
        assertFalse(createViewModel().isYearView.value)
    }

    @Test
    fun init_yearViewProgressDefaultsZero() {
        assertEquals(0f, createViewModel().yearViewProgress.value, 0.001f)
    }

    @Test
    fun init_yearViewYearDefaultsToTodayYear() {
        assertEquals(2026, createViewModel().yearViewYear.value)
    }

    @Test
    fun init_showLegalHolidayDefaultsFalse() {
        assertFalse(createViewModel().showLegalHoliday.value)
    }

    @Test
    fun init_shiftPatternHasDefault() {
        val pattern = createViewModel().shiftPattern.value
        assertNotNull(pattern)
        assertEquals(LocalDate(2026, 5, 15), pattern.anchorDate)
        assertEquals(4, pattern.cycle.size)
    }

    @Test
    fun init_currentMonthMatchesToday() {
        assertEquals(5, createViewModel().currentMonth)
    }

    @Test
    fun init_currentYearMatchesToday() {
        assertEquals(2026, createViewModel().currentYear)
    }

    // ---- selectDate ----

    @Test
    fun selectDate_updatesSelectedDate() {
        val vm = createViewModel()
        vm.selectDate(LocalDate(2026, 6, 1))
        assertEquals(LocalDate(2026, 6, 1), vm.selectedDate.value)
    }

    @Test
    fun selectDate_currentMonthFollowsSelection() {
        val vm = createViewModel()
        vm.selectDate(LocalDate(2026, 8, 20))
        assertEquals(8, vm.currentMonth)
        assertEquals(2026, vm.currentYear)
    }

    @Test
    fun selectDate_yearFollowsSelection() {
        val vm = createViewModel()
        vm.selectDate(LocalDate(2027, 1, 1))
        assertEquals(2027, vm.currentYear)
        assertEquals(1, vm.currentMonth)
    }

    @Test
    fun selectDate_pastDate_updatesCorrectly() {
        val vm = createViewModel()
        vm.selectDate(LocalDate(2020, 12, 31))
        assertEquals(LocalDate(2020, 12, 31), vm.selectedDate.value)
        assertEquals(12, vm.currentMonth)
        assertEquals(2020, vm.currentYear)
    }

    // ---- incrementYear / decrementYear ----

    @Test
    fun incrementYear_increasesYearViewYear() {
        val vm = createViewModel()
        vm.incrementYear()
        assertEquals(2027, vm.yearViewYear.value)
    }

    @Test
    fun decrementYear_decreasesYearViewYear() {
        val vm = createViewModel()
        vm.decrementYear()
        assertEquals(2025, vm.yearViewYear.value)
    }

    @Test
    fun incrementDecrementYear_consecutiveCalls() {
        val vm = createViewModel()
        repeat(5) { vm.incrementYear() }
        assertEquals(2031, vm.yearViewYear.value)
        repeat(3) { vm.decrementYear() }
        assertEquals(2028, vm.yearViewYear.value)
    }

    @Test
    fun incrementYear_doesNotAffectSelectedDate() {
        val vm = createViewModel()
        val before = vm.selectedDate.value
        vm.incrementYear()
        assertEquals(before, vm.selectedDate.value)
    }

    // ---- selectMonthFromYearView ----

    @Test
    fun selectMonthFromYearView_sameYearOtherMonth_setsFirstDayOfMonth() {
        val vm = createViewModel()
        vm.selectMonthFromYearView(8)
        assertEquals(LocalDate(2026, 8, 1), vm.selectedDate.value)
    }

    @Test
    fun selectMonthFromYearView_currentYearAndMonth_setsToToday() {
        val vm = createViewModel()
        // yearViewYear = 2026, today.month = 5
        vm.selectMonthFromYearView(5)
        assertEquals(LocalDate(2026, 5, 15), vm.selectedDate.value)
    }

    @Test
    fun selectMonthFromYearView_otherYear_setsFirstDay() {
        val vm = createViewModel()
        vm.incrementYear() // yearViewYear = 2027
        vm.selectMonthFromYearView(5)
        assertEquals(LocalDate(2027, 5, 1), vm.selectedDate.value)
    }

    @Test
    fun selectMonthFromYearView_setsIsYearViewFalse() {
        val vm = createViewModel()
        vm.selectMonthFromYearView(3)
        assertFalse(vm.isYearView.value)
    }

    @Test
    fun selectMonthFromYearView_january() {
        val vm = createViewModel()
        vm.selectMonthFromYearView(1)
        assertEquals(LocalDate(2026, 1, 1), vm.selectedDate.value)
    }

    @Test
    fun selectMonthFromYearView_december() {
        val vm = createViewModel()
        vm.selectMonthFromYearView(12)
        assertEquals(LocalDate(2026, 12, 1), vm.selectedDate.value)
    }

    // ---- shiftKindAt ----

    @Test
    fun shiftKindAt_anchorDate_returnsWork() {
        // default pattern: anchor 2026-05-15, cycle WORK/WORK/OFF/OFF
        val vm = createViewModel()
        assertEquals(ShiftKind.WORK, vm.shiftKindAt(LocalDate(2026, 5, 15)))
    }

    @Test
    fun shiftKindAt_dayAfterAnchor_returnsWork() {
        val vm = createViewModel()
        assertEquals(ShiftKind.WORK, vm.shiftKindAt(LocalDate(2026, 5, 16)))
    }

    @Test
    fun shiftKindAt_twoDaysAfterAnchor_returnsOff() {
        val vm = createViewModel()
        assertEquals(ShiftKind.OFF, vm.shiftKindAt(LocalDate(2026, 5, 17)))
    }

    // ---- onDrag: 折叠拖拽（同步路径，直接修改 StateFlow）----

    @Test
    fun onDrag_positiveDelta_increasesProgress() {
        val vm = createViewModel()
        vm.onDrag(0.3f)
        assertEquals(0.3f, vm.collapseProgress.value, 0.001f)
    }

    @Test
    fun onDrag_accumulatesAcrossCalls() {
        val vm = createViewModel()
        vm.onDrag(0.2f)
        vm.onDrag(0.3f)
        assertEquals(0.5f, vm.collapseProgress.value, 0.001f)
    }

    @Test
    fun onDrag_clampsAtOne() {
        val vm = createViewModel()
        vm.onDrag(0.8f)
        vm.onDrag(0.8f)
        assertEquals(1f, vm.collapseProgress.value, 0.001f)
    }

    @Test
    fun onDrag_clampsAtZeroWhenNegativeFromZero() {
        val vm = createViewModel()
        vm.onDrag(-0.3f)
        assertEquals(0f, vm.collapseProgress.value, 0.001f)
    }

    @Test
    fun onDrag_negativeAfterPositive_canDecrease() {
        val vm = createViewModel()
        vm.onDrag(0.5f)
        vm.onDrag(-0.2f)
        assertEquals(0.3f, vm.collapseProgress.value, 0.001f)
    }

    // ---- onExpandDrag: 展开拖拽 ----

    @Test
    fun onExpandDrag_updatesProgress() {
        val vm = createViewModel()
        // 先把 progress 推到 1
        vm.onDrag(1f)
        assertEquals(1f, vm.collapseProgress.value, 0.001f)
        // 展开方向：delta 为负
        vm.onExpandDrag(-0.4f)
        assertEquals(0.6f, vm.collapseProgress.value, 0.001f)
    }

    @Test
    fun onExpandDrag_clampsAtZero() {
        val vm = createViewModel()
        vm.onDrag(0.5f)
        vm.onExpandDrag(-1f)
        assertEquals(0f, vm.collapseProgress.value, 0.001f)
    }

    @Test
    fun onExpandDrag_clampsAtOne() {
        val vm = createViewModel()
        vm.onExpandDrag(2f)
        assertEquals(1f, vm.collapseProgress.value, 0.001f)
    }

    // ---- onDragEnd / onExpandDragEnd ----

    @Test
    fun onDragEnd_progressAboveThreshold_collapses() {
        val vm = createViewModel()
        vm.onDrag(0.6f)
        vm.onDragEnd()
        assertTrue(vm.isCollapsed.value)
        assertEquals(1f, vm.collapseProgress.value, 0.001f)
    }

    @Test
    fun onDragEnd_progressBelowThreshold_expands() {
        val vm = createViewModel()
        vm.onDrag(0.05f)
        vm.onDragEnd()
        assertFalse(vm.isCollapsed.value)
        assertEquals(0f, vm.collapseProgress.value, 0.001f)
    }

    @Test
    fun onExpandDragEnd_progressBelowThreshold_expands() {
        val vm = createViewModel()
        vm.onDrag(1f)
        vm.onExpandDrag(-0.95f)
        vm.onExpandDragEnd()
        assertFalse(vm.isCollapsed.value)
        assertEquals(0f, vm.collapseProgress.value, 0.001f)
    }

    @Test
    fun onExpandDragEnd_progressAboveThreshold_staysCollapsed() {
        val vm = createViewModel()
        vm.onDrag(1f)
        vm.onExpandDrag(-0.05f)
        vm.onExpandDragEnd()
        assertTrue(vm.isCollapsed.value)
        assertEquals(1f, vm.collapseProgress.value, 0.001f)
    }

    // ---- getMonthDays 与 selectedDate 配合 ----

    @Test
    fun getMonthDays_updatesIsSelectedAfterSelectDate() {
        val vm = createViewModel()
        vm.selectDate(LocalDate(2026, 5, 20))
        val days = vm.getMonthDays(2026, 5)
        val selectedCell = days.first { it.isSelected }
        assertEquals(20, selectedCell.date.day)
    }

    @Test
    fun getMonthDays_noCellSelectedInOtherMonth() {
        val vm = createViewModel()
        // selectedDate 默认是今天(5/15)，不在 2026/8 月内（含跨月填充也不可能）
        val days = vm.getMonthDays(2026, 8)
        assertTrue(days.none { it.isSelected })
    }

    @Test
    fun getMonthDays_todayCellAlwaysReflectsTodayClock() {
        val vm = createViewModel()
        // 即便选中其他日期，isToday 依然根据 clock 注入的 today
        vm.selectDate(LocalDate(2026, 5, 20))
        val days = vm.getMonthDays(2026, 5)
        val todayCell = days.first { it.isToday }
        assertEquals(15, todayCell.date.day)
    }

    @Test
    fun getMonthDays_returnsMultipleOfSeven() {
        val vm = createViewModel()
        // 任何月份，cells 数都应该是 7 的倍数
        for (month in 1..12) {
            val size = vm.getMonthDays(2026, month).size
            assertEquals(0, size % 7, "Month 2026/$month size=$size not multiple of 7")
            assertTrue(size in 28..42, "Month 2026/$month size=$size out of [28, 42]")
        }
    }

    // ---- toggleYearView ----

    @Test
    fun toggleYearView_fromMonthToYear_setsIsYearViewTrue() {
        val vm = createViewModel()
        assertFalse(vm.isYearView.value)
        vm.toggleYearView()
        assertTrue(vm.isYearView.value)
        assertEquals(1f, vm.yearViewProgress.value, 0.001f)
    }

    @Test
    fun toggleYearView_fromYearToMonth_setsIsYearViewFalse() {
        val vm = createViewModel()
        vm.toggleYearView()
        assertTrue(vm.isYearView.value)
        vm.toggleYearView()
        assertFalse(vm.isYearView.value)
        assertEquals(0f, vm.yearViewProgress.value, 0.001f)
    }

    @Test
    fun toggleYearView_setsYearViewYearToSelectedDateYear() {
        val vm = createViewModel()
        vm.selectDate(LocalDate(2027, 3, 15))
        vm.toggleYearView()
        assertEquals(2027, vm.yearViewYear.value)
    }
}
