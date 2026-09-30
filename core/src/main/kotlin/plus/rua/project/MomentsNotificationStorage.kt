package plus.rua.project

import android.content.Context
import android.content.SharedPreferences

/**
 * 朋友圈互动消息存储仓库，负责消息列表的持久化（基于 SharedPreferences 与自定义字符串编码）。
 *
 * @param prefs SharedPreferences 实例
 * @param momentsStorage 可选的朋友圈存储实例，用于首次初始化时自动关联动态配图与 ID
 */
class MomentsNotificationStorage(
    private val prefs: SharedPreferences,
    private val momentsStorage: MomentsStorage? = null,
) {
    companion object {
        private const val PREFS_NAME = "moments_notifications_prefs"
        private const val KEY_NOTIFICATIONS = "notifications_list"
        private const val KEY_INITIALIZED = "notifications_initialized"
        private const val NOTIFICATIONS_SEPARATOR = "\n"

        fun fromContext(context: Context): MomentsNotificationStorage {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val momentsStorage = MomentsStorage.fromContext(context)
            return MomentsNotificationStorage(prefs, momentsStorage)
        }
    }

    /**
     * 获取全部互动消息列表，按时间戳降序排列。首次进入时自动加载默认交互消息。
     */
    fun getNotifications(): List<MomentNotification> {
        val isInitialized = prefs.getBoolean(KEY_INITIALIZED, false)
        if (!isInitialized) {
            val defaultList = createInitialNotifications()
            saveNotifications(defaultList)
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
            return defaultList
        }

        val raw = prefs.getString(KEY_NOTIFICATIONS, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        return raw.split(NOTIFICATIONS_SEPARATOR)
            .mapNotNull { MomentNotification.decodeFromString(it) }
            .sortedByDescending { it.timestamp }
    }

    /**
     * 覆盖保存互动消息列表。
     */
    fun saveNotifications(notifications: List<MomentNotification>) {
        val serialized = notifications
            .sortedByDescending { it.timestamp }
            .joinToString(NOTIFICATIONS_SEPARATOR) { it.encodeToString() }
        prefs.edit().putString(KEY_NOTIFICATIONS, serialized).apply()
    }

    /**
     * 插入一条新的互动消息（置于列表头部）。
     */
    fun addNotification(notification: MomentNotification) {
        val current = getNotifications().toMutableList()
        current.removeAll { it.id == notification.id }
        current.add(0, notification)
        saveNotifications(current)
    }

    /**
     * 删除指定 ID 的互动消息。
     */
    fun deleteNotification(id: String) {
        val current = getNotifications().filter { it.id != id }
        saveNotifications(current)
    }

    /**
     * 清空所有互动消息。
     */
    fun clearNotifications() {
        prefs.edit()
            .putString(KEY_NOTIFICATIONS, "")
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
    }

    /**
     * 清空偏好设置（主要用于测试）。
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * 生成与截图完全一致的默认互动消息列表，并尽可能关联已有的动态首图与 ID。
     */
    private fun createInitialNotifications(): List<MomentNotification> {
        val posts = momentsStorage?.getPosts().orEmpty()
        // 优先查找含配图的历史动态作为关联对象
        val targetPost = posts.firstOrNull { it.photoPaths.isNotEmpty() } ?: posts.firstOrNull()
        val postId = targetPost?.id.orEmpty()
        val photoPath = targetPost?.photoPaths?.firstOrNull()

        return listOf(
            MomentNotification(
                id = "notif_1",
                postId = "",
                authorName = "吴振宇",
                type = MomentNotificationType.LIKE,
                timestamp = 1779418980000L, // 2026-05-22 11:03:00
            ),
            MomentNotification(
                id = "notif_2",
                postId = "",
                authorName = "邹志兵",
                type = MomentNotificationType.LIKE,
                timestamp = 1779414180000L, // 2026-05-22 09:43:00
            ),
            MomentNotification(
                id = "notif_3",
                postId = postId,
                authorName = "孙雨涵",
                type = MomentNotificationType.LIKE,
                timestamp = 1776655860000L, // 2026-04-20 11:31:00
                postPhotoPath = photoPath,
            ),
            MomentNotification(
                id = "notif_4",
                postId = postId,
                authorName = "廖香梅",
                type = MomentNotificationType.LIKE,
                timestamp = 1760601480000L, // 2025-10-16 15:58:00
                postPhotoPath = photoPath,
            ),
            MomentNotification(
                id = "notif_5",
                postId = postId,
                authorName = "王景豪",
                type = MomentNotificationType.COMMENT,
                content = "买了吗",
                timestamp = 1760598660000L, // 2025-10-16 15:11:00
                postPhotoPath = photoPath,
            ),
            MomentNotification(
                id = "notif_6",
                postId = postId,
                authorName = "桥联租车 夏韬 17718199831",
                type = MomentNotificationType.LIKE,
                timestamp = 1760579160000L, // 2025-10-16 09:46:00
                postPhotoPath = photoPath,
            ),
            MomentNotification(
                id = "notif_7",
                postId = postId,
                authorName = "张晶鹏",
                type = MomentNotificationType.LIKE,
                timestamp = 1760574120000L, // 2025-10-16 08:22:00
                postPhotoPath = photoPath,
            ),
            MomentNotification(
                id = "notif_8",
                postId = postId,
                authorName = "杨晨",
                type = MomentNotificationType.LIKE,
                timestamp = 1760553300000L, // 2025-10-16 02:35:00
                postPhotoPath = photoPath,
            ),
            MomentNotification(
                id = "notif_9",
                postId = postId,
                authorName = "下水道",
                type = MomentNotificationType.COMMENT,
                content = "猫猫",
                timestamp = 1760547060000L, // 2025-10-16 00:51:00
                postPhotoPath = photoPath,
            ),
        )
    }
}
