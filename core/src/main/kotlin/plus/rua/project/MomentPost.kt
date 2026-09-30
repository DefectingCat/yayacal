package plus.rua.project

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

/**
 * 朋友圈单条动态数据模型。
 *
 * @param id 动态唯一标识符
 * @param text 正文文本内容
 * @param photoPaths 配图地址列表（远端 URL 或旧记录的本地路径，最多 9 张）
 * @param location 所在地理位置名称（为 null 表示不显示位置）
 * @param locationAddress 所在地理位置详细地址或距离（可选）
 * @param timestamp 发布时的时间戳（毫秒）
 * @param visibility 可见性范围（如 "公开"、"私密"）
 * @param isLikedByMe 当前选择账号是否在服务端点赞人列表中
 * @param comments 服务端评论预览或详情页已加载的评论
 */
data class MomentPost(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val photoPaths: List<String> = emptyList(),
    val location: String? = null,
    val locationAddress: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val visibility: String = "公开",
    val isLikedByMe: Boolean = false,
    val comments: List<MomentComment> = emptyList(),
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarPath: String? = null,
    val likes: List<MomentPerson> = emptyList(),
    val commentCount: Int = comments.size,
) {
    /**
     * 将动态对象编码为持久化单行字符串（无 JSON 依赖）。
     */
    fun encodeToString(): String {
        val encText = URLEncoder.encode(text, "UTF-8")
        val encLocation = URLEncoder.encode(location ?: "", "UTF-8")
        val encAddress = URLEncoder.encode(locationAddress ?: "", "UTF-8")
        val encPhotos = photoPaths.joinToString(";") { URLEncoder.encode(it, "UTF-8") }
        val encComments = comments.joinToString(";") { URLEncoder.encode(it.encodeToString(), "UTF-8") }
        return "$id|$timestamp|$visibility|$encLocation|$encAddress|$encPhotos|$encText|${if (isLikedByMe) 1 else 0}|$encComments"
    }

    companion object {
        /**
         * 从持久化字符串解码出动态对象。若解析失败返回 null。
         */
        fun decodeFromString(str: String): MomentPost? {
            val parts = str.split("|", limit = 9)
            if (parts.size < 7) return null
            return try {
                val id = parts[0]
                val timestamp = parts[1].toLong()
                val visibility = parts[2]
                val location = URLDecoder.decode(parts[3], "UTF-8").ifEmpty { null }
                val address = URLDecoder.decode(parts[4], "UTF-8").ifEmpty { null }
                val photos =
                    if (parts[5].isEmpty()) {
                        emptyList()
                    } else {
                        parts[5].split(";").map { URLDecoder.decode(it, "UTF-8") }
                    }
                val text = URLDecoder.decode(parts[6], "UTF-8")
                MomentPost(
                    id = id,
                    text = text,
                    photoPaths = photos,
                    location = location,
                    locationAddress = address,
                    timestamp = timestamp,
                    visibility = visibility,
                    isLikedByMe = parts.getOrNull(7) == "1",
                    comments = parts.getOrNull(8).orEmpty().split(";")
                        .filter { it.isNotEmpty() }
                        .mapNotNull { MomentComment.decodeFromString(URLDecoder.decode(it, "UTF-8")) },
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * 朋友圈评论。作者与回复由服务端账号和评论 ID 关联，旧编码仅用于读取历史本地数据。
 *
 * @param id 评论唯一标识
 * @param authorName 发送时的用户昵称
 * @param text 评论正文
 * @param timestamp 发送时间（毫秒）
 * @param replyToName 回复对象的昵称，为 null 时评论整条动态
 * @param photoPath 评论图片的远端 URL 或旧记录路径，为 null 时没有图片
 */
data class MomentComment(
    val id: String = UUID.randomUUID().toString(),
    val authorName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val replyToName: String? = null,
    val photoPath: String? = null,
    val authorId: String = "",
    val authorAvatarPath: String? = null,
    val replyToId: String? = null,
    val deleted: Boolean = false,
) {
    internal fun encodeToString(): String = listOf(
        id,
        timestamp.toString(),
        authorName,
        text,
        replyToName.orEmpty(),
        photoPath.orEmpty(),
    ).joinToString("|") { URLEncoder.encode(it, "UTF-8") }

    companion object {
        internal fun decodeFromString(raw: String): MomentComment? = runCatching {
            val fields = raw.split("|", limit = 6).map { URLDecoder.decode(it, "UTF-8") }
            if (fields.size != 6) return null
            MomentComment(
                id = fields[0],
                timestamp = fields[1].toLong(),
                authorName = fields[2],
                text = fields[3],
                replyToName = fields[4].ifEmpty { null },
                photoPath = fields[5].ifEmpty { null },
            )
        }.getOrNull()
    }
}

/** 服务端返回的账号身份，用于作者资料与点赞人列表。 */
data class MomentPerson(val id: String, val name: String, val avatarPath: String? = null, val coverPath: String? = null)
