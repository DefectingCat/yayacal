package plus.rua.project

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PeriodOpTest {
    private val today = LocalDate(2026, 10, 9)

    private fun d(text: String) = LocalDate.parse(text)

    private fun doc(vararg ranges: PeriodRange) = PeriodDocument(revision = 7, ranges = ranges.toList())

    private fun closed(
        start: String,
        end: String,
    ) = PeriodRange(d(start), d(end))

    private fun ongoing(start: String) = PeriodRange(d(start), null)

    @Test
    fun startPeriod_noRanges_createsOngoingRange() {
        val result = PeriodOp.StartPeriod(today).applyTo(doc())

        assertEquals(listOf(PeriodRange(today, null)), result.ranges)
        assertEquals(7, result.revision)
    }

    @Test
    fun startPeriod_existingOngoingOrNotAfterLatestEnd_unchanged() {
        val withOngoing = doc(ongoing("2026-10-07"))
        val withClosed = doc(closed("2026-10-01", "2026-10-05"))

        assertEquals(withOngoing, PeriodOp.StartPeriod(today).applyTo(withOngoing))
        assertEquals(withClosed, PeriodOp.StartPeriod(d("2026-10-05")).applyTo(withClosed))
    }

    @Test
    fun startPeriod_dayAfterLatestEnd_reopensLatestRange() {
        val result = PeriodOp.StartPeriod(d("2026-10-06")).applyTo(doc(closed("2026-10-01", "2026-10-05")))

        assertEquals(listOf(ongoing("2026-10-01")), result.ranges)
    }

    @Test
    fun endPeriod_ongoing_setsEnd_andIgnoresDateBeforeStart() {
        val document = doc(closed("2026-09-10", "2026-09-14"), ongoing("2026-10-07"))

        assertEquals(
            listOf(closed("2026-09-10", "2026-09-14"), closed("2026-10-07", "2026-10-09")),
            PeriodOp.EndPeriod(today).applyTo(document).ranges,
        )
        assertEquals(document, PeriodOp.EndPeriod(d("2026-10-06")).applyTo(document))
        assertEquals(doc(), PeriodOp.EndPeriod(today).applyTo(doc()))
    }

    @Test
    fun setPeriodDay_markGapBetweenRanges_mergesThem() {
        val document = doc(closed("2026-09-10", "2026-09-12"), closed("2026-09-14", "2026-09-15"))

        val result = PeriodOp.SetPeriodDay(d("2026-09-13"), true, today).applyTo(document)

        assertEquals(listOf(closed("2026-09-10", "2026-09-15")), result.ranges)
    }

    @Test
    fun setPeriodDay_unmarkMiddleOfClosedRange_splitsIt() {
        val result = PeriodOp.SetPeriodDay(d("2026-09-12"), false, today).applyTo(doc(closed("2026-09-10", "2026-09-14")))

        assertEquals(listOf(closed("2026-09-10", "2026-09-11"), closed("2026-09-13", "2026-09-14")), result.ranges)
    }

    @Test
    fun setPeriodDay_unmarkEdgeDays_shrinksRange() {
        val document = doc(closed("2026-09-10", "2026-09-14"))

        assertEquals(
            listOf(closed("2026-09-11", "2026-09-14")),
            PeriodOp.SetPeriodDay(d("2026-09-10"), false, today).applyTo(document).ranges,
        )
        assertEquals(
            listOf(closed("2026-09-10", "2026-09-13")),
            PeriodOp.SetPeriodDay(d("2026-09-14"), false, today).applyTo(document).ranges,
        )
    }

    @Test
    fun setPeriodDay_unmarkMiddleOfOngoing_keepsTailOngoing() {
        val result = PeriodOp.SetPeriodDay(d("2026-10-07"), false, today).applyTo(doc(ongoing("2026-10-05")))

        assertEquals(listOf(closed("2026-10-05", "2026-10-06"), ongoing("2026-10-08")), result.ranges)
    }

    @Test
    fun setPeriodDay_unmarkToday_endsOngoingYesterday() {
        val result = PeriodOp.SetPeriodDay(today, false, today).applyTo(doc(ongoing("2026-10-05")))

        assertEquals(listOf(closed("2026-10-05", "2026-10-08")), result.ranges)
    }

    @Test
    fun setPeriodDay_markTodayWithoutOngoing_startsOngoingRange() {
        val result = PeriodOp.SetPeriodDay(today, true, today).applyTo(doc(closed("2026-09-10", "2026-09-14")))

        assertEquals(listOf(closed("2026-09-10", "2026-09-14"), ongoing("2026-10-09")), result.ranges)
    }

    @Test
    fun setPeriodDay_markPastDayBeforeOngoing_extendsOngoingStart() {
        val result = PeriodOp.SetPeriodDay(d("2026-10-06"), true, today).applyTo(doc(ongoing("2026-10-07")))

        assertEquals(listOf(ongoing("2026-10-06")), result.ranges)
    }

    @Test
    fun setPeriodDay_futureDate_unchanged() {
        val document = doc(ongoing("2026-10-07"))

        assertEquals(document, PeriodOp.SetPeriodDay(d("2026-10-10"), true, today).applyTo(document))
    }

    @Test
    fun setPeriodDay_appliedTwice_isIdempotent() {
        val op = PeriodOp.SetPeriodDay(d("2026-09-12"), false, today)
        val once = op.applyTo(doc(closed("2026-09-10", "2026-09-14")))

        assertEquals(once, op.applyTo(once))
    }

    @Test
    fun updateRange_missingOldStart_unchanged() {
        val document = doc(closed("2026-09-10", "2026-09-14"))

        assertEquals(document, PeriodOp.UpdateRange(d("2026-09-11"), d("2026-09-01"), d("2026-09-03")).applyTo(document))
    }

    @Test
    fun updateRange_overlappingOther_mergesRanges() {
        val document = doc(closed("2026-08-10", "2026-08-14"), closed("2026-09-10", "2026-09-14"))

        val result = PeriodOp.UpdateRange(d("2026-09-10"), d("2026-08-13"), d("2026-08-16")).applyTo(document)

        assertEquals(listOf(closed("2026-08-10", "2026-08-16")), result.ranges)
    }

    @Test
    fun updateRange_ongoingWhenLaterRangeExists_unchanged() {
        val document = doc(closed("2026-08-10", "2026-08-14"), closed("2026-09-10", "2026-09-14"))

        assertEquals(document, PeriodOp.UpdateRange(d("2026-08-10"), d("2026-08-10"), null).applyTo(document))
    }

    @Test
    fun deleteRange_removesOnlyMatchingStart() {
        val document = doc(closed("2026-08-10", "2026-08-14"), ongoing("2026-10-07"))

        assertEquals(listOf(ongoing("2026-10-07")), PeriodOp.DeleteRange(d("2026-08-10")).applyTo(document).ranges)
        assertEquals(document, PeriodOp.DeleteRange(d("2026-08-11")).applyTo(document))
    }

    @Test
    fun setNote_replacesAndBlankDeletes() {
        val document = PeriodOp.SetNote(today, PeriodMood.CALM, " 热水袋 ").applyTo(doc())

        assertEquals(listOf(PeriodNote(today, PeriodMood.CALM, "热水袋")), document.notes)
        assertEquals(emptyList(), PeriodOp.SetNote(today, null, "  ").applyTo(document).notes)
    }

    @Test
    fun updateSettings_replacesSettings() {
        val settings = PeriodSettings(cycleLength = 30, periodLength = 6, lutealLength = 13)

        assertEquals(settings, PeriodOp.UpdateSettings(settings).applyTo(doc()).settings)
    }

    @Test
    fun applyValidated_resultTooLong_returnsNull() {
        val document = doc(ongoing("2026-09-01"))

        assertNull(PeriodOp.EndPeriod(today).applyValidated(document))
    }

    @Test
    fun replay_onConcurrentServerDocument_keepsBothDevicesEdits() {
        // 服务器上另一台设备已经标记了经期开始；本机离线时写了备注、又点了"经期来了"
        val server = doc(closed("2026-09-10", "2026-09-14"), ongoing("2026-10-08"))
        val ops =
            listOf(
                PeriodOp.SetNote(today, PeriodMood.TIRED, "腰酸"),
                PeriodOp.StartPeriod(today),
                PeriodOp.EndPeriod(d("2026-08-01")),
            )

        val result = server.replay(ops)

        assertEquals(server.ranges, result.ranges)
        assertEquals(listOf(PeriodNote(today, PeriodMood.TIRED, "腰酸")), result.notes)
        assertEquals(7, result.revision)
    }

    @Test
    fun replay_skipsOpsThatBecomeInvalid() {
        val server = doc(ongoing("2026-09-01"))
        val ops = listOf(PeriodOp.EndPeriod(today), PeriodOp.SetNote(today, PeriodMood.LOW, ""))

        val result = server.replay(ops)

        assertEquals(server.ranges, result.ranges)
        assertEquals(listOf(PeriodNote(today, PeriodMood.LOW, "")), result.notes)
    }
}
