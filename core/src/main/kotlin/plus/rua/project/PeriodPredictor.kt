package plus.rua.project

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** 闭区间日期段。 */
data class DateSpan(
    val start: LocalDate,
    val end: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = date >= start && date <= end
}

enum class PeriodRegularity(
    val label: String,
) {
    INSUFFICIENT("记录不足"),
    REGULAR("规律"),
    FAIRLY_REGULAR("较规律"),
    IRREGULAR("不规律"),
}

/** 首页状态卡展示的当前状态。 */
sealed interface PeriodStatus {
    data object NoData : PeriodStatus

    /** 经期第 [day] 天，[expectedEnd] 不早于今天。 */
    data class InPeriod(
        val day: Int,
        val expectedEnd: LocalDate,
    ) : PeriodStatus

    /** 进行中的经期已超过 [PeriodPredictor.FORGOT_TO_END_DAYS] 天，可能忘记点结束。 */
    data class ForgotToEnd(
        val day: Int,
        val start: LocalDate,
    ) : PeriodStatus

    data class Upcoming(
        val daysUntil: Int,
        val nextStart: LocalDate,
        val isOvulation: Boolean,
    ) : PeriodStatus

    data class Late(
        val days: Int,
        val expectedStart: LocalDate,
    ) : PeriodStatus
}

/**
 * 预测结果。[cycleLength]、[periodLength] 是近期记录的中位数，记录不足时取设置默认值。
 *
 * @property usesDefaultCycle 完整周期不足 2 个，周期长度来自设置
 * @property cycleProgress 当前周期进度 0..1，用于状态卡环形进度
 * @property predictedStarts 之后几次经期的预测开始日，推迟时从明天起顺延
 */
data class PeriodForecast(
    val status: PeriodStatus,
    val cycleLength: Int,
    val periodLength: Int,
    val regularity: PeriodRegularity,
    val usesDefaultCycle: Boolean,
    val cycleProgress: Float,
    val predictedPeriods: List<DateSpan>,
    val predictedStarts: List<LocalDate>,
    val ovulationDays: List<LocalDate>,
) {
    fun isPredictedPeriod(date: LocalDate): Boolean = predictedPeriods.any { date in it }

    fun isOvulation(date: LocalDate): Boolean = date in ovulationDays
}

/** 基于历史记录的经期预测，纯函数，`today` 由调用方注入。 */
object PeriodPredictor {
    const val HISTORY_SIZE = 6
    const val FORGOT_TO_END_DAYS = 10
    const val PREDICTED_CYCLES = 3
    private val VALID_CYCLE = 15..60

    fun forecast(
        document: PeriodDocument,
        today: LocalDate,
    ): PeriodForecast {
        val settings = document.settings
        val ranges = document.ranges.sortedBy { it.start }
        // 超出合理范围的周期多半是漏记，不参与统计
        val cycles =
            ranges
                .zipWithNext { a, b -> a.start.daysUntil(b.start) }
                .takeLast(HISTORY_SIZE)
                .filter { it in VALID_CYCLE }
        val usesDefaultCycle = cycles.size < 2
        val cycleLength = if (usesDefaultCycle) settings.cycleLength else median(cycles)
        val periods = ranges.mapNotNull { range -> range.end?.let { range.start.daysUntil(it) + 1 } }.takeLast(HISTORY_SIZE)
        val periodLength = if (periods.isEmpty()) settings.periodLength else median(periods).coerceIn(PeriodSettings.PERIOD_RANGE)
        val regularity =
            when {
                cycles.size < 3 -> PeriodRegularity.INSUFFICIENT
                cycles.max() - cycles.min() <= 3 -> PeriodRegularity.REGULAR
                cycles.max() - cycles.min() <= 7 -> PeriodRegularity.FAIRLY_REGULAR
                else -> PeriodRegularity.IRREGULAR
            }

        val last =
            ranges.lastOrNull() ?: return PeriodForecast(
                status = PeriodStatus.NoData,
                cycleLength = cycleLength,
                periodLength = periodLength,
                regularity = regularity,
                usesDefaultCycle = usesDefaultCycle,
                cycleProgress = 0f,
                predictedPeriods = emptyList(),
                predictedStarts = emptyList(),
                ovulationDays = emptyList(),
            )

        val dayOfCycle = (last.start.daysUntil(today) + 1).coerceAtLeast(1)
        val expectedStart = last.start.plusDays(cycleLength)
        val late = !last.isOngoing && today > expectedStart
        val expectedEnd = maxOf(last.start.plusDays(periodLength - 1), today)
        val lastEnd = last.end ?: expectedEnd

        val predicted = mutableListOf<DateSpan>()
        if (last.isOngoing) {
            val tailStart = maxOf(today.plusDays(1), last.start)
            val tailEnd = last.start.plusDays(periodLength - 1)
            if (tailStart <= tailEnd) predicted += DateSpan(tailStart, tailEnd)
        }
        // 推迟时已过期的预测不再显示，后续预测从明天顺延
        val firstStart = if (late) today.plusDays(1) else expectedStart
        val starts = (0 until PREDICTED_CYCLES).map { firstStart.plusDays(it * cycleLength) }
        val upcoming = starts.map { DateSpan(it, it.plusDays(periodLength - 1)) }
        predicted += upcoming

        val ovulationDays = mutableListOf<LocalDate>()
        starts.forEachIndexed { index, start ->
            if (late && index == 0) return@forEachIndexed
            val previousEnd = if (index == 0) lastEnd else upcoming[index - 1].end
            val ovulation = start.minus(DatePeriod(days = settings.lutealLength))
            if (ovulation <= previousEnd) return@forEachIndexed
            ovulationDays += ovulation
        }

        val status =
            when {
                last.isOngoing && dayOfCycle > FORGOT_TO_END_DAYS -> {
                    PeriodStatus.ForgotToEnd(dayOfCycle, last.start)
                }

                last.isOngoing -> {
                    PeriodStatus.InPeriod(dayOfCycle, expectedEnd)
                }

                late -> {
                    PeriodStatus.Late(expectedStart.daysUntil(today), expectedStart)
                }

                else -> {
                    PeriodStatus.Upcoming(
                        daysUntil = today.daysUntil(expectedStart),
                        nextStart = expectedStart,
                        isOvulation = today in ovulationDays,
                    )
                }
            }
        return PeriodForecast(
            status = status,
            cycleLength = cycleLength,
            periodLength = periodLength,
            regularity = regularity,
            usesDefaultCycle = usesDefaultCycle,
            cycleProgress = if (late) 1f else (dayOfCycle.toFloat() / cycleLength).coerceIn(0f, 1f),
            predictedPeriods = predicted,
            predictedStarts = starts,
            ovulationDays = ovulationDays,
        )
    }

    /** 偶数个时取中间两个的平均并四舍五入。 */
    internal fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle] + 1) / 2
    }

    private fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))
}

/** 一个完整周期：从 [start] 开始，到下一次经期开始前共 [days] 天。 */
data class CycleLength(
    val start: LocalDate,
    val days: Int,
)

/**
 * 最近 [limit] 个完整周期的长度，按时间升序，供历史页趋势图使用。
 * 与预测不同，这里如实列出所有周期（包括疑似漏记的异常值），让用户看到真实记录。
 */
fun PeriodDocument.recentCycleLengths(limit: Int = 12): List<CycleLength> = ranges
    .sortedBy { it.start }
    .zipWithNext { current, next -> CycleLength(current.start, current.start.daysUntil(next.start)) }
    .takeLast(limit)
