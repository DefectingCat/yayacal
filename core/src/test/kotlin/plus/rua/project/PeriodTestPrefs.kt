package plus.rua.project

import android.content.SharedPreferences

/** 经期相关测试共用的内存 SharedPreferences。 */
internal class PeriodTestPrefs : SharedPreferences {
    val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = data.toMap()

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = data[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST") // 测试替身只存放 putStringSet 写入的值
    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ): Set<String>? = data[key] as? Set<String> ?: defValues

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = data[key] as? Int ?: defValue

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = data[key] as? Long ?: defValue

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = data[key] as? Float ?: defValue

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = data[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(
            key: String,
            value: String?,
        ) = apply { pending[key] = value }

        override fun putStringSet(
            key: String,
            values: Set<String>?,
        ) = apply { pending[key] = values }

        override fun putInt(
            key: String,
            value: Int,
        ) = apply { pending[key] = value }

        override fun putLong(
            key: String,
            value: Long,
        ) = apply { pending[key] = value }

        override fun putFloat(
            key: String,
            value: Float,
        ) = apply { pending[key] = value }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ) = apply { pending[key] = value }

        override fun remove(key: String) = apply { pending[key] = null }

        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) data.clear()
            pending.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value }
        }
    }
}
