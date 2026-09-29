package plus.rua.project

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MomentsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val prefs = MomentsVmTestInMemoryPrefs()
    private val storage = MomentsStorage(prefs)
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "moments_vm_test")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    @Test
    fun initialState_loadsFromStorage() = runTest(testDispatcher) {
        storage.clear()
        storage.saveUsername("CustomUser")
        storage.saveAvatarPath("/custom/avatar.jpg")

        val viewModel =
            MomentsViewModel(
                storage = storage,
                filesDir = tempDir,
                ioDispatcher = testDispatcher,
            )

        val state = viewModel.uiState.value
        assertEquals("CustomUser", state.username)
        assertEquals("/custom/avatar.jpg", state.avatarPath)
        assertNull(state.coverPath)
    }

    @Test
    fun setAvatarPath_updatesStorageAndUiState() = runTest(testDispatcher) {
        storage.clear()
        val viewModel =
            MomentsViewModel(
                storage = storage,
                filesDir = tempDir,
                ioDispatcher = testDispatcher,
            )

        assertNull(viewModel.uiState.value.avatarPath)

        viewModel.setAvatarPath("/new/avatar.jpg")
        assertEquals("/new/avatar.jpg", viewModel.uiState.value.avatarPath)
        assertEquals("/new/avatar.jpg", storage.getAvatarPath())
    }
}

private class MomentsVmTestInMemoryPrefs : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = data.toMap()

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = data[key] as? String ?: defValue

    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? Set<String> ?: defValues
    }

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

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class EditorImpl : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putStringSet(
            key: String,
            values: Set<String>?,
        ): SharedPreferences.Editor {
            temp[key] = values
            return this
        }

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            temp[key] = this
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) data.clear()
            for ((k, v) in temp) {
                if (v === this) data.remove(k) else data[k] = v
            }
            temp.clear()
            clear = false
        }
    }
}
