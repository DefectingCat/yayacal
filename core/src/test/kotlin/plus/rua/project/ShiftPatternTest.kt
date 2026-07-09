package plus.rua.project

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShiftPatternTest {

    private val anchor = LocalDate(2026, 5, 15)
    private val twoOnTwoOff = ShiftPattern(
        anchorDate = anchor,
        cycle = listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF)
    )

    // ---- kindAt: 锚点与同周期内 ----

    @Test
    fun kindAt_anchorDate_returnsFirstInCycle() {
        assertEquals(ShiftKind.WORK, twoOnTwoOff.kindAt(anchor))
    }

    @Test
    fun kindAt_oneAfterAnchor_returnsSecondInCycle() {
        assertEquals(ShiftKind.WORK, twoOnTwoOff.kindAt(LocalDate(2026, 5, 16)))
    }

    @Test
    fun kindAt_twoAfterAnchor_returnsThirdInCycle() {
        assertEquals(ShiftKind.OFF, twoOnTwoOff.kindAt(LocalDate(2026, 5, 17)))
    }

    @Test
    fun kindAt_threeAfterAnchor_returnsFourthInCycle() {
        assertEquals(ShiftKind.OFF, twoOnTwoOff.kindAt(LocalDate(2026, 5, 18)))
    }

    // ---- kindAt: 周期循环 ----

    @Test
    fun kindAt_fourAfterAnchor_wrapsToCycleStart() {
        // (5/19 - 5/15) % 4 = 0
        assertEquals(ShiftKind.WORK, twoOnTwoOff.kindAt(LocalDate(2026, 5, 19)))
    }

    @Test
    fun kindAt_eightAfterAnchor_wrapsTwice() {
        // (5/23 - 5/15) % 4 = 0
        assertEquals(ShiftKind.WORK, twoOnTwoOff.kindAt(LocalDate(2026, 5, 23)))
    }

    @Test
    fun kindAt_oneCycleLater_idx2_returnsOff() {
        // (5/21 - 5/15) % 4 = 2
        assertEquals(ShiftKind.OFF, twoOnTwoOff.kindAt(LocalDate(2026, 5, 21)))
    }

    @Test
    fun kindAt_manyCyclesLater_correctlyWraps() {
        // 100天后: (100) % 4 = 0
        assertEquals(ShiftKind.WORK, twoOnTwoOff.kindAt(LocalDate(2026, 8, 23)))
    }

    // ---- kindAt: 锚点之前的日期（负差值处理）----

    @Test
    fun kindAt_oneDayBeforeAnchor_returnsLastInCycle() {
        // -1 mod 4 = 3 -> OFF (cycle[3])
        assertEquals(ShiftKind.OFF, twoOnTwoOff.kindAt(LocalDate(2026, 5, 14)))
    }

    @Test
    fun kindAt_twoDaysBeforeAnchor_returnsThirdInCycle() {
        // -2 mod 4 = 2 -> OFF (cycle[2])
        assertEquals(ShiftKind.OFF, twoOnTwoOff.kindAt(LocalDate(2026, 5, 13)))
    }

    @Test
    fun kindAt_threeDaysBeforeAnchor_returnsSecondInCycle() {
        // -3 mod 4 = 1 -> WORK (cycle[1])
        assertEquals(ShiftKind.WORK, twoOnTwoOff.kindAt(LocalDate(2026, 5, 12)))
    }

    @Test
    fun kindAt_fourDaysBeforeAnchor_returnsFirstInCycle() {
        // -4 mod 4 = 0 -> WORK (cycle[0])
        assertEquals(ShiftKind.WORK, twoOnTwoOff.kindAt(LocalDate(2026, 5, 11)))
    }

    @Test
    fun kindAt_manyDaysBeforeAnchor_correctlyWraps() {
        // -100 mod 4 = 0 -> WORK
        assertEquals(ShiftKind.WORK, twoOnTwoOff.kindAt(LocalDate(2026, 2, 4)))
    }

    // ---- kindAt: 边界情况 ----

    @Test
    fun kindAt_emptyCycle_returnsNull() {
        val pattern = ShiftPattern(anchorDate = anchor, cycle = emptyList())
        assertNull(pattern.kindAt(anchor))
        assertNull(pattern.kindAt(LocalDate(2026, 5, 16)))
        assertNull(pattern.kindAt(LocalDate(2026, 5, 14)))
    }

    @Test
    fun kindAt_singleElementCycle_alwaysReturnsThatElement() {
        val pattern = ShiftPattern(anchorDate = anchor, cycle = listOf(ShiftKind.WORK))
        assertEquals(ShiftKind.WORK, pattern.kindAt(anchor))
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 20)))
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 1, 1)))
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2027, 12, 31)))
    }

    @Test
    fun kindAt_singleOffCycle_alwaysReturnsOff() {
        val pattern = ShiftPattern(anchorDate = anchor, cycle = listOf(ShiftKind.OFF))
        assertEquals(ShiftKind.OFF, pattern.kindAt(anchor))
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2030, 6, 15)))
    }

    // ---- kindAt: 多样化周期 ----

    @Test
    fun kindAt_threeOnOneOffCycle() {
        // 4 day cycle: WORK WORK WORK OFF
        val pattern = ShiftPattern(
            anchorDate = anchor,
            cycle = listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF)
        )
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 15))) // 0
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 16))) // 1
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 17))) // 2
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 5, 18)))  // 3
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 19))) // 0
    }

    @Test
    fun kindAt_weekCycle_returnsCorrectDay() {
        // 7天周期：4天上班3天休息
        val pattern = ShiftPattern(
            anchorDate = anchor,
            cycle = listOf(
                ShiftKind.WORK, ShiftKind.WORK, ShiftKind.WORK, ShiftKind.WORK,
                ShiftKind.OFF, ShiftKind.OFF, ShiftKind.OFF
            )
        )
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 18))) // idx 3
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 5, 19)))  // idx 4
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 5, 21)))  // idx 6
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 22))) // idx 0 (next cycle)
    }

    // ---- ShiftPattern: 元数据 ----

    @Test
    fun shiftPattern_defaultNameIsChinese() {
        val pattern = ShiftPattern(anchorDate = anchor, cycle = listOf(ShiftKind.WORK))
        assertEquals("默认", pattern.name)
    }

    @Test
    fun shiftPattern_customNameIsPreserved() {
        val pattern = ShiftPattern(
            anchorDate = anchor,
            cycle = listOf(ShiftKind.WORK),
            name = "夜班"
        )
        assertEquals("夜班", pattern.name)
    }

    @Test
    fun shiftPattern_dataClassEquality() {
        val a = ShiftPattern(anchor, listOf(ShiftKind.WORK, ShiftKind.OFF))
        val b = ShiftPattern(anchor, listOf(ShiftKind.WORK, ShiftKind.OFF))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ---- kindAt: 单日 override ----

    @Test
    fun kindAt_overrideOnBaseDay_flipsToOff() {
        // 基础周期下 5/15 = WORK(锚点),override 为 OFF
        val pattern = twoOnTwoOff.copy(
            overrides = mapOf(LocalDate(2026, 5, 15) to ShiftKind.OFF)
        )
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 5, 15)))
        // 隔天不受影响
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 16)))
    }

    @Test
    fun kindAt_overrideOnOffDay_flipsToWork() {
        // 基础周期下 5/17 = OFF,override 为 WORK
        val pattern = twoOnTwoOff.copy(
            overrides = mapOf(LocalDate(2026, 5, 17) to ShiftKind.WORK)
        )
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 17)))
    }

    // ---- kindAt: 相位断点 phaseBreak ----

    @Test
    fun kindAt_phaseBreak_restartsCycleFromBreakDate() {
        // 断点设在 5/19,从这天起重新 cycle[0]
        val pattern = twoOnTwoOff.copy(
            phaseBreaks = listOf(PhaseBreak(LocalDate(2026, 5, 19), 0))
        )
        // 5/19 = cycle[0] = WORK
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 19)))
        // 5/20 = cycle[1] = WORK
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 20)))
        // 5/21 = cycle[2] = OFF
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 5, 21)))
        // 断点之前保持原相位:5/18 = (18-15)%4=3 = OFF
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 5, 18)))
    }

    @Test
    fun kindAt_phaseBreakWithOffset_shiftsPhase() {
        // 断点 5/19,cycleOffset=2:5/19 对应 cycle[2]=OFF(而非 cycle[0]=WORK)
        val pattern = twoOnTwoOff.copy(
            phaseBreaks = listOf(PhaseBreak(LocalDate(2026, 5, 19), 2))
        )
        // 5/19 = cycle[(0+2)%4] = cycle[2] = OFF
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 5, 19)))
        // 5/20 = cycle[(1+2)%4] = cycle[3] = OFF
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 5, 20)))
        // 5/21 = cycle[(2+2)%4] = cycle[0] = WORK
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 21)))
    }

    @Test
    fun kindAt_multiplePhaseBreaks_usesLatestApplicable() {
        // 两个断点:5/19(offset 0) 和 5/25(offset 1)
        val pattern = twoOnTwoOff.copy(
            phaseBreaks = listOf(
                PhaseBreak(LocalDate(2026, 5, 19), 0),
                PhaseBreak(LocalDate(2026, 5, 25), 1)
            )
        )
        // 5/19-5/24 受第一个断点支配(offset 0)
        // 5/19 = cycle[0] = WORK
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 19)))
        // 5/24 = cycle[(5)%4] = cycle[1] = WORK
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 24)))
        // 5/25 起受第二个断点支配(offset 1)
        // 5/25 = cycle[(0+1)%4] = cycle[1] = WORK
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 5, 25)))
        // 5/26 = cycle[(1+1)%4] = cycle[2] = OFF
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 5, 26)))
    }

    // ---- kindAt: override + phaseBreak 组合(用户原始例子)----

    @Test
    fun kindAt_userExample_combinedOverridesAndPhaseBreak() {
        // 基础锚点 7/8,周期 [WORK,WORK,OFF,OFF]
        val pattern = ShiftPattern(
            anchorDate = LocalDate(2026, 7, 8),
            cycle = listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF),
            overrides = mapOf(
                LocalDate(2026, 7, 12) to ShiftKind.OFF,  // 班→休
                LocalDate(2026, 7, 14) to ShiftKind.WORK, // 休→班
                LocalDate(2026, 7, 15) to ShiftKind.WORK, // 休→班
                LocalDate(2026, 7, 16) to ShiftKind.OFF,  // 班→休
                LocalDate(2026, 7, 17) to ShiftKind.OFF   // 班→休
            ),
            phaseBreaks = listOf(PhaseBreak(LocalDate(2026, 7, 18), 0))
        )
        // 7/10,11 = 基础周期自动算 (idx 2,3) = OFF,OFF
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 10)))
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 11)))
        // 7/12 = override OFF
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 12)))
        // 7/13 = 基础周期 (idx 5%4=1) = WORK
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 13)))
        // 7/14,15 = override WORK
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 14)))
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 15)))
        // 7/16,17 = override OFF
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 16)))
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 17)))
        // 7/18 起 phaseBreak 重排:cycle[0,1,2,3] = WORK,WORK,OFF,OFF
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 18)))
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 19)))
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 20)))
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 21)))
    }

    // ---- 长按"翻转并重排"场景(重构安全网)----
    // 以下测试模拟当前 UI 长按产出:override[date] + PhaseBreak(date+1, 0)
    // 重构为 RephaseFlip 后,这些场景的 kindAt 输出必须保持一致。

    /**
     * 单次长按:锚点 7/10,2班2休。长按 7/10 把班翻转为休,7/11 起重排。
     * 期望:7/10=休,7/11-12=班,7/13-14=休(后续按 cycle 顺延)
     */
    @Test
    fun longPress_singleFlip_dateFlippedAndRephased() {
        // 锚点 7/10,7/10=班(基础)
        val pattern = ShiftPattern(
            anchorDate = LocalDate(2026, 7, 10),
            cycle = listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF),
            overrides = mapOf(LocalDate(2026, 7, 10) to ShiftKind.OFF),
            phaseBreaks = listOf(PhaseBreak(LocalDate(2026, 7, 11), 0))
        )
        // 7/10 翻转为休
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 10)))
        // 7/11 起重排:cycle[0,1]=班班
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 11)))
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 12)))
        // cycle[2,3]=休休
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 13)))
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 14)))
        // 继续循环:7/15=cycle[0]=班
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 15)))
    }

    /**
     * 连续两次长按:7/10 一次、7/14 一次。两个重排点共存。
     * 期望:7/11-13 受第一个断点支配,7/14 起受第二个断点支配
     */
    @Test
    fun longPress_twoFlips_bothRephasesApplied() {
        val pattern = ShiftPattern(
            anchorDate = LocalDate(2026, 7, 10),
            cycle = listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF),
            overrides = mapOf(
                LocalDate(2026, 7, 10) to ShiftKind.OFF,
                LocalDate(2026, 7, 14) to ShiftKind.OFF
            ),
            phaseBreaks = listOf(
                PhaseBreak(LocalDate(2026, 7, 11), 0),
                PhaseBreak(LocalDate(2026, 7, 15), 0)
            )
        )
        // 7/10 翻转为休
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 10)))
        // 7/11-13 受第一个断点:班班休
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 11)))
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 12)))
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 13)))
        // 7/14 翻转为休
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 14)))
        // 7/15 起受第二个断点:班班休休
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 15)))
        assertEquals(ShiftKind.WORK, pattern.kindAt(LocalDate(2026, 7, 16)))
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 17)))
        assertEquals(ShiftKind.OFF, pattern.kindAt(LocalDate(2026, 7, 18)))
    }

    /**
     * 撤销长按:移除 7/10 的 override 和关联的 7/11 断点后,回到基础周期。
     */
    @Test
    fun longPress_undo_returnsToBaseCycle() {
        val before = ShiftPattern(
            anchorDate = LocalDate(2026, 7, 10),
            cycle = listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF),
            overrides = mapOf(LocalDate(2026, 7, 10) to ShiftKind.OFF),
            phaseBreaks = listOf(PhaseBreak(LocalDate(2026, 7, 11), 0))
        )
        // 撤销:移除 override 和断点
        val after = before.copy(
            overrides = before.overrides - LocalDate(2026, 7, 10),
            phaseBreaks = emptyList()
        )
        // 回到基础:7/10=班(锚点),7/12=休
        assertEquals(ShiftKind.WORK, after.kindAt(LocalDate(2026, 7, 10)))
        assertEquals(ShiftKind.WORK, after.kindAt(LocalDate(2026, 7, 11)))
        assertEquals(ShiftKind.OFF, after.kindAt(LocalDate(2026, 7, 12)))
    }
}
