package plus.rua.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
 *
 * 动画完成的最终状态（例如 [CalendarViewModel.isCollapsed] 在 spring
 * 动画结束后的取值）需要 MonotonicFrameClock 驱动，不在本测试集合范围内。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelStateTest {

    // 固定 today = 2026/5/15
    private val fixedInstant = Instant.parse("2026-05-15T00:00:00Z")
    private val testClock = StateTestFixedClock(fixedInstant)

    private fun createViewModel(dispatcher: TestDispatcher = StandardTestDispatcher()): CalendarViewModel {
        val scope = CoroutineScope(dispatcher)
        return CalendarViewModel(coroutineScope = scope, clock = testClock)
    }

    // ---- 初始状态 ----

    @Test
    fun init_selectedDateIsToday() {
        val vm = createViewModel()
        assertEquals(LocalDate(2026, 5, 15), vm.selectedDate)
    }

    @Test
    fun init_isCollapsedDefaultsFalse() {
        assertFalse(createViewModel().isCollapsed)
    }

    @Test
    fun init_collapseProgressDefaultsZero() {
        assertEquals(0f, createViewModel().collapseProgress, 0.001f)
    }

    @Test
    fun init_isYearViewDefaultsFalse() {
        assertFalse(createViewModel().isYearView)
    }

    @Test
    fun init_yearViewProgressDefaultsZero() {
        assertEquals(0f, createViewModel().yearViewProgress, 0.001f)
    }

    @Test
    fun init_yearViewYearDefaultsToTodayYear() {
        assertEquals(2026, createViewModel().yearViewYear)
    }

    @Test
    fun init_showLegalHolidayDefaultsFalse() {
        assertFalse(createViewModel().showLegalHoliday)
    }

    @Test
    fun init_shiftPatternHasDefault() {
        val pattern = createViewModel().shiftPattern
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
        assertEquals(LocalDate(2026, 6, 1), vm.selectedDate)
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
        assertEquals(LocalDate(2020, 12, 31), vm.selectedDate)
        assertEquals(12, vm.currentMonth)
        assertEquals(2020, vm.currentYear)
    }

    // ---- incrementYear / decrementYear ----

    @Test
    fun incrementYear_increasesYearViewYear() {
        val vm = createViewModel()
        vm.incrementYear()
        assertEquals(2027, vm.yearViewYear)
    }

    @Test
    fun decrementYear_decreasesYearViewYear() {
        val vm = createViewModel()
        vm.decrementYear()
        assertEquals(2025, vm.yearViewYear)
    }

    @Test
    fun incrementDecrementYear_consecutiveCalls() {
        val vm = createViewModel()
        repeat(5) { vm.incrementYear() }
        assertEquals(2031, vm.yearViewYear)
        repeat(3) { vm.decrementYear() }
        assertEquals(2028, vm.yearViewYear)
    }

    @Test
    fun incrementYear_doesNotAffectSelectedDate() {
        val vm = createViewModel()
        val before = vm.selectedDate
        vm.incrementYear()
        assertEquals(before, vm.selectedDate)
    }

    // ---- selectMonthFromYearView ----

    @Test
    fun selectMonthFromYearView_sameYearOtherMonth_setsFirstDayOfMonth() {
        val vm = createViewModel()
        vm.selectMonthFromYearView(8)
        assertEquals(LocalDate(2026, 8, 1), vm.selectedDate)
    }

    @Test
    fun selectMonthFromYearView_currentYearAndMonth_setsToToday() {
        val vm = createViewModel()
        // yearViewYear = 2026, today.month = 5
        vm.selectMonthFromYearView(5)
        assertEquals(LocalDate(2026, 5, 15), vm.selectedDate)
    }

    @Test
    fun selectMonthFromYearView_otherYear_setsFirstDay() {
        val vm = createViewModel()
        vm.incrementYear() // yearViewYear = 2027
        vm.selectMonthFromYearView(5)
        assertEquals(LocalDate(2027, 5, 1), vm.selectedDate)
    }

    @Test
    fun selectMonthFromYearView_setsIsYearViewFalse() {
        val vm = createViewModel()
        vm.selectMonthFromYearView(3)
        assertFalse(vm.isYearView)
    }

    @Test
    fun selectMonthFromYearView_january() {
        val vm = createViewModel()
        vm.selectMonthFromYearView(1)
        assertEquals(LocalDate(2026, 1, 1), vm.selectedDate)
    }

    @Test
    fun selectMonthFromYearView_december() {
        val vm = createViewModel()
        vm.selectMonthFromYearView(12)
        assertEquals(LocalDate(2026, 12, 1), vm.selectedDate)
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

    @Test
    fun shiftKindAt_nullPattern_returnsNull() {
        val vm = createViewModel()
        vm.shiftPattern = null
        assertNull(vm.shiftKindAt(LocalDate(2026, 5, 15)))
    }

    @Test
    fun shiftKindAt_customPattern_usesNewPattern() {
        val vm = createViewModel()
        vm.shiftPattern = ShiftPattern(
            anchorDate = LocalDate(2026, 5, 15),
            cycle = listOf(ShiftKind.OFF, ShiftKind.WORK)
        )
        assertEquals(ShiftKind.OFF, vm.shiftKindAt(LocalDate(2026, 5, 15)))
        assertEquals(ShiftKind.WORK, vm.shiftKindAt(LocalDate(2026, 5, 16)))
        assertEquals(ShiftKind.OFF, vm.shiftKindAt(LocalDate(2026, 5, 17)))
    }

    // ---- showLegalHoliday ----

    @Test
    fun showLegalHoliday_canBeToggled() {
        val vm = createViewModel()
        assertFalse(vm.showLegalHoliday)
        vm.showLegalHoliday = true
        assertTrue(vm.showLegalHoliday)
        vm.showLegalHoliday = false
        assertFalse(vm.showLegalHoliday)
    }

    // ---- onDrag: 折叠拖拽（异步路径：launch + snapTo）----

    @Test
    fun onDrag_positiveDelta_increasesProgress() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = createViewModel(dispatcher = dispatcher)
        vm.onDrag(0.3f)
        advanceUntilIdle()
        assertEquals(0.3f, vm.collapseProgress, 0.001f)
    }

    @Test
    fun onDrag_accumulatesAcrossCalls() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = createViewModel(dispatcher = dispatcher)
        vm.onDrag(0.2f)
        advanceUntilIdle()
        vm.onDrag(0.3f)
        advanceUntilIdle()
        assertEquals(0.5f, vm.collapseProgress, 0.001f)
    }

    @Test
    fun onDrag_clampsAtOne() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = createViewModel(dispatcher = dispatcher)
        vm.onDrag(0.8f)
        advanceUntilIdle()
        vm.onDrag(0.8f)
        advanceUntilIdle()
        assertEquals(1f, vm.collapseProgress, 0.001f)
    }

    @Test
    fun onDrag_clampsAtZeroWhenNegativeFromZero() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = createViewModel(dispatcher = dispatcher)
        vm.onDrag(-0.3f)
        advanceUntilIdle()
        assertEquals(0f, vm.collapseProgress, 0.001f)
    }

    @Test
    fun onDrag_negativeAfterPositive_canDecrease() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = createViewModel(dispatcher = dispatcher)
        vm.onDrag(0.5f)
        advanceUntilIdle()
        vm.onDrag(-0.2f)
        advanceUntilIdle()
        assertEquals(0.3f, vm.collapseProgress, 0.001f)
    }

    // ---- onExpandDrag: 展开拖拽 ----

    @Test
    fun onExpandDrag_updatesProgress() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = createViewModel(dispatcher = dispatcher)
        // 先把 progress 推到 1
        vm.onDrag(1f)
        advanceUntilIdle()
        assertEquals(1f, vm.collapseProgress, 0.001f)
        // 展开方向：delta 为负
        vm.onExpandDrag(-0.4f)
        advanceUntilIdle()
        assertEquals(0.6f, vm.collapseProgress, 0.001f)
    }

    @Test
    fun onExpandDrag_clampsAtZero() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = createViewModel(dispatcher = dispatcher)
        vm.onDrag(0.5f)
        advanceUntilIdle()
        vm.onExpandDrag(-1f)
        advanceUntilIdle()
        assertEquals(0f, vm.collapseProgress, 0.001f)
    }

    @Test
    fun onExpandDrag_clampsAtOne() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = createViewModel(dispatcher = dispatcher)
        vm.onExpandDrag(2f)
        advanceUntilIdle()
        assertEquals(1f, vm.collapseProgress, 0.001f)
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
}
