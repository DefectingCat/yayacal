package plus.rua.project

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PeriodDocumentTest {
    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun normalized_overlappingAndAdjacentRanges_mergesInStartOrder() {
        val document =
            PeriodDocument(
                ranges =
                listOf(
                    PeriodRange(d("2026-09-17"), d("2026-09-18")),
                    PeriodRange(d("2026-08-10"), d("2026-08-14")),
                    PeriodRange(d("2026-09-12"), d("2026-09-16")),
                    PeriodRange(d("2026-08-12"), d("2026-08-13")),
                ),
            ).normalized()

        assertEquals(
            listOf(
                PeriodRange(d("2026-08-10"), d("2026-08-14")),
                PeriodRange(d("2026-09-12"), d("2026-09-18")),
            ),
            document.ranges,
        )
    }

    @Test
    fun normalized_ongoingRange_absorbsLaterRanges() {
        val document =
            PeriodDocument(
                ranges =
                listOf(
                    PeriodRange(d("2026-10-01"), null),
                    PeriodRange(d("2026-10-05"), d("2026-10-06")),
                ),
            ).normalized()

        assertEquals(listOf(PeriodRange(d("2026-10-01"), null)), document.ranges)
    }

    @Test
    fun normalized_notes_trimsDropsBlankAndKeepsLastPerDay() {
        val document =
            PeriodDocument(
                notes =
                listOf(
                    PeriodNote(d("2026-10-09"), null, "  热水袋  "),
                    PeriodNote(d("2026-10-08"), null, "   "),
                    PeriodNote(d("2026-10-07"), PeriodMood.CALM, "旧"),
                    PeriodNote(d("2026-10-07"), PeriodMood.TIRED, "新"),
                ),
            ).normalized()

        assertEquals(
            listOf(
                PeriodNote(d("2026-10-07"), PeriodMood.TIRED, "新"),
                PeriodNote(d("2026-10-09"), null, "热水袋"),
            ),
            document.notes,
        )
    }

    @Test
    fun violation_closedRangeLength_allows31DaysAndRejects32() {
        val longest = PeriodDocument(ranges = listOf(PeriodRange(d("2026-09-01"), d("2026-10-01"))))
        val tooLong = PeriodDocument(ranges = listOf(PeriodRange(d("2026-09-01"), d("2026-10-02"))))

        assertNull(longest.violation())
        assertEquals("单次经期不能超过 31 天", tooLong.violation())
    }

    @Test
    fun violation_ongoingNotLatest_returnsReason() {
        val document =
            PeriodDocument(
                ranges = listOf(PeriodRange(d("2026-09-01"), null), PeriodRange(d("2026-10-01"), null)),
            )

        assertEquals("只能有一段进行中的经期，且必须是最近一段", document.violation())
    }

    @Test
    fun violation_noteLength_countsEmojiAsOneCharacter() {
        val longest = PeriodDocument(notes = listOf(PeriodNote(d("2026-10-09"), null, "😊".repeat(500))))
        val tooLong = PeriodDocument(notes = listOf(PeriodNote(d("2026-10-09"), null, "字".repeat(501))))

        assertNull(longest.violation())
        assertEquals("备注最多 500 字", tooLong.violation())
    }

    @Test
    fun violation_settingsOutOfRange_returnsReason() {
        val document = PeriodDocument(settings = PeriodSettings(cycleLength = 61))

        assertEquals("周期需为 15–60 天、经期 2–10 天、黄体期 10–16 天", document.violation())
    }

    @Test
    fun violation_dateBeforeSupportedRange_returnsReason() {
        val document = PeriodDocument(ranges = listOf(PeriodRange(d("1999-12-31"), d("2000-01-02"))))

        assertEquals("日期需在 2000 年至 2100 年之间", document.violation())
    }
}
