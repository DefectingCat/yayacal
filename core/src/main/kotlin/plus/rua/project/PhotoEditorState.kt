package plus.rua.project

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 手写笔触：一条由多个采样点组成的路径，附带颜色与粗细。
 *
 * @param color 笔触颜色
 * @param widthPx 笔触线宽（像素）
 * @param points 采样点序列（基于图片显示坐标系）
 * @param isFinished 该笔触是否已结束（手指已抬起）。下一条笔触必须另起新段，
 * 不能再追加到 [points] 上；否则两次手写会被 [toPath] 用一条折线连在一起。
 */
data class HandStroke(
    val color: Color,
    val widthPx: Float,
    val points: List<Offset> = emptyList(),
    val isFinished: Boolean = false
)

/**
 * 照片编辑器状态。
 *
 * 持有原始 Bitmap（经 inSampleSize 降采样后）、当前旋转角度（0/90/180/270）、
 * 裁剪矩形（基于旋转后图片坐标系，null 表示不裁剪）、手写笔触列表。
 *
 * @param sourceBitmap 降采样后的源图（显示用）
 * @param sourceAbsolutePath 源图绝对路径（保存时回放笔触用原图尺寸）
 * @param rotationDegrees 累计旋转角度，始终为 90 的倍数
 * @param cropLeft 裁剪左边界比例 [0,1]；null 表示未启用裁剪
 * @param cropTop 裁剪上边界比例 [0,1]
 * @param cropRight 裁剪右边界比例 [0,1]
 * @param cropBottom 裁剪下边界比例 [0,1]
 * @param strokes 手写笔触列表（显示坐标系）
 * @param strokeColor 当前笔触颜色
 * @param strokeWidthPx 当前笔触线宽
 */
data class PhotoEditorState(
    val sourceBitmap: Bitmap,
    val sourceAbsolutePath: String,
    val rotationDegrees: Int = 0,
    val cropLeft: Float? = null,
    val cropTop: Float = 0f,
    val cropRight: Float? = null,
    val cropBottom: Float = 1f,
    val strokes: List<HandStroke> = emptyList(),
    val strokeColor: Color = Color.Red,
    val strokeWidthPx: Float = 8f
) {
    /** 当前是否已启用裁剪 */
    val cropEnabled: Boolean get() = cropLeft != null && cropRight != null

    /** 当前旋转后的 Bitmap（不含裁剪/手写），用于显示预览 */
    val rotatedBitmap: Bitmap
        get() = if (rotationDegrees % 360 == 0) sourceBitmap
        else PhotoProcessor.rotate(sourceBitmap, rotationDegrees)
}

/**
 * 笔触绘制配置，供 Canvas drawPath 使用。
 */
fun strokeDraw(widthPx: Float) = Stroke(
    width = widthPx,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round
)

/**
 * 将 [HandStroke] 的采样点转换为 Compose [Path]。
 */
fun HandStroke.toPath(): Path {
    if (points.isEmpty()) return Path()
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    return path
}

/**
 * 在当前状态下追加一个手写采样点，返回新状态。
 *
 * 若上一条笔触不存在、为空、或已结束（[HandStroke.isFinished]），则另起新段；
 * 否则把点追加到上一条笔触的末尾。
 */
fun PhotoEditorState.withAddedPoint(offset: Offset): PhotoEditorState {
    val last = strokes.lastOrNull()
    val newStrokes = if (last == null || last.points.isEmpty() || last.isFinished) {
        strokes + HandStroke(strokeColor, strokeWidthPx, listOf(offset))
    } else {
        strokes.dropLast(1) + last.copy(points = last.points + offset)
    }
    return copy(strokes = newStrokes)
}

/**
 * 结束当前正在绘制的笔触，返回新状态。若没有进行中的笔触则原样返回。
 */
fun PhotoEditorState.withEndedStroke(): PhotoEditorState {
    val last = strokes.lastOrNull() ?: return this
    if (!last.isFinished && last.points.isNotEmpty()) {
        return copy(strokes = strokes.dropLast(1) + last.copy(isFinished = true))
    }
    return this
}
