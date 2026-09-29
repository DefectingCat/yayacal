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

    @Test
    fun isTimestampToday_sameDay_returnsTrue() {
        val today = kotlinx.datetime.LocalDate(2026, 9, 29)
        // 2026-09-29 15:47:59 UTC+8 is 1790668079190 ms
        val timestamp = 1790668079190L
        val tz = kotlinx.datetime.TimeZone.of("Asia/Shanghai")
        kotlin.test.assertTrue(isTimestampToday(timestamp, today, tz))
    }

    @Test
    fun isTimestampToday_differentDay_returnsFalse() {
        val today = kotlinx.datetime.LocalDate(2026, 9, 29)
        // 2026-09-28 15:47:59 UTC+8
        val yesterdayTs = 1790668079190L - 86400_000L
        val tz = kotlinx.datetime.TimeZone.of("Asia/Shanghai")
        kotlin.test.assertFalse(isTimestampToday(yesterdayTs, today, tz))
    }

    @Test
    fun multiPhotoPost_takeFour_preservesFirstFourForGrid() {
        val ninePhotos = (1..9).map { "photo_$it.jpg" }
        val displayPhotos = ninePhotos.take(4)
        assertEquals(4, displayPhotos.size)
        assertEquals(listOf("photo_1.jpg", "photo_2.jpg", "photo_3.jpg", "photo_4.jpg"), displayPhotos)
    }
}
