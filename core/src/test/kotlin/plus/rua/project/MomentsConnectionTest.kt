package plus.rua.project

import android.content.SharedPreferences
import plus.rua.project.shared.BuildConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class MomentsConnectionTest {
    private val preferences = ConnectionTestPreferences()
    private val debug = MomentsConnectionSettings(preferences, isDebug = true, defaultUrl = LOCAL_URL)
    private val release = MomentsConnectionSettings(preferences, isDebug = false, defaultUrl = ONLINE_URL)

    @Test
    fun url_currentBuildWithoutSavedAddress_returnsConfiguredDefault() {
        val expected = if (BuildConfig.DEBUG) LOCAL_URL else ONLINE_URL
        assertEquals(expected, BuildConfig.MOMENTS_DEFAULT_URL)
        assertEquals(expected, MomentsConnectionSettings(preferences).url())
    }

    @Test
    fun url_freshInstall_usesEachEnvironmentDefault() {
        assertEquals(LOCAL_URL, debug.url())
        assertEquals(ONLINE_URL, release.url())
    }

    @Test
    fun save_debugAndReleaseSharingPreferences_keepsAddressesSeparate() {
        debug.save("http://127.0.0.1:9090")
        release.save("https://release.example.com")
        assertEquals("http://127.0.0.1:9090", debug.url())
        assertEquals("https://release.example.com", release.url())

        debug.save("https://debug.example.com")
        assertEquals("https://debug.example.com", debug.url())
        assertEquals("https://release.example.com", release.url())
    }

    @Test
    fun url_legacyHttp_migratesToDebugAndRemovesSharedAddress() {
        preferences.edit().putString("url", " http://127.0.0.1:8088/ ").apply()
        assertEquals("http://127.0.0.1:8088", debug.url())
        assertEquals("http://127.0.0.1:8088", preferences.getString("url_debug", null))
        assertFalse(preferences.contains("url"))
        assertEquals(ONLINE_URL, release.url())
    }

    @Test
    fun url_releaseStartedBeforeDebug_preservesLegacyHttpForDebug() {
        preferences.edit().putString("url", "http://192.168.1.20:8088").apply()
        assertEquals(ONLINE_URL, release.url())
        assertEquals("http://192.168.1.20:8088", debug.url())
    }

    @Test
    fun url_legacyHttpWithSavedDebugAddress_keepsNewDebugSetting() {
        preferences.edit().putString("url", "http://10.0.2.2:8088").apply()
        debug.save("http://127.0.0.1:9090")
        assertEquals(ONLINE_URL, release.url())
        assertEquals("http://127.0.0.1:9090", debug.url())
        assertFalse(preferences.contains("url"))
    }

    @Test
    fun url_legacyHttpWithSavedReleaseAddress_keepsReleaseSetting() {
        preferences.edit().putString("url", "http://127.0.0.1:8088").apply()
        release.save("https://custom.example.com")
        assertEquals("https://custom.example.com", release.url())
        assertEquals("http://127.0.0.1:8088", debug.url())
    }

    @Test
    fun url_legacyHttps_doesNotOverrideEitherEnvironmentDefault() {
        preferences.edit().putString("url", "https://old.example.com").apply()
        assertEquals(LOCAL_URL, debug.url())
        assertEquals(ONLINE_URL, release.url())
        assertFalse(preferences.contains("url"))
    }

    @Test
    fun url_invalidLegacyAddress_fallsBackAndCompletesMigration() {
        preferences.edit().putString("url", "http://10.0.2.2:8088/api/v1").apply()
        assertEquals(ONLINE_URL, release.url())
        assertEquals(LOCAL_URL, debug.url())
        assertFalse(preferences.contains("url"))
    }

    @Test
    fun url_savedHttpInRelease_fallsBackToOnlineDefault() {
        preferences.edit().putString("url_release", "http://127.0.0.1:8088").apply()
        assertEquals(ONLINE_URL, release.url())
    }

    @Test
    fun save_addressWithWhitespaceAndTrailingSlash_storesNormalizedRoot() {
        debug.save("  http://127.0.0.1:8088/  ")
        release.save("  https://yaya.rua.plus/  ")
        assertEquals("http://127.0.0.1:8088", debug.url())
        assertEquals(ONLINE_URL, release.url())
    }

    @Test
    fun save_httpInRelease_rejectsAndPreservesPreviousAddress() {
        release.save("https://custom.example.com")
        assertFailsWith<IllegalArgumentException> { release.save(LOCAL_URL) }
        assertEquals("https://custom.example.com", release.url())
        assertEquals(LOCAL_URL, debug.url())
    }

    @Test
    fun save_invalidServiceRoot_rejectsAndPreservesPreviousAddress() {
        release.save(ONLINE_URL)
        listOf(
            "",
            "yaya.rua.plus",
            "https://user:password@yaya.rua.plus",
            "https://yaya.rua.plus/api/v1",
            "https://yaya.rua.plus?account_id=xiaobai",
            "https://yaya.rua.plus#fragment",
            "ftp://yaya.rua.plus",
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException>(invalid) { release.save(invalid) }
            assertEquals(ONLINE_URL, release.url())
        }
    }

    private companion object {
        const val LOCAL_URL = "http://10.0.2.2:8088"
        const val ONLINE_URL = "https://yaya.rua.plus"
    }
}

private class ConnectionTestPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = data.toMap()
    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? Set<String> ?: defValues
    }

    override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearPending = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = apply { pending[key] = values }
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
            if (clearPending) data.clear()
            pending.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value }
        }
    }
}
