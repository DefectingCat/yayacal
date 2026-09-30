package plus.rua.project

import android.content.SharedPreferences
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MomentNotificationTest {

    private val tz = TimeZone.of("Asia/Shanghai")

    @Test
    fun encodeAndDecode_roundTrip_preservesAllFields() {
        val original = MomentNotification(
            id = "notif_test_1",
            postId = "post_100",
            authorName = "王景豪",
            authorAvatar = "/path/to/avatar.jpg",
            type = MomentNotificationType.COMMENT,
            content = "买了吗",
            timestamp = 1760598660000L,
            postPhotoPath = "/path/to/photo.jpg",
            postText = "动态正文测试",
        )

        val encoded = original.encodeToString()
        val decoded = MomentNotification.decodeFromString(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun encodeAndDecode_withNulls_preservesNulls() {
        val original = MomentNotification(
            id = "notif_test_2",
            postId = "post_200",
            authorName = "吴振宇",
            authorAvatar = null,
            type = MomentNotificationType.LIKE,
            content = null,
            timestamp = 1779418980000L,
            postPhotoPath = null,
            postText = "",
        )

        val encoded = original.encodeToString()
        val decoded = MomentNotification.decodeFromString(encoded)

        assertEquals(original, decoded)
        assertNull(decoded?.authorAvatar)
        assertNull(decoded?.content)
        assertNull(decoded?.postPhotoPath)
    }

    @Test
    fun formatNotificationTimestamp_sameYear_omitsYear() {
        // 2026-05-22 11:03:00 UTC+8 -> 1779418980000L
        val formatted = formatNotificationTimestamp(
            timestamp = 1779418980000L,
            currentYear = 2026,
            timeZone = tz,
        )
        assertEquals("5月22日 11:03", formatted)
    }

    @Test
    fun formatNotificationTimestamp_crossYear_includesYear() {
        // 2025-10-16 15:58:00 UTC+8 -> 1760601480000L
        val formatted = formatNotificationTimestamp(
            timestamp = 1760601480000L,
            currentYear = 2026,
            timeZone = tz,
        )
        assertEquals("2025年10月16日 15:58", formatted)
    }

    @Test
    fun formatNotificationTimestamp_midnightAndPaddedMinutes() {
        // 2025-10-16 00:51:00 UTC+8 -> 1760547060000L
        val formatted = formatNotificationTimestamp(
            timestamp = 1760547060000L,
            currentYear = 2026,
            timeZone = tz,
        )
        assertEquals("2025年10月16日 00:51", formatted)
    }

    @Test
    fun storage_newInstallation_doesNotCreateDemoNotifications() {
        assertTrue(MomentsNotificationStorage(NotificationTestInMemoryPrefs()).getNotifications().isEmpty())
    }

    @Test
    fun storage_addAndDeleteNotification() {
        val prefs = NotificationTestInMemoryPrefs()
        val storage = MomentsNotificationStorage(prefs)

        val newNotif = MomentNotification(
            id = "custom_notif",
            authorName = "新朋友",
            type = MomentNotificationType.COMMENT,
            content = "赞美！",
            timestamp = 1780000000000L,
        )

        storage.addNotification(newNotif)
        val listAfterAdd = storage.getNotifications()
        assertEquals("custom_notif", listAfterAdd[0].id)
        assertEquals(1, listAfterAdd.size)

        storage.deleteNotification("custom_notif")
        val listAfterDelete = storage.getNotifications()
        assertEquals(0, listAfterDelete.size)
        assertTrue(listAfterDelete.none { it.id == "custom_notif" })
    }

    @Test
    fun storage_clearNotifications() {
        val prefs = NotificationTestInMemoryPrefs()
        val storage = MomentsNotificationStorage(prefs)

        storage.clearNotifications()
        assertTrue(storage.getNotifications().isEmpty())
    }
}

private class NotificationTestInMemoryPrefs : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = data

    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = data[key] as? Set<String> ?: defValues

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
