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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MomentsPublishViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val prefs = MomentsPublishVmTestInMemoryPrefs()
    private val storage = MomentsStorage(prefs)
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "moments_publish_test_${System.currentTimeMillis()}")

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
    fun canPublish_initiallyFalse_becomesTrueWhenTextOrPhotoAdded() {
        val vm = MomentsPublishViewModel(
            storage = storage,
            filesDir = tempDir,
            ioDispatcher = testDispatcher,
        )

        assertFalse(vm.uiState.value.canPublish)

        vm.onTextChanged("Hello World")
        assertTrue(vm.uiState.value.canPublish)

        vm.onTextChanged("   ")
        assertFalse(vm.uiState.value.canPublish)

        vm.addPhotoPaths(listOf("/path/img1.jpg"))
        assertTrue(vm.uiState.value.canPublish)
    }

    @Test
    fun addPhotos_capsAtNinePhotos() {
        val vm = MomentsPublishViewModel(
            storage = storage,
            filesDir = tempDir,
            ioDispatcher = testDispatcher,
        )

        val elevenPhotos = (1..11).map { "/path/img_$it.jpg" }
        vm.addPhotoPaths(elevenPhotos)

        assertEquals(9, vm.uiState.value.photos.size)
        assertEquals(0, vm.uiState.value.remainingPhotoSlots)
    }

    @Test
    fun removePhotoAt_removesCorrectItem() {
        val vm = MomentsPublishViewModel(
            storage = storage,
            filesDir = tempDir,
            ioDispatcher = testDispatcher,
        )

        vm.addPhotoPaths(listOf("/path/1.jpg", "/path/2.jpg", "/path/3.jpg"))
        vm.removePhotoAt(1)

        assertEquals(listOf("/path/1.jpg", "/path/3.jpg"), vm.uiState.value.photos)
    }

    @Test
    fun locationSelection_setsAndClearsLocation() {
        val vm = MomentsPublishViewModel(
            storage = storage,
            filesDir = tempDir,
            ioDispatcher = testDispatcher,
        )

        val poi = MomentLocationItem("创维大厦", "高新南一道8号")
        vm.selectLocation(poi)
        assertEquals("创维大厦", vm.uiState.value.selectedLocation?.name)

        // 选择“不显示位置”
        vm.selectLocation(MomentsLocationProvider.NONE_ITEM)
        assertNull(vm.uiState.value.selectedLocation)
    }

    @Test
    fun publish_savesPostToStorageAndTriggersCallback() = runTest(testDispatcher) {
        val vm = MomentsPublishViewModel(
            storage = storage,
            filesDir = tempDir,
            ioDispatcher = testDispatcher,
        )

        vm.onTextChanged("记录今天好心情")
        vm.addPhotoPaths(listOf("/path/p1.jpg"))
        val poi = MomentLocationItem("创维半导体设计大厦", "高新南四道18号")
        vm.selectLocation(poi)

        var successCalled = false
        vm.publish {
            successCalled = true
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(successCalled)
        val posts = storage.getPosts()
        assertEquals(1, posts.size)
        assertEquals("记录今天好心情", posts[0].text)
        assertEquals(listOf("/path/p1.jpg"), posts[0].photoPaths)
        assertEquals("创维半导体设计大厦", posts[0].location)
    }
}

private class MomentsPublishVmTestInMemoryPrefs : SharedPreferences {
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
    override fun edit(): SharedPreferences.Editor = EditorImpl()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class EditorImpl : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            temp[key] = value
            return this
        }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            temp[key] = values
            return this
        }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            temp[key] = value
            return this
        }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            temp[key] = value
            return this
        }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            temp[key] = value
            return this
        }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
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
