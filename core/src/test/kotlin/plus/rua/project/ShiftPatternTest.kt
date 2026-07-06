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
}
