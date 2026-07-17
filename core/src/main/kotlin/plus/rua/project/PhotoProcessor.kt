package plus.rua.project

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import java.io.File
import java.io.FileOutputStream

/**
 * 照片处理工具：加载、旋转、裁剪、合成手写笔触并落盘。
 *
 * 所有方法均为纯函数（不持有状态），便于测试与复用。
 */
object PhotoProcessor {

    /**
     * 按目标显示宽度降采样加载 Bitmap，避免大图 OOM。
     *
     * @param path 图片绝对路径
     * @param reqWidth 期望显示宽度（像素），实际宽度会 >= reqWidth 的最小 2 次幂采样
     * @return 降采样后的 Bitmap，加载失败抛异常
     */
    fun loadSampled(path: String, reqWidth: Int = 1080): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = calculateInSampleSize(options.outWidth, reqWidth)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
            ?: error("无法加载图片: $path")
    }

    private fun calculateInSampleSize(srcWidth: Int, reqWidth: Int): Int =
        calculateInSampleSizePublic(srcWidth, reqWidth)

    /**
     * 降采样倍数计算（对测试可见）。
     *
     * 选择使 `srcWidth / sample <= reqWidth * 2` 的最小 2 次幂，
     * 保证显示清晰度的同时避免大图 OOM。
     */
    internal fun calculateInSampleSizePublic(srcWidth: Int, reqWidth: Int): Int {
        var sample = 1
        while (srcWidth / sample > reqWidth * 2) sample *= 2
        return sample
    }

    /**
     * 旋转 Bitmap。
     *
     * @param bitmap 源图
     * @param degrees 旋转角度（正向任意值，内部取模 360）
     * @return 旋转后的新 Bitmap；0° 时返回原对象
     */
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = degrees % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * 按比例裁剪 Bitmap。
     *
     * @param bitmap 源图
     * @param left 左边界比例 [0,1]
     * @param top 上边界比例 [0,1]
     * @param right 右边界比例 [0,1]
     * @param bottom 下边界比例 [0,1]
     * @return 裁剪后的新 Bitmap
     */
    fun crop(
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Bitmap {
        val x = (left.coerceIn(0f, 1f) * bitmap.width).toInt()
        val y = (top.coerceIn(0f, 1f) * bitmap.height).toInt()
        val w = ((right - left).coerceIn(0f, 1f) * bitmap.width).toInt()
        val h = ((bottom - top).coerceIn(0f, 1f) * bitmap.height).toInt()
        return Bitmap.createBitmap(bitmap, x, y, w, h, null, false)
    }

    /**
     * 将编辑结果（旋转 + 裁剪 + 手写笔触）合成为最终 Bitmap 并写入 [destFile]。
     *
     * 笔触坐标基于 [displayWidth]×[displayHeight] 的显示坐标系，
     * 内部按 `最终Bitmap尺寸 / 显示尺寸` 缩放后绘制，保证落盘精度。
     *
     * @param source 源 Bitmap
     * @param rotationDegrees 累计旋转角度
     * @param cropLeft 裁剪左比例，null 表示不裁剪
     * @param cropTop 裁剪上比例
     * @param cropRight 裁剪右比例，null 表示不裁剪
     * @param cropBottom 裁剪下比例
     * @param strokes 手写笔触列表
     * @param displayWidth 显示区域宽度（笔触坐标系基准）
     * @param displayHeight 显示区域高度（笔触坐标系基准）
     * @param destFile 输出文件
     * @return 输出文件绝对路径
     */
    fun render(
        source: Bitmap,
        rotationDegrees: Int,
        cropLeft: Float?,
        cropTop: Float,
        cropRight: Float?,
        cropBottom: Float,
        strokes: List<HandStroke>,
        displayWidth: Float,
        displayHeight: Float,
        destFile: File
    ): String {
        // 1. 旋转
        var result = rotate(source, rotationDegrees)
        // 2. 裁剪
        if (cropLeft != null && cropRight != null) {
            result = crop(result, cropLeft, cropTop, cropRight, cropBottom)
        }
        // 3. 合成手写笔触
        if (strokes.isNotEmpty()) {
            result = drawStrokes(result, strokes, displayWidth, displayHeight)
        }
        // 4. 落盘（JPEG 90% 质量）
        FileOutputStream(destFile).use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return destFile.absolutePath
    }

    private fun drawStrokes(
        bitmap: Bitmap,
        strokes: List<HandStroke>,
        displayWidth: Float,
        displayHeight: Float
    ): Bitmap {
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = AndroidCanvas(mutable)
        // 笔触坐标从显示尺寸映射到 Bitmap 尺寸
        val scaleX = bitmap.width / displayWidth
        val scaleY = bitmap.height / displayHeight
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            paint.color = stroke.color.toArgb()
            paint.strokeWidth = stroke.widthPx * ((scaleX + scaleY) / 2f)
            val path = android.graphics.Path()
            val first = stroke.points[0]
            path.moveTo(first.x * scaleX, first.y * scaleY)
            for (i in 1 until stroke.points.size) {
                val p = stroke.points[i]
                path.lineTo(p.x * scaleX, p.y * scaleY)
            }
            canvas.drawPath(path, paint)
        }
        return mutable
    }

    private fun Color.toArgb(): Int = AndroidColor.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
