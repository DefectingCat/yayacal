package plus.rua.project

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 图片旋转显示几何计算（纯函数，无 Android 依赖，可单测）。
 *
 * 解决「旋转动画中容器露黑边」问题的数学核心：旋转矩形无法填满它自己的轴对齐
 * 外接矩形（AABB）的四个角，因此若图片按 AABB 缩放必留 4 个黑三角。本对象提供
 * 两个公式，让图片在任意角度都恰好覆盖容器（零黑边），代价是动画中斜角处的裁剪。
 */
object RotationGeometry {

    /**
     * 目标稳态下容器的宽高比。
     *
     * 旋转 90° 的倍数后，图片宽高在 0/180° 与 90/270° 间互换。
     *
     * @param srcAspect 源图宽高比（宽/高）
     * @param rotationDegrees 累计旋转角，90 的倍数
     * @return 该稳态角度下容器的宽高比
     */
    fun stableAspect(srcAspect: Float, rotationDegrees: Int): Float =
        if (rotationDegrees % 180 == 0) srcAspect else 1f / srcAspect

    /**
     * 计算当前动画角度 [angle] 对应的最近 90° 倍数基准角度。
     *
     * 动态基准角度避免了在动画刚开始时以终点角度直接切换转置状态导致的错位黑边。
     */
    fun baseRotation(angle: Float): Int =
        kotlin.math.round(angle / 90f).toInt() * 90

    /**
     * 当前动画角度 [angle] 相对最近基准角度 [baseRotation] 的偏移角度 [-45°, 45°]。
     */
    fun offsetDegrees(angle: Float): Float =
        angle - baseRotation(angle)

    /**
     * 判断在给定的 90° 倍数基准角度下，宽高尺寸是否转置。
     */
    fun isSwapped(baseRotation: Int): Boolean =
        baseRotation % 180 != 0

    /**
     * 原始宽高比为 [srcAspect] 的照片在旋转 [angleDegrees] 角度时，
     * 包围盒（AABB）适应视口所需的未旋转 Layout 布局尺寸 (Width, Height)。
     *
     * 该尺寸的宽高比恒等于 [srcAspect]，保证 ContentScale.Fit 内部 0 留白 Padding，
     * 且旋转后的包围盒平滑居中适应视口，无任何 Card 卡片容器遮挡与黑边。
     */
    fun calculateLayoutSize(
        srcAspect: Float,
        angleDegrees: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): Pair<Float, Float> {
        if (viewportWidth <= 0f || viewportHeight <= 0f || srcAspect <= 0f) {
            return Pair(0f, 0f)
        }
        val rad = Math.toRadians(angleDegrees.toDouble())
        val c = abs(cos(rad)).toFloat()
        val s = abs(sin(rad)).toFloat()

        // 归一化 AABB 包围盒尺寸 (baseWidth = srcAspect, baseHeight = 1.0)
        val wAabb = srcAspect * c + 1.0f * s
        val hAabb = 1.0f * c + srcAspect * s

        // 适应视口的整体缩放系数
        val k = minOf(viewportWidth / wAabb, viewportHeight / hAabb)

        return Pair(srcAspect * k, 1.0f * k)
    }

    /**
     * 让「与容器同比例的图片矩形旋转 [offsetDegrees] 后仍完整覆盖容器」所需的最小缩放因子。
     *
     * 推导：图片 base 矩形 = 容器尺寸（宽 [aspect]、高 1，归一化）。绕中心旋转 θ 后，
     * 容器四角（±aspect/2, ±1/2）反旋转到图片坐标系，必须落在缩放后图片的半尺寸内。
     * 解得最小缩放：
     * ```
     * k = max( |cosθ| + |sinθ|/aspect,  |sinθ|·aspect + |cosθ| )
     * ```
     * - θ = 0（稳态）→ k = 1（零裁剪零黑边）
     * - θ = ±45°（旋转中点）→ k 最大（斜角处裁剪最多，但零黑边）
     *
     * @param offsetDegrees 图片当前显示角度相对目标稳态的偏移；稳态为 0
     * @param aspect 容器宽高比
     * @return 最小覆盖缩放因子，恒 >= 1
     */
    fun coverScale(offsetDegrees: Float, aspect: Float): Float {
        val rad = Math.toRadians(offsetDegrees.toDouble())
        val c = abs(cos(rad)).toFloat()
        val s = abs(sin(rad)).toFloat()
        return maxOf(c + s / aspect, s * aspect + c)
    }
}
