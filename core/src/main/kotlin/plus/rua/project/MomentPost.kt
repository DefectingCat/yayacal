package plus.rua.project

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

/**
 * 朋友圈单条动态数据模型。
 *
 * @param id 动态唯一标识符
 * @param text 正文文本内容
 * @param photoPaths 配图本地持久化路径列表（最多 9 张）
 * @param location 所在地理位置名称（为 null 表示不显示位置）
 * @param locationAddress 所在地理位置详细地址或距离（可选）
 * @param timestamp 发布时的时间戳（毫秒）
 * @param visibility 可见性范围（如 "公开"、"私密"）
 */
data class MomentPost(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val photoPaths: List<String> = emptyList(),
    val location: String? = null,
    val locationAddress: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val visibility: String = "公开",
) {
    /**
     * 将动态对象编码为持久化单行字符串（无 JSON 依赖）。
     */
    fun encodeToString(): String {
        val encText = URLEncoder.encode(text, "UTF-8")
        val encLocation = URLEncoder.encode(location ?: "", "UTF-8")
        val encAddress = URLEncoder.encode(locationAddress ?: "", "UTF-8")
        val encPhotos = photoPaths.joinToString(";") { URLEncoder.encode(it, "UTF-8") }
        return "$id|$timestamp|$visibility|$encLocation|$encAddress|$encPhotos|$encText"
    }

    companion object {
        /**
         * 从持久化字符串解码出动态对象。若解析失败返回 null。
         */
        fun decodeFromString(str: String): MomentPost? {
            val parts = str.split("|", limit = 7)
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
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
