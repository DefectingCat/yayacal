package plus.rua.project

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * [RotationGeometry] 单元测试。
 *
 * 核心回归断言：用 [RotationGeometry.coverScale] 缩放后，旋转图片的四个角
 * 在任意偏移角度下都覆盖容器四角 —— 即「零黑边」。这是「旋转中容器露黑边」
 * bug 的几何重现与防护：缩放因子不足时角点会落进容器内 → 容器角露黑。
 */
class RotationGeometryTest {

    @Test
    fun stableAspect_noRotation_returnsSourceAspect() {
        assertEquals(1.5f, RotationGeometry.stableAspect(1.5f, 0))
        assertEquals(1.5f, RotationGeometry.stableAspect(1.5f, 180))
        assertEquals(1.5f, RotationGeometry.stableAspect(1.5f, 360))
    }

    @Test
    fun stableAspect_90Degrees_invertsAspect() {
        // 4:3 横图 → 3:4 竖向
        assertEquals(0.75f, RotationGeometry.stableAspect(1.3333f, 90), 1e-3f)
        assertEquals(0.75f, RotationGeometry.stableAspect(1.3333f, 270), 1e-3f)
    }

    @Test
    fun stableAspect_negativeAndMultipleRotations_invertsCorrectly() {
        // -450° (即 270°) 应与 90° 一致，反转宽高比
        assertEquals(0.75f, RotationGeometry.stableAspect(1.3333f, -450), 1e-3f)
        assertEquals(1.3333f, RotationGeometry.stableAspect(1.3333f, -360), 1e-3f)
    }

    @Test
    fun coverScale_atStableOffset_isOne() {
        // 稳态（offset=0）零裁剪零黑边
        listOf(0.5f, 0.75f, 1.0f, 1.3333f, 2.0f).forEach { aspect ->
            assertEquals(1.0f, RotationGeometry.coverScale(0f, aspect), 1e-5f)
        }
    }

    @Test
    fun coverScale_duringAnimation_isAtLeastOne() {
        // 动画中任意偏移都 >= 1（裁剪填满，不缩出黑边）
        val aspect = 0.75f
        for (offset in -89..89) {
            val k = RotationGeometry.coverScale(offset.toFloat(), aspect)
            assertTrue(k >= 1.0f, "offset=$offset scale=$k 应 >= 1")
        }
    }

    /**
     * 回归核心：缩放后旋转的图片四角必须覆盖容器四角，否则容器角露黑。
     *
     * 模型：图片 base = 容器矩形（宽 aspect、高 1，居中于原点），先放大 k 倍，
     * 再旋转 offset。容器四角（±aspect/2, ±1/2）反旋转到图片坐标后，
     * 必须落在缩放图片的半尺寸（±k·aspect/2, ±1/2）内。
     */
    @Test
    fun coverScale_rotatedImageCornersCoverContainer_noBlackAtAnyAngle() {
        val aspects = listOf(0.5f, 0.75f, 1.0f, 1.3333f, 2.0f, 3.0f)
        val maxViolation = aspects.maxOf { aspect ->
            (-90..90).maxOf { offsetDeg ->
                val offset = offsetDeg.toFloat()
                val k = RotationGeometry.coverScale(offset, aspect)
                val rad = offset * PI.toFloat() / 180f
                val ca = cos(rad); val sa = sin(rad)
                // 容器四角反旋转到图片坐标系，与缩放图片半尺寸比较（>0 表示露出黑边）
                listOf(
                    aspect / 2 to 0.5f,
                    aspect / 2 to -0.5f,
                    -aspect / 2 to 0.5f,
                    -aspect / 2 to -0.5f
                ).maxOf { (x, y) ->
                    val xp = x * ca + y * sa
                    val yp = -x * sa + y * ca
                    maxOf(abs(xp) - k * aspect / 2, abs(yp) - k * 0.5f)
                }
            }
        }
        // 无任何角点超出缩放图片 → 无黑边
        assertTrue(maxViolation <= 1e-4f, "存在角点露出黑边，最大越界=$maxViolation")
    }

    @Test
    fun coverScale_symmetricAroundZero() {
        // 正负偏移对称（旋转方向不影响裁剪量）
        val aspect = 1.3333f
        for (offset in 1..89) {
            assertEquals(
                RotationGeometry.coverScale(offset.toFloat(), aspect),
                RotationGeometry.coverScale(-offset.toFloat(), aspect),
                1e-5f
            )
        }
    }
    @Test
    fun baseRotation_and_isSwapped_dynamicAngleTransition() {
        assertEquals(0, RotationGeometry.baseRotation(0f))
        assertEquals(0, RotationGeometry.baseRotation(20f))
        assertEquals(0, RotationGeometry.baseRotation(44.9f))

        assertEquals(90, RotationGeometry.baseRotation(45.1f))
        assertEquals(90, RotationGeometry.baseRotation(80f))
        assertEquals(90, RotationGeometry.baseRotation(134f))

        assertEquals(180, RotationGeometry.baseRotation(135.1f))
        assertEquals(270, RotationGeometry.baseRotation(270f))

        assertEquals(-90, RotationGeometry.baseRotation(-45.1f))
        assertEquals(-180, RotationGeometry.baseRotation(-135.1f))

        assertFalse(RotationGeometry.isSwapped(0))
        assertTrue(RotationGeometry.isSwapped(90))
        assertFalse(RotationGeometry.isSwapped(180))
        assertTrue(RotationGeometry.isSwapped(270))
        assertTrue(RotationGeometry.isSwapped(-90))
        assertFalse(RotationGeometry.isSwapped(-180))

        assertEquals(0f, RotationGeometry.offsetDegrees(0f), 1e-5f)
        assertEquals(20f, RotationGeometry.offsetDegrees(20f), 1e-5f)
        assertEquals(-44.9f, RotationGeometry.offsetDegrees(45.1f), 1e-3f)
        assertEquals(-10f, RotationGeometry.offsetDegrees(80f), 1e-5f)
        assertEquals(0f, RotationGeometry.offsetDegrees(90f), 1e-5f)
    }
    @Test
    fun calculateLayoutSize_maintainsSourceAspect_and_fitsViewportAABB() {
        val srcAspect = 1.3333f // 4:3 横图
        val vWidth = 1000f
        val vHeight = 1400f

        // 0° 未旋转：layout 宽 = 1000, 高 = 1000 / 1.3333 = 750
        val (w0, h0) = RotationGeometry.calculateLayoutSize(srcAspect, 0f, vWidth, vHeight)
        assertEquals(srcAspect, w0 / h0, 1e-3f)
        assertEquals(1000f, w0, 1e-1f)
        assertEquals(750f, h0, 1e-1f)

        val (w90, h90) = RotationGeometry.calculateLayoutSize(srcAspect, 90f, vWidth, vHeight)
        assertEquals(srcAspect, w90 / h90, 1e-3f)
        assertEquals(1333.33f, w90, 1e-1f)
        assertEquals(1000f, h90, 1e-1f)
        // 视觉 AABB 包围盒不超过视口
        val aabbWidth90 = h90
        val aabbHeight90 = w90
        assertTrue(aabbWidth90 <= vWidth + 1e-2f)
        assertTrue(aabbHeight90 <= vHeight + 1e-2f)
    }
}
