package plus.rua.project

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 朋友圈各图片入口共用的上传边界，大小单位与后端一致。 */
internal object MomentsImagePolicy {
    const val MAX_UPLOAD_BYTES = 50L * 1024 * 1024
    const val SIZE_ERROR = "图片不能超过 50 MiB"
    const val VERSION = "1"
    const val SMALL_IMAGE_BYTES = 1024L * 1024
    const val TARGET_BYTES = 2L * 1024 * 1024
    const val MAX_PIXELS = 8_000_000L
    const val MAX_EDGE = 12000
    const val PHOTO_EDGE = 2560
    const val LONG_IMAGE_WIDTH = 1080

    fun isLongImage(width: Int, height: Int): Boolean = max(width, height).toLong() >= min(width, height).toLong() * 3

    /** 同时限制两个方向和总像素；长图保留宽度，普通照片限制长边，不放大小图。 */
    fun targetSize(width: Int, height: Int): MomentsImageSize {
        require(width > 0 && height > 0) { "图片尺寸无效" }
        val longest = max(width, height)
        val shortest = min(width, height)
        val scale = if (isLongImage(width, height)) {
            min(LONG_IMAGE_WIDTH.toDouble() / shortest, MAX_EDGE.toDouble() / longest)
        } else {
            PHOTO_EDGE.toDouble() / longest
        }
        val pixelScale = sqrt(MAX_PIXELS.toDouble() / (width.toLong() * height))
        val ratio = min(1.0, min(scale, pixelScale))
        return MomentsImageSize(max(1, (width * ratio).toInt()), max(1, (height * ratio).toInt()))
    }
}

/** 上传副本的像素尺寸，与文件字节上限分别校验。 */
internal data class MomentsImageSize(val width: Int, val height: Int)
