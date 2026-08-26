package plus.rua.project.ui

import androidx.compose.ui.geometry.Size
import plus.rua.project.StaffGeometry
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 谱面自适应布局（staffMetrics + fitNote）的边界不变量测试。
 *
 * 高音低音探索器的谱面契约：对任何一个可选音（C2~C6，step -14..14），
 * 谱面刚好容纳完整五线 + 该音符——五条谱线、符头、光晕、符干、加线
 * 全部落在画布内（不高不低时没有多余留白，也不需要预留留白）。
 */
class StaffMetricsTest {
    private val size = Size(width = 400f, height = 200f)

    /** 与 StaffCanvas 一致的绘制参数：光晕半径与符干长度（线距倍数）。 */
    private val haloRadiusUnits = 1.35f
    private val stemUnits = 3.1f

    private fun noteY(
        m: StaffMetrics,
        step: Float,
    ): Float = m.bottomLineY - (step - StaffGeometry.BOTTOM_LINE_STEP) * m.spacing / 2f

    @Test
    fun staffMetrics_everySelectableStep_staffLinesInsideCanvas() {
        for (step in -14..14) {
            val m = staffMetrics(size, step.toFloat())
            assertTrue(m.topLineY >= 0f, "step=$step 顶线越界: ${m.topLineY}")
            assertTrue(m.bottomLineY <= size.height, "step=$step 底线越界: ${m.bottomLineY}")
            assertTrue(m.spacing > 0f, "step=$step 线距非正: ${m.spacing}")
        }
    }

    @Test
    fun staffMetrics_everySelectableStep_noteHaloAndStemInsideCanvas() {
        for (step in -14..14) {
            val m = staffMetrics(size, step.toFloat())
            val y = noteY(m, step.toFloat())
            val halo = haloRadiusUnits * m.spacing
            assertTrue(
                y - halo >= 0f && y + halo <= size.height,
                "step=$step 符头/光晕越界: y=$y halo=$halo height=${size.height}",
            )
            // 符干：中线以下朝上 3.1 线距，中线及以上朝下
            val offset = StaffGeometry.halfUnitsFromBottomLine(step)
            val stemEnd = if (offset < 4) y - stemUnits * m.spacing else y + stemUnits * m.spacing
            assertTrue(
                stemEnd >= 0f && stemEnd <= size.height,
                "step=$step 符干越界: stemEnd=$stemEnd height=${size.height}",
            )
            // 加线也必须在画布内
            for (ledger in StaffGeometry.ledgerOffsets(step)) {
                val ledgerY = m.bottomLineY - ledger * m.spacing / 2f
                assertTrue(
                    ledgerY >= 0f && ledgerY <= size.height,
                    "step=$step 加线越界: ledgerY=$ledgerY height=${size.height}",
                )
            }
        }
    }

    @Test
    fun staffMetrics_metricsContinuousAcrossNoteChange() {
        // fitNote 微小扰动只应引起微小布局变化（选音动画不跳变）
        var s = -14f
        while (s <= 14f) {
            val a = staffMetrics(size, s - 0.005f)
            val b = staffMetrics(size, s + 0.005f)
            assertTrue(
                kotlin.math.abs(a.bottomLineY - b.bottomLineY) < 1f,
                "s=$s 底线跳变: ${a.bottomLineY} vs ${b.bottomLineY}",
            )
            assertTrue(
                kotlin.math.abs(a.spacing - b.spacing) < 0.1f,
                "s=$s 线距跳变: ${a.spacing} vs ${b.spacing}",
            )
            s += 0.5f
        }
    }
}
