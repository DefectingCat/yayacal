package plus.rua.project

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PhotoProcessor 降采样计算逻辑单元测试。
 *
 * 仅覆盖纯计算部分（[PhotoProcessor.calculateInSampleSizePublic]），
 * 涉及 Bitmap/IO 的部分依赖 Android 框架，需 instrumented 测试。
 */
class PhotoProcessorTest {

    @Test
    fun sampleSize_smallImage_returns1() {
        // 源宽 500，要求 1080 → 不需降采样
        assertEquals(1, PhotoProcessor.calculateInSampleSizePublic(500, 1080))
    }

    @Test
    fun sampleSize_exactly2xTarget_returns1() {
        // 源宽 2160，要求 1080 → 2160/1 = 2160 <= 2160(1080*2)，返回 1
        assertEquals(1, PhotoProcessor.calculateInSampleSizePublic(2160, 1080))
    }

    @Test
    fun sampleSize_largeImage_returnsPowerOf2() {
        // 源宽 8000，要求 1080 → 8000/2=4000 > 2160, 8000/4=2000 <= 2160 → 4
        assertEquals(4, PhotoProcessor.calculateInSampleSizePublic(8000, 1080))
    }

    @Test
    fun sampleSize_hugeImage_returnsLargerPowerOf2() {
        // 源宽 20000，要求 1080 → 20000/2=10000, /4=5000, /8=2500, /16=1250 <= 2160 → 16
        assertEquals(16, PhotoProcessor.calculateInSampleSizePublic(20000, 1080))
    }
}
