package plus.rua.project

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeriodPredictorTest {
    private val today = LocalDate(2026, 10, 9)

    private fun d(text: String) = LocalDate.parse(text)

    private fun span(
        start: String,
        end: String,
    ) = DateSpan(d(start), d(end))

    /** 每段 5 天的已结束经期。 */
    private fun fiveDayPeriods(vararg starts: String) = PeriodDocument(
        ranges = starts.map { PeriodRange(d(it), d(it).plus(DatePeriod(days = 4))) },
    )

    @Test
    fun forecast_noRanges_returnsNoDataWithDefaults() {
        val forecast = PeriodPredictor.forecast(PeriodDocument(), today)

        assertEquals(PeriodStatus.NoData, forecast.status)
        assertEquals(28, forecast.cycleLength)
        assertEquals(5, forecast.periodLength)
        assertTrue(forecast.usesDefaultCycle)
        assertEquals(emptyList(), forecast.predictedPeriods)
    }

    @Test
    fun forecast_singleRange_usesDefaultCycleAndPredictsThreePeriods() {
        val forecast = PeriodPredictor.forecast(fiveDayPeriods("2026-09-20"), today)

        assertEquals(PeriodStatus.Upcoming(9, d("2026-10-18"), isOvulation = false), forecast.status)
        assertTrue(forecast.usesDefaultCycle)
        assertEquals(PeriodRegularity.INSUFFICIENT, forecast.regularity)
        assertEquals(
            listOf(span("2026-10-18", "2026-10-22"), span("2026-11-15", "2026-11-19"), span("2026-12-13", "2026-12-17")),
            forecast.predictedPeriods,
        )
        assertEquals(listOf(d("2026-10-04"), d("2026-11-01"), d("2026-11-29")), forecast.ovulationDays)
        assertEquals(20f / 28, forecast.cycleProgress)
    }

    @Test
    fun forecast_regularCycles_usesMedianAndPredictsOvulation() {
        val forecast =
            PeriodPredictor.forecast(
                fiveDayPeriods("2026-06-01", "2026-06-29", "2026-07-28", "2026-08-25", "2026-09-23"),
                today,
            )

        assertEquals(29, forecast.cycleLength)
        assertEquals(PeriodRegularity.REGULAR, forecast.regularity)
        assertFalse(forecast.usesDefaultCycle)
        assertEquals(PeriodStatus.Upcoming(13, d("2026-10-22"), isOvulation = false), forecast.status)
        assertTrue(forecast.isOvulation(d("2026-10-08")))
    }

    @Test
    fun forecast_outlierCycle_isExcluded() {
        val forecast =
            PeriodPredictor.forecast(
                fiveDayPeriods("2026-05-01", "2026-05-29", "2026-08-26", "2026-09-23"),
                today,
            )

        assertEquals(28, forecast.cycleLength)
        assertFalse(forecast.usesDefaultCycle)
        assertEquals(PeriodRegularity.INSUFFICIENT, forecast.regularity)
        assertEquals(d("2026-10-21"), (forecast.status as PeriodStatus.Upcoming).nextStart)
    }

    @Test
    fun forecast_irregularCycles_reportsIrregular() {
        val forecast =
            PeriodPredictor.forecast(
                fiveDayPeriods("2026-05-01", "2026-05-25", "2026-07-01", "2026-07-26", "2026-09-01"),
                today,
            )

        assertEquals(PeriodRegularity.IRREGULAR, forecast.regularity)
    }

    @Test
    fun forecast_pastExpectedStart_reportsLateAndShiftsPredictionsFromTomorrow() {
        val forecast = PeriodPredictor.forecast(fiveDayPeriods("2026-09-01"), today)

        assertEquals(PeriodStatus.Late(10, d("2026-09-29")), forecast.status)
        assertEquals(span("2026-10-10", "2026-10-14"), forecast.predictedPeriods.first())
        assertEquals(listOf(d("2026-10-24"), d("2026-11-21")), forecast.ovulationDays)
        assertEquals(1f, forecast.cycleProgress)
    }

    @Test
    fun forecast_ongoingPeriod_reportsDayAndPredictsRemainingDays() {
        val document =
            PeriodDocument(
                ranges = listOf(PeriodRange(d("2026-09-10"), d("2026-09-14")), PeriodRange(d("2026-10-07"), null)),
            )

        val forecast = PeriodPredictor.forecast(document, today)

        assertEquals(PeriodStatus.InPeriod(3, d("2026-10-11")), forecast.status)
        assertEquals(span("2026-10-10", "2026-10-11"), forecast.predictedPeriods.first())
        assertEquals(span("2026-11-04", "2026-11-08"), forecast.predictedPeriods[1])
        assertEquals(d("2026-10-21"), forecast.ovulationDays.first())
    }

    @Test
    fun forecast_ongoingOverTenDays_suggestsForgottenEnd() {
        val forecast = PeriodPredictor.forecast(PeriodDocument(ranges = listOf(PeriodRange(d("2026-09-28"), null))), today)

        assertEquals(PeriodStatus.ForgotToEnd(12, d("2026-09-28")), forecast.status)
    }

    @Test
    fun forecast_ovulationToday_flagsStatus() {
        val document =
            PeriodDocument(
                settings = PeriodSettings(cycleLength = 22),
                ranges = listOf(PeriodRange(d("2026-10-01"), d("2026-10-07"))),
            )

        val forecast = PeriodPredictor.forecast(document, today)

        assertEquals(7, forecast.periodLength)
        assertEquals(PeriodStatus.Upcoming(14, d("2026-10-23"), isOvulation = true), forecast.status)
        assertTrue(forecast.isOvulation(today))
    }

    @Test
    fun median_evenCount_roundsHalfUp() {
        assertEquals(29, PeriodPredictor.median(listOf(29, 28)))
        assertEquals(28, PeriodPredictor.median(listOf(30, 27, 28)))
    }
}
