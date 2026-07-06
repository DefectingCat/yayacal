package plus.rua.project

import android.content.SharedPreferences
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShiftPatternStorageTest {

    private val prefs = ShiftPatternTestPrefs()
    private val storage = ShiftPatternStorage(prefs)

    @Test
    fun load_noSavedData_returnsNull() {
        storage.clear()
        assertNull(storage.load())
    }

    @Test
    fun saveAndLoad_roundTrips_basicPattern() {
        storage.clear()
        val pattern = ShiftPattern(
            anchorDate = LocalDate(2026, 5, 15),
            cycle = listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF)
        )
        storage.save(pattern)
        val result = storage.load()
        assertEquals(pattern, result)
    }

    @Test
    fun saveAndLoad_roundTrips_withOverridesAndBreaks() {
        storage.clear()
        val pattern = ShiftPattern(
            anchorDate = LocalDate(2026, 7, 8),
            cycle = listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF),
            overrides = mapOf(
                LocalDate(2026, 7, 12) to ShiftKind.OFF,
                LocalDate(2026, 7, 14) to ShiftKind.WORK
            ),
            phaseBreaks = listOf(PhaseBreak(LocalDate(2026, 7, 18), 0))
        )
        storage.save(pattern)
        val result = storage.load()
        assertEquals(pattern, result)
    }

    @Test
    fun saveAndLoad_emptyOverridesAndBreaks() {
        storage.clear()
        val pattern = ShiftPattern(
            anchorDate = LocalDate(2026, 5, 15),
            cycle = listOf(ShiftKind.WORK, ShiftKind.OFF),
            overrides = emptyMap(),
            phaseBreaks = emptyList()
        )
        storage.save(pattern)
        assertEquals(pattern, storage.load())
    }
}

// 复制自 DateCheckerStorageTest(其为 private,无法共享);重命名以避免同包下 private 类同名冲突
private class ShiftPatternTestPrefs : SharedPreferences {

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

    override fun edit(): SharedPreferences.Editor = ShiftPatternTestPrefsEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

private class ShiftPatternTestPrefsEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {

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
