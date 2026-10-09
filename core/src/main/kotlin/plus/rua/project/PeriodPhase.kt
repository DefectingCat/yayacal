package plus.rua.project

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** 一个周期内的阶段，按时间先后排列。 */
enum class CyclePhase(
    val label: String,
) {
    MENSTRUAL("月经期"),
    FOLLICULAR("卵泡期"),
    OVULATION("排卵日"),
    LUTEAL("黄体期"),
}

/**
 * 一个周期：从经期开始日 [start] 到下次经期开始日 [nextStart] 的前一天。
 *
 * @property periodEnd 本次经期最后一天；进行中或预测时为预计值
 * @property ovulation 下次开始日往前推黄体期天数估算的排卵日；不晚于 [periodEnd] 时为 null，此周期没有卵泡期
 * @property recorded 本次经期来自记录而非预测
 * @property periodEndKnown 本次经期已记录结束日
 */
data class CycleSpan(
    val start: LocalDate,
    val periodEnd: LocalDate,
    val nextStart: LocalDate,
    val ovulation: LocalDate?,
    val recorded: Boolean,
    val periodEndKnown: Boolean,
) {
    val length: Int get() = start.daysUntil(nextStart)

    /** 本次经期天数。 */
    val periodDays: Int get() = start.daysUntil(periodEnd) + 1

    /** [date] 所处的阶段；调用方保证 [date] 落在本周期内。 */
    fun phaseOf(date: LocalDate): CyclePhase = when {
        date <= periodEnd -> CyclePhase.MENSTRUAL
        ovulation == null || date > ovulation -> CyclePhase.LUTEAL
        date == ovulation -> CyclePhase.OVULATION
        else -> CyclePhase.FOLLICULAR
    }
}

/** 某一天的周期解读，供「今日周期」页展示。 */
sealed interface PeriodDayInfo {
    val date: LocalDate

    /** 还没有任何经期记录。 */
    data class NoRecord(
        override val date: LocalDate,
    ) : PeriodDayInfo

    /** [date] 是 [cycle] 中 [phase] 的第 [phaseDay] 天。 */
    data class InPhase(
        override val date: LocalDate,
        val cycle: CycleSpan,
        val phase: CyclePhase,
        val phaseDay: Int,
    ) : PeriodDayInfo {
        val daysUntilNext: Int get() = date.daysUntil(cycle.nextStart)
    }

    /** 已过 [cycle] 预计的下次开始日仍没有记录经期；[days] 为比预计晚了几天，预计当天为 0。 */
    data class Late(
        override val date: LocalDate,
        val cycle: CycleSpan,
        val days: Int,
    ) : PeriodDayInfo
}

/**
 * 记录与预测串成的连续周期，以及「今日周期」页可浏览的日期范围 [firstDate]..[lastDate]。
 *
 * 推迟时，最后一次记录的周期止于原预计开始日，之后到今天是推迟的空档，预测周期从明天开始。
 */
data class PeriodTimeline(
    val cycles: List<CycleSpan>,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
) {
    val dates: List<LocalDate> =
        generateSequence(firstDate) { it.plus(DatePeriod(days = 1)) }
            .takeWhile { it <= lastDate }
            .toList()

    fun dayInfo(date: LocalDate): PeriodDayInfo {
        val cycle = cycles.lastOrNull { it.start <= date } ?: return PeriodDayInfo.NoRecord(date)
        if (date >= cycle.nextStart) return PeriodDayInfo.Late(date, cycle, cycle.nextStart.daysUntil(date))
        val phase = cycle.phaseOf(date)
        val phaseDay =
            when (phase) {
                CyclePhase.MENSTRUAL -> cycle.start.daysUntil(date) + 1
                CyclePhase.FOLLICULAR -> cycle.periodEnd.daysUntil(date)
                CyclePhase.OVULATION -> 1
                CyclePhase.LUTEAL -> (cycle.ovulation ?: cycle.periodEnd).daysUntil(date)
            }
        return PeriodDayInfo.InPhase(date, cycle, phase, phaseDay)
    }

    companion object {
        /** 往前可浏览的周期数，不含当前周期。 */
        const val HISTORY_CYCLES = 3

        /** 由文档和同一 [today] 算出的 [forecast] 构建，纯函数。 */
        fun build(
            document: PeriodDocument,
            forecast: PeriodForecast,
            today: LocalDate,
        ): PeriodTimeline {
            val ranges = document.ranges.sortedBy { it.start }
            val last = ranges.lastOrNull() ?: return PeriodTimeline(emptyList(), today, today)
            val periodLength = forecast.periodLength
            val lastEnd = last.end ?: maxOf(last.start.plusDays(periodLength - 1), today)
            // 进行中的经期比预测周期还长（多半忘记点结束）时，落在经期内的预测开始日没有意义
            val predictedStarts = forecast.predictedStarts.filter { it > lastEnd }
            val lateStart = (forecast.status as? PeriodStatus.Late)?.expectedStart

            val starts = ranges.map { it.start } + predictedStarts
            val cycles =
                starts.mapIndexed { index, start ->
                    val range = ranges.getOrNull(index)
                    val periodEnd =
                        when {
                            range == null -> start.plusDays(periodLength - 1)
                            range.end != null -> range.end
                            else -> lastEnd
                        }
                    val next =
                        when {
                            index < ranges.lastIndex -> ranges[index + 1].start
                            index == ranges.lastIndex && lateStart != null -> lateStart
                            else -> starts.getOrNull(index + 1) ?: start.plusDays(forecast.cycleLength)
                        }
                    val nextStart = maxOf(next, periodEnd.plusDays(1))
                    val ovulation = nextStart.minus(DatePeriod(days = document.settings.lutealLength))
                    CycleSpan(
                        start = start,
                        periodEnd = periodEnd,
                        nextStart = nextStart,
                        ovulation = ovulation.takeIf { it > periodEnd },
                        recorded = range != null,
                        periodEndKnown = range?.end != null,
                    )
                }

            val current = cycles.indexOfLast { it.start <= today }.coerceAtLeast(0)
            val firstDate = minOf(cycles[(current - HISTORY_CYCLES).coerceAtLeast(0)].start, today)
            val nextPredicted = cycles.firstOrNull { !it.recorded && it.periodEnd >= today }
            val lastDate = maxOf(nextPredicted?.periodEnd ?: today, today)
            return PeriodTimeline(cycles, firstDate, lastDate)
        }

        private fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))
    }
}
