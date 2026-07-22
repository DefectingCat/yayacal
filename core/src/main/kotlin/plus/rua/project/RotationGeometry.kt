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
