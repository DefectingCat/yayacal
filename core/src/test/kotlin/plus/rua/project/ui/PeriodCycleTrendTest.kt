package plus.rua.project.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodCycleTrendTest {
    @Test
    fun cycleAxisTicks_narrowRange_usesFiveDaySteps() {
        assertEquals(listOf(25, 30, 35), cycleAxisTicks(listOf(27, 28, 30, 29)))
    }

    @Test
    fun cycleAxisTicks_wideRange_usesTenDaySteps() {
        assertEquals(listOf(20, 30, 40, 50, 60, 70, 80), cycleAxisTicks(listOf(28, 70, 26)))
    }

    @Test
    fun cycleAxisTicks_equalValues_stillSpansAtLeastOneStep() {
        assertEquals(listOf(25, 30), cycleAxisTicks(listOf(28, 28)))
    }
}
