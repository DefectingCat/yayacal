package plus.rua.project

import android.content.SharedPreferences
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateCheckerStorageTest {

    private val prefs = InMemorySharedPreferences()
    private val storage = DateCheckerStorage(prefs)

    @Test
    fun load_noSavedData_returnsDefault() {
        storage.clear()
        val result = storage.load()
        assertEquals(null, result)
    }

    @Test
    fun saveAndLoad_roundTrips_correctly() {
        storage.clear()
        val date = LocalDate(2026, 5, 15)
        val rows = listOf(30, 60, 180, 365)
        storage.save(date, rows)
        val result = storage.load()
        assertEquals(date to rows, result)
    }

    @Test
    fun saveAndLoad_nullDaysNotPersisted() {
        storage.clear()
        val date = LocalDate(2026, 6, 1)
        val rows = listOf(30, null, 180)
        storage.save(date, rows)
        val result = storage.load()
        assertEquals(date to listOf(30, 180), result)
    }

    @Test
    fun saveAndLoad_emptyRows_savesSuccessfully() {
        storage.clear()
        val date = LocalDate(2026, 1, 1)
        storage.save(date, emptyList())
        val result = storage.load()
        assertEquals(date to emptyList(), result)
    }
}

private class InMemorySharedPreferences : SharedPreferences {

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

    override fun edit(): SharedPreferences.Editor = InMemoryEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

private class InMemoryEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {

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
