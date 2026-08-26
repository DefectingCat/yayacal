package plus.rua.project.ui

import androidx.compose.ui.geometry.Size
import plus.rua.project.StaffGeometry
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 八度视窗布局（staffMetrics + fitWindowStart）的边界不变量测试。
 *
 * 高音低音探索器曾把右箭头放到键盘区间之外（最高 C6），而谱面只按
 * C3~C5 预留空间，导致高音音符画出画布顶部、遮挡键盘。视窗布局的
 * 核心契约：三组八度视窗（C3/C4/C5）内任何一个可选音，其符头、
 * 光晕、加线与五条谱线都必须完整落在画布内。
 */
class StaffMetricsTest {
    private val size = Size(width = 400f, height = 200f)

    /** 与 StaffCanvas 一致的绘制参数：光晕半径与符干长度（线距倍数）。 */
    private val haloRadiusUnits = 1.35f
    private val stemUnits = 3.1f

    private fun noteY(
        m: StaffMetrics,
        step: Int,
    ): Float = m.bottomLineY - StaffGeometry.halfUnitsFromBottomLine(step) * m.spacing / 2f

    @Test
    fun staffMetrics_everyExplorerWindow_staffLinesInsideCanvas() {
        for (w in listOf(-7f, 0f, 7f)) {
            val m = staffMetrics(size, w)
            assertTrue(m.topLineY >= 0f, "w=$w 顶线越界: ${m.topLineY}")
            assertTrue(m.bottomLineY <= size.height, "w=$w 底线越界: ${m.bottomLineY}")
            assertTrue(m.spacing > 0f, "w=$w 线距非正: ${m.spacing}")
        }
    }

    @Test
    fun staffMetrics_everySelectableStep_noteHaloAndStemInsideCanvas() {
        // 探索器可选区间即键盘区间 C3~C5（step -7..7），右箭头不得再放到 C6
        for (step in -7..7) {
            val w = Math.floorDiv(step, 7) * 7f
            val m = staffMetrics(size, w)
            val y = noteY(m, step)
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
    fun staffMetrics_windowMetricsContinuousAcrossOctaveSwitch() {
        // 视窗起始 step 微小扰动只应引起微小布局变化（跨八度动画不跳变）
        for (w in listOf(-7.01f, -6.99f, -0.01f, 0.01f, 6.99f, 7.01f)) {
            val a = staffMetrics(size, w - 0.005f)
            val b = staffMetrics(size, w + 0.005f)
            assertTrue(
                kotlin.math.abs(a.bottomLineY - b.bottomLineY) < 1f,
                "w=$w 底线跳变: ${a.bottomLineY} vs ${b.bottomLineY}",
            )
            assertTrue(
                kotlin.math.abs(a.spacing - b.spacing) < 0.1f,
                "w=$w 线距跳变: ${a.spacing} vs ${b.spacing}",
            )
        }
    }
}
