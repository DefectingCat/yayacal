package plus.rua.project

import android.content.SharedPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MomentsStorageTest {
    private val prefs = MomentsStorageTestInMemoryPrefs()
    private val storage = MomentsStorage(prefs)

    @Test
    fun getUsername_default_returnsDefault() {
        storage.clear()
        assertEquals(MomentsStorage.DEFAULT_USERNAME, storage.getUsername())
    }

    @Test
    fun saveAndGetUsername_roundTrips() {
        storage.clear()
        storage.saveUsername("CatOwner")
        assertEquals("CatOwner", storage.getUsername())
    }

    @Test
    fun getAvatarPath_initially_returnsNull() {
        storage.clear()
        assertNull(storage.getAvatarPath())
    }

    @Test
    fun saveAndGetAvatarPath_roundTrips() {
        storage.clear()
        storage.saveAvatarPath("/path/to/avatar.jpg")
        assertEquals("/path/to/avatar.jpg", storage.getAvatarPath())
        storage.saveAvatarPath(null)
        assertNull(storage.getAvatarPath())
    }

    @Test
    fun saveAndGetCoverPath_roundTrips() {
        storage.clear()
        storage.saveCoverPath("/path/to/cover.jpg")
        assertEquals("/path/to/cover.jpg", storage.getCoverPath())
        storage.saveCoverPath(null)
        assertNull(storage.getCoverPath())
    }

    @Test
    fun saveAndGetPosts_roundTripsInReverseChronologicalOrder() {
        storage.clear()
        val post1 = MomentPost(id = "1", text = "First", timestamp = 1000L)
        val post2 = MomentPost(id = "2", text = "Second", timestamp = 2000L)

        storage.savePost(post1)
        storage.savePost(post2)

        val posts = storage.getPosts()
        assertEquals(2, posts.size)
        assertEquals("2", posts[0].id)
        assertEquals("1", posts[1].id)

        storage.deletePost("2")
        val remaining = storage.getPosts()
        assertEquals(1, remaining.size)
        assertEquals("1", remaining[0].id)
    }
}

private class MomentsStorageTestInMemoryPrefs : SharedPreferences {
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
