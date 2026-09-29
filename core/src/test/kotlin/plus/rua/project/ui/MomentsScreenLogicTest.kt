package plus.rua.project.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class MomentsScreenLogicTest {
    @Test
    fun formatMomentDetailTime_formatsLocalDateAndZeroPaddedTime() {
        val timestamp = kotlin.time.Instant.parse("2026-09-28T16:05:00Z").toEpochMilliseconds()
        assertEquals("2026年9月29日 00:05", formatMomentDetailTime(timestamp, kotlinx.datetime.TimeZone.of("Asia/Shanghai")))
        assertEquals("2026年9月28日 16:05", formatMomentDetailTime(timestamp, kotlinx.datetime.TimeZone.UTC))
    }

    // ---- calculateTopBarAlpha ----

    @Test
    fun calculateTopBarAlpha_zeroOffset_returnsZero() {
        assertEquals(0f, calculateTopBarAlpha(0, 300f))
    }

    @Test
    fun calculateTopBarAlpha_halfOffset_returnsHalf() {
        assertEquals(0.5f, calculateTopBarAlpha(150, 300f))
    }

    @Test
    fun calculateTopBarAlpha_maxOffset_returnsOne() {
        assertEquals(1f, calculateTopBarAlpha(300, 300f))
    }

    @Test
    fun calculateTopBarAlpha_overflowOffset_clampedToOne() {
        assertEquals(1f, calculateTopBarAlpha(600, 300f))
    }

    @Test
    fun calculateTopBarAlpha_negativeOffset_clampedToZero() {
        assertEquals(0f, calculateTopBarAlpha(-50, 300f))
    }

    @Test
    fun calculateTopBarAlpha_zeroOrNegativeMaxOffset_returnsOne() {
        assertEquals(1f, calculateTopBarAlpha(50, 0f))
        assertEquals(1f, calculateTopBarAlpha(50, -100f))
    }
}
