package plus.rua.project.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class UserMomentsScreenLogicTest {
    @Test
    fun formatTimelineMonth_validMonths_returnsCorrectChineseNames() {
        assertEquals("一月", formatTimelineMonth(1))
        assertEquals("二月", formatTimelineMonth(2))
        assertEquals("三月", formatTimelineMonth(3))
        assertEquals("四月", formatTimelineMonth(4))
        assertEquals("五月", formatTimelineMonth(5))
        assertEquals("六月", formatTimelineMonth(6))
        assertEquals("七月", formatTimelineMonth(7))
        assertEquals("八月", formatTimelineMonth(8))
        assertEquals("九月", formatTimelineMonth(9))
        assertEquals("十月", formatTimelineMonth(10))
        assertEquals("十一月", formatTimelineMonth(11))
        assertEquals("十二月", formatTimelineMonth(12))
    }

    @Test
    fun formatTimelineMonth_outOfRange_returnsFallback() {
        assertEquals("0月", formatTimelineMonth(0))
        assertEquals("13月", formatTimelineMonth(13))
    }
}
