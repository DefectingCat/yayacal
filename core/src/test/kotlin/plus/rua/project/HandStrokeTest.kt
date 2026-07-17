package plus.rua.project

import androidx.compose.ui.geometry.Offset
import sun.misc.Unsafe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 手写笔触分段逻辑单元测试。
 *
 * 覆盖 [PhotoEditorState.withAddedPoint] / [PhotoEditorState.withEndedStroke]：
 * 抬手后再次落笔必须另起新段，不能与上一条笔触连成一条折线。
 */
class HandStrokeTest {

    private fun emptyState() = PhotoEditorState(
        sourceBitmap = UninitializedBitmap,
        sourceAbsolutePath = "/tmp/fake.jpg"
    )

    @Test
    fun twoStrokes_afterLiftAreSeparate() {
        // 画第一笔 A→B
        var s = emptyState()
            .withAddedPoint(Offset(0f, 0f))
            .withAddedPoint(Offset(10f, 0f))
        // 抬手
        s = s.withEndedStroke()
        // 在别处落笔 C→D
        s = s.withAddedPoint(Offset(100f, 100f))
            .withAddedPoint(Offset(110f, 100f))

        // 期望：两条独立笔触，而不是 [A,B,C,D] 一条
        assertEquals(2, s.strokes.size, "两笔应分隔为两段")
        assertEquals(listOf(Offset(0f, 0f), Offset(10f, 0f)), s.strokes[0].points)
        assertEquals(listOf(Offset(100f, 100f), Offset(110f, 100f)), s.strokes[1].points)
    }

    @Test
    fun points_accumulateWithinSameStroke() {
        val s = emptyState()
            .withAddedPoint(Offset(1f, 1f))
            .withAddedPoint(Offset(2f, 2f))
            .withAddedPoint(Offset(3f, 3f))

        assertEquals(1, s.strokes.size)
        assertEquals(
            listOf(Offset(1f, 1f), Offset(2f, 2f), Offset(3f, 3f)),
            s.strokes[0].points
        )
    }

    @Test
    fun endStroke_marksCurrentStrokeFinished() {
        val s = emptyState()
            .withAddedPoint(Offset(0f, 0f))
            .withEndedStroke()

        assertEquals(1, s.strokes.size)
        assertTrue(s.strokes[0].isFinished, "抬手后笔触应标记为已结束")
    }

    @Test
    fun endStroke_withNoStrokes_isNoop() {
        val s = emptyState().withEndedStroke()
        assertTrue(s.strokes.isEmpty())
    }

    @Test
    fun endStroke_calledTwice_keepsStrokeFinished() {
        val s = emptyState()
            .withAddedPoint(Offset(0f, 0f))
            .withEndedStroke()
            .withEndedStroke()

        assertEquals(1, s.strokes.size)
        assertTrue(s.strokes[0].isFinished)
    }

    @Test
    fun addPoint_afterFinished_startsNewStroke() {
        // 抬手之后再落点：上一条 isFinished=true，应另起新段
        val s = emptyState()
            .withAddedPoint(Offset(0f, 0f))
            .withEndedStroke()
            .withAddedPoint(Offset(50f, 50f))

        assertEquals(2, s.strokes.size)
        assertFalse(s.strokes[0].points.contains(Offset(50f, 50f)))
    }

    private companion object {
        /**
         * 通过 Unsafe.allocateInstance 创建 Bitmap 桩，跳过 Android 框架的静态初始化。
         * 笔触分段逻辑不读 Bitmap 任何字段/方法，桩仅用于满足 PhotoEditorState 构造器。
         */
        @Suppress("DiscouragedPrivateApi", "DEPRECATION")
        val UninitializedBitmap: android.graphics.Bitmap by lazy {
            val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val unsafe = unsafeField.get(null) as Unsafe
            @Suppress("UNCHECKED_CAST")
            val bmp = unsafe.allocateInstance(android.graphics.Bitmap::class.java)
                as android.graphics.Bitmap
            bmp
        }
    }
}
