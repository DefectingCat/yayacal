package plus.rua.project

import android.content.SharedPreferences
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import plus.rua.project.ShiftKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

class CalendarViewModelTest {

    private val fixedInstant = Instant.parse("2026-05-15T00:00:00Z")
    private val testClock = FixedClock(fixedInstant)
    private fun createViewModel(): CalendarViewModel {
        return CalendarViewModel(clock = testClock)
    }

    // ---- getIsoWeekNumber ----

    @Test
    fun getIsoWeekNumber_regular_date() {
        val vm = createViewModel()
        assertEquals(20, vm.getIsoWeekNumber(LocalDate(2026, 5, 15)))
    }

    @Test
    fun getIsoWeekNumber_jan_1() {
        val vm = createViewModel()
        assertEquals(1, vm.getIsoWeekNumber(LocalDate(2026, 1, 1)))
    }

    @Test
    fun getIsoWeekNumber_dec_31() {
        val vm = createViewModel()
        assertEquals(53, vm.getIsoWeekNumber(LocalDate(2026, 12, 31)))
    }

    @Test
    fun getIsoWeekNumber_week_52_boundary() {
        val vm = createViewModel()
        assertEquals(52, vm.getIsoWeekNumber(LocalDate(2025, 12, 28)))
    }

    @Test
    fun getIsoWeekNumber_monday_starts_week() {
        val vm = createViewModel()
        assertEquals(20, vm.getIsoWeekNumber(LocalDate(2026, 5, 11)))
    }

    @Test
    fun getIsoWeekNumber_week_53_year() {
        val vm = createViewModel()
        assertEquals(53, vm.getIsoWeekNumber(LocalDate(2020, 12, 31)))
    }

    // ---- getMonthDays ----

    @Test
    fun getMonthDays_returns_correct_size() {
        val vm = createViewModel()
        // May 2026: 5 rows × 7 = 35 cells
        val days = vm.getMonthDays(2026, 5)
        assertEquals(35, days.size)
    }

    @Test
    fun getMonthDays_may_2026_starts_on_thursday() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 5)
        assertFalse(days[0].isCurrentMonth)
        assertEquals(4, days[0].date.month.number)
        assertEquals(27, days[0].date.day)
    }

    @Test
    fun getMonthDays_may_2026_first_day_is_may_1() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 5)
        assertTrue(days[4].isCurrentMonth)
        assertEquals(1, days[4].date.day)
        assertEquals(5, days[4].date.month.number)
    }

    @Test
    fun getMonthDays_may_2026_last_day_is_may_31() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 5)
        val may31 = days.first { it.isCurrentMonth && it.date.day == 31 }
        assertEquals(31, may31.date.day)
    }

    @Test
    fun getMonthDays_february_2026_28_days() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 2)
        val febDays = days.filter { it.isCurrentMonth }
        assertEquals(28, febDays.size)
    }

    @Test
    fun getMonthDays_february_2024_29_days_leap_year() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2024, 2)
        val febDays = days.filter { it.isCurrentMonth }
        assertEquals(29, febDays.size)
    }

    @Test
    fun getMonthDays_today_is_marked() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 5)
        val todayCell = days.first { it.isToday }
        assertEquals(15, todayCell.date.day)
        assertTrue(todayCell.isCurrentMonth)
    }

    @Test
    fun getMonthDays_selected_date_is_marked() {
        val vm = createViewModel()
        val days = vm.getMonthDays(2026, 5)
        val selectedCell = days.first { it.isSelected }
        assertEquals(15, selectedCell.date.day)
    }

    // ---- shiftPattern: 默认值与 refresh ----

    @Test
    fun shiftPattern_noStorage_returnsDefault() {
        val vm = createViewModel()   // 不传 storage
        assertEquals(ShiftKind.WORK, vm.shiftKindAt(LocalDate(2026, 5, 15)))
        assertEquals(ShiftKind.OFF, vm.shiftKindAt(LocalDate(2026, 5, 17)))
    }

    @Test
    fun refreshShiftPattern_reloadsFromStorage() {
        val prefs = CalendarVmTestPrefs()
        val storage = ShiftPatternStorage(prefs)
        // storage 里存一个 1班1休,锚点 2026-01-01
        storage.save(
            ShiftPattern(
                anchorDate = LocalDate(2026, 1, 1),
                cycle = listOf(ShiftKind.WORK, ShiftKind.OFF)
            )
        )
        val vm = CalendarViewModel(clock = testClock, shiftStorage = storage)
        // 初始即从 storage 读
        assertEquals(ShiftKind.WORK, vm.shiftKindAt(LocalDate(2026, 1, 1)))
        assertEquals(ShiftKind.OFF, vm.shiftKindAt(LocalDate(2026, 1, 2)))
    }

    @Test
    fun refreshShiftPattern_reloadsAfterStorageChange() {
        val prefs = CalendarVmTestPrefs()
        val storage = ShiftPatternStorage(prefs)
        // 初始:2 班 2 休(默认),构造 VM(不传 storage → 用 DEFAULT_PATTERN)
        val vm = CalendarViewModel(clock = testClock, shiftStorage = storage)
        // 2026-05-15 = WORK(默认 2班2休 锚点)
        assertEquals(ShiftKind.WORK, vm.shiftKindAt(LocalDate(2026, 5, 15)))
        // 改 storage 为 1班1休,锚点 2026-01-01
        storage.save(
            ShiftPattern(
                anchorDate = LocalDate(2026, 1, 1),
                cycle = listOf(ShiftKind.WORK, ShiftKind.OFF)
            )
        )
        // refresh 前:VM 还持有旧 pattern
        assertEquals(ShiftKind.WORK, vm.shiftKindAt(LocalDate(2026, 5, 15)))
        // 调用 refresh
        vm.refreshShiftPattern()
        // refresh 后:VM 已从 storage 重读
        // 2026-05-15 距 2026-01-01 = 134 天,134 % 2 = 0 → cycle[0] = WORK
        assertEquals(ShiftKind.WORK, vm.shiftKindAt(LocalDate(2026, 5, 15)))
        // 2026-01-02 距锚点 1 天,1 % 2 = 1 → cycle[1] = OFF
        assertEquals(ShiftKind.OFF, vm.shiftKindAt(LocalDate(2026, 1, 2)))
    }
}

private class CalendarVmTestPrefs : SharedPreferences {

    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = data.toMap()

    override fun getString(key: String, defValue: String?): String? =
        data[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        data[key] as? Set<String> ?: defValues

    override fun getInt(key: String, defValue: Int): Int =
        data[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        data[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        data[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        data[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = CalendarVmTestPrefsEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

private class CalendarVmTestPrefsEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {

    private val pending = mutableMapOf<String, Any?>()
    private var clearPending = false

    override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
        pending[key] = value
    }

    override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = apply {
        pending[key] = values
    }

    override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }

    override fun remove(key: String): SharedPreferences.Editor = apply { pending[key] = null }

    override fun clear(): SharedPreferences.Editor = apply { clearPending = true }

    override fun commit(): Boolean {
        apply()
        return true
    }

    override fun apply() {
        if (clearPending) {
            data.clear()
            clearPending = false
        }
        data.putAll(pending)
        pending.clear()
    }
}
