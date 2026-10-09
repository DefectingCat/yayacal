package plus.rua.project

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeriodPhaseTest {
    private val today = LocalDate(2026, 10, 9)

    private fun d(text: String) = LocalDate.parse(text)

    /** 每段 5 天的已结束经期。 */
    private fun fiveDayPeriods(vararg starts: String) = PeriodDocument(
        ranges = starts.map { PeriodRange(d(it), d(it).plus(DatePeriod(days = 4))) },
    )

    private fun timeline(
        document: PeriodDocument,
        day: LocalDate = today,
    ) = PeriodTimeline.build(document, PeriodPredictor.forecast(document, day), day)

    private fun PeriodTimeline.phase(date: String) = assertIs<PeriodDayInfo.InPhase>(dayInfo(d(date)))

    @Test
    fun dayInfo_sevenDaysBeforePredictedStart_isLutealDaySeven() {
        val info = timeline(fiveDayPeriods("2026-09-18")).phase("2026-10-09")

        assertEquals(CyclePhase.LUTEAL, info.phase)
        assertEquals(7, info.phaseDay)
        assertEquals(7, info.daysUntilNext)
        assertEquals(d("2026-10-16"), info.cycle.nextStart)
        assertEquals(d("2026-10-02"), info.cycle.ovulation)
    }

    @Test
    fun dayInfo_eachPhaseOfRecordedCycle_countsDaysWithinPhase() {
        val timeline = timeline(fiveDayPeriods("2026-09-18"))

        val menstrual = timeline.phase("2026-09-20")
        assertEquals(CyclePhase.MENSTRUAL to 3, menstrual.phase to menstrual.phaseDay)
        assertTrue(menstrual.cycle.periodEndKnown)
        val follicularFirst = timeline.phase("2026-09-23")
        assertEquals(CyclePhase.FOLLICULAR to 1, follicularFirst.phase to follicularFirst.phaseDay)
        val follicularLast = timeline.phase("2026-10-01")
        assertEquals(CyclePhase.FOLLICULAR to 9, follicularLast.phase to follicularLast.phaseDay)
        val ovulation = timeline.phase("2026-10-02")
        assertEquals(CyclePhase.OVULATION to 1, ovulation.phase to ovulation.phaseDay)
        val luteal = timeline.phase("2026-10-03")
        assertEquals(CyclePhase.LUTEAL to 1, luteal.phase to luteal.phaseDay)
    }

    @Test
    fun dayInfo_pastCycle_usesRecordedNextStart() {
        val info = timeline(fiveDayPeriods("2026-08-20", "2026-09-18")).phase("2026-09-10")

        assertEquals(d("2026-09-18"), info.cycle.nextStart)
        assertEquals(29, info.cycle.length)
        assertEquals(d("2026-09-04"), info.cycle.ovulation)
        assertEquals(CyclePhase.LUTEAL to 6, info.phase to info.phaseDay)
        assertTrue(info.cycle.recorded)
    }

    @Test
    fun dayInfo_afterPredictedStart_isPredictedMenstrual() {
        val info = timeline(fiveDayPeriods("2026-09-18")).phase("2026-10-17")

        assertEquals(CyclePhase.MENSTRUAL to 2, info.phase to info.phaseDay)
        assertEquals(d("2026-10-16"), info.cycle.start)
        assertEquals(d("2026-10-20"), info.cycle.periodEnd)
        assertFalse(info.cycle.recorded)
        assertFalse(info.cycle.periodEndKnown)
    }

    @Test
    fun dayInfo_shortCycle_skipsFollicularAndOvulation() {
        val document =
            PeriodDocument(
                settings = PeriodSettings(cycleLength = 18),
                ranges = listOf(PeriodRange(d("2026-10-01"), d("2026-10-07"))),
            )

        val info = timeline(document).phase("2026-10-09")

        assertNull(info.cycle.ovulation)
        assertEquals(CyclePhase.LUTEAL to 2, info.phase to info.phaseDay)
    }

    @Test
    fun dayInfo_pastExpectedStart_reportsLateUntilToday() {
        val timeline = timeline(fiveDayPeriods("2026-09-01"))

        val expectedDay = assertIs<PeriodDayInfo.Late>(timeline.dayInfo(d("2026-09-29")))
        assertEquals(0, expectedDay.days)
        val late = assertIs<PeriodDayInfo.Late>(timeline.dayInfo(today))
        assertEquals(10, late.days)
        assertEquals(d("2026-09-29"), late.cycle.nextStart)
        val tomorrow = timeline.phase("2026-10-10")
        assertEquals(CyclePhase.MENSTRUAL to 1, tomorrow.phase to tomorrow.phaseDay)
        assertEquals(d("2026-10-14"), timeline.lastDate)
    }

    @Test
    fun dayInfo_ongoingPeriod_usesExpectedEnd() {
        val document =
            PeriodDocument(
                ranges = listOf(PeriodRange(d("2026-09-10"), d("2026-09-14")), PeriodRange(d("2026-10-07"), null)),
            )
        val timeline = timeline(document)

        val info = timeline.phase("2026-10-09")
        assertEquals(CyclePhase.MENSTRUAL to 3, info.phase to info.phaseDay)
        assertEquals(d("2026-10-11"), info.cycle.periodEnd)
        assertTrue(info.cycle.recorded)
        assertFalse(info.cycle.periodEndKnown)
        assertEquals(d("2026-11-04"), info.cycle.nextStart)
        val follicular = timeline.phase("2026-10-12")
        assertEquals(CyclePhase.FOLLICULAR to 1, follicular.phase to follicular.phaseDay)
    }

    @Test
    fun build_ongoingLongerThanCycle_dropsPredictedStartsInsidePeriod() {
        val timeline = timeline(PeriodDocument(ranges = listOf(PeriodRange(d("2026-09-01"), null))))

        val info = timeline.phase("2026-10-09")
        assertEquals(CyclePhase.MENSTRUAL to 39, info.phase to info.phaseDay)
        assertEquals(d("2026-10-27"), info.cycle.nextStart)
        assertEquals(listOf(d("2026-09-01"), d("2026-10-27"), d("2026-11-24")), timeline.cycles.map { it.start })
    }

    @Test
    fun build_noRanges_onlyTodayWithoutRecord() {
        val timeline = timeline(PeriodDocument())

        assertEquals(listOf(today), timeline.dates)
        assertEquals(PeriodDayInfo.NoRecord(today), timeline.dayInfo(today))
    }

    @Test
    fun build_manyCycles_spansThreePastCyclesToNextPredictedPeriodEnd() {
        val starts = (0 until 6).map { d("2026-04-30").plus(DatePeriod(days = it * 28)) }
        val timeline = timeline(PeriodDocument(ranges = starts.map { PeriodRange(it, it.plus(DatePeriod(days = 4))) }))

        assertEquals(d("2026-06-25"), timeline.firstDate)
        assertEquals(d("2026-10-19"), timeline.lastDate)
        assertEquals(timeline.firstDate, timeline.dates.first())
        assertEquals(timeline.lastDate, timeline.dates.last())
    }
}
