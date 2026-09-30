package plus.rua.project

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import kotlin.time.Instant

/**
 * 朋友圈动态互动类型：点赞或评论。
 */
enum class MomentNotificationType {
    LIKE,
    COMMENT,
    REPLY,
}

/**
 * 朋友圈互动消息数据模型（对应微信“全部互动消息”单条条目）。
 *
 * @param id 互动消息唯一标识符
 * @param postId 发生互动的动态 ID
 * @param authorName 互动发出者昵称
 * @param authorAvatar 互动发出者头像路径或 URI
 * @param type 互动类型（点赞或评论）
 * @param content 互动内容（评论内容文本，点赞时为 null）
 * @param timestamp 互动时间戳（毫秒）
 * @param postPhotoPath 对应动态的首张缩略图路径（纯文字动态时为 null）
 * @param postText 对应动态的正文文本（用于纯文字动态预览或备用展示）
 */
data class MomentNotification(
    val id: String = UUID.randomUUID().toString(),
    val postId: String = "",
    val authorName: String = "",
    val authorAvatar: String? = null,
    val type: MomentNotificationType = MomentNotificationType.LIKE,
    val content: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val postPhotoPath: String? = null,
    val postText: String = "",
    val isRead: Boolean = false,
) {
    /**
     * 将互动消息对象编码为持久化单行字符串（无 JSON 依赖）。
     */
    fun encodeToString(): String = listOf(
        id,
        postId,
        authorName,
        authorAvatar.orEmpty(),
        type.name,
        content.orEmpty(),
        timestamp.toString(),
        postPhotoPath.orEmpty(),
        postText,
    ).joinToString("|") { URLEncoder.encode(it, "UTF-8") }

    companion object {
        /**
         * 从持久化字符串解码出互动消息对象。若解析失败返回 null。
         */
        fun decodeFromString(raw: String): MomentNotification? = runCatching {
            val fields = raw.split("|", limit = 9).map { URLDecoder.decode(it, "UTF-8") }
            if (fields.size != 9) return null
            MomentNotification(
                id = fields[0],
                postId = fields[1],
                authorName = fields[2],
                authorAvatar = fields[3].ifEmpty { null },
                type = MomentNotificationType.valueOf(fields[4]),
                content = fields[5].ifEmpty { null },
                timestamp = fields[6].toLong(),
                postPhotoPath = fields[7].ifEmpty { null },
                postText = fields[8],
            )
        }.getOrNull()
    }
}

/**
 * 格式化朋友圈互动消息时间戳（严格还原微信格式：当年为“M月d日 HH:mm”，往年为“yyyy年M月d日 HH:mm”）。
 *
 * @param timestamp 毫秒时间戳
 * @param currentYear 当前年份（用于判断是否跨年）
 * @param timeZone 时区（默认系统当前时区）
 * @return 格式化后的时间字符串
 */
fun formatNotificationTimestamp(
    timestamp: Long,
    currentYear: Int,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val dt = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(timeZone)
    val hourStr = dt.hour.toString().padStart(2, '0')
    val minuteStr = dt.minute.toString().padStart(2, '0')

    @Suppress("DEPRECATION") // kotlinx-datetime monthNumber
    val monthNumber = dt.monthNumber

    @Suppress("DEPRECATION") // kotlinx-datetime dayOfMonth
    val dayOfMonth = dt.dayOfMonth

    return if (dt.year == currentYear) {
        "${monthNumber}月${dayOfMonth}日 $hourStr:$minuteStr"
    } else {
        "${dt.year}年${monthNumber}月${dayOfMonth}日 $hourStr:$minuteStr"
    }
}
