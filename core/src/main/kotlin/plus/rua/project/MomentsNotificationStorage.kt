package plus.rua.project

import android.content.Context
import android.content.SharedPreferences

/**
 * 旧版本朋友圈互动消息存储仓库（仅保留历史数据读取兼容），负责消息列表的持久化（基于 SharedPreferences 与自定义字符串编码）。
 *
 * @param prefs SharedPreferences 实例
 * @param momentsStorage 可选的朋友圈存储实例，用于首次初始化时自动关联动态配图与 ID
 */
class MomentsNotificationStorage(
    private val prefs: SharedPreferences,
) {
    companion object {
        private const val PREFS_NAME = "moments_notifications_prefs"
        private const val KEY_NOTIFICATIONS = "notifications_list"
        private const val KEY_INITIALIZED = "notifications_initialized"
        private const val NOTIFICATIONS_SEPARATOR = "\n"

        fun fromContext(context: Context): MomentsNotificationStorage {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return MomentsNotificationStorage(prefs)
        }
    }

    /**
     * 获取全部互动消息列表，按时间戳降序排列。首次进入为空，不再生成示例消息。
     */
    fun getNotifications(): List<MomentNotification> {
        val isInitialized = prefs.getBoolean(KEY_INITIALIZED, false)
        if (!isInitialized) {
            val defaultList = emptyList<MomentNotification>()
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
}
