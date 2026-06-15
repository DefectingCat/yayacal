package plus.rua.project.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DateCheckerScreenLogicTest {

    // ---- clampExpiryDays ----

    @Test
    fun clampExpiryDays_positiveValue_unchanged() {
        assertEquals(30, clampExpiryDays(30))
    }

    @Test
    fun clampExpiryDays_zero_unchanged() {
        assertEquals(0, clampExpiryDays(0))
    }

    @Test
    fun clampExpiryDays_negativeValue_clampedToZero() {
        assertEquals(0, clampExpiryDays(-1))
    }

    @Test
    fun clampExpiryDays_largeNegative_clampedToZero() {
        assertEquals(0, clampExpiryDays(-365))
    }
}
