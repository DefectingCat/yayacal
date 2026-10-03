package plus.rua.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MomentsImagePolicyTest {
    @Test fun targetSize_normalPhoto_capsBothOrientationsAndKeepsAspect() {
        assertEquals(MomentsImageSize(2560, 1920), MomentsImagePolicy.targetSize(8000, 6000))
        assertEquals(MomentsImageSize(1920, 2560), MomentsImagePolicy.targetSize(6000, 8000))
        assertEquals(MomentsImageSize(320, 240), MomentsImagePolicy.targetSize(320, 240))
    }

    @Test fun targetSize_longScreenshot_preservesLengthWithinPixelBudget() {
        val size = MomentsImagePolicy.targetSize(1440, 10000)
        assertTrue(size.width in 1000..1080)
        assertTrue(size.height > 7000)
        assertTrue(size.width.toLong() * size.height <= 8_000_000)
        assertEquals(size, MomentsImagePolicy.targetSize(10000, 1440).let { MomentsImageSize(it.height, it.width) })
    }

    @Test fun targetSize_extremeDimensions_staysWithinMemoryAndEdgeLimits() {
        for ((width, height) in listOf(12000 to 12000, Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE)) {
            val size = MomentsImagePolicy.targetSize(width, height)
            assertTrue(size.width in 1..12000 && size.height in 1..12000)
            assertTrue(size.width.toLong() * size.height <= 8_000_000)
        }
        assertFailsWith<IllegalArgumentException> { MomentsImagePolicy.targetSize(0, 100) }
    }
}
